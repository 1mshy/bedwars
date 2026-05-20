# Review: HUD Rendering

Files:
- src/main/java/com/imshy/bedwars/render/BedwarsHudRenderer.java
- src/main/java/com/imshy/bedwars/render/MatchSummaryRenderer.java
- src/main/java/com/imshy/bedwars/render/PreGameBriefingRenderer.java
- src/main/java/com/imshy/bedwars/runtime/PreGameBriefing.java (data model; lives in `runtime/`, not `render/`)

## Summary

All three renderers run every frame via `BedwarsRuntime.onRenderOverlay` (RenderGameOverlayEvent.Post, ElementType.TEXT). `MatchSummaryRenderer` and `PreGameBriefingRenderer` are clean: their GL handling is symmetric (color reset to opaque white before `popMatrix`, balanced push/pop), they build off immutable snapshots, and they guard against null/empty data. `PreGameBriefing` builds its snapshot once at construction and is effectively immutable on the render path — good design.

`BedwarsHudRenderer` is where the real issues live. The most serious is that the HUD rebuilds the team-danger summary up to three times per frame, and that rebuild is not a pure read — it enqueues async Hypixel API fetches as a side effect. There is also a genuine GL state leak (`enableBlend` with no matching disable) and a heavy per-frame world scan for the proximity glow. None of the renderers appear to crash under normal data, but several read shared mutable collections on the render thread, and a couple of defensive guards are missing.

Counts: 1 High, 4 Medium, 5 Low.

## Findings

### [HIGH] Team-danger summary rebuilt up to 3x per frame, with per-frame async API fetches as a side effect
- Location: BedwarsHudRenderer.java:171, 178, 202 (calls), via TeamDangerAnalyzer.buildTeamDangerSummary at TeamDangerAnalyzer.java:65; the side effect is at TeamDangerAnalyzer.java:90-97
- Issue: `buildTeamDangerSummary(mc)` is called independently by `addHighestThreatSection` (line 233), `addGoodTeamsSection` (line 271), and `addTeamLevelSummary` (line 403). When all three HUD sub-toggles are enabled, that's three full rebuilds per frame: each call runs `TabListScanner.scanAllPlayers(mc)`, builds a `HashMap` keyed by team color, computes threat scores, allocates a list, and sorts with a freshly allocated anonymous `Comparator`. Worse, the rebuild is not a pure read — for any tab-list player whose stats are not yet cached it adds to `state.pendingTabListFetches` and fires `HypixelAPI.fetchStatsAsync(...)` (TeamDangerAnalyzer.java:90-97). So the render thread can kick off (or attempt to kick off) network fetches up to 3x per frame, every frame.
- Impact: Wasted CPU at the per-frame level (3x redundant tab scan + map build + sort + allocations at ~60+ fps), and — more importantly — a rendering method drives network/queue side effects from the render thread, redundantly. This couples frame rate to fetch-scheduling pressure and makes the HUD a hidden source of API request volume.
- Suggested fix: Call `buildTeamDangerSummary(mc)` once at the top of `buildHudContent` and pass the resulting `List<TeamDangerEntry>` into `addHighestThreatSection`, `addGoodTeamsSection`, and `addTeamSummarySection`. Separately, the fetch-scheduling side effect does not belong on the render path at all — move stat prefetching into a periodic tick handler (e.g. client tick) so rendering is a pure read of the cache. Caching the summary per frame (or per N ms) is the minimum fix; decoupling the fetch is the correct one.

### [MEDIUM] `GlStateManager.enableBlend()` is never reset (GL state leak)
- Location: BedwarsHudRenderer.java:122
- Issue: Inside the skin-face branch, `GlStateManager.enableBlend()` is called but there is no matching `disableBlend()` anywhere in `render()`. The render method finishes with `popMatrix()` (line 160) without restoring blend state. (Note: this branch only fires when a `HudLine` carries a `skinLocation`, i.e. player/threat rows — so the leak is data-dependent, which makes it intermittent and harder to notice.)
- Impact: Once any skin face is drawn, blend stays enabled for whatever Forge renders after the HUD on the same overlay pass (chat, hotbar text, F3, other mods' overlays). Leaked blend state can cause subtle transparency/compositing artifacts in later-drawn elements. This is a real GL state leak, not a stylistic one.
- Suggested fix: Restore blend at the end of the branch (or at the end of `render()`): pair the `enableBlend()` with `GlStateManager.disableBlend()` after the face is drawn, mirroring how the item-render branch already pairs `enableDepth`/`disableDepth` and `enableRescaleNormal`/`disableRescaleNormal` (lines 137-138 / 151-153). Also consider resetting `GlStateManager.color(1,1,1,1)` defensively before `popMatrix`, as `MatchSummaryRenderer` does at line 84.

### [MEDIUM] `isExtremeThreatNearby` scans every world player + cache lookup every frame
- Location: BedwarsHudRenderer.java:106 (call), 503-518 (impl)
- Issue: When `isThreatProximityGlowEnabled()` is on, every frame the HUD iterates all of `mc.theWorld.playerEntities`, calls `HypixelAPI.getCachedStats(player.getName())` per player, and computes a squared distance. This runs unconditionally per frame (the config flag is the only gate) regardless of whether the result could have changed.
- Impact: Per-frame O(players) work including a cache map lookup and string allocation (`player.getName()`) per entity, at frame rate. On a full 16-player lobby this is meaningful avoidable cost, and the boolean it produces changes far slower than once per frame.
- Suggested fix: Cache the result with a short TTL (e.g. recompute at most every 200-500 ms) and reuse it between frames; the pulse animation can still read `System.currentTimeMillis()` each frame for smoothness while the expensive scan is throttled.

### [MEDIUM] `addHighThreatPlayerList` runs a 4th independent `scanAllPlayers` + per-frame sort
- Location: BedwarsHudRenderer.java:436 (`scanAllPlayers`), 474-482 (per-frame anonymous Comparator + `Collections.sort`)
- Issue: After the team-summary phase elapses (`matchStartTime` older than 10s), `addTeamSummarySection` routes to `addHighThreatPlayerList`, which calls `TabListScanner.scanAllPlayers(mc)` again (independent of the up-to-3 calls in finding #1), does a `HypixelAPI.getCachedStats` per player, builds a `List<ThreatPlayerEntry>`, and sorts it with a freshly allocated anonymous `Comparator` — all per frame. It also calls `findWorldPlayer` (line 457) which is itself an O(players) linear scan of `playerEntities`, making the per-player distance lookup O(players^2).
- Impact: Another full tab scan plus an O(n^2) world lookup and a per-frame sort/allocation, every frame, during the bulk of a match.
- Suggested fix: Reuse the single cached `scanAllPlayers`/summary result from finding #1. Hoist the `Comparator` to a `static final` instance instead of allocating per frame. Replace the per-entry `findWorldPlayer` linear scan with a single name→entity map built once per frame.

### [MEDIUM] Shared mutable world player list iterated on render thread without synchronization
- Location: BedwarsHudRenderer.java:508, 538, 588, 684 (iterating `mc.theWorld.playerEntities`)
- Issue: `render()` iterates `mc.theWorld.playerEntities` in several places. In 1.8.9 the client world entity lists are mutated on the client main thread as network packets are applied, and the overlay render also runs on the main thread, so in practice this is usually safe. However, the code does not assume or document that invariant, and `chatDetectedPlayers` (the one collection that IS shared with the API executor thread) is correctly wrapped in `synchronized` at line 575 — the inconsistency suggests the unsynchronized iterations were not a deliberate decision.
- Impact: Low real-world risk on 1.8.9 (single-threaded world mutation), but a `ConcurrentModificationException` is possible if any code path ever mutates the entity list off the main thread. Worth a note rather than a fix.
- Suggested fix: No code change strictly required for 1.8.9; add a comment documenting that these reads rely on main-thread-only world mutation. If defensiveness is wanted, snapshot `new ArrayList<>(mc.theWorld.playerEntities)` once per frame and iterate the copy.

### [LOW] `cdp.stats.getThreatLevel()` dereferenced without null check
- Location: BedwarsHudRenderer.java:577-578
- Issue: `BedwarsStats stats = cdp.stats; BedwarsStats.ThreatLevel threat = stats.getThreatLevel();` with no null guard. `ChatDetectedPlayer.stats` is `final` and is populated only from `HypixelAPI`'s `onStatsLoaded(BedwarsStats stats)` callback (BedwarsRuntime.java:984) and `addToChatDetectedIfEligible` (BedwarsRuntime.java:1079), both of which supply a non-null stats object (the callback itself dereferences `stats` at line 971). So in practice this is non-null today.
- Impact: No crash under current call sites. Purely a missing defensive guard — if a future caller ever constructs `ChatDetectedPlayer` with a null `stats`, this becomes a render-thread NPE.
- Suggested fix: Add `if (stats == null) continue;` after line 577, or enforce non-null in the `ChatDetectedPlayer` constructor.

### [LOW] `getHudScale()` div-by-zero relies entirely on config bounds
- Location: BedwarsHudRenderer.java:93, 95-96
- Issue: `GlStateManager.scale(scale, scale, 1.0)` and `scaledWidth / scale` / `scaledHeight / scale` assume `scale > 0`. The value comes from `ModConfig.getHudScale()`, which is a Forge `Property` bounded to [0.5, 2.0] (ModConfig.java:646), so 0 is not reachable via normal config. There is no clamp at the read site itself.
- Impact: No bug under normal config. If the bounds were ever removed or the field set programmatically to 0, lines 95-96 would divide by zero (→ Infinity → undefined `(int)` cast) and the panel would mis-position or vanish.
- Suggested fix: Defensively clamp at the read site, e.g. `double scale = Math.max(0.1, ModConfig.getHudScale());`.

### [LOW] `bgAlpha`/`tintAlpha` use `& 0xFF` masking instead of clamping
- Location: BedwarsHudRenderer.java:102; MatchSummaryRenderer.java:65, 69; PreGameBriefingRenderer.java:62, 65
- Issue: `(int)(opacity * 255) & 0xFF` masks rather than clamps. If `getHudBackgroundOpacity()` ever exceeds 1.0 (its config bound is [0.0, 1.0], so currently safe), the masked byte wraps to a wrong, possibly near-zero alpha rather than saturating at 255.
- Impact: None under current config bounds; a robustness footgun if bounds change. The fade-alpha multiplications in the summary/briefing renderers are already clamped to [0,1] via `Math.max`, so those are safe.
- Suggested fix: Clamp before masking: `int bgAlpha = (int)(Math.max(0f, Math.min(1f, opacity)) * 255);`.

### [LOW] Per-frame anonymous `Comparator` allocation
- Location: BedwarsHudRenderer.java:474 (per frame in `addHighThreatPlayerList`)
- Issue: A new anonymous `Comparator<ThreatPlayerEntry>` is allocated every frame. (Note: `PreGameBriefing.java:155` also allocates an anonymous comparator, but that is on briefing *build*, not per frame, so it is not a concern.) `TeamDangerAnalyzer` similarly allocates a comparator per `buildTeamDangerSummary` call.
- Impact: Minor per-frame garbage; contributes to GC churn at frame rate.
- Suggested fix: Hoist these to `private static final Comparator<...>` constants.

### [LOW] `getPlayerTeamColor` re-parses team prefix string every frame per player
- Location: BedwarsHudRenderer.java:771-790 (called from 591, 715)
- Issue: For every relevant player every frame, `getPlayerTeamColor` scans the team color-prefix string character-by-character to extract the first color code.
- Impact: Small, but it is string scanning at frame rate proportional to the number of detected/tracked players.
- Suggested fix: Minor — acceptable for small player counts. Could be folded into the per-frame snapshot from findings #1/#4 if those are reworked, so the color is computed once per player per frame rather than re-derived.

### [LOW] `addEnemyTrackingSection` return value discarded (latent gap bug)
- Location: BedwarsHudRenderer.java:227 (call), 673 (method returns boolean)
- Issue: `addEnemyTrackingSection` returns a `boolean` indicating whether it added content, but the caller at line 227 ignores it (unlike every other section, which does `addedSection = addedSection || added`). It happens to be the last section in `buildHudContent`, so nothing reads `addedSection` after it and the gap logic is not broken today.
- Impact: None currently. Latent bug: if any section is added after the enemy-tracking block, it will not get its leading `HudLine.gap()` separator (or will get a spurious one), because `addedSection` was never updated to reflect this section.
- Suggested fix: Assign the result: `addedSection = addEnemyTrackingSection(...) || addedSection;` to keep the section-gap bookkeeping consistent.

## Improvements

- Consolidate all tab-list/team-danger reads into a single per-frame (or short-TTL-cached) snapshot computed once at the top of `buildHudContent`, and pass it down. This single change addresses findings #1, #4, and most of #9/#10.
- Move all stat-prefetch scheduling out of `buildTeamDangerSummary` (and therefore out of the render path) into a tick-based handler so rendering becomes a pure read of the cache.
- Establish a consistent GL-state discipline in `BedwarsHudRenderer.render`: every `enable*` paired with a `disable*`, and a `GlStateManager.color(1,1,1,1)` reset before the final `popMatrix`, matching the already-clean pattern in `MatchSummaryRenderer`.
- Hoist all per-frame anonymous `Comparator` instances to `static final` constants.
- `MatchSummaryRenderer` and `PreGameBriefingRenderer` are clean and need no changes; the duplicated `applyAlpha`/`drawBorder`/fade-math between them could optionally be factored into a shared helper for maintainability, but this is cosmetic.
