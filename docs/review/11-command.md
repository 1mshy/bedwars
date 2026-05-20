# Review: Command (/bw)

Files: src/main/java/com/imshy/bedwars/command/BedwarsCommand.java

## Summary

`BedwarsCommand` is a single `CommandBase` implementation that dispatches 16 subcommands through a clean `if/else-if` chain with a terminal `else` for unknown input. Argument-bounds handling is generally solid: every subcommand that indexes `args[1]`/`args[2]` is preceded by an `args.length < N` guard, and there is no `parseInt`/`parseDouble` of user input anywhere in the file, so there are no `ArrayIndexOutOfBounds` or `NumberFormatException` exposures from user input.

The one serious correctness defect is **async callback safety**: the `lookup` and `all` subcommands fire `HypixelAPI` callbacks that call `addChatMessage` directly, and on the cache-miss path those callbacks execute on the background executor thread rather than the Minecraft client thread. This mutates client chat state off-thread. Everything else is robustness/maintainability polish: a plaintext API-key note, missing trim/empty validation on player-name input, a couple of usage-string inconsistencies, and a hardcoded-constant duplication in the history command.

Counts: 1 High, 0 Medium, 5 Low.

## Findings

### [HIGH] Async stat callbacks touch Minecraft chat off the main thread

- Location: BedwarsCommand.java:87-133 (`lookup`) and BedwarsCommand.java:161-185 (`all`)
- Issue: Both subcommands pass a `HypixelAPI.StatsCallback` whose `onStatsLoaded`/`onError` bodies call `mc.thePlayer.addChatMessage(new ChatComponentText(...))` (lookup at line 122 / 129; `all` at line 167 and in its `onError` at line 180). `HypixelAPI.fetchStatsAsync`/`fetchStatsWithUuid` invoke the callback **synchronously on the caller (main) thread** only for the cache-hit and no-API-key paths (HypixelAPI.java:95, 101, 158, 164). On a cache **miss** they run inside `executor.submit(...)` (HypixelAPI.java:106, 172), so the callback — and therefore `addChatMessage` — executes on a background pool thread. `addChatMessage` mutates `GuiNewChat`'s internal chat-line list without synchronization, and `mc.thePlayer` may change/become null between the null check and the deref.
- Impact: On the most common path (uncached player — exactly what `/bw lookup` and `/bw all` are for), chat lines are appended from a non-render thread. This is a data race that can throw `ConcurrentModificationException`/NPE inside the GUI chat code, corrupt the chat buffer, or crash the client. The `mc.thePlayer != null` check at line 166 is not a thread-safety guard and does not prevent this.
- Suggested fix: Marshal the UI work back onto the client thread inside each callback, e.g. wrap the body in `Minecraft.getMinecraft().addScheduledTask(() -> { ... })`. Apply to all four callback bodies (lookup `onStatsLoaded`/`onError`, `all` `onStatsLoaded`/`onError`). This is safe even on the synchronous cache-hit path since `addScheduledTask` simply runs next tick.

### [LOW] API key persisted/echoed in plaintext

- Location: BedwarsCommand.java:71-77 (`setkey`); ModConfig.setApiKey at ModConfig.java:870-877
- Issue: `/bw setkey <key>` stores the key verbatim via `ModConfig.setApiKey(args[1])`, which writes it to `config/bedwars.cfg` in plaintext. The typed command also lands in the client's local chat scrollback. The success message (line 77) does not echo the key, which is good.
- Impact: Low — this is standard for client-side mods of this era and the key never leaves the client. Worth noting only because the key sits in plaintext config and in local chat history (e.g. visible if the user screenshares/streams).
- Suggested fix: Optional. Consider masking the key in any future debug logging and adding a brief reminder in the success message not to share screenshots after running `setkey`. No code change strictly required.

### [LOW] Player-name arguments are not trimmed or empty-checked

- Location: BedwarsCommand.java:84 (`lookup` -> `targetPlayer = args[1]`); BedwarsCommand.java:433 (`blacklist add`); BedwarsCommand.java:493 (`history`)
- Issue: User-supplied names are taken straight from `args[1]` with no `trim()` or non-empty validation before being used as cache keys / DB keys / Mojang lookups. A whitespace-only or oddly-cased token flows directly into `HypixelAPI.fetchStatsAsync` and `PlayerDatabase` keys (the DB lowercases keys but does not trim).
- Impact: Low — produces a wasted Mojang/Hypixel lookup that fails with a generic error, or stores a degenerate blacklist/history key. Not a crash.
- Suggested fix: Trim the name and reject empty input with the existing usage message, e.g. `String targetPlayer = args[1].trim(); if (targetPlayer.isEmpty()) { sendMessage(... usage ...); return; }`.

### [LOW] Usage strings inconsistent with accepted subcommands/modes

- Location: BedwarsCommand.java:35 (`getCommandUsage`) and BedwarsCommand.java:522 (`autoplay` usage)
- Issue: `getCommandUsage` advertises `afk`/`pearlpreview`/`nametags` and is mostly in sync, but the autoplay usage line at 522 lists `ones|twos|threes|fours|stop|requeue` while the invalid-mode error at line 558 says only `ones, twos, threes, fours, or stop` (omitting `requeue`). Minor message drift between the two autoplay help strings.
- Impact: Low — purely cosmetic; can confuse users about whether `requeue` is valid.
- Suggested fix: Align the invalid-mode message at line 558 with the usage list at 522 (include `requeue`), or centralize the mode list in one constant referenced by both.

### [LOW] Subcommand list duplicated between dispatcher and tab-completion (drift risk)

- Location: BedwarsCommand.java:283-285 (tab-completion list) vs. the dispatch chain at BedwarsCommand.java:71-274
- Issue: The tab-completion `getListOfStringsMatchingLastWord(...)` array and the `if/else-if` dispatch chain are maintained as two independent hardcoded lists. They currently match (all 16 subcommands present in both), so this is not a present bug — but adding/removing a subcommand requires editing both places, and the autoplay usage drift above shows that kind of divergence already creeps in.
- Impact: Low — future maintenance hazard; a forgotten edit yields a subcommand that works but never tab-completes (or vice versa).
- Suggested fix: Define a single `static final String[] SUBCOMMANDS` (or an enum) and drive both tab-completion and, ideally, dispatch from it.

### [LOW] Hardcoded "5" duplicated in history output

- Location: BedwarsCommand.java around 506-508 (`handleHistoryCommand`)
- Issue: The recent-games display computes a bound with `Math.min(5, ...)` and then iterates with a separately hardcoded `5`. They agree today, but the limit is expressed twice.
- Impact: Low — purely maintainability; if the limit changes in one spot the loop and the label diverge.
- Suggested fix: Extract a single `int RECENT_GAMES_LIMIT = 5` (or local variable) and use it in both the `Math.min` and the loop bound.

## Improvements

- Dispatch completeness is good: the chain at lines 71-274 ends with a proper `else` (line 274) sending "Unknown command. Use /bw for help." There is no fall-through or missing subcommand.
- Argument bounds are correct throughout — every `args[1]`/`args[2]` access is guarded by an `args.length < N` check (`setkey` 72, `lookup` 80, `autoplay` 521, `blacklist` 421/428, `history` 486, tab-completion `args.length == 1/2/3`). No AIOOB and no integer/double parsing of user input, so no NumberFormatException surface.
- DB-backed reads used by the command (`getEncounterHistory`, `getBlacklistedPlayers`/`getBlacklistSize`, `hasPlayedBefore`, `getWinLossRecord`, `getHistorySize`) are in-memory map lookups (PlayerDatabase.java:213, 297, 333, 348), not synchronous SQLite queries — so `handleHistoryCommand`/`handleBlacklistCommand`/`handleInfoCommand` do not block the main thread on disk I/O. No finding here.
- `handleAutoplayCommand` is well-ordered: valid-mode check (557) and API-key check (562) both run before `runtime.startAutoplay` (567), and `runtime.requestPartyList(mc)` (570) is internally null-guarded (BedwarsRuntime.java:176), with an additional `mc.thePlayer != null` guard at line 579. No NPE risk.
- `handleInfoCommand` correctly guards `mc.theWorld == null || mc.thePlayer == null` (line 327) before iterating `playerEntities`.
- Consider centralizing the repeated `EnumChatFormatting.GOLD + "[BW] " + ...` prefix used across the toggle subcommands (afk/pearlpreview/nametags) into the existing `sendMessage` helper or a small `sendBwMessage` wrapper to reduce duplication.
