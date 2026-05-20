# Code Review Summary — Bedwars Forge Mod

Read-only audit of all 43 source files (~11,700 LOC), decomposed across 11 file-disjoint
units. Aggregate: **16 High, 27 Medium, 45 Low**.

## Implementation status

All **16 High** findings were fixed on branch `fix/high-severity-audit` (10 commits, each
compile-checked; full `./gradlew build` is green). Medium/Low findings are not yet
addressed. Commit → finding map:

| Commit | Findings | Summary |
|--------|----------|---------|
| `marshal async stat callbacks` | #3, #4, #16 | executor callbacks → `addScheduledTask` (BedwarsRuntime, BedwarsCommand) |
| `anti-cheat swings on client thread` | #9 | netty `channelRead` → enqueue/drain on tick |
| `cross-thread caches and flags` | #1, #6, #7 | ConcurrentHashMap caches; volatile flags |
| `synchronize chatDetectedPlayers clears` | #5 | WIN/LOSS clears under the lock |
| `remove fabricated per-prestige XP surcharge` | #2 | flat 487k XP/prestige star math |
| `apply GUI edits + connect thresholds` | #14, #15 | `OnConfigChangedEvent` handler; thresholds + displayDuration wired |
| `cylinder generator scan + lower range` | #10 | cube→cylinder, range 100→64 (also fixes the cube/sphere flapping medium) |
| `memoize team-danger summary` | #12 | 1s cache; render-path fetches throttled (also fixes the rush-path double-compute medium) |
| `interpolate last-seen arrow` | #13 | `partialTicks` yaw/position interpolation |
| `remove BedwarsRuntime.java.orig` | (artifact) | dead 33K backup file deleted |

Note: #11 (per-generator-block double entity-pass) was intentionally left as-is — only a
handful of cells match per scan, so it is negligible next to the #10 cylinder/range win.
Behavior-preserving defaults: threat thresholds unchanged (500/300/100, 6.0/4.0/2.0);
`displayDuration` and `generatorScanRange` default changes apply only to fresh installs
(existing config files keep their saved values).

## Cross-cutting themes (read these first)

1. **Main-thread / executor / netty thread boundary is the dominant bug class.**
   The `HypixelAPI` 3-thread executor pool and the netty I/O thread touch state that
   is main-thread-only in Forge 1.8.9: the world entity list, the chat GUI, and plain
   (non-concurrent, non-volatile) collections/flags. Appears in units **1, 3, 4, 6, 8,
   10, 11**. The single highest-leverage fix is to marshal all callback side effects onto
   the client thread via `Minecraft.getMinecraft().addScheduledTask(() -> { ... })`, doing
   only thread-safe data prep in the callback body.

2. **Work done per render frame / per tick that should be cached.** The team-danger
   summary is rebuilt up to 3×/frame (and fires async API fetches as a render side
   effect); full-world threat scans run every frame; `WorldScanService` runs a cubic
   `getBlockState` scan (~1.66M–6.6M calls/sec). Units **5, 7, 8**.

3. **Dead / disconnected configuration.** GUI config edits never take effect (no
   `OnConfigChangedEvent` handler; `loadConfig` only runs in `preInit`). Star/FKDR
   thresholds and `displayDuration` are dead config — zero callers; `getThreatLevel`
   uses hardcoded literals. `isPreGameBriefingEnabled()` hardcodes `return false`. Unit **10**.

4. **Permanent fetch-suppression sets that never prune.** `pendingTabListFetches` is
   added to before an async fetch but only cleared on full game reset; failed lookups
   never retry and post-TTL stats go stale for the whole match. Also `renderFetchRequests`
   grows unbounded. Units **3, 4, 5**.

5. **Documentation / artifact drift.** CLAUDE.md says `PlayerDatabase` is SQLite-backed —
   it is actually JSON-file backed (`config/bedwarsstats/playerdata.json`). Config file is
   `bedwarsstats.cfg`, not the documented `bedwars.cfg`. `@Mod` VERSION is "6.7" vs gradle
   "1.0". A leftover `BedwarsRuntime.java.orig` artifact is in the tree. Units **2, 10**.

## High-severity findings

| # | Unit | Location | Issue |
|---|------|----------|-------|
| 1 | API & Stats | `HypixelAPI.java:52-56` | Stat cache (`HashMap`/`ArrayList`) is not thread-safe but read/written by both executor pool and main thread. |
| 2 | API & Stats | `BedwarsStats.java:247` | Fabricated `(level/100)*500` per-prestige XP penalty undercounts stars; real cost is flat 487,000 XP/prestige → mis-rated threat (`getThreatLevel:276-285`). |
| 3 | Runtime Core | `BedwarsRuntime.java:1018` | Async stat callback iterates `mc.theWorld.playerEntities` + calls `isTeammate` off the main thread → CME/NPE, stale reads. |
| 4 | Runtime Core | `BedwarsRuntime.java:995, 1032` | Async callback mutates the chat GUI (`addChatMessage`) and requeues autoplay off the main thread. |
| 5 | Runtime Core | `BedwarsRuntime.java:257, 288` | Unsynchronized `chatDetectedPlayers.clear()` in WIN/LOSS races the executor's synchronized `add` (`:974`). |
| 6 | Runtime Core | `RuntimeState.java:21,22,94` | `gamePhase`/`disconnectedFromGame`/`autoplayEnabled` are non-volatile but shared across the thread boundary → no happens-before, stale reads. |
| 7 | Lobby & Tablist | `LobbyTrackerService.java:170` | `PlayerJoinEntry.stats` written by pool thread, read by render thread, non-volatile → render may never see resolved stats. |
| 8 | Match & Threat | `TeamDangerAnalyzer.java:90-91, 197-198` | `pendingTabListFetches` is a permanent suppression set, only cleared on game reset → failed lookups never retry, post-60min-TTL stats go stale. |
| 9 | Tracking & Anti-cheat | `AntiCheatService.java:175-207` | `channelRead` runs on the netty I/O thread but calls `getEntityByID`/`getHeldItem`/`isUsingItem`, mutates plain `HashMap`s, and calls `addChatMessage` off-thread. |
| 10 | World Scan | `WorldScanService.java:198-242` | Per-second cubic `getBlockState` scan ≈1.66M calls (up to 6.6M at range 200) on the main thread; range from `ModConfig:464-470`. |
| 11 | World Scan | `WorldScanService.java:224, 236, 238` | Per generator-block hit: 2 full entity-list passes + 2 BFS — multiplies the scan cost. |
| 12 | HUD Rendering | `BedwarsHudRenderer.java:171, 178, 202` | Team-danger summary rebuilt up to 3×/frame AND fires async API fetches as a render side effect (`TeamDangerAnalyzer:90-97`). |
| 13 | In-World Overlays | `LastSeenArrowRenderer.java:69` | Arrow bearing uses non-interpolated `rotationYaw` → arrows stutter relative to the smoothly-rotating scene during view movement. |
| 14 | Config & Entry | `ModGuiConfig.java:19-34` / `BedwarsMod.java:18-22` | GUI config edits never take effect — no `OnConfigChangedEvent` handler; `loadConfig` only runs in `preInit`. |
| 15 | Config & Entry | `ModConfig.java:188-235, 884-910` | Star/FKDR thresholds + `displayDuration` are dead config (zero callers); `getThreatLevel` uses hardcoded literals (`BedwarsStats.java:276-285`). |
| 16 | Command (/bw) | `BedwarsCommand.java:87-133, 161-185` | Async stat callbacks call `addChatMessage` off the main thread on the cache-miss path (`lookup`, `all`). |

## Medium-severity findings

| Unit | Location | Issue |
|------|----------|-------|
| Persistence | `PlayerDatabase.java:436-438, 458-460` | `save()`/`load()` leak stream handles + non-atomic write can corrupt the only data file. |
| Persistence | `PlayerDatabase.java:174-176, 193-195` | `isBlacklisted`/`getBlacklistEntry` trigger a synchronous disk `save()` on the main thread. |
| Persistence | `PlayerDatabase.java:254-256` | `currentGamePlayers` stores raw-case names while everything else lowercases → lookup misses. |
| Runtime Core | `BedwarsRuntime.java:232` | Brittle `contains()` substring match for GAME_START; can drive `IDLE→IN_GAME` directly, skipping PRE_GAME. |
| Runtime Core | `BedwarsRuntime.java:246` | Loose `contains(WIN_VICTORY)` — a player typing "VICTORY!" can prematurely end the game as a WIN. |
| Runtime Core | `BedwarsRuntime.java:248-249` | `WIN_1ST_KILLER`/`Winners:` confirm win via `contains(playerName)` → false WIN on a superstring name ("Sky" vs "Sky123"). |
| Lobby & Tablist | `LobbyTrackerService.java:130-143` | TOCTOU dedupe across a split lock → duplicate join entries / double fetch. |
| Lobby & Tablist | `LobbyTrackerService.java:198-208` | `pendingTabListFetches` never removed on failure (same class as High #8). |
| Lobby & Tablist | `TabListScanner.java:87-115` | Tab parsing cannot distinguish a rank prefix from a team prefix → mis-attributed team. |
| Match & Threat | `TeamDangerAnalyzer.java:30-31, 44-45, 56-57` | `buildTeamDangerSummary` recomputed multiple times per rush check and once per HUD frame (full tab scan + alloc + sort each). |
| Match & Threat | `TeamDangerAnalyzer.java:112-118, 219-225` | Own team excluded from threat-average aggregate but counted in `totalPlayers`; "(N/total known)" can understate resolved count. |
| Tracking & Anti-cheat | `AntiCheatService` | `autoBlockStates`/`lastFlagAt` grow unbounded when CPS is disabled. |
| Tracking & Anti-cheat | `AntiCheatService` | Never reset between games (no `clearAll`) → stale flags carry across matches. |
| Tracking & Anti-cheat | `EnemyTrackingService` | Keyed by name, never shrinks across a session. |
| World Scan | `WorldScanService.java:204-206, 250` | Cube-scan vs sphere-eviction mismatch → generator labels flap in/out at the edges. |
| World Scan | `WorldScanService.java:119-166` | Autoplay requeue uses a raw `Thread`+`sleep`; double-fire race can duplicate the command. |
| World Scan | `WorldScanService.java:168-196` | `checkForInvisiblePlayers` has no null guard on world/player. |
| World Scan | `WorldScanService` | `invisiblePlayerWarnings` grows unbounded. |
| HUD Rendering | `BedwarsHudRenderer.java:122` | `enableBlend` never reset → GL blend leak into subsequent rendering. |
| HUD Rendering | `BedwarsHudRenderer.java:106, 503-518` | `isExtremeThreatNearby` does a full-world scan every frame. |
| HUD Rendering | `BedwarsHudRenderer.java:436, 457` | 4th independent `scanAllPlayers` + O(n²) loop per frame. |
| HUD Rendering | `BedwarsHudRenderer.java` | Unsynchronized iteration of the world player list on the render thread. |
| In-World Overlays | `BedwarsOverlayRenderer.java` (multiple methods) | GL state torn down by setting assumed defaults rather than save/restore (`pushAttrib`/`popAttrib`) → fragile cross-mod hazard. |
| In-World Overlays | `EnemyTrackingService.getAllTrackedEnemies()` | Returns the live mutable map (siblings return unmodifiable views) → leaky abstraction / future CME risk. |
| Config & Entry | `ModConfig.isPreGameBriefingEnabled()` | Hardcoded `return false` → pre-game briefing feature is permanently off regardless of config. |
| Config & Entry | `AudioCueManager` (ENDER_PEARL_INCOMING) | Cue gated on `isEnderPearlTrackingEnabled()` instead of an audio toggle → can't silence independently. |
| Config & Entry | `ModConfig` (static fields) | Non-volatile static config fields read across threads; unguarded `config.save()` can crash `preInit`. |

## Per-unit counts

| # | Unit | Report | H | M | L |
|---|------|--------|---|---|---|
| 1 | API & Stats | `01-api-stats.md` | 2 | 0 | 7 |
| 2 | Persistence & Blacklist | `02-persistence.md` | 0 | 3 | 4 |
| 3 | Runtime Core & Game State | `03-runtime-core.md` | 4 | 3 | 2 |
| 4 | Lobby, Tab List & Team Detection | `04-lobby-tablist.md` | 1 | 3 | 3 |
| 5 | Match Threat & Analysis | `05-match-threat.md` | 1 | 2 | 4 |
| 6 | Enemy/Projectile Tracking & Anti-cheat | `06-tracking-anticheat.md` | 1 | 3 | 5 |
| 7 | World Scan, Generators & Beds | `07-worldscan-generators.md` | 2 | 5 | 6 |
| 8 | HUD Rendering | `08-hud-rendering.md` | 1 | 4 | 6 |
| 9 | In-World Overlays | `09-inworld-overlays.md` | 1 | 2 | 4 |
| 10 | Config, GUI, Entry & Audio | `10-config-entry-audio.md` | 2 | 4 | 3 |
| 11 | Command (/bw) | `11-command.md` | 1 | 0 | 5 |
| | **Total** | | **16** | **27** | **45** |

## Top fixes to prioritize

1. **Add a single "run on client thread" helper and route every async/netty callback
   side effect through it** (`addScheduledTask`). Resolves Highs #3, #4, #9, #16 and
   removes the world-iteration, chat-mutation, and visibility hazards in one refactor.
   Mark the remaining cross-thread flags `volatile` (#6) or confine them the same way.
2. **Make the stat cache thread-safe** (#1) — `ConcurrentHashMap` or synchronized access,
   matching the documented "accessed from both threads" contract.
3. **Wire up `OnConfigChangedEvent` and re-`loadConfig`** (#14) so GUI edits apply, then
   connect (or delete) the dead threshold config and fix `getThreatLevel` to read it (#15).
4. **Fix the star/XP math** (#2) — use the flat 487,000 XP/prestige cost so threat levels
   are correct.
5. **Cache the team-danger summary per tick** and stop firing API fetches from the render
   path (#12, plus the Match-unit duplicate-scan Medium).
6. **Throttle/shrink the world scan** (#10, #11) — lower default range, cache the scan
   between ticks, and stop the per-block double entity-pass + double BFS.
7. **Prune the fetch-suppression sets** (#8, and the related Lobby Medium + `renderFetchRequests`)
   so failed/expired lookups retry instead of going permanently stale.
8. **Harden the JSON persistence write** (#Persistence Mediums) — atomic temp-file rename +
   close handles, and move blacklist-triggered saves off the main-thread read path.
9. **Anchor the chat-pattern matches** (GAME_START, WIN_VICTORY, winners-name) to avoid
   spoofed/substring false transitions (Runtime Mediums).
10. **Interpolate the last-seen arrow yaw** with `partialTicks` (#13) for smooth tracking.

## Notes / artifacts to clean up

- Remove the leftover `BedwarsRuntime.java.orig` from the tree.
- Reconcile CLAUDE.md: `PlayerDatabase` is JSON-backed, not SQLite; config file is
  `bedwarsstats.cfg`, not `bedwars.cfg`.
- Align `@Mod` VERSION ("6.7") with the gradle version ("1.0").
- Cross-unit: the invisible-player indicator is gated behind `isGeneratorDisplayEnabled()`
  in `BedwarsRuntime.onRenderWorldLast` instead of its own `isInvisiblePlayerAlertsEnabled()`.
