# Review: Match Threat & Analysis

Files:
- src/main/java/com/imshy/bedwars/runtime/MatchThreatService.java
- src/main/java/com/imshy/bedwars/runtime/TeamDangerAnalyzer.java
- src/main/java/com/imshy/bedwars/runtime/MatchSummary.java
- src/main/java/com/imshy/bedwars/runtime/FinalKillLedger.java
- src/main/java/com/imshy/bedwars/RushRiskPredictor.java

## Summary

The distance/proximity math is correct: `getDistanceToNearestTrackedBed` (MatchThreatService:280-288) and `getNearestEnemyDistanceToBed` (MatchThreatService:303-323) both have proper `if (distance < closest)` minimization guards, and `getNearestEnemyDistanceToBed` correctly prefers the real detected bed (`playerBedBlocks.get(0)`) over the spawn fallback. Threat aggregation in `TeamDangerAnalyzer` is sound and guards its averages against div-by-zero. The final-kill regex is matched against a `§`-stripped message (BedwarsRuntime:223), so the `^` anchor works. All MatchThreatService tick methods are entered only after a `mc.theWorld == null || mc.thePlayer == null` guard (BedwarsRuntime:601) and `GamePhase.IN_GAME` gate (BedwarsRuntime:591), so the unguarded entity-loop dereferences inside them are safe at the live call site.

The one genuine correctness defect is the `pendingTabListFetches` set being a permanent fetch-suppression that is never pruned (High). The remaining items are robustness/perf/maintainability: redundant full tab-list scans per rush check and per HUD frame, double `isBlacklisted` calls, a self-team threat double-count gap, and several `static final` / cosmetic cleanups.

Thread-safety: in Forge 1.8.9 both `ClientTickEvent`, `ClientChatReceivedEvent`, and `RenderGameOverlayEvent` fire on the client main thread, so the plain `ArrayList`/`HashMap`/`HashSet`/`LinkedHashMap` collections shared between the tick/chat handlers and the HUD renderer are not actually raced. The async `HypixelAPI` executor callbacks used here are empty no-ops and never touch shared state. `MatchSummary.build` iterates a `getCurrentGamePlayersSnapshot()` copy, not the live set. No real cross-thread race was found in this unit.

## Findings

### [High] `pendingTabListFetches` is a permanent fetch-suppression set that is never pruned, so failed lookups never retry and post-TTL stats go stale for the rest of the match
- Location: TeamDangerAnalyzer.java:90-91 and 197-198 (adds), with RuntimeState.java:151 the only removal (full game reset)
- Issue: Both `buildTeamDangerSummary` and `buildTeamDangerSummaryFromWorld` add a name to `state.pendingTabListFetches` before kicking off `HypixelAPI.fetchStatsAsync(...)`, but nothing ever removes the name except `RuntimeState.reset()` between games. `HypixelAPI.getCachedStats` only returns non-null on a successful or "nicked" fetch (HypixelAPI.java:115/139/190); on UUID-resolution failure (120), API error (130), or exception (145) nothing is cached. The async callbacks passed here are empty (`onStatsLoaded`/`onError` do nothing), so they cannot re-arm a retry either.
- Impact: (1) Any player whose first lookup fails (timeout, rate-limit, transient API error) is suppressed forever for the match — their team's `playersWithKnownStars`/`playersWithKnownThreat` stay under-counted, skewing every team-danger average and the rush prediction. (2) Even a *successful* lookup goes stale after the 60-min cache TTL: `getCachedStats` returns null once expired (HypixelAPI.java:387), but the name is still in `pendingTabListFetches`, so the guard at line 90/197 blocks any refresh for the remainder of the game. (3) The set grows monotonically across a long game with many distinct names. The "fall back to world" branch at line 123 can also be entered spuriously when too many lookups are stuck pending and few teams have known stars.
- Suggested fix: Remove the name from `pendingTabListFetches` in both the `onStatsLoaded` and `onError` callbacks (so a failed/expired entry can be retried on a later tick), e.g. `state.pendingTabListFetches.remove(tp.name)` in each. Alternatively, key the suppression on a short timestamp ("last attempt < N seconds ago") instead of a sticky set, which also bounds growth. Either approach should also re-fetch when a previously-cached entry has expired.

### [Medium] `buildTeamDangerSummary` is recomputed multiple times per rush check and once per HUD frame (repeated full tab-list scan + map alloc + sort)
- Location: TeamDangerAnalyzer.java:30-31 (`getHighestStarEnemyTeam`), 44-45 (`getHighestEnemyTeamThreatAverage`), 56-57 (`buildTeamDangerLines`); driven from MatchThreatService.java:162 + 180 and from the HUD renderer
- Issue: Each of `getHighestStarEnemyTeam`, `getHighestEnemyTeamThreatAverage`, and `buildTeamDangerLines` independently calls `buildTeamDangerSummary(mc)`, which does a full `TabListScanner.scanAllPlayers(mc)`, allocates a `HashMap` + `ArrayList`, and runs a `Collections.sort` every call. `checkRushRiskPredictor` calls it at least twice (line 162 via the threat-average helper, and line 180 via `getHighestStarEnemyTeam`), and `buildTeamDangerLines` is invoked from the HUD on render frames.
- Impact: Redundant O(n) tab scans and per-call allocations/sorts. On the HUD path this runs every rendered frame; on the rush path it duplicates the scan within a single check. Wasteful GC churn and CPU on the render thread, scaling with lobby size.
- Suggested fix: Build the summary once and pass the resulting `List<TeamDangerEntry>` into the helpers, or cache the summary per tick (e.g. memoize with a timestamp / generation counter and invalidate when the tab list changes). At minimum, in `checkRushRiskPredictor` compute the summary list once and derive both the highest-threat-average and highest-star team from it.

### [Medium] Own team is excluded from the threat-average aggregate but counted in `totalPlayers`, and a player with all-LOW (score 0 filtered) teams can read as "UNKNOWN" despite known stats
- Location: TeamDangerAnalyzer.java:112-118 and 219-225 (`if (score <= 0.0) continue;`), plus the `(N/total known)` label at line 267
- Issue: `threatToScore` returns `0.0` only for the `default` (unrecognized/null) threat level; LOW maps to `1.0`, so the `if (score <= 0.0) continue;` is effectively dead for normal levels — but if `getThreatLevel()` ever returns a value outside the enum-mapped set, that player is counted in `totalPlayers` and `playersWithKnownStars` yet excluded from `playersWithKnownThreat`. The "(X/total known)" suffix then reports fewer "known" than actually resolved. This is a latent inconsistency rather than a live crash.
- Impact: The displayed "known" count can understate how many players were actually resolved, and the average-threat denominator can diverge from the star denominator for the same team. Minor display/accuracy drift; not a crash.
- Suggested fix: Make the threat counter consistent with the star counter — count any successfully-loaded, non-null threat level toward `playersWithKnownThreat` (treat `default` as a real but low score, or skip it for *both* stars and threat). Remove the now-redundant `score <= 0.0` early-continue or align it with the stars handling.

### [Low] `getDistanceToNearestTrackedBed` / `getNearestEnemyDistanceToBed` take a `Math.sqrt` per entity instead of comparing squared distances
- Location: MatchThreatService.java:282, 317
- Issue: Both loops compute `Math.sqrt(position.distanceSq(...))` for every bed/entity just to find the minimum, then compare against a threshold (`BED_PROXIMITY_WARNING_DISTANCE`, the rush distance bands). The minimum of squared distances is the minimum of real distances, so the sqrt can be deferred until after the loop.
- Impact: Minor: one sqrt per player per tick on the proximity path. Negligible at typical lobby sizes but trivially avoidable.
- Suggested fix: Track the minimum `distanceSq` in the loop and call `Math.sqrt` once on the winner before returning; compare thresholds against squared values where possible.

### [Low] `FINAL_KILL_PATTERN` is matched with a single `find()`, so a chat line carrying two "FINAL KILL!" markers would only tally one
- Location: BedwarsRuntime.java:1096-1136 (`handleFinalKill`), pattern at HypixelMessages.java:55-56
- Issue: `handleFinalKill` calls `m.find()` once and records a single kill. Hypixel sends each final kill on its own chat line, so in practice this is one kill per message and the count is correct — but the design assumes that invariant silently.
- Impact: Theoretical undercount if a message ever batches multiple final kills on one line. No impact under current Hypixel behavior.
- Suggested fix: Either document the one-kill-per-line assumption at the call site, or loop with `while (m.find())` and record each match for robustness.

### [Low] `MatchSummary.build` calls `db.isBlacklisted(name)` twice per loop iteration
- Location: MatchSummary.java:91 and 103
- Issue: The blacklist status is queried once for the `blacklistedFaced++` counter and again inside the EXTREME/LOSS branch (`!db.isBlacklisted(name)`).
- Impact: Two DB/lookup calls per player where one would do. Runs only at match end over a snapshot set, so impact is negligible.
- Suggested fix: Hoist `boolean blacklisted = db.isBlacklisted(name);` once at the top of the loop body and reuse it.

### [Low] Several constants are instance-`final` rather than `static final`
- Location: MatchThreatService.java:29 (`TEAMMATE_SPAWN_CAPTURE_WINDOW_MS`), 31 (`RUSH_PREDICTOR_ACTIVE_WINDOW_MS`)
- Issue: These are compile-time constants but declared as per-instance `final` fields, unlike the sibling constants above them which are `static final`.
- Impact: Cosmetic / per-instance memory only; no behavioral effect (the service is effectively a singleton).
- Suggested fix: Promote both to `private static final` for consistency with the other constants in the class.

## Improvements

- Distance math and the rush-window time math are correct. In `checkRushRiskPredictor` (MatchThreatService.java:172-173), `estimate.etaSeconds` is capped to [12,70] (RushRiskPredictor.java:44), so `etaSeconds * 1000L` cannot overflow `int` before the `long` literal promotion, and the `+ 999L) / 1000L` ceiling-rounding of `secondsUntilRush` is intentional and correct. The `secondsUntilRush <= 0` guard at line 175 prevents a stale warning past the predicted moment.

- `FinalKillLedger` (FinalKillLedger.java:43-69) is clean for a single-threaded design: counts are accurate, the `null` victim-team is bucketed under "UNKNOWN" so totals stay consistent, and the `LinkedHashMap` preserves insertion order for stable HUD display. No synchronization is needed because chat parsing and HUD rendering both run on the Forge client main thread — worth a one-line comment stating this invariant so a future reader doesn't add locking or break it by reading the ledger off-thread.

- `RushRiskPredictor.estimateFirstRush` (RushRiskPredictor.java) is pure, allocation-light, and free of division — no div-by-zero or time-math issues. The threat/distance bands are reasonable. Consider extracting the magic thresholds (3.2/2.5/1.5, 28/40/56, the 12/70 clamp) into named constants for maintainability, but there is no bug.

- `isWatchdogBotName` (MatchThreatService.java:252-266) treats any name with only lowercase letters/digits and length > 10 as a bot; this is a heuristic and could occasionally exclude a legitimate player with such a name from bed/rush distance calculations. Not a correctness bug, but worth a comment noting the heuristic's false-positive risk.

- `getNearestEnemyDistanceToBed` and `checkBedProximityWarnings` both re-run the relatively expensive `isTeammate` chain (scoreboard lookups, armor-color comparison, tab-color parsing) for every world player every tick. If profiling ever shows this hot, caching team membership per UUID per tick would cut the work; not urgent.
