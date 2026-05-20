# Review: World Scan, Generators & Beds

Files:
- src/main/java/com/imshy/bedwars/runtime/WorldScanService.java
- src/main/java/com/imshy/bedwars/runtime/GeneratorTierSchedule.java
- src/main/java/com/imshy/bedwars/BedLocator.java
- src/main/java/com/imshy/bedwars/MapMetadataRegistry.java

## Summary

The most serious problem in this unit is the generator scan: `scanForGenerators`
runs a fixed cubic block scan every ~1 second whose default cost is on the order
of 1.6 million `getBlockState`/`isBlockLoaded` calls per scan (up to ~3.3 million
at the configured maximum range), and each generator block it finds then triggers
two more full `loadedEntityList` iterations plus two cluster BFS traversals. On a
busy Bedwars map this is a large, recurring main-thread cost that will cause
visible stutter. There is also a geometry mismatch between the cubic scan and the
spherical (3D `distanceSq`) eviction test that causes corner generators to flap
in and out of the tracked map on every scan.

The autoplay requeue path is functional but uses a raw `Thread` with
`Thread.sleep`, has no rate limiting on the chat commands it fires (relying only
on a single re-entry guard), and silently drops concurrent requeue requests.
`checkForInvisiblePlayers` and several scan helpers assume non-null
`mc.theWorld`/`mc.thePlayer` and the invisible-warning map is never pruned.

`GeneratorTierSchedule` is essentially clean. `BedLocator` is correct but only
ever attaches a single adjacent bed block and does a full cubic scan.
`MapMetadataRegistry` has no duplicate keys or off-by-one issues, but the rush
table is incomplete relative to the live map pool.

Counts: 2 High, 5 Medium, 6 Low.

## Findings

### [HIGH] Per-second generator scan is a multi-million-block main-thread loop
- Location: WorldScanService.java:198-242 (loop), with cost driven by ModConfig.java:65 / 464-470
- Issue: `scanForGenerators` runs `for x in [-scanRange,scanRange], y in [-20,20], z in [-scanRange,scanRange]`. `generatorScanRange` defaults to 100 and is configurable 50–200 (ModConfig.java:464-470). At the default that is `201 * 41 * 201 ≈ 1.66M` iterations; at max range `401 * 41 * 401 ≈ 6.6M`. Each iteration calls `isBlockLoaded`, `isAirBlock(checkPos.up())`, and on a hit `getBlockState`. This whole scan runs every `GENERATOR_SCAN_INTERVAL = 1000ms` (RuntimeState.java:15) on the client (main) thread (BedwarsRuntime.java:637-642).
- Impact: Sustained, large per-second CPU cost on the render/client thread. On real maps this is a primary stutter source whenever the generator HUD is enabled. The cost scales cubically with the user-tunable range with no warning.
- Suggested fix: Don't brute-force a cube every second. Generators sit at known/limited Y bands — restrict Y to a small window around the player or the known pad height (e.g. ±6), and/or cache the discovered generator block positions after the first full scan and only re-scan resource counts (which is the cheap part) on the interval, doing a full re-discovery far less frequently (e.g. every 10–15 s). Also clamp the effective range used for the scan independently of the render distance.

### [HIGH] Per-hit work multiplies the scan cost: 2 entity-list passes + 2 BFS per generator block
- Location: WorldScanService.java:224 (`isCollapsedClusterMember`), 236 (`scanGeneratorResources`), 238 (`pickGeneratorLabelPosition`)
- Issue: For every diamond/emerald block found in the cube, the code calls:
  1. `isCollapsedClusterMember` → `findGeneratorCluster` BFS (up to 32 nodes, each with a `getBlockState`),
  2. `scanGeneratorResources` → full iteration of `mc.theWorld.loadedEntityList`,
  3. `pickGeneratorLabelPosition` → another `findGeneratorCluster` BFS plus another full `loadedEntityList` iteration.
  A standard Bedwars map exposes many diamond/emerald blocks (each team island plus center), so this fires several times per scan, and `loadedEntityList` on a populated map is large. The two BFS calls in (1) and (3) recompute the identical cluster for the same anchor.
- Impact: The constant factor on top of the already-huge cube loop is severe; the entity-list passes are O(entities) repeated per generator block per second.
- Suggested fix: Compute the cluster once per anchor and pass it to both `isCollapsedClusterMember` and `pickGeneratorLabelPosition`. Iterate `loadedEntityList` once per scan, bucketing nearby diamond/emerald `EntityItem`s by region, and let each generator query its bucket instead of re-scanning all entities. Run label-position selection only when the cluster has >1 air-topped candidate (cheap pre-check already exists at line 478 but only after the BFS).

### [MEDIUM] Cube scan vs. 3D-sphere eviction mismatch causes generators to flap
- Location: WorldScanService.java:204-206 (cubic scan) and 250 (`playerPos.distanceSq(generatorPos) > scanRange * scanRange`)
- Issue: The scan collects blocks in a cube of half-width `scanRange` (X/Z) and ±20 (Y), but the eviction test uses 3D Euclidean `distanceSq` against `scanRange²`. A generator near a horizontal corner of the cube has `distanceSq ≈ dx²+dz²` up to `2*scanRange²`, which exceeds `scanRange²`, so it is removed at line 251 — then re-discovered on the next scan, re-added, re-evicted, repeatedly. The Y component (±20) also feeds the 3D distance even though the scan box is asymmetric in Y.
- Impact: Generators in the outer ring of the scan box continuously thrash between tracked and untracked, losing their accumulated prediction baseline/interval estimate every cycle and wasting the per-hit work above on them. The HUD entry for such generators flickers.
- Suggested fix: Make the keep/evict test match the scan shape. Compare horizontal distance only (`dx²+dz² > scanRange²`) or, more simply, use a Chebyshev/box test: keep if `|dx| <= scanRange && |dz| <= scanRange && |dy| <= 20`.

### [MEDIUM] Autoplay requeue: raw thread, no command rate limiting, silent drop of concurrent triggers
- Location: WorldScanService.java:119-166
- Issue: (a) `requeueAutoplay` spawns a bare `new Thread(...)` per call with `Thread.sleep(1500/500/1500)` and sends `/l`, `/p warp` (twice), and the play command. The only guard against repeats is `now - state.lastRequeueTime < SPAM_RETRY_DELAY` (7000ms) at line 125. Multiple distinct requeue triggers fire `requeueAutoplay` (BedwarsRuntime.java:881, 930, 1032; WorldScanService.java:409) — within the 7s window all but the first are silently dropped, but if two calls land in the same millisecond before `lastRequeueTime` is written there is a small race that can start two requeue threads. (b) The inner field `Minecraft mc` at line 143 shadows the parameter; harmless but confusing. (c) The thread re-checks `mc.thePlayer == null || !state.autoplayEnabled` between sleeps but can still send a `/play` command into a lobby/menu the user manually navigated to during the 3.5s window.
- Impact: Potential double-requeue race; chat-command spam if `SPAM_RETRY_DELAY` is ever shortened; commands can fire after the user has manually intervened. Unhandled `InterruptedException` only does `printStackTrace()`.
- Suggested fix: Use a single shared scheduler (or reuse `HypixelAPI`'s executor) instead of spawning threads; set `lastRequeueTime` and an `inFlightRequeue` flag atomically before sending anything and clear it when the sequence completes; gate the final `/play` send on `state.gamePhase == GamePhase.IDLE` still being true. Remove the shadowed `mc`.

### [MEDIUM] `checkForInvisiblePlayers` dereferences `mc.theWorld`/`mc.thePlayer` without null checks
- Location: WorldScanService.java:168-196 (esp. 172-173)
- Issue: The method immediately iterates `mc.theWorld.playerEntities` and calls `mc.thePlayer.getUniqueID()`. It relies entirely on the caller (BedwarsRuntime.java:601-603, 633-635) having checked non-null. That is currently true, but the method is `public` and has no internal guard, unlike its sibling `performAutoplayCheck` which does null-check at 368-370.
- Impact: NPE risk if invoked from any path that hasn't pre-checked (e.g. during world unload/teardown, or future callers). On a Forge client world transitions can null `theWorld`/`thePlayer` between the caller's check and use, though within one tick this is unlikely.
- Suggested fix: Add `if (mc == null || mc.theWorld == null || mc.thePlayer == null) return;` at the top, mirroring `performAutoplayCheck`.

### [MEDIUM] `invisiblePlayerWarnings` map grows unbounded
- Location: WorldScanService.java:183-191; field RuntimeState.java:61; only cleared in RuntimeState reset (line 142)
- Issue: Every distinct invisible player name encountered is stored permanently in `state.invisiblePlayerWarnings` and only cleared on full state reset. Across a long session of many games this accumulates one entry per unique name ever seen.
- Impact: Slow unbounded memory growth and a growing map looked up each tick. Minor, but it never self-prunes per game.
- Suggested fix: Prune entries older than the cooldown during iteration, or clear/scope the map at game end alongside the existing reset points.

### [LOW] `BedLocator.locateNearestBed` attaches at most one adjacent bed block
- Location: BedLocator.java:64-70
- Issue: After finding the nearest bed block it loops the four horizontal facings and `break`s on the first adjacent `Blocks.bed`. A real bed is exactly two blocks (head+foot), so one adjacent is correct in normal cases — but if the nearest block found is itself a foot adjacent to another bed's head (beds placed back-to-back on some maps), it can pair the wrong two blocks. Also the full cube scan (lines 40-55) is O(range³) like the generator scan, though it is called far less often.
- Impact: Edge-case mispairing of bed blocks; the second connected block selection is "first found" rather than "the matching bed half." Low because beds are rarely placed adjacent.
- Suggested fix: Determine the bed orientation from the block state metadata (head/foot direction) and offset to the matching half rather than scanning all four neighbours; or at minimum prefer a neighbour whose facing aligns with the bed's orientation.

### [LOW] `secondsUntilNextSpawn` returns a full interval exactly at tier boundaries / start
- Location: GeneratorTierSchedule.java:55-83
- Issue: At `elapsedSeconds == 0`, `elapsedInTier = 0`, `remainder = 0`, `untilNext = interval`, so it reports the full 30 s. At each tier boundary (60 s, 180 s) it likewise resets to the full new interval. This matches the documented "cycle restarts at the boundary" intent (lines 50-54), but it does not model that Hypixel typically also drops a resource at the tier-up moment, so the displayed ETA can be up to one full interval pessimistic right after a tier change.
- Impact: Cosmetic ETA inaccuracy of up to one interval immediately after 0/60/180 s. Not a crash.
- Suggested fix: If matching real Hypixel behaviour matters, treat the tier boundary as a spawn instant and compute the next spawn from `interval` after the boundary; otherwise document the approximation as intended.

### [LOW] `predictGeneratorCountWhenUnobserved` can over-count after long occlusion
- Location: WorldScanService.java:309-336
- Issue: When a generator goes unobserved, the predictor adds `generated = elapsed / estimatedGenerationIntervalMs` resources with no cap. If the player wanders far for a long time, this can predict an arbitrarily large count (real generators are picked up by enemies, so the true count plateaus). `estimatedGenerationIntervalMs` is an EMA (line 286) so a single noisy sample can skew predictions.
- Impact: HUD resource counts for distant/occluded generators can drift far above reality. Display-only.
- Suggested fix: Cap predicted `resourceCount` at a realistic ceiling, and decay/discard predictions after a staleness threshold rather than extrapolating indefinitely.

### [LOW] `MapMetadataRegistry` rush table is incomplete and silently defaults
- Location: MapMetadataRegistry.java:22-40, 87-95
- Issue: Only 13 maps are listed; the live Bedwars rotation has many more (e.g. Amazon, Glacier, Aquarium, Eastwood, Invasion, Carapace, Sandstorm, Rooftop, etc.). Unknown maps fall back to `DEFAULT_BASE_RUSH_SECONDS = 36`, the slowest bucket, which under-warns on fast unlisted maps. No duplicate keys and no off-by-one coordinate issues exist (this registry holds only rush-second integers, not coordinates, despite the project overview mentioning generator/team coordinates).
- Impact: Rush predictions for unlisted maps default to the conservative 36 s, producing late warnings on fast maps not in the table.
- Suggested fix: Expand the table to the current map pool, or key off a map "size class" detected from the sidebar; consider a lower default than the slowest bucket.

### [LOW] `detectCurrentMapName` substring index is correct but brittle to format drift
- Location: MapMetadataRegistry.java:76-80
- Issue: It matches `lower.startsWith("map:")` then `trimmed.substring(4)`. The `4` is correct for the literal `Map:`, but is a magic number tied to the prefix length; if Hypixel changes the label (e.g. `Map -`) this silently mis-slices. The match is also case-insensitive on the prefix but assumes no leading whitespace before `Map:` (already handled by the earlier `trim()`).
- Impact: Fragile to sidebar wording changes; would yield a wrong/empty map name rather than crash.
- Suggested fix: Use `trimmed.substring(trimmed.indexOf(':') + 1).trim()` so the slice tracks the actual delimiter position.

### [LOW] `findGeneratorCluster` 32-node BFS cap can make `lexGreater` anchor non-deterministic
- Location: WorldScanService.java:445-465, used by 425-436 and 472-473
- Issue: The BFS stops at `result.size() < 32`. For an unusually large contiguous diamond/emerald structure (>32 blocks, rare but possible with map decoration), the discovered cluster — and therefore the lex-greatest "canonical" anchor — depends on BFS traversal order, so different starting blocks of the same physical cluster can resolve to different anchors. That would cause `isCollapsedClusterMember` to disagree and track duplicate entries for one pad.
- Impact: Possible duplicate generator entries / inconsistent collapse on pathologically large clusters. Very low likelihood on real maps.
- Suggested fix: If a cap is needed for safety, detect when the cap is hit and fall back to treating the block as its own anchor (or raise the cap well above any real pad size).

## Improvements

- Cache discovered generator positions and separate the expensive discovery pass from the cheap per-interval resource recount (ties to the two High findings).
- Iterate `mc.theWorld.loadedEntityList` once per scan and reuse the result across `scanGeneratorResources` and `pickGeneratorLabelPosition`; compute each anchor's cluster once.
- Extract the repeated `for (EntityPlayer player : mc.theWorld.playerEntities) { skip self }` pattern (used in `checkForInvisiblePlayers`, `performAutoplayCheck`, `isAnyPlayerNearGenerator`) into a shared helper.
- Replace the raw `new Thread` in `requeueAutoplay` with a shared scheduler/executor and add an in-flight guard so the requeue command sequence cannot start twice.
- Add internal null guards to all `public` scan methods so they are safe regardless of caller (consistency with `performAutoplayCheck`).
- Make the generator keep/evict geometry match the scan geometry (box test) to stop edge generators from flapping.
- Pull the magic numbers (`±20` Y span at line 205, `32` cluster cap, `0.75/3.0/9.0/4.0` distance thresholds) into named constants alongside the existing `MIN_VALID_INTERVAL_MS` group for clarity and tuning.
