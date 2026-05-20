# Review: Enemy/Projectile Tracking & Anti-Cheat

Files reviewed (all under `src/main/java/com/imshy/bedwars/runtime/`):

- `EnemyTrackingService.java`
- `TrackedEnemy.java`
- `ProjectileTrackingService.java`
- `TrackedProjectile.java`
- `FireballTrackingService.java`
- `TrackedFireball.java`
- `EnderPearlPredictionService.java`
- `AntiCheatService.java`

Cross-referenced: `BedwarsRuntime.java` (service wiring + lifecycle), `ModConfig.java` (toggles).

## Summary

The projectile / fireball / ender-pearl physics is **correct** — the integration loop in `ProjectileTrackingService.integrateArcFrom` faithfully reproduces vanilla 1.8.9 `EntityThrowable` per-tick order (raytrace → move → `motion *= 0.99` → `motionY -= 0.03`), and the fireball closest-point-of-approach math is unit-consistent. The tracking services (enemy, projectile, fireball) are all driven on the main client/render thread with bounded, per-game-cleared state, and are largely sound.

The serious problems are concentrated in `AntiCheatService`. Its packet handler runs on the **netty I/O thread** but reaches directly into single-threaded Minecraft client APIs (`World.getEntityByID`, `EntityOtherPlayerMP.getHeldItem`, `EntityPlayerSP.addChatMessage`) and mutates plain `HashMap`s that the main thread also mutates. This is the headline issue: it is a genuine cross-thread data race that can corrupt the maps and can read torn/half-updated entity state. There is also an unbounded-growth path in the anti-cheat state maps when the CPS module is disabled, and the anti-cheat service is never reset between games while the other tracking services are.

Severity tally: **1 high, 3 med, 5 low.**

## Findings

### [HIGH] Anti-cheat packet handler touches single-threaded client state and plain HashMaps from the netty thread

**Location:** `AntiCheatService.java` — `InboundHandler.channelRead` (175–207); `handleAnimation` (213–250); `recordSwing` (261–288); `sendLocalFlag` (479–500); fields `lastFlagAt`/`scaffoldStates`/`autoBlockStates` declared as plain `HashMap` (84–86); `pruneStateMaps` (462–473).

**Issue:** `channelRead` runs on the netty I/O thread (a different thread from the main client/render thread). From there:

- `handleAnimation` calls `mc.theWorld.getEntityByID(...)` (222), `player.getHeldItem()` (227), and `player.isUsingItem()` (231) — none of these are thread-safe; the entity list and entity field state are read/written by the main thread every tick.
- `recordSwing` calls `mc.theWorld.getEntityByID(...)` (269) on the netty thread as well.
- `handleAnimation` then reads/writes `autoBlockStates` (a plain `HashMap`, 238–248) on the netty thread, while `pruneStateMaps` does `autoBlockStates.keySet().retainAll(live)` (470) on the main thread.
- `sendLocalFlag` reads/writes `lastFlagAt` (a plain `HashMap`, 484/489) on the netty thread, while `pruneStateMaps` does `lastFlagAt.keySet().retainAll(live)` (471) on the main thread, and then calls `mc.thePlayer.addChatMessage(...)` (499) — which mutates the GUI chat list that the render thread reads — from the netty thread.

**Impact:** Concurrent mutation of a non-thread-safe `HashMap` can corrupt its internal structure on a resize (lost entries, or a spin in `HashMap` that pins a CPU). Reading entity state mid-tick can observe torn position/item values. `addChatMessage` from off-thread can race the chat GUI render. The `try/catch (Throwable)` wrappers in `channelRead` (180–204) prevent a single exception from killing the pipeline, but they do **not** prevent map corruption, torn reads, or the chat-list race — those are silent and intermittent. (`swingTimes` is correctly a `ConcurrentHashMap` with per-deque `synchronized` blocks, so it is the one piece of state that is safe.)

**Suggested fix:** Do the bare minimum on the netty thread: read the immutable packet fields (`getEntityID`, `getAnimationType`, block-change positions) and push a small record onto a thread-safe queue (the codebase already uses `ConcurrentLinkedQueue` for `pendingPlacements`). Drain that queue at the top of `onClientTick` on the main thread, and perform all `getEntityByID`/`getHeldItem`/`isUsingItem` lookups, all `autoBlockStates`/`lastFlagAt` mutation, and all `addChatMessage` calls there. This collapses every netty-thread access into the existing main-thread tick and removes the data races and the unsafe-API calls in one move.

### [MED] Anti-cheat state maps grow unbounded when the CPS module is disabled

**Location:** `AntiCheatService.java` — `onClientTick` (100–116), prune trigger at 113 (`swingTimes.size() > CPS_MAP_PRUNE_THRESHOLD`); `recordSwing` only invoked under `ModConfig.isAntiCheatCpsEnabled()` (182–184); `pruneStateMaps` (462–473).

**Issue:** The only call to `pruneStateMaps` outside `shutdown()` is gated on `swingTimes.size() > 256` (line 113). But `swingTimes` is only ever populated by `recordSwing`, which only runs when the CPS module is enabled (181–184). If a user runs with CPS disabled but Scaffold or AutoBlock enabled, `swingTimes` stays empty, the prune trigger never fires, and `scaffoldStates`, `autoBlockStates`, and `lastFlagAt` accumulate one entry per distinct player observed for the entire session — and (per the next finding) across games too.

**Impact:** Slow, unbounded memory growth keyed by player UUID over a long play session when CPS is off. Bounded in practice by total distinct players seen, but on a busy lobby-hopping session that is unbounded for the process lifetime.

**Suggested fix:** Drive `pruneStateMaps` off a size check over the union of all state maps (or just call it unconditionally every N ticks), not off `swingTimes.size()` alone. A simple tick counter modulo (e.g. every 200 ticks) is enough.

### [MED] Anti-cheat state is never cleared on game/world transitions

**Location:** `AntiCheatService.java` has no `clearAll()` — only `shutdown()` (118–126), called at mod teardown. `BedwarsRuntime.java` clears the other trackers at every game transition (`enemyTrackingService`/`fireballTrackingService`/`projectileTrackingService.clearAll()` at 130–132, 262–264, 293–295, 339–341) but never touches `antiCheatService`.

**Issue:** `lastFlagAt` cooldowns, `scaffoldStates`, and `autoBlockStates` survive across games and lobby returns. The netty handler does self-reinstall on connection swap (`ensureHandlerInstalled`, 143–154) and self-uninstall when the world is gone (`onClientTick`, 104–107), so the handler lifecycle is fine — but the accumulated detection state is not reset.

**Impact:** Stale cross-game state: a flag cooldown started in one game suppresses a legitimate flag at the start of the next; scaffold/autoblock partial-progress counters carry stale `firstHit` timestamps and tick counters into a new match (mostly self-healing via the window checks, but the flag cooldown is a real correctness issue). Combined with the previous finding, it is also the main contributor to unbounded growth.

**Suggested fix:** Add an `AntiCheatService.clearAll()` that clears `scaffoldStates`, `autoBlockStates`, `lastFlagAt`, `pendingPlacements`, and `recentPlacements` (but keeps the installed handler), and call it alongside the other `clearAll()` calls at the four `BedwarsRuntime` transition points.

### [MED] `enemyTrackingService.trackedEnemies` is keyed by player name and never shrinks except on full clear

**Location:** `EnemyTrackingService.java` — `getOrCreateTracked` (160–167), `state.trackedEnemies` keyed by name; `handleDeathMessage` calls `tracked.clear()` on the entry but leaves the map entry in place (≈142); `clearAll` (147–150) is the only path that empties the map.

**Issue:** Entries are added per distinct enemy *name* and only removed when the whole map is cleared at a game transition. Within a single long game the map only grows. Keying by name (rather than UUID) also means a name change or a nicked player produces a separate entry, and there is no per-entry expiry/staleness eviction.

**Impact:** Bounded by distinct names seen in one game (small for Bedwars — at most a few teams), so memory impact is minor; the more practical concern is stale entries for dead/disconnected players lingering with old positions until the next game clear. Low memory risk, medium correctness/clarity.

**Suggested fix:** Prefer keying by UUID where available, and add a last-seen timestamp with an eviction pass (the projectile/fireball trackers already do exactly this with their `seenThisTick` prune — mirror that pattern).

### [LOW] `loadedEntityList` iteration is a concurrent-modification risk surface (main-thread, low likelihood)

**Location:** `ProjectileTrackingService.scanProjectiles` (69, `for (Entity entity : mc.theWorld.loadedEntityList)`); `FireballTrackingService.scanFireballs` (54, same).

**Issue:** `World.loadedEntityList` is a plain `ArrayList`. Iterating it with an enhanced-for while anything mutates it would throw `ConcurrentModificationException`. Both scans run on the main client tick, the same thread that mutates the list, so under normal flow this will not throw — flagged as a risk surface, not a live bug.

**Impact:** Low. Would only manifest if a mod or callback invoked during the scan added/removed an entity, which this code does not do.

**Suggested fix:** None required. If extra safety is wanted, snapshot to a local list before iterating, or catch and skip the rare CME.

### [LOW] Repeated allocation in per-tick / per-sighting hot paths

**Location:** `EnemyTrackingService.scanItemPickups` allocates a fresh `HashMap` every call (≈34); `TrackedEnemy.addHotbarItem` calls `Item.itemRegistry.getNameForObject(...).toString()` per recorded item; `ProjectileTrackingService.integrateArcFrom` allocates a `Vec3` start and end plus `TrackedProjectile.Point` objects each step (177–185, 199); `EnderPearlPredictionService.predict` and the scan paths run these every tick.

**Issue:** These run on the client tick / render path and churn short-lived objects. For the pearl/fireball arcs the per-step `Vec3`/`Point` allocations are multiplied by `MAX_INTEGRATION_STEPS` (200) per tracked projectile per tick.

**Impact:** Low — modern GC handles short-lived garbage well and the entity counts are small — but it is avoidable churn on a hot path.

**Suggested fix:** Reuse scratch buffers / mutable point holders where practical, and avoid rebuilding the name-lookup string on every `addHotbarItem`. Not urgent.

### [LOW] Ender-pearl prediction reuses a single shared `cached` instance

**Location:** `EnderPearlPredictionService.java` — single `private final TrackedProjectile cached` (28) returned from `predict` (85).

**Issue:** `predict` mutates and returns the same `TrackedProjectile` object every call (including its `arcPoints` list). This is fine for the current single-consumer, same-thread render usage, but the returned object is shared mutable state — a second consumer or any retained reference would see it overwritten on the next tick.

**Impact:** Low given current usage (one render consumer on the main thread). A latent footgun if the prediction is ever consumed elsewhere.

**Suggested fix:** Document that the returned instance is transient/reused (the class doc partially does), or return a defensive copy if any caller retains it.

### [LOW] `MAX_INTEGRATION_STEPS` cap can yield a non-landing "landing" point

**Location:** `ProjectileTrackingService.integrateArcFrom` (172–206); on loop exhaustion it sets `landingX/Y/Z` to the last integrated position (203–206) but leaves `landingValid` false.

**Issue:** When the 200-step cap is hit without a raytrace hit (very long/steep throw), the terminal `landing*` is set to the last simulated point while `landingValid` stays `false`. Downstream `scanProjectiles` computes `landingPlayerDistance`/`landingBedDistance` from `landing*` regardless of `landingValid` (106–118), so a capped arc still produces threat distances from a point that is not an actual landing.

**Impact:** Low — only affects extreme arcs that exceed ~10 s of flight, rare for pearls in Bedwars. Could produce a spurious or missing proximity alert in that edge case.

**Suggested fix:** Have `scanProjectiles` skip threat evaluation (or treat distance as "unknown") when `tp.landingValid` is false, rather than trusting the capped terminal point.

### [LOW] AutoBlock / scaffold heuristics are tuned for false-negative safety but lack a confidence/decay model

**Location:** `AntiCheatService.java` — AutoBlock threshold `AUTOBLOCK_MIN_HITS = 2` within `AUTOBLOCK_WINDOW_MS = 4000` (245, window reset 239–242); scaffold heuristics gated on `placedNearby` with `PLACEMENT_PROXIMITY_SQ = 25` and `PLACEMENT_RECENCY_MS = 2500` (`hasRecentPlacementNear`, 447–459).

**Issue:** The detectors are conservative (require recent nearby placement + multiple confirmations), which is good for false-positive avoidance. But there is no decay/confidence accumulation: a flag fires on a hard threshold and the counter resets, so detections are binary and the flag-cooldown (per-player, `lastFlagAt`) is the only smoothing. The 5×5×... proximity radius (`PLACEMENT_PROXIMITY_SQ = 25` ⇒ 5-block radius) is generous and may attribute another player's nearby block placements to the observed player in a crowded bridge fight.

**Impact:** Low — this is a tuning/maintainability observation, not a correctness bug. Possible occasional misattribution of placements between two players bridging side-by-side.

**Suggested fix:** Consider attributing placements to the placing player (if the packet/source allows) rather than a proximity radius, and/or a decaying suspicion score instead of hard hit thresholds. Not required for correctness.

## Improvements

- **Centralize anti-cheat state mutation on the main thread** (see High). Once the netty handler only enqueues raw packet data, every state map can stay a plain `HashMap` safely and the `addChatMessage` call becomes correct.
- **Add `AntiCheatService.clearAll()`** and wire it into the four `BedwarsRuntime` transition points next to the other trackers, so anti-cheat state has the same per-game lifecycle as enemy/projectile/fireball tracking.
- **Unify the tracking-map eviction pattern.** The projectile and fireball trackers already prune via `seenThisTick`; apply the same last-seen eviction to `EnemyTrackingService.trackedEnemies` and drive `AntiCheatService.pruneStateMaps` off a tick counter rather than `swingTimes.size()`.
- **Key tracking by UUID, not name**, in `EnemyTrackingService` to survive nick/name changes and align with the anti-cheat maps.
- **Respect `landingValid`** in projectile threat evaluation so capped arcs don't generate distances from a non-landing point.
- The shared physics integration in `ProjectileTrackingService.integrateArcFrom` (reused by both the enemy-pearl tracker and the local pre-throw preview) is a clean abstraction and the constants (`GRAVITY = 0.03`, `DRAG = 0.99`) match vanilla 1.8.9 — no change needed there.

REPORT: docs/review/06-tracking-anticheat.md — 1 high, 3 med, 5 low
