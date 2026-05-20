# Review: API & Stats

Files:
- src/main/java/com/imshy/bedwars/HypixelAPI.java
- src/main/java/com/imshy/bedwars/BedwarsStats.java
- src/main/java/com/imshy/bedwars/HypixelMessages.java

## Summary

The async fetch lifecycle, Mojang/Hypixel error handling, and JSON null-guarding in `BedwarsStats` are generally careful and defensive. The two highest-impact problems are (1) the shared caches and rate-limit list are plain `HashMap`/`ArrayList` read from the Forge main thread (render/HUD code) while written from the 3-thread executor pool, which is a genuine data-corruption / `ConcurrentModificationException` race, and (2) the `calculateStars` formula adds a fabricated per-prestige XP penalty, undercounting stars for high-level players and thereby mis-rating their threat level. Several smaller robustness and maintainability items follow.

## Findings

### [High] Shared caches and rate-limit list are not thread-safe across executor + main thread

- Location: HypixelAPI.java:52-56 (declarations); written at :115, :139, :169, :190, :261, :293; read on main thread at :386 (`getCachedStats`), :397-409 and :414-418 (`getCacheStatus`)
- Issue: `statsCache` (HashMap), `uuidCache` (HashMap), and `requestTimestamps` (ArrayList) are unsynchronized. The executor pool (`newFixedThreadPool(3)`, :63-64) writes them from up to 3 background threads (`fetchStatsAsync`/`fetchStatsWithUuid` runnables, `resolveUUID`, `checkRateLimit`). Meanwhile `getCachedStats` is called every frame from render code on the Forge main thread (confirmed callers: render/HUD paths, `LobbyTrackerService`, `TeamDangerAnalyzer`, `WorldScanService`, `BedwarsRuntime`), and `getCacheStatus` iterates `statsCache.values()` and `requestTimestamps` on the command/main thread.
- Impact: Concurrent `HashMap.put` from multiple writer threads can corrupt the internal table; on Java 8 a corrupted bucket can make a later `get()` spin in an infinite loop (game freeze). Iterating `requestTimestamps`/`statsCache` in `getCacheStatus` while a background thread mutates the same collection throws `ConcurrentModificationException`. Both manifest non-deterministically under lobby load when many players are looked up at once.
- Suggested fix: Use `java.util.concurrent.ConcurrentHashMap` for `statsCache` and `uuidCache`, and a thread-safe structure (e.g. a `synchronized` list or `ConcurrentLinkedDeque`) for `requestTimestamps`, or guard all access with a single lock object. At minimum, synchronize every read/write of these three fields on a common monitor.

### [High] `calculateStars` uses an incorrect XP-per-level formula (fabricated per-prestige penalty)

- Location: BedwarsStats.java:233-263 (esp. :242, :246-248)
- Issue: Hypixel's real Bedwars leveling cost is fixed per prestige: the first 4 levels of each 100-level prestige cost 500/1000/2000/3500 XP and every subsequent level costs a flat 5000 XP, for a constant 487,000 XP per prestige cycle — the cost does **not** escalate with prestige. This code adds `threshold += (level / 100) * 500;` (:247), inflating the XP cost of every level beyond level 100. The `levelThresholds` table's 5th entry (5000) combined with `Math.min(level % 100, 4)` is roughly right for the flat tier, but the prestige bonus is invented.
- Impact: Stars are progressively undercounted for higher-level players (the error compounds each prestige). Since `getThreatLevel()` (:276-285) gates EXTREME on `stars >= 500` and HIGH on `stars >= 300`, a strong opponent can be under-rated, defeating the mod's core threat-assessment purpose. FKDR thresholds partially mask this, but star-only escalation is broken.
- Suggested fix: Replace the loop with the documented formula: per prestige of 487,000 XP, derive completed prestiges via `exp / 487000`, then map the remainder through the fixed 500/1000/2000/3500/5000 step table, with no per-prestige threshold increase. Removing the `(level / 100) * 500` term and keeping the flat 5000 tier is the minimal correctness fix.

### [Low] `getRecentWindow` threshold contradicts its constant's documented meaning

- Location: BedwarsStats.java:68-72 (`MIN_RECENT_FK_FOR_RELIABLE_FKDR`), :352, :355
- Issue: The constant is named for and documented as a minimum recent *final-kill* volume ("FK"), but the checks sum kills **and** deaths: `monthlyFinalKills + monthlyFinalDeaths >= 25`. A player who has 2 final kills and 24 final deaths passes the "reliable FKDR" gate despite negligible kill volume.
- Impact: The recent-FKDR window can be marked "reliable" for players with very few actual kills (e.g. low-skill players who died a lot), producing a noisy/misleading recent-form delta in the HUD. Low because it only affects an informational delta, not threat tiering.
- Suggested fix: Decide the intended semantics. If volume should be kills-only, change the checks to `monthlyFinalKills >= MIN_RECENT_FK_FOR_RELIABLE_FKDR`. If games-played volume is intended, rename the constant to reflect "final-kill+death sample size."

### [Low] Non-atomic increment of `rateLimitedRequests`

- Location: HypixelAPI.java:57 (declaration), :288, :319
- Issue: `rateLimitedRequests++` is executed from background executor threads (in `checkRateLimit` and `fetchHypixelStats`) without synchronization or `AtomicInteger`. It is also read on the main thread in `getCacheStatus` (:426).
- Impact: Lost updates under concurrency; the "requests blocked" counter shown by `/bw status` can under-report. Cosmetic only.
- Suggested fix: Make it an `AtomicInteger` (or guard under the same lock as the rate-limit list).

### [Low] `lastFetchError` attribution race across threads

- Location: HypixelAPI.java:58 (volatile field), set at :303, :320, :325, :330, :334, :348; read at :129 and :180
- Issue: `lastFetchError` is a single shared volatile updated by every fetch on every executor thread. After thread A sees `response == null`, thread B can overwrite `lastFetchError` before A reads it at :129/:180.
- Impact: A user-facing error message can name the wrong failure reason for a given lookup. Low — diagnostic text only.
- Suggested fix: Return the failure reason from `fetchHypixelStats` (e.g. a small result object or an out-param) instead of stashing it in shared state, so the reason is bound to the request that produced it.

### [Low] HTTP connections / streams not closed on non-200 and error paths

- Location: HypixelAPI.java:227-268 (`resolveUUID`), :300-351 (`fetchHypixelStats`)
- Issue: `HttpURLConnection` is opened but never explicitly disconnected, and on the early-return branches (404/non-200 at :235-239, 429/403/non-200 at :317-332) the connection's error stream is never read or closed. The success-path `BufferedReader` is closed via `reader.close()` (:248, :342) but only if no exception is thrown between open and close — there is no `try/finally`.
- Impact: Under HTTP errors (e.g. a burst of 429/403), the JVM may leak the underlying socket/keep-alive resources because the error stream is left unconsumed; `HttpURLConnection` keep-alive reuse degrades. Low for a client mod, but accumulates over a long session with many failed lookups.
- Suggested fix: Wrap reads in try/finally (or try-with-resources) and call `conn.disconnect()` / drain `conn.getErrorStream()` on the error branches. On JDK 8, prefer reading and closing the error stream so the connection can be pooled.

### [Low] Caches grow unbounded; `getCacheStatus` counts expired entries into `oldestAge`

- Location: HypixelAPI.java:52-53, :385-391, :397-409
- Issue: Expired entries are never evicted — `getCachedStats` simply returns null for an expired entry but leaves it in the map; only `clearCache()` (:376) empties the caches. `getCacheStatus` computes `oldestAge` (:405-408) across all entries including expired ones, so the reported "oldest" can refer to a stale entry.
- Impact: Memory slowly grows with every distinct player/nick name observed across a session (lobbies churn many names); the status line can over-report oldest age. Low.
- Suggested fix: Add lazy eviction (drop expired entries when encountered) or a periodic sweep, and skip expired entries when computing `oldestAge`.

### [Low] Executor uses non-daemon threads; shutdown only via JVM shutdown hook

- Location: HypixelAPI.java:63-64 (`newFixedThreadPool(3)`), :369-371 (`shutdown`); BedwarsMod.java:34-39 (shutdown hook)
- Issue: `Executors.newFixedThreadPool(3)` uses the default thread factory, producing non-daemon threads. `shutdown()` is only invoked from a JVM `Runtime.addShutdownHook` (BedwarsMod.java:34), not on mod/world unload, and `executor.shutdown()` does not interrupt in-flight 5s-timeout HTTP calls.
- Impact: Background threads remain alive for the whole client session and can briefly delay clean exit; in-flight requests are not cancelled on shutdown. Practically minor because the shutdown hook does eventually run.
- Suggested fix: Supply a custom `ThreadFactory` that names the threads (e.g. "bw-api-N") and marks them daemon, so they never block JVM exit and are easier to diagnose.

### [Low] `FINAL_KILL_PATTERN` killer groups (2/3) are fragile and effectively unreliable

- Location: HypixelMessages.java:55-56
- Issue: The regex `^([A-Za-z0-9_]{1,16}) .*?(?:by ([A-Za-z0-9_]{1,16})|escape ([A-Za-z0-9_]{1,16}))?\.?\s*FINAL KILL!` combines a lazy `.*?` with an *optional* killer group. Because the killer alternation is optional, the engine prefers to satisfy the trailing `FINAL KILL!` by backtracking without capturing the killer, so groups 2/3 frequently come back null even for messages that do name a killer.
- Impact: Currently latent — the only consumer (`BedwarsRuntime.handleFinalKill`, ~:1100-1108) reads `group(1)` (victim) only, so nothing breaks today. But the documented intent that "Group 2 may be the killer" is misleading and will bite anyone who later relies on the killer capture.
- Suggested fix: If killer attribution is wanted, make the killer segment required within its branch and anchor it (e.g. require `by <name>` / `escape <name>` immediately before the optional `.` and `FINAL KILL!`), or split into explicit per-cause patterns. Otherwise document that groups 2/3 are unreliable.

## Improvements

- HypixelMessages.java is otherwise clean: it is a constants holder with a private constructor and good documentation. No correctness issues beyond the regex note above.
- `BedwarsStats.parseFromJson` is well-guarded against null/missing/off-shape JSON (`getInt`/`getObject`/`getBoolean` all null-check, type-check, and try/catch). `computeRatio` (:222-227) correctly avoids integer div-by-zero and returns `Double.MAX_VALUE` for n/0 (rendered as ∞), so FKDR/WLR/win-rate division is safe.
- `resolveUUID` correctly distinguishes Mojang HTTP 404 (definitive nick) from transient failures via `UuidLookupResult.notFound`, and the nick-detection cascade in `parseFromJson` (:117-174) is thoughtfully layered. This logic is sound.
- Consider extracting the duplicated cache-check + fetch-runnable body shared by `fetchStatsAsync` and `fetchStatsWithUuid` (:90-148 / :153-200) into one private helper to reduce drift between the two paths.
- Threading contract is inconsistent: on a cache hit the callback runs synchronously on the *caller* (main) thread (:95, :158), but on a miss it runs on an executor thread (:116/:141/:192). Document this, or always dispatch the callback on a consistent thread, so consumers know whether `onStatsLoaded` may touch main-thread-only Minecraft state. (Several runtime callbacks do touch Minecraft world/entity state from the executor thread — root-caused here, but the consumer fix lives in the runtime package.)
- `calculateStars` allocates a fresh `int[]` table on every call (:242); make it a `static final` field.

REPORT: docs/review/01-api-stats.md — 2 high, 0 med, 7 low
