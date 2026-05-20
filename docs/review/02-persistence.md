# Review: Persistence & Blacklist

Files:
- src/main/java/com/imshy/bedwars/PlayerDatabase.java
- src/main/java/com/imshy/bedwars/AutoBlacklistManager.java

## Summary

The brief and `CLAUDE.md` describe `PlayerDatabase` as "SQLite-backed persistent storage." The actual implementation is a single Gson-serialized JSON file at `config/bedwarsstats/playerdata.json` — there is no JDBC, `Connection`, `Statement`, or `ResultSet` anywhere in the codebase (`grep` for `sqlite|jdbc|java.sql` returns nothing). This review therefore audits what exists: file I/O, an in-memory `HashMap`/`HashSet` model, and the JSON load/save path. The SQLite-specific concerns in the brief (connection lifecycle, prepared-statement binding, SQL injection, transactions, schema migration) do not apply — there is no SQL.

All current call sites of both classes execute on the Forge client main thread: DB mutations originate from `BedwarsRuntime` (chat/game events), `BedwarsCommand` (client command), `LobbyTrackerService` (chat/tick events), and `AutoBlacklistManager` (invoked synchronously from `BedwarsRuntime` on a LOSS). The `HypixelAPI` async pool does **not** write to the DB — its callbacks in `LobbyTrackerService` only mutate a local `PlayerJoinEntry`. Thread-safety issues are therefore *latent* (correct today, fragile the moment a caller moves to the executor pool), not active races, and are scored accordingly.

The two real correctness/robustness concerns are: (1) file I/O that leaks handles and is non-atomic (a crash mid-write destroys the only data file), and (2) nominally read-only query methods that silently perform synchronous disk writes on the main thread.

`AutoBlacklistManager.processLossOutcome` is logically correct: threshold/lookback/cooldown checks are sound, the current loss is intentionally counted (`recordGameEnd` runs before `processLossOutcome`), and `countRecentOutcomes` filters outcome and window correctly. No defect found there.

## Findings

### [Medium] save() and load() leak file handles and are not atomic
- Location: PlayerDatabase.java:436-438 (save), 458-460 (load)
- Issue: `save()` opens a `FileWriter`, calls `gson.toJson(root, writer)`, then `writer.close()` — all in a plain `try` with no `finally`/try-with-resources (JDK 8 supports try-with-resources). If `gson.toJson` throws (e.g. disk full, serialization error), `close()` never runs and the handle leaks. `load()` has the same shape with `FileReader` at 458-460. More importantly, `save()` writes directly to `DATA_FILE`: if the process is killed or throws mid-write, `playerdata.json` is left truncated/corrupt. There is no temp-file-plus-rename and no backup.
- Impact: Leaked descriptors accumulate across the many `save()` calls (every blacklist add/remove and every game end). A crash during a write corrupts the user's entire blacklist + encounter history with no recovery. On the next `load()`, the broad `catch (Exception)` (line 487) swallows the parse failure and silently starts the user with an empty DB — all history and blacklist entries gone, no warning beyond a debug-less `LOGGER.error`.
- Suggested fix: Use try-with-resources for both streams. In `save()`, write to `playerdata.json.tmp`, flush/close, then atomically `File.renameTo` (or `Files.move` with `ATOMIC_MOVE`) over the real file; on parse failure in `load()`, rename the bad file to `playerdata.json.corrupt` before resetting to empty so data is recoverable.

### [Medium] Query methods isBlacklisted / getBlacklistEntry perform synchronous disk writes on the main thread
- Location: PlayerDatabase.java:174-176 (isBlacklisted), 193-195 (getBlacklistEntry)
- Issue: Both methods are named/documented as reads but, when they encounter an expired AUTO entry, call `blacklist.remove(key)` followed by `save()` — a full pretty-printed JSON serialization of the entire DB written to disk. These are invoked from hot, per-player paths: `LobbyTrackerService:150-151` (every lobby join), `PreGameBriefing:116` (per tab-list player), and `MatchSummary:91,103` (per player at game end, twice per name on a LOSS).
- Impact: A lookup that callers reasonably assume is cheap can trigger blocking file I/O on the render/main thread, multiplied by player count (up to ~16). Frame stutter on join bursts; also surprising side-effects from "getter" methods (a `getBlacklistEntry` call mutates persistent state).
- Suggested fix: Separate concerns — keep `isBlacklisted`/`getBlacklistEntry` pure (treat an expired entry as absent without removing/saving), and let the existing `cleanupExpiredAutoBlacklistEntries()` / `purgeExpiredBlacklistEntriesIfNeeded()` handle removal in batch. If lazy removal must stay, drop the inline `save()` and let the next real mutation persist it.

### [Medium] currentGamePlayers stored with raw case while all other keys are lowercased
- Location: PlayerDatabase.java:254-256 (addToCurrentGame), vs. 287 (recordEncounter lowercases), 115/125/155/168/187 (blacklist lowercases)
- Issue: `addToCurrentGame` inserts the raw `playerName` into the `currentGamePlayers` HashSet. Every other map/lookup in the class keys on `playerName.toLowerCase()`. `recordGameEnd` (line ~276) iterates `currentGamePlayers` and calls `recordEncounter(playerKey, ...)`, which then lowercases when writing history. The set therefore holds original-case names while history/blacklist hold lowercase keys.
- Impact: Today there is a single caller (`LobbyTrackerService:147`), so duplicates are unlikely in practice. But the set's identity is case-sensitive: if the same player is ever added from a second source with different casing (e.g. a future tab-list path) or rejoins under a differently-cased nick within the session, the HashSet keeps both entries and `recordGameEnd` records the loss twice into the same lowercase history bucket. That double-counted loss feeds directly into `AutoBlacklistManager.countRecentOutcomes`, skewing the threshold. It is a latent correctness landmine in the exact data path that drives auto-blacklisting.
- Suggested fix: Lowercase on insert in `addToCurrentGame` (`currentGamePlayers.add(playerName.toLowerCase())`) so the session set is keyed consistently with everything else. If original case is needed for display, store a separate `Map<String,String>` lowercase→displayName.

### [Low] Non-thread-safe lazy singleton (getInstance) — latent
- Location: PlayerDatabase.java:26 (`private static PlayerDatabase instance;`), 102-107 (getInstance)
- Issue: Classic unsynchronized, non-`volatile` check-then-act lazy init. Two threads calling `getInstance()` concurrently can both see `instance == null` and each run the constructor (which calls `load()`), and a thread could observe a partially-constructed instance.
- Impact: None today — all callers are on the main thread. Becomes a real double-load / partial-publication bug the moment any caller runs on the `HypixelAPI` executor pool.
- Suggested fix: Initialize eagerly (`private static final PlayerDatabase instance = new PlayerDatabase();`) or use the holder idiom / `synchronized` getInstance with a `volatile` field.

### [Low] Unsynchronized mutation of shared collections — latent
- Location: PlayerDatabase.java:29 (blacklist), 32 (history), 35 (currentGamePlayers) — plain `HashMap`/`HashSet`, mutated in addToBlacklist/removeFromBlacklist/recordEncounter/addToCurrentGame/clearCurrentGame and the expiry-removal paths
- Issue: None of the collections are synchronized or concurrent. `getCurrentGamePlayersSnapshot()` (268) copies under no lock; `recordGameEnd` (275) iterates `currentGamePlayers` directly while other code could mutate it.
- Impact: None today (single-threaded access). If DB writes ever move to the async pool, expect `ConcurrentModificationException` and lost updates.
- Suggested fix: Document the main-thread-only invariant explicitly, or wrap in `Collections.synchronized*` / use concurrent collections if off-thread access is ever introduced.

### [Low] addOrRefreshAutoBlacklist overwrites original addedAt on refresh
- Location: PlayerDatabase.java:142 (`existing.addedAt = now;`)
- Issue: When refreshing an existing AUTO entry, `addedAt` is reset to the current time. `BedwarsCommand:459` renders "added N days ago" from `addedAt`.
- Impact: The `/bw blacklist` list shows the most recent refresh time, not when the player was first auto-blacklisted — minor semantic drift / misleading display. The dedicated `lastAutoAddAt` field already tracks the refresh time, so resetting `addedAt` is redundant.
- Suggested fix: Leave `addedAt` unchanged on refresh; only update `lastAutoAddAt`, `reason`, and `expiresAt`.

### [Low] getBlacklistedPlayers() returns a live mutable view
- Location: PlayerDatabase.java:213-216
- Issue: Returns `blacklist.values()` directly — a live, modifiable view of the internal map. `BedwarsCommand:458` iterates it. Because `purgeExpiredBlacklistEntriesIfNeeded()` runs first and the iteration is main-thread, there is no CME today, but the encapsulation is broken: a caller could mutate the backing map, and any future off-thread expiry removal would CME the command's iteration.
- Impact: Latent fragility / leaky abstraction. No active bug.
- Suggested fix: Return a defensive copy: `new ArrayList<BlacklistEntry>(blacklist.values())`.

## Improvements

- **Async/debounced persistence.** `save()` runs synchronously and serializes the whole DB on every single mutation (each blacklist add/remove, each `recordGameEnd`, each lazy expiry removal). Consider a dirty-flag + periodic/async flush, or at minimum coalescing the multiple saves that occur during a single game-end sequence (`recordGameEnd` saves once, then `processLossOutcome` saves once per auto-added player, then `clearCurrentGame`). On a LOSS with several eligible opponents this is several full-file writes back-to-back on the main thread.
- **Doc/code mismatch.** Update `CLAUDE.md` and any architecture notes to say the store is a Gson JSON file, not SQLite, to avoid future contributors hunting for a DB layer that doesn't exist.
- **Confirmed non-issues (verified, do not need changes):** `int * DAY_MS` arithmetic in `AutoBlacklistManager:32`/`PlayerDatabase:134,314` does not overflow — the `int` is widened to `long` before the multiply and config caps days at 90/180. `countRecentOutcomes` outcome/window filtering is correct. The `recordGameEnd`-before-`processLossOutcome` ordering is intentional so the current loss counts toward the threshold.
