# Review: Runtime Core & Game State

Files:
- `src/main/java/com/imshy/bedwars/runtime/BedwarsRuntime.java`
- `src/main/java/com/imshy/bedwars/runtime/RuntimeState.java`
- `src/main/java/com/imshy/bedwars/runtime/ScoreboardGameStateDetector.java`
- `src/main/java/com/imshy/bedwars/runtime/GamePhase.java`
- `src/main/java/com/imshy/bedwars/runtime/ChatDetectedPlayer.java`

## Summary

The runtime core is the central Forge event handler (`BedwarsRuntime`) plus its
mutable state bag (`RuntimeState`), the scoreboard-based phase detector, and two
small data/enum types. The state machine and chat-driven transitions are
generally sound, and several chat patterns (LOSS, PLAYER_LEFT/SENDING) have
clearly been hardened against substring false positives.

The dominant problem area is the **thread boundary** between the Forge main
(client) thread and the `HypixelAPI` executor pool. `HypixelAPI.fetchStatsAsync`
delivers its callback on a background thread, yet the callback body in
`BedwarsRuntime.handleChatMessageStatLookup` reads/writes shared non-volatile
state, iterates the live Minecraft world entity list, and sends chat — all of
which are main-thread-only operations in 1.8.9. These are the highest-severity
findings. There is also one remaining `chatDetectedPlayers` synchronization gap
in the WIN/LOSS handlers, and a few brittle chat substring matches plus two
small leaks/cleanup gaps.

`GamePhase.java` and `ChatDetectedPlayer.java` are clean.
`ScoreboardGameStateDetector.java` is functionally clean (only a minor
per-poll allocation note in Improvements).

## Findings

### [HIGH] Async stat callback iterates live Minecraft world off the main thread

- Location: `BedwarsRuntime.java:1018` (inside `StatsCallback.onStatsLoaded`, registered at line 968)
- Issue: For a cache-miss, `HypixelAPI.fetchStatsAsync` runs `onStatsLoaded` on
  its executor thread pool. The callback body iterates
  `mcInner.theWorld.playerEntities` (line 1018) and calls
  `matchThreatService.isTeammate(...)` (line 1020). The Minecraft `World` object
  and its `playerEntities` list are not thread-safe and are mutated every tick
  by the client thread.
- Impact: Concurrent iteration while the main thread mutates the list can throw
  `ConcurrentModificationException` or `NullPointerException` on the executor
  thread (silently dropping the autoplay/threat decision), and reads stale or
  half-updated entity state. Heisenbug — only manifests when a chat-detected
  player's stats miss the cache during active world mutation.
- Suggested fix: Marshal the world-touching work onto the client thread, e.g.
  `Minecraft.getMinecraft().addScheduledTask(() -> { ... })`, and only do
  thread-safe data prep in the callback itself.

### [HIGH] Async stat callback mutates the chat GUI off the main thread

- Location: `BedwarsRuntime.java:995` (`mc.thePlayer.addChatMessage(...)`) and `BedwarsRuntime.java:1032` (`worldScanService.requeueAutoplay(mcInner, threatMessage)`), both inside the executor-thread callback
- Issue: The callback fallback at line 995 calls `addChatMessage(...)`, and
  `WorldScanService.requeueAutoplay` also calls `addChatMessage(...)`
  (WorldScanService.java:188) — both mutate the chat GUI directly from the
  executor thread, which is not thread-safe in 1.8.9. The requeue path
  additionally issues `mc.thePlayer.sendChatMessage(...)`
  (WorldScanService.java:136/147/152/159), a secondary concern (packet sends via
  `addToSendQueue` are more tolerant off-thread, but still better confined).
- Impact: Mutating client GUI state off-thread can corrupt the chat-line buffer
  or crash; the autoplay requeue command can be dropped or duplicated. This is
  the autoplay "chat threat detected → requeue" path, so it fires precisely when
  the user is relying on automation.
- Suggested fix: Wrap the requeue/chat side effects in
  `Minecraft.getMinecraft().addScheduledTask(...)` so they execute on the next
  client tick on the main thread.

### [HIGH] Unsynchronized `chatDetectedPlayers.clear()` in WIN/LOSS handlers races executor writes

- Location: `BedwarsRuntime.java:257` (WIN) and `BedwarsRuntime.java:288` (LOSS)
- Issue: Both call `state.chatDetectedPlayers.clear()` with no synchronization,
  while the async callback adds to the same plain `ArrayList` under
  `synchronized (state.chatDetectedPlayers)` (line 974), and the PLAYER_LEFT
  handler correctly synchronizes its clear (line 327). The list is a plain
  `java.util.ArrayList` (RuntimeState.java:44), not concurrent.
- Impact: A WIN/LOSS message processed on the main thread can clear the list at
  the same instant the executor thread is mid-`add`, producing
  `ArrayIndexOutOfBoundsException`/`ConcurrentModificationException` or a
  corrupted internal array. The renderer also iterates this list under its own
  lock, so an unsynced structural mutation is observable inconsistently.
- Suggested fix: Wrap both clears in `synchronized (state.chatDetectedPlayers) { ... }`
  to match line 327 and the callback's lock.

### [HIGH] Shared phase/flag primitives are non-volatile across the thread boundary

- Location: `RuntimeState.java:21` (`gamePhase`), `:22` (`disconnectedFromGame`), `:94` (`autoplayEnabled`); read/written from the executor callback at `BedwarsRuntime.java:1002`, `:1031`, `:1034` and from the main thread at `BedwarsRuntime.java:939`, `:233`, `:255`, etc.
- Issue: `gamePhase`, `disconnectedFromGame`, and `autoplayEnabled` are plain
  non-volatile fields shared between the main thread and the executor callback
  with no synchronization on these accesses. Under the Java Memory Model there is
  no happens-before edge, so writes on one thread are not guaranteed visible to
  the other.
- Impact: The executor callback can read a stale `gamePhase`/`autoplayEnabled`
  (e.g. act on autoplay after the user disabled it, or branch on a phase the
  main thread already left), and the main thread may not see
  `autoplayEnabled = false` (line 1034) promptly. Intermittent, hard-to-reproduce
  logic errors rather than crashes.
- Suggested fix: Mark these cross-thread flags `volatile`, or (preferably,
  together with the HIGH findings above) confine all callback logic that reads
  them to a `addScheduledTask` block running on the main thread so no
  cross-thread sharing remains.

### [MEDIUM] Brittle substring match for GAME_START

- Location: `BedwarsRuntime.java:232`
- Issue: `message.contains(HypixelMessages.GAME_START)` uses a loose
  `contains()` on `"Protect your bed and destroy the enemy beds."`. Any chat
  line quoting that text (player chat, a future Hypixel reformat) triggers a
  match. The guard only checks `state.gamePhase != IN_GAME`, so it can also
  drive `IDLE → IN_GAME` directly, skipping `PRE_GAME`.
- Impact: A spoofed/quoted line can start match tracking from the lobby; the
  IDLE→IN_GAME jump bypasses any PRE_GAME-only setup. Low blast radius (it only
  starts tracking) but incorrect state transitions.
- Suggested fix: Match against the trimmed message and confirm the line is a
  bare system message (no rank/name prefix), consistent with how PLAYER_LEFT is
  handled at line 324-326; consider requiring the prior phase to be PRE_GAME.

### [MEDIUM] Loose `contains(WIN_VICTORY)` for win detection

- Location: `BedwarsRuntime.java:246`
- Issue: `message.contains(HypixelMessages.WIN_VICTORY)` matches `"VICTORY!"`
  anywhere in the line. The inline comment argues this is safe because it is an
  ASCII-art header, but it is still a raw substring check on a short token a
  player could type in chat (only reachable while already `IN_GAME`, which
  bounds the risk).
- Impact: A teammate/enemy typing "VICTORY!" while the match is in progress can
  prematurely end the game as a WIN, recording a false outcome to
  `PlayerDatabase` and resetting state.
- Suggested fix: Anchor the check (e.g. trimmed line starts with the header, or
  match against a stripped-formatting equality), as already done for the
  `WIN_YOU_WON` branch on the next line.

### [MEDIUM] `WIN_1ST_KILLER` / `Winners:` name check is name-collision prone

- Location: `BedwarsRuntime.java:248-249`
- Issue: `trimmedMessage.startsWith(WIN_1ST_KILLER) && trimmedMessage.contains(playerName)`
  (and the parallel `"Winners: "` branch) confirms a win by checking the local
  player's name appears anywhere in the line. `contains(playerName)` matches a
  substring, so a 1st-killer/winners line listing a *different* player whose
  name contains the local name (e.g. local `"Sky"` vs listed `"Sky123"`/`"xSky"`)
  yields a false WIN.
- Impact: Mis-attributed wins recorded to stats/`PlayerDatabase` when another
  player's name is a superstring of the local name. Reachable only while
  `IN_GAME`.
- Suggested fix: Tokenize the winners list and require an exact name token match
  rather than `contains`.

### [LOW] `state.reset()` does not release held AFK movement keys

- Location: `RuntimeState.java:105-181` (notably the comment at `:160`); related held-key logic in `BedwarsRuntime.tickAfkMovement` (lines 1167-1197) and `toggleAfk` (lines 1199-1216)
- Issue: `reset()` intentionally leaves AFK state alone ("user toggles manually",
  line 160), but if a reset occurs mid-strafe, the simulated key bindings set
  down by `tickAfkMovement` are never released by `reset()`.
- Impact: After a reset that lands while an AFK strafe key is held, the player
  can be left auto-walking until the user manually toggles AFK or presses the
  key. Minor, recoverable.
- Suggested fix: In `reset()` (or wherever a hard state reset is triggered),
  call the same key-release logic `toggleAfk` uses when disabling, or clear the
  AFK move phase and unpress the bindings.

### [LOW] `renderFetchRequests` map grows unbounded

- Location: `BedwarsRuntime.java:65` (declaration); populated in `maybeRequestRenderFetch` (lines 1048-1070)
- Issue: `renderFetchRequests` is a `ConcurrentHashMap` used to deduplicate
  render-driven fetch requests, but entries are never removed or expired. Over a
  long session the map accumulates one entry per unique player name ever rendered.
- Impact: Slow unbounded memory growth (small per entry, but never reclaimed) and
  permanent dedup suppression — a name fetched once is never re-requested even
  after its cache entry expires.
- Suggested fix: Store timestamps and evict entries older than the cache TTL, or
  clear the map on phase reset / game end alongside the other state cleanup.

## Improvements

- `BedwarsRuntime.java:592` (approx) — `clearTrackedGenerators()` runs every tick
  while not `IN_GAME`. After the first clear the collection is already empty, so
  this is wasted per-tick work. Gate it to run once on the IN_GAME→non-IN_GAME
  transition.
- `ScoreboardGameStateDetector.java:94-152` — `parseTeamStatuses` allocates a new
  `ArrayList` and a regex `Matcher` per call, and it is called from the
  scoreboard poll (every 10-20 ticks). Minor; consider reusing a `Matcher` via
  `matcher.reset(...)` if profiling ever flags it. Functionally correct.
- The chat transition handlers in `onChatReceived` repeat a large near-identical
  cleanup block for WIN (257-265), LOSS (288-296), and PLAYER_LEFT (328-342).
  Extracting a single `private void endMatchCleanup(...)` would remove the risk
  of these blocks drifting apart (e.g. the synchronization gap in the WIN/LOSS
  HIGH finding above is exactly this kind of drift).
- Centralizing the cross-thread state access (the four HIGH findings) behind a
  single "run on client thread" helper would resolve the iteration, chat-send,
  and visibility issues together and is the highest-leverage refactor.
