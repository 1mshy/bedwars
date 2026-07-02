# What Can Be Improved — Bedwars Mod (audit @ HEAD, 2026-07)

Fresh multi-agent audit of the current tree, scoped against the prior audit (commit `863d631`,
`docs/review/*.md`). Partition: **43 files audited before, 7 net-new never-audited, 12 modified since.**
New/modified code was re-reviewed fresh and every defect claim was adversarially verified against the
current working tree; still-open prior findings in unchanged files were carried forward and re-verified.

## Honest assessment

The mod's worst historical problems are genuinely fixed. All 16 High findings from the prior audit are
resolved: the async/netty→client thread boundary is now marshaled through `addScheduledTask`, the
`AntiCheatService` netty path is deliberately enqueue-only (with a "do not inline" guard comment),
`TeamDangerAnalyzer` memoizes its scan to 1s, the generator scan is now a distance-gated sphere instead
of a cube, and ~209 unit tests cover the pure-logic classes. The new code (`ChatNameLinker`,
`KillFeedTracker`, `MapLearningService`, `TabStatsInjector`, `GuiHudEditor`, `HudAnchorMath`) is mostly
well-structured — `HudAnchorMath` in particular is clean, pure, and already unit-tested.

What remains: **one genuinely High correctness bug that slipped in with the new chat parsing** (spoofable
WIN detection), **one High-ish perf hot path** (the per-second generator scan is reduced but still walks
the whole sphere allocating per cell on the main thread), a recurring **fetch-suppression leak** that
staled stats in long autoplay sessions, a handful of per-frame/per-tick waste, crash-unsafe JSON
persistence, and real structural debt (`BedwarsRuntime` and `ModConfig` are god objects; the "run on
client thread" idiom is copy-pasted ~10×). The single biggest *project* risk isn't a runtime bug at all:
`build.gradle` still points at decommissioned Maven repos, so dependency resolution is the top threat.

---

## Correctness & threading

- **`BedwarsRuntime.java:296` — HIGH — spoofable WIN detection.** `message.contains(WIN_VICTORY)`
  ("VICTORY!") during IN_GAME fires on stripped player all-chat (`"[MVP+] Name: VICTORY!"`). A false WIN
  captures a corrupted WIN row into `PlayerDatabase`, sets `gamePhase = IDLE`, and wipes all per-match
  tracking (chat/enemy/fireball/projectile/bed/killfeed/ledger); the real match then never re-arms
  (no new GAME_START while IN_GAME). Case-sensitive, so it's deliberate griefing, not accidental — but
  for a threat mod, silently disabling tracking for a whole match + DB corruption is serious.
  *Fix:* require a system line (`!CHAT_MESSAGE_PATTERN.matcher(message).matches()`) or anchor the token
  as a standalone header, not a substring.

- **`BedwarsRuntime.java:280` — MEDIUM — GAME_START unanchored + not gated to PRE_GAME.** `contains(GAME_START)`
  with only a `gamePhase != IN_GAME` guard: a player typing the 44-char phrase in a lobby flips the mod to
  IN_GAME (starts tab scan / bed tracking / `/p list` burst) and can leave it stuck IN_GAME until
  LOSS/leave/`/bw reset`. *Fix:* gate to PRE_GAME, require a system line, use `endsWith` not `contains`.

- **`BedwarsRuntime.java:320` + `LobbyTrackerService.java:218` + `BedwarsCommand.java:232` — MEDIUM —
  `pendingTabListFetches` never pruned.** The tab-scan success callbacks are empty (`onStatsLoaded(){}`),
  so a name is removed only on retryable error — a *successful* fetch leaves it suppressed forever. Neither
  match-end, `activateMatchTracking`, nor `/bw clear` clears the set (only `/bw reset` does, via
  `RuntimeState.reset()`). After the 60-min cache TTL, a reappearing player's tab-list/team-danger stats
  never reload in a multi-hour autoplay session. This is the same class as the prior audit's High #8,
  resurfacing across the lobby + clear paths. *Fix:* remove on success; clear on match start and `/bw clear`.

- **`ModConfig.java:1112` — MEDIUM — clearing the API key in the GUI is ignored.** `syncFromConfig()` only
  pushes the key `if (!apiKey.isEmpty())`, so revoking it in the config GUI keeps using the old key until
  restart. *Fix:* call `HypixelAPI.setApiKey(apiKey)` unconditionally.

- **`ProjectileTrackingService.java:122` — MEDIUM — ender-pearl alert ignores `landingValid`.** The
  `threatening` flag + `ENDER_PEARL_INCOMING` cue read `landing*` without checking `tp.landingValid`,
  which `integrateArcFrom` leaves `false` when the 200-step arc cap is hit — so a long/void pearl is judged
  from a non-landing point (usually a missed alert). *Fix:* gate the alert on `landingValid`.

- **`ChatNameLinker.java:185` — LOW — hover overwrite.** `buildSegment` overwrites a matched run's hover
  with the stats tooltip; the message-level guard bails only on click events, never hovers, so a
  server-supplied hover on a hover-only node is discarded (rare on Hypixel). *Fix:* only install the stats
  hover when `style.getChatHoverEvent() == null`.

- **`BedwarsRuntime.java:299` — LOW (was overstated High) — `Winners:` substring name-collision.** Both
  winner branches are `startsWith`-guarded (not chat-spoofable), but `contains(playerName)` matches a local
  "Sam" against a winner "Samuel" → false WIN. Narrow. *Fix:* token/`equals` match on split names.

- **`PlayerDatabase.java:142` — LOW.** `addOrRefreshAutoBlacklist` resets `addedAt = now` on every refresh
  (though `lastAutoAddAt` already records that), so `/bw blacklist` shows time-since-refresh, not
  time-since-first-add. *Fix:* leave `addedAt` untouched.

- **`PlayerDatabase.java:255` — LOW.** `addToCurrentGame` stores raw-case names while everything else
  lowercases (latent double-record; unreachable today). *Fix:* `.toLowerCase()` for consistency.

- **`GuiHudEditor.java:216` — LOW.** A pure click (down+up, no drag) on an already-custom element still runs
  the anchor re-snap + `applyLive` in `mouseReleased`, silently changing the stored anchor (alters
  resize re-flow) and forcing a persist though nothing moved. *Fix:* set a `moved` flag in `mouseClickMove`
  and skip the snap/persist when it's false.

- **`HypixelAPI.java:35` / `ModConfig.java:179-183` — LOW (visibility).** `API_KEY` and the anti-cheat
  toggle statics are non-`volatile` but read on executor/netty threads. Self-corrects per packet; no crash.
  *Fix:* mark `volatile`.

---

## Performance

- **`WorldScanService.java:234` — HIGH — per-second full-volume generator scan.** The round-2 cylinder+3D-gate
  fix *did* land (column pre-filter + sphere gate + range 64), but it still walks the entire spherical
  volume every second on the client thread, allocating a fresh immutable `BlockPos` per cell (`playerPos.add`)
  plus another via `checkPos.up()`. At range 64 that's hundreds of thousands of block reads + allocations
  each second → a periodic hitch. *Fix:* reuse a `BlockPos.MutableBlockPos`, cache/skip unchanged chunks
  between passes, and/or shrink the y-band or the 1s interval.

- **`MatchThreatService.java:228` — MEDIUM — bed-proximity check every tick (20Hz).** `checkBedProximityWarnings`
  runs the heavy `isTeammate()` chain per enemy per tick *before* any distance cull. *Fix:* interval-gate
  (e.g. every 10 ticks) and do the cheap distance check first.

- **`BedwarsHudRenderer.java:434` — MEDIUM — per-frame threat list.** `addHighThreatPlayerList` calls the
  *uncached* `TabListScanner.scanAllPlayers` (fresh list + per-entry regex alloc) plus an
  O(threats × worldPlayers) loop every frame, unlike the memoized `TeamDangerAnalyzer`. *Fix:* memoize on a
  ~1s window, or build one `name→entity` map per frame.

- **`HypixelAPI.java:268` — LOW.** `statsCache` has TTL but no eviction; expired entries are only overwritten,
  so the map grows for the session (bounded, single-digit MB). *Fix:* sweep expired on encounter or add an LRU cap.

- **`PlayerDatabase.java:176`/`:194` — LOW (was Medium).** Read-only `isBlacklisted`/`getBlacklistEntry` do a
  synchronous JSON `save()` on the main thread when they hit an expired AUTO entry (fires at most once per
  entry). *Fix:* treat expired as absent without `remove()+save()`; let batch cleanup persist.

- **`AntiCheatService.java:122`/`:426` — LOW.** `pruneStateMaps` only triggers on `swingTimes`/`scaffoldStates`
  growth; with AutoBlock-only those stay empty while `autoBlockStates`/`lastFlagAt` grow unbounded.
  `shutdown()` is never called and there's no per-game reset. *Fix:* tick-counter pruning over all maps + a
  `clearAll()` wired into the match-transition points.

- **`WorldScanService.java:467` — LOW.** `predictGeneratorCountWhenUnobserved` increments `resourceCount` with
  no upper bound. *Fix:* clamp to the tier cap.

- **`TeamDangerAnalyzer.java:171` — LOW.** `buildTeamDangerSummaryFromWorld` re-runs `scanAllPlayers` and
  re-derives the local color a second time within the same computation. *Fix:* compute once, pass down.

- **`KillFeedRenderer.java:113` — LOW.** Regular deaths always pass `killerName=null`, so the GRAY branch never
  reads `victimTeamColorCode` — `resolveTeamColorCode` (a `playerEntities` scan + scoreboard query) at
  `BedwarsRuntime.java:372` is dead work per death. *Fix:* pass `null` color for regular deaths, or color the
  victim in the null-killer branch for consistency.

---

## Robustness / edge cases

**JSON persistence isn't crash-safe (shared root cause).** Fix pattern everywhere: serialize to `.tmp` in
try-with-resources, `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`; quarantine a bad file to `.corrupt` on parse
failure and suppress overwrites until resolved.

- **`PlayerDatabase.java:436` — MEDIUM.** `save()` opens the live `playerdata.json` with a bare `FileWriter`
  (no temp+rename). A JVM kill or `toJson` throw mid-write truncates the only data file; next `load()` swallows
  the parse error and the next mutation persists empty state → **blacklist + encounter history wiped, no recovery.**
- **`MapLearningService.java:398`/`:376` — MEDIUM (was High ×2).** `save()` deletes the target before `renameTo`
  (no-file window); corrupt loads are swallowed and overwritten empty. Self-rebuilding display-only feature, so
  graceful loss — but same atomic-write + `.corrupt` fix applies.
- **`MapLearningService.java:129` — MEDIUM.** Only the single richest per-match snapshot is kept, but
  `trackedGenerators` evicts generators the player walked away from, so no snapshot holds a spread-out map.
  *Fix:* accumulate a per-match union de-duped by `packKey`.
- **`MapLearningService.java:174` — LOW (was Medium).** Learned data keyed by `mapName.toLowerCase()` with no
  mode/size discriminator; same-named layouts blend. Only affects the informational `/bw maps` list. *Fix:* fold
  team-count/mode into the key — if same-name/differing-build reuse is confirmed in-game.
- **`WorldScanService.java:226` — LOW (was overstated).** Vertical scan hard-coded to `y ∈ [-20,20]` while range
  is 50-200 → generators far above/below the player are missed; the "matches eviction exactly" comment is
  slightly misleading. Usually fine (generators are near island level). *Fix:* widen the y-band or document it.
- **`WorldScanService.java:146` — LOW.** `requeueAutoplay` takes an unused `reasonMessage` param and drives the
  requeue from a raw unmanaged `Thread`+`sleep`. *Fix:* drop the param; use a scheduled/tick-driven requeue.
- **`MatchThreatService.java:253` — LOW.** `isWatchdogBotName` flags any 10-char all-lowercase-alphanumeric name
  as a bot → can misclassify real players. *Fix:* tighten the heuristic or cross-check tab presence.
- **`LobbyTrackerService.java:130` — LOW.** `trackPlayerJoin` splits the dedupe check and insert across two
  `synchronized` blocks (non-atomic gap). *Fix:* single synchronized block.
- **`LobbyTrackerService.java:210` — LOW.** The match-start tab scan is one-shot; if it runs during rate-limit
  backoff it queues nobody and never re-arms. *Fix:* retry once backoff clears.
- **`BedwarsRuntime.java:1259` — LOW.** `renderFetchRequests` grows unbounded for the whole session (never
  pruned/reset). *Fix:* prune on match end / cap size.
- **`BedwarsRuntime.java:54-55` — LOW.** `DEATH_MESSAGE_PATTERN` is a closed 8-verb allow-list; non-final
  phrasings whose leading verb isn't listed (e.g. `"<name> hit the ground too hard."`) are silently omitted
  from killfeed + enemy-death tracking. *Fix:* add `hit `, or capture the name gated on absence of
  `FINAL_KILL_SUFFIX`.
- **`HypixelAPI.java:369` — LOW.** Mojang `resolveUUID` is unthrottled (only Hypixel is limited); an autoplay
  burst can draw a Mojang 429 → generic "lookup failed" with no retry. *Fix:* second limiter/backoff + treat
  429 as retryable.
- **`BedwarsStats.java:314`/`:321` (surfaces at `TabStatsInjector.java:224`) — LOW.** FKDR formatters use
  `String.format` without a `Locale`, so a de/fr/LATAM JVM shows `2,0` not `2.0`. *Fix:* `Locale.ROOT` (also
  cures HUD/nametags).

---

## Architecture & maintainability

- **Extract a client-thread marshaling helper + `StatsCallback` adapter — S, High value.** The dominant
  historical bug idiom (`addScheduledTask(() -> player.addChatMessage(...))`) plus the `[BW]` prefix is
  copy-pasted ~10× across `BedwarsRuntime`, `BedwarsCommand`, `AudioCueManager`, `LobbyTrackerService`. A
  `ClientThread.run(Runnable)` + `ChatNotifier.mod(String)` + a `MainThreadCallback` decorator make the safe
  path the default and a raw off-thread `addChatMessage` the visible exception. Correctness-preserving, not just DRY.
- **Extract match-lifecycle `endMatch()` + a `MatchResettable` interface — M, High.** Three near-identical
  ~15-line teardown blocks (WIN 305-322 / LOSS 329-357 / UNKNOWN 402-415) repeat the same 7-step sequence and
  drift has already started (the `pendingTabListFetches` bug above is exactly that drift). Hold trackers in a
  `List<MatchResettable>` and iterate once.
- **Declarative `ConfigOption` registry for `ModConfig` — L, High.** 1759 LOC defines ~115 settings three
  times each (field + ~147 `config.get` in a 900-line `syncFromConfig` + 115 getters). Migrate incrementally:
  descriptor list → typed map → thin getter facades, one category at a time.
- **Split `RuntimeState`'s ~60-field bag into service-owned sub-states — M, Medium.** `reset()` hand-clears
  every field and already carries a fragile "AFK state intentionally NOT reset" special case. Start by moving
  the AFK cluster into a self-contained `AfkService`.
- **Decompose `BedwarsRuntime` + shared `TeamColorResolver` — L, Medium.** One class owns 22 collaborators and
  all 7 `@SubscribeEvent` handlers plus inline parsing; `handleFinalKill` open-codes the same legacy-color scan
  `firstColorCode` already implements. Extract `TeamColorResolver` first, then `AfkService`, then turn
  `onChatReceived` into an ordered list of `ChatHandler` objects.
- **`BedwarsCommand` dispatch table + `AnchoredElement` abstraction — M each, Medium.** The 21-branch
  `subCommand.equals(...)` chain, help text, and tab-complete are three unsynchronized places → a
  `Map<String,SubCommand>`. Four renderers open-code the same "custom vs legacy origin" branch with 5 parallel
  getters each → an `AnchoredElement.resolveOrigin(...)`.
- **Dead code / consistency — S, Low each.** `ModConfig.isPreGameBriefingEnabled()` hardcodes `return false`
  (the whole briefing config surface is a GUI no-op); `FinalKillLedger` per-team map/streak fields are dead
  (only `getTotalFinalKills()` is used — collapse to an `int`, but keep the `handleFinalKill` scan, which still
  resolves the killfeed's live color); `EnemyTrackingService.getAllTrackedEnemies()` returns the live map
  (should be `unmodifiableMap` like its siblings); `BedwarsHudRenderer.render()` leaves GL color/blend dirty at
  `popMatrix`; naming drift (`MODID` is `bedwars` but config is `bedwarsstats.cfg` and CLAUDE.md documents the
  wrong path).

---

## Feature & UX

- **Rewrite `README.md` — S, High.** Still the stock Forge MDK template — zero mention of `/bw`, the API key,
  or any feature. A new installer has no idea the mod needs a key. Add what-it-is, getting-started
  (key at developer.hypixel.net/dashboard, `/bw setkey`), a command table, a feature list.
- **Validate `/bw setkey` + first-run key prompt — S, High.** `setkey` replies "saved!" with no test request,
  so a bad key → silent 403s; with no key the HUD shows nothing. *Fix:* fire a self-lookup, report GREEN/RED;
  on world join without a key, print a one-time clickable dashboard link.
- **Per-mode (solo/doubles/threes/fours) stats — L, High.** Only overall + monthly/weekly are parsed, so a
  solos main and a fours stacker at the same star look identical. The mode prefixes already exist in
  `WorldScanService`. *Fix:* parse the four buckets, surface the active-mode row in `/bw lookup`, tier on mode FKDR.
- **Prestige-colored stars — S, Medium.** Add `BedwarsStats.getPrestigeColor()` (Hypixel palette) and color the
  star token so 120★ vs 800★ is distinguishable at a glance.
- **Sort `/bw all` by threat — S, Medium.** Callbacks print by latency, not danger. Collect into a list with an
  `AtomicInteger` remaining count; at 0, marshal one `addScheduledTask` that sorts by threat then stars.
- **BBLR + finals-per-star — M, Medium.** `beds_broken` is parsed but `beds_lost` isn't (no BBLR); add
  `getBblr()` + `getFinalsPerStar()` to `/bw lookup`. Read winstreak defensively (often API-private).
- **Keybinds — M, Medium.** No `registerKeyBinding`; add default-unbound keys (toggle HUD, scan lobby, open HUD
  editor, open config) via `ClientRegistry.registerKeyBinding` + a `KeyInputEvent` handler.
- **Colorblind fallback — M, Medium.** Threat is signaled almost purely by red/yellow/green. Add a redundant
  glyph prefix (`EX/HI/MD/LO`) + a `colorblindMode` palette swap, gated at `getThreatColor`.
- **Smaller wins — S, Low each.** A `/bw config` shortcut (the config screen is otherwise buried under
  Mods→Config); reserve the flashing TNT/Fireball icon width unconditionally so the panel doesn't pulse; a
  dedicated `enderPearlAudioCueEnabled` toggle (currently tied to the master tracking toggle).

---

## Testing & build

- **Fix dead Maven repos in `build.gradle:5,8` — S, High.** `jcenter()` (decommissioned) and
  `http://files.minecraftforge.net/maven` (plain HTTP now refused; superseded by `https://maven.minecraftforge.net`)
  are exactly what Gradle hits to resolve ForgeGradle. *Fix:* `mavenCentral()` + `gradlePluginPortal()` + the
  https forge URL; verify with `--refresh-dependencies`.
- **`AntiCheatService` netty→client threading test — M, High.** `channelRead` is carefully enqueue-only with a
  "Do NOT inline this" comment — the author already fears the regression but nothing locks the contract. Feed a
  fake packet on a non-client thread with `mc.theWorld=null`, assert it only grows `pendingSwings`.
- **`HypixelAPI` cache/rate-limit tests — M, High.** The 60-min expiry, 120/min window rollover, 30s 429 backoff,
  and 30-day UUID TTL have no coverage; off-by-one here means bans or stale stats. Inject a `LongSupplier` clock,
  test the boundaries.
- **Add minimal CI — M, High.** No `.github/workflows`; the 209 tests never run automatically. Temurin JDK 8,
  gradle cache, `setupDecompWorkspace` then `build`, gate PRs.
- **Pin ForgeGradle off `2.1-SNAPSHOT` — S, Medium.** A moving snapshot means non-reproducible builds.
- **Scan/threat + chat→phase pure-logic tests — Medium.** Extract `TeamDangerAnalyzer` comparator and
  `MatchThreatService` nearest-bed distance into MC-free helpers; extract a pure `GamePhase next(current, line)`
  from `onChatReceived` and test the full transition table (this is exactly where the WIN/GAME_START bugs above live).
- **Trim build surface — S, Low.** `essential:elementa` + `universalcraft` have 0 references in `src/` — remove
  them (and the essential repo) if unused. Add a fail-fast JDK-8 guard so a wrong JDK gives a clear message.

---

## Top 10 highest-leverage (do these first)

1. **Fix spoofable WIN + GAME_START chat matching** — require system-line / anchored tokens.
   `BedwarsRuntime.java:296,280`. **S.** (The only new High + a stuck-state Medium.)
2. **Fix `build.gradle` dead Maven repos** — jcenter→mavenCentral+gradlePluginPortal, http→https forge.
   `build.gradle:5,8`. **S.** (Nothing builds if resolution breaks.)
3. **Crash-safe atomic JSON persistence + `.corrupt` quarantine** — prevents silent wipe of the user's
   blacklist/history. `PlayerDatabase.java:436`, `MapLearningService.java:376,398`. **M.**
4. **Prune/clear `pendingTabListFetches`** — remove on success, clear on match start + `/bw clear`; stops stats
   going permanently stale in long sessions. `BedwarsRuntime:320`, `LobbyTrackerService:218`, `BedwarsCommand:232`. **S.**
5. **Reduce the per-second generator scan** — `MutableBlockPos`, cache between passes, shrink the y-band/interval.
   `WorldScanService.java:234`. **S–M.**
6. **Extract the client-thread marshaling helper + `StatsCallback` adapter** — makes the dominant historical bug
   class the default-safe path. **S.**
7. **Rewrite README + validate `/bw setkey` + first-run key prompt** — the entire onboarding surface tells a new
   user nothing today. **S.**
8. **Interval-gate the bed-proximity check + memoize the HUD threat list** — kills two per-tick/per-frame hot paths.
   `MatchThreatService.java:228`, `BedwarsHudRenderer.java:434`. **S.**
9. **Add CI + the `AntiCheatService` netty threading test + `HypixelAPI` cache/rate-limit tests** — a standing
   guard on the exact regressions the code is one edit away from. **M.**
10. **Parse per-mode stats + tier on mode FKDR** — the highest-value scouting upgrade for the mod's core purpose.
    `BedwarsStats.java`. **L.**

## What's already good (don't regress it)

- The netty/executor→client-thread marshaling from the prior fixes holds up under re-review.
- `TeamDangerAnalyzer`'s 1s memoization and the generator scan's sphere+range reduction landed correctly.
- `HudAnchorMath` is clean, pure, round-trip-correct, and unit-tested; `GuiHudEditor` is well-structured.
- ~209 unit tests already cover the pure-logic classes — the base to build the missing threading/cache tests on.
