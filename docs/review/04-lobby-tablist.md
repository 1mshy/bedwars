# Review: Lobby, Tab List & Team Detection

Files:
- `src/main/java/com/imshy/bedwars/runtime/LobbyTrackerService.java`
- `src/main/java/com/imshy/bedwars/runtime/TabListScanner.java`
- `src/main/java/com/imshy/bedwars/runtime/ArmorColorTeamDetector.java`

## Summary

The three files are generally defensive about nulls and use `synchronized` blocks
around the shared `recentJoins` list. The most material problems are: (1) a cross-thread
data race on `PlayerJoinEntry.stats`, which is written by the async stat-lookup pool and
read by the render thread without synchronization or `volatile`; (2) a check-then-act
(TOCTOU) gap in the `recentJoins` dedupe in `trackPlayerJoin`; (3) `pendingTabListFetches`
entries are added but never removed on lookup failure, so a player whose first lookup
fails (rate limit, transient network error) is never retried for the rest of the
game/lobby; and (4) tab-list color/team parsing is brittle in the lobby because
`parseFirstColorCode` and `parseTeamName` cannot distinguish a rank prefix (`[MVP+]`,
`§b...`) from a team prefix. `ArmorColorTeamDetector` is correct and well-guarded, with
one inherent heuristic-ambiguity caveat. `scanAllPlayers` is re-run (full iteration +
`getFormattedText()` + regex strip per player) on every HUD render frame, which is a
modest but avoidable per-frame cost.

Note: the project's CLAUDE.md attributes "party detection via join-message burst analysis"
to `LobbyTrackerService`, but that logic (`joinBurstNames`, `joinMessageBurstCount`,
`chatDetectedPlayers`) actually lives in `BedwarsRuntime` and `RuntimeState`, outside this
review unit. The burst-window timing logic itself is therefore not assessed here; only its
interaction points with the assigned files are noted.

## Findings

### [High] `PlayerJoinEntry.stats` written by pool thread, read by render thread — data race
- Location: `LobbyTrackerService.java:170` (write), `PlayerJoinEntry.stats` field at `LobbyTrackerService.java:235`; read on render thread via `BedwarsHudRenderer`/`recentJoins`
- Issue: In `trackPlayerJoin`, the async callback does `entry.stats = stats;` (line 170). `entry` is a `PlayerJoinEntry` stored in the shared `state.recentJoins` list. The HypixelAPI callbacks run on the 3-thread executor pool (per CLAUDE.md / `HypixelAPI`), while the HUD reads `recentJoins` entries on the Forge main thread. The `stats` field is a plain (non-`volatile`) reference and is written/read outside any common lock — the `synchronized (state.recentJoins)` blocks guard list structure, not the mutation of `entry.stats` after the entry is already in the list.
- Impact: Under the Java Memory Model there is no happens-before edge between the pool-thread write and the main-thread read, so the HUD may indefinitely see stale `null` (stats never appear) or, on weak memory orderings, a partially published object. Symptom would be intermittent "stats never show for some players" that is hard to reproduce.
- Suggested fix: Make `PlayerJoinEntry.stats` `volatile`, or assign it inside a `synchronized (state.recentJoins)` block and read it inside the same lock in the renderer. `volatile` is the lowest-friction fix for a single-reference field.

### [Medium] TOCTOU race — duplicate `recentJoins` entries when two callers race
- Location: `LobbyTrackerService.java:130-143` (`trackPlayerJoin`)
- Issue: The dedupe is split across two separate `synchronized (state.recentJoins)` blocks: the first (130-137) scans for an existing entry with the same name, updates its timestamp, and `return`s on a match (line 134) — so the single-threaded path is correct and does not duplicate. The flaw is that the check (130-137) and the add (141-143) are not in the *same* critical section. Because the lock is released between them, two concurrent callers (e.g. the `scanExistingPlayers` loop on one path and an `onEntityJoinWorld`/event-driven `trackPlayerJoin` on another) can both pass the "not present" check and both reach the add, inserting two entries for the same player.
- Impact: Duplicate `recentJoins` entries for the same player → duplicated HUD lines and duplicated DB/blacklist/stat work. Whether this can actually happen depends on whether `trackPlayerJoin` is ever invoked off the main thread; today its callers (`scanExistingPlayers`, `onEntityJoinWorld`) appear main-thread, so the practical risk is low, but the split-lock pattern is fragile and will break the moment a second caller thread is added.
- Suggested fix: Perform find-or-create inside a single `synchronized (state.recentJoins)` block: scan, and if not found, create and add the entry before releasing the lock. Do the DB/blacklist/stat work after releasing the lock, keyed on whether a new entry was actually created.

### [Medium] `pendingTabListFetches` entries never removed → failed lookups never retried
- Location: `LobbyTrackerService.java:198-208` (`scanTabListPlayers`); same pattern in `TeamDangerAnalyzer.java:90-98`
- Issue: A name is added to `state.pendingTabListFetches` before the async fetch (line 202), but neither `onStatsLoaded` nor `onError` removes it (the callbacks are empty, lines ~206-208). The set is only cleared on full phase reset (`RuntimeState.reset()` line 151). The guard at line 198 then permanently suppresses re-fetching that name.
- Impact: If the *first* lookup for a player fails (client-side rate limit hit, transient network error, Mojang UUID resolve failure), that player's stats are never retried for the remainder of the lobby/game, even though a retry seconds later might succeed. Players silently show as "unknown" with no recovery path.
- Suggested fix: Remove the name from `pendingTabListFetches` in `onError` (and optionally in `onStatsLoaded`) so a later scan can retry. To avoid hammering a hard-failing name, track a small per-name retry count or a "failed-recently" timestamp instead of a permanent suppression set.

### [Medium] Tab-list color/team parsing cannot distinguish rank prefix from team prefix (lobby brittleness)
- Location: `TabListScanner.java:87-97` (`parseFirstColorCode`) and `TabListScanner.java:99-115` (`parseTeamName`)
- Issue: `parseFirstColorCode` returns the *first* `§<0-9a-f>` color code in the formatted name. `parseTeamName` returns the first `[...]` bracket group. In a Bedwars *lobby* (PRE_GAME) and in many tab formats, the displayed name leads with a rank tag like `§b[MVP§c+§b] §fPlayerName`. The first color code is then the *rank* color (`b`), not a team color, and the first bracket group is the rank string (`MVP+`), not a team. The in-game (IN_GAME) tab format is team-colored, so the heuristic works there, but it is fragile to any leading rank/level/prestige decoration.
- Impact: When this parse is used outside the clean in-game team layout (e.g. the HUD high-threat list `BedwarsHudRenderer.addHighThreatPlayerList`, which filters by `teamColorCode` equality with the local player's), players can be mis-grouped or wrongly included/excluded from "enemy" lists. `teamName` can surface "MVP+"/"VIP" as a team label. `TeamDangerAnalyzer` is gated to IN_GAME so it is largely safe, but the heuristic's contract is implicit and easily violated by formatting changes.
- Suggested fix: Anchor team parsing to the known game format rather than "first code/bracket". Options: (a) skip a leading rank tag by consuming the first bracketed token if it matches a known rank set before reading the team token; (b) in-game, derive the team from the scoreboard/`ScorePlayerTeam` color (`mc.theWorld.getScoreboard().getPlayersTeam(name)`), which is unambiguous; (c) at minimum, document that these methods assume the in-game (rank-stripped) tab format and are not valid in the lobby.

### [Low] `scanAllPlayers` re-parses entire tab list on every HUD render frame
- Location: `TabListScanner.java:13-63` (`scanAllPlayers`), invoked from `BedwarsHudRenderer.java:436` (per-frame render path) and `TeamDangerAnalyzer.java:70,156`
- Issue: Each call allocates a new `ArrayList`, iterates the full player-info map, and for every player calls `info.getDisplayName().getFormattedText()` plus `EnumChatFormatting.getTextWithoutFormattingCodes(...)` (regex-based strip) and constructs a `TabListPlayer`. `BedwarsHudRenderer.addHighThreatPlayerList` calls it once per render frame (potentially 60+ times/sec).
- Impact: Repeated allocation and string/regex work each frame. Small for typical lobby sizes but pure garbage churn on the render hot path; it scales with player count and frame rate.
- Suggested fix: Cache the `scanAllPlayers` result for a short interval (e.g. 250–500 ms) keyed off `clientTickCounter` / `System.currentTimeMillis()`, or compute it once per tick in the runtime and share it with the renderer, rather than recomputing per frame.

### [Low] `recentJoins.clear()` in `RuntimeState.reset()` is unsynchronized while all other accesses are synchronized
- Location: `RuntimeState.java:133` (`recentJoins.clear()`), contrasted with the synchronized clear of `chatDetectedPlayers` at `RuntimeState.java:123-125`
- Issue: Every other access to `recentJoins` (`LobbyTrackerService.java:130,141,216,227`; `WorldScanService.java:132`) is wrapped in `synchronized (state.recentJoins)`, but `reset()` clears it without the lock — unlike the adjacent `chatDetectedPlayers.clear()`, which *is* synchronized. This is an inconsistency in the locking discipline.
- Impact: If `reset()` ever overlaps an iteration/add on another path, it can produce a `ConcurrentModificationException` or lost/duplicated entries. Practical risk depends on whether `reset()` is strictly main-thread; even if so, the inconsistency is a latent footgun.
- Suggested fix: Wrap `recentJoins.clear()` in `synchronized (recentJoins)` to match the established discipline (and consider doing the same for the other collections cleared in `reset()` that are synchronized elsewhere).

### [Low] `InterruptedException` swallowed with `printStackTrace()` on a spawned thread (`/p list`)
- Location: `LobbyTrackerService.java` around the `/p list` background thread (~lines 90-101)
- Issue: The party-list helper spawns a raw `new Thread(...)` that sleeps and then sends `/p list`, catching `InterruptedException` with `e.printStackTrace()`. The interrupt status is not restored and the exception is only printed.
- Impact: Minor: a spawned thread per invocation (no pool) and a swallowed interrupt. Not a correctness bug, but inconsistent with the rest of the codebase's logging (`LOGGER`) and thread management.
- Suggested fix: Log via `LOGGER.warn(...)` and restore the interrupt (`Thread.currentThread().interrupt()`), or schedule the delayed send off the existing tick loop (compare `System.currentTimeMillis()` against a scheduled time) instead of spawning a thread.

## Improvements

- `ArmorColorTeamDetector` is correct and well-guarded: it checks null/`getItem()==null`,
  verifies `instanceof ItemArmor` and `ArmorMaterial.LEATHER`, and reads the NBT path
  `display.color` with proper tag-type checks (`hasKey("display", 10)`, `hasKey("color", 3)`),
  returning `null` for undyed leather. The helmet→chestplate fallback and the 0=boots…3=helmet
  slot mapping match MCP 1.8.9. One inherent caveat worth a code comment: the heuristic is an
  *equality of dye RGB* test, so it cannot disambiguate teams that share a dye color, and two
  unrelated players whose first dyed-leather slot happens to match will read as "same team".
  In standard Bedwars the eight team dyes are distinct, so this is acceptable, but the
  ambiguity (and the fact that a missing/non-leather slot yields `false` from
  `hasSameTeamColor`, i.e. "unknown" is treated as "not same team") should be documented at
  the call site (`MatchThreatService.java:364`).

- `parseFirstColorCode` loop bound `i < length - 1` correctly avoids an out-of-bounds read
  when `§` is the final character; this edge is handled. No change needed, but a one-line
  comment would prevent a future "off-by-one fix" that reintroduces a bug.

- The NPC/bot filter that `scanAllPlayers` relies on, `MatchThreatService.isWatchdogBotName`
  (`MatchThreatService.java:252`), classifies any name that is exactly 10 characters of
  lowercase letters/digits as a bot. Real Hypixel usernames can be 10 chars of lowercase
  letters (e.g. `darkknight`), so this can false-positive and silently drop a legitimate
  player from tab-list scans and stat lookups. It is outside this unit's files but is a direct
  dependency of `TabListScanner`; consider tightening it (Watchdog bot names are typically a
  fixed pattern) or documenting the false-positive risk.

- Consider extracting the duplicated "add to `pendingTabListFetches` + `fetchStatsAsync` with
  empty callbacks" block (`LobbyTrackerService.scanTabListPlayers` and
  `TeamDangerAnalyzer.buildTeamDangerSummary`) into a single helper so the
  fix for the never-removed-on-failure issue (above) lands in one place.

- Leftover artifact: `src/main/java/com/imshy/bedwars/runtime/BedwarsRuntime.java.orig` (33 KB)
  is checked into the source tree. Not in this unit's files, but it sits in the same package
  and should be removed from version control.
