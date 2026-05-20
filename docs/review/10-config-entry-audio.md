# Review: Config, GUI, Entry & Audio

Files:
- src/main/java/com/imshy/bedwars/ModConfig.java
- src/main/java/com/imshy/bedwars/ModGuiConfig.java
- src/main/java/com/imshy/bedwars/ModGuiFactory.java
- src/main/java/com/imshy/bedwars/BedwarsMod.java
- src/main/java/com/imshy/bedwars/AudioCueManager.java

## Summary

Unit #10 covers configuration load/save, the in-game config GUI, the `@Mod`
entry point, and the audio cue manager. The code is generally straightforward
and the single-shot `loadConfig()` in `preInit` is structurally sound, but
there are several real correctness issues:

- The in-game GUI config screen (`ModGuiConfig` / Forge `GuiConfig`) writes and
  saves to the underlying `Configuration` file, but **nothing ever re-reads
  those values back into `ModConfig`'s static fields at runtime** — there is no
  `OnConfigChangedEvent` handler and `loadConfig()` is only invoked once. Edits
  made via the GUI silently have no effect until the game is restarted.
- **Six advertised threat-threshold config options (star + FKDR low/med/high)
  and `displayDuration` are dead** — their getters have zero callers, and
  `BedwarsStats.getThreatLevel()` uses hardcoded literals instead. Changing
  these settings does nothing.
- The static config fields are read from the render thread and async API
  threads but are plain (non-`volatile`) statics written from the main thread.
- `isPreGameBriefingEnabled()` is hardcoded to `return false`, leaving the
  loaded `preGameBriefingEnabled` field and its GUI toggle disconnected.
- The `ENDER_PEARL_INCOMING` audio cue is gated on an unrelated toggle.
- Config file name (`bedwarsstats.cfg`) and `@Mod` version ("6.7") drift from
  the documentation / `build.gradle` / `mcmod.info`.

The audio cooldown timing math itself is correct, null handling in
`AudioCueManager` is good, and its `HashMap` is effectively main-thread-confined.

## Findings

### [High] GUI config edits never take effect at runtime (no config-reload / OnConfigChangedEvent)

- Location: ModGuiConfig.java:19-34; ModConfig.java:166 (`loadConfig`), 150-154 (`init`); BedwarsMod.java:18-22
- Issue: `ModGuiConfig extends GuiConfig` and feeds it the Forge `Configuration`
  categories. When the user edits values in that screen and closes it, Forge's
  stock `GuiConfig` writes the new values into the in-memory `Configuration`
  object and posts a `ConfigChangedEvent.OnConfigChangedEvent` — it does **not**
  itself save the file; the mod is expected to handle that event (re-read values
  and call `config.save()`). Here there is no such handler: every
  `ModConfig.isXxx()/getXxx()` accessor returns a **cached static field** only
  ever populated by `loadConfig()`, which is called exactly once from `preInit`.
  Grep confirms no `OnConfigChangedEvent` handler exists anywhere (only the two
  `@Mod.EventHandler`s in `BedwarsMod`). Result: GUI edits are applied to the
  in-memory `Configuration` object but are neither propagated into the static
  fields the mod actually reads nor reliably persisted to disk.
- Impact: Every setting changed through the in-game GUI appears to do nothing at
  runtime, and the change is not saved either, so it looks like a fully broken
  feature. This affects the entire config surface (HUD, alerts, audio, ranges).
- Suggested fix: Add a `ConfigChangedEvent.OnConfigChangedEvent` handler (registered
  on `MinecraftForge.EVENT_BUS` or `FMLCommonHandler` bus) that, when
  `event.getModID().equals(BedwarsMod.MODID)`, calls a refresh routine that
  re-reads all properties into the static fields (refactor the body of
  `loadConfig()` after `config.load()` into a `syncFromConfig()` method and call
  it from both `loadConfig()` and the change handler). This is the standard
  Forge 1.8.9 pattern.

### [High] Star/FKDR threat thresholds and displayDuration are dead config — getters have no callers; threat logic is hardcoded

- Location: ModConfig.java:188-235 (load), 884-910 (getters); BedwarsStats.java:276-285
- Issue: The config defines `lowStarThreshold`, `mediumStarThreshold`,
  `highStarThreshold`, `lowFkdrThreshold`, `mediumFkdrThreshold`,
  `highFkdrThreshold`, and `displayDuration` with full comments, defaults, and
  GUI exposure. A repo-wide search shows `getLowStarThreshold`,
  `getMediumStarThreshold`, `getHighStarThreshold`, `getLowFkdrThreshold`,
  `getMediumFkdrThreshold`, `getHighFkdrThreshold`, and `getDisplayDuration`
  have **zero callers** outside ModConfig. The actual threat classification in
  `BedwarsStats.getThreatLevel()` uses hardcoded literals:
  `stars >= 500 || fkdr >= 6.0` → EXTREME, `>= 300 || >= 4.0` → HIGH,
  `>= 100 || >= 2.0` → MEDIUM. These literals coincide with the config defaults,
  so the disconnect is invisible until a user customizes the values — at which
  point nothing changes.
- Impact: Seven user-facing settings silently do nothing. Users tuning threat
  thresholds (a core feature) get no behavioral change, eroding trust in all the
  toggles.
- Suggested fix: Either (a) make `BedwarsStats.getThreatLevel()` read the
  `ModConfig` getters instead of literals, or (b) if the thresholds are
  intentionally fixed, remove the dead config keys + getters so the GUI does not
  advertise non-functional options. Same for `displayDuration` — wire it into
  the relevant on-screen display TTL or remove it.

### [Medium] preGameBriefingEnabled is loaded into a field but the getter is hardcoded to false

- Location: ModConfig.java:117 (field), 728-733 (load), 1179-1184 (getter)
- Issue: `preGameBriefingEnabled` is declared, loaded from config, exposed in the
  GUI (CATEGORY_NEW_FEATURES), and persisted — but `isPreGameBriefingEnabled()`
  unconditionally `return false;` (with a comment that the feature was disabled
  as noisy). The loaded field is therefore write-only/never read, and the GUI
  toggle is a no-op. Note `getPreGameBriefingDurationSeconds()` *is* still used
  by `BedwarsRuntime`/`PreGameBriefingRenderer`, so the duration setting works
  but the master toggle is dead.
- Impact: The "Pre-game briefing" GUI toggle does nothing; the renderer is fully
  gated off regardless of the user's choice. Confusing dead UI, and a latent
  inconsistency if someone re-enables the feature.
- Suggested fix: Either return the loaded `preGameBriefingEnabled` field (and
  re-enable the feature) or remove the config key, the field, and the GUI
  exposure so the screen does not show a non-functional toggle.

### [Medium] ENDER_PEARL_INCOMING audio cue is gated on the wrong toggle

- Location: AudioCueManager.java:75-76 (`isCueEnabled`)
- Issue: Every other cue is gated on its dedicated audio toggle
  (`isInvisibleAudioCueEnabled`, `isBedDangerAudioCueEnabled`,
  `isFireballAudioCueEnabled`). The ender-pearl case instead returns
  `ModConfig.isEnderPearlTrackingEnabled()` — the master feature toggle, not an
  audio toggle. There is no ender-pearl-specific audio toggle in ModConfig at
  all (the related keys are `enderPearlTrackingEnabled`, `enderPearlOverlayEnabled`,
  `enderPearlAlertRadius`, `enderPearlPreviewEnabled`).
- Impact: Users cannot silence the ender-pearl cue independently; turning off all
  ender-pearl audio requires disabling tracking entirely. Inconsistent with the
  fireball/invisible/bed cues. Also note: the outer `isAudioAlertsEnabled()` and
  `isModEnabled()` master gates still apply, so the cue is not completely
  ungated, but the per-cue control is wrong.
- Suggested fix: Add an `enderPearlAudioCueEnabled` config key + getter and gate
  the cue on it, matching the pattern of the other three cues.

### [Medium] Static config fields are non-volatile but read from render and async threads

- Location: ModConfig.java:23-142 (all `private static` fields); read sites e.g.
  BedwarsHudRenderer.java:62,547 (render thread), AudioCueManager.java:63, plus
  HypixelAPI async usage of `getApiKey()`/setApiKey
- Issue: All settings are plain `private static` fields, written on the main
  thread by `loadConfig()` and by the setters (`setApiKey`, `setModEnabled`,
  `setNameTagsEnabled`, `setAutoplayRequeueEnabled`, `setLobbyMaxPlayerCount`,
  `setEnderPearlPreviewEnabled`), and read from the render thread (HUD/overlay)
  and the API thread pool. None are `volatile`. Under the JMM there is no
  happens-before guarantee, so a render/async thread can observe a stale value
  after a setter runs (e.g. `/bw nametags` toggling `nameTagsEnabled` may not be
  seen by the render thread promptly). For `apiKey` (a reference written by
  `setApiKey` and read by `HypixelAPI`), there is also no safe-publication
  guarantee.
- Impact: Low-probability but real visibility bug: toggles set at runtime can be
  ignored by other threads for an unbounded window; in the worst case a partially
  published reference. Mostly benign for primitive booleans on x86 but not
  guaranteed.
- Suggested fix: Mark the fields that are written by runtime setters or read off
  the main thread `volatile` (at minimum `apiKey`, `modEnabled`, `nameTagsEnabled`,
  `autoplayRequeueEnabled`, `lobbyMaxPlayerCount`, `enderPearlPreviewEnabled`, and
  any field read in render/overlay). Cheap and removes the hazard.

### [Medium] config.save() in finally block can throw on its own; setters call save() without try/catch

- Location: ModConfig.java:860-864 (finally `config.save()`), 870-877 (`setApiKey`),
  1008-1012, 1018-1022, 1222-1226, 1232-1236, 1242-1246
- Issue: `loadConfig()` wraps property reads in try/catch, but the `finally` block
  calls `config.save()` outside any guard — if the save throws (disk full,
  read-only config dir, permissions), it propagates out of `loadConfig()` and
  thus out of `preInit`, which can hard-crash mod loading. The runtime setters
  (`setApiKey` etc.) likewise call `config.save()` with no try/catch; a failed
  save while running (e.g. user runs `/bw setkey ...`) throws on the command
  thread.
- Impact: A non-writable config directory turns a recoverable condition into a
  startup crash or a command-time exception. Robustness gap.
- Suggested fix: Wrap `config.save()` calls in try/catch and log a warning rather
  than propagating. In `loadConfig`, guard the `finally` save.

### [Low] No cross-field validation — thresholds can be set out of order

- Location: ModConfig.java:188-235 (star/FKDR thresholds), 322-328 (lobbyMaxPlayerCount)
- Issue: Each numeric property has an individual min/max clamp (good), but there
  is no validation that low <= medium <= high for the star or FKDR thresholds.
  A user can set lowStarThreshold=900, highStarThreshold=100. (Currently moot
  because the thresholds are dead per the High finding above, but it becomes a
  live bug the moment they are wired in.)
- Impact: If/when the thresholds are connected to `getThreatLevel()`, an
  out-of-order configuration produces nonsensical threat classification.
- Suggested fix: After loading, clamp/reorder so `low <= medium <= high`, or log
  a warning and fall back to defaults when the ordering is violated.

### [Low] @Mod version and config file name drift from documentation / build metadata

- Location: BedwarsMod.java:14 (`VERSION = "6.7"`); ModConfig.java:151
  (`"bedwarsstats.cfg"`)
- Issue: (a) `BedwarsMod.VERSION` is "6.7" while `build.gradle` sets
  `version = "1.0"` and `mcmod.info` uses `${version}` (resolved from gradle),
  so the version shown in the mod list vs. the @Mod annotation disagree.
  (b) The config file is named `bedwarsstats.cfg`, but `CLAUDE.md` and the
  project overview state `config/bedwars.cfg`. Not a runtime bug, but a
  maintenance trap.
- Impact: Confusing for anyone debugging "where is my config" or reconciling
  version numbers; harmless at runtime.
- Suggested fix: Derive `VERSION` from a single source or align it with gradle,
  and update CLAUDE.md to reference `bedwarsstats.cfg` (or rename the file).

### [Low] setApiKey re-fetches the Property with a "" default that can mask the comment, and getApiKey may return value before HypixelAPI is updated elsewhere

- Location: ModConfig.java:870-877
- Issue: `setApiKey` does `config.get(CATEGORY_GENERAL, "apiKey", "").set(key)` —
  this re-creates the property with a bare default and no comment, which is fine
  functionally (Forge keeps the existing entry) but loses the descriptive comment
  if the property did not already exist. Minor. More notably, the method updates
  the static field, the file, and `HypixelAPI` — but there is no synchronization,
  see the volatility finding.
- Impact: Cosmetic (lost comment in edge case); covered by the thread-safety
  finding otherwise.
- Suggested fix: Reuse the same `get(...)` overload with the original comment, or
  centralize key definitions so set/get share one Property reference.

## Improvements

- Refactor `loadConfig()`: the 700-line body is one giant try block of repetitive
  `config.get(...)` + assignment pairs. Extract the post-`config.load()` body into
  a `syncFromConfig()` method (enables reuse by an `OnConfigChangedEvent` handler,
  see High finding) and consider a small helper to reduce the boilerplate.
- Audio cooldown math (AudioCueManager.java:56-64) is correct: it compares
  `now - lastPlayed < cooldownMs` and only records `lastCueTime` after a successful
  play, so the cooldown starts from the last *played* cue (good — no drift). Null
  handling is solid: `playCue` null-checks `mc`/`cueType`, reschedules off-thread
  work to the MC thread, and `playCueInternal` null-checks `thePlayer`. The
  `getSoundProfile` default branch guards against future enum additions. No NPE
  risk found here.
- AudioCueManager's `lastCueTime` HashMap is effectively main-thread-confined:
  the only writer (`playCueInternal`) always runs on the MC thread (rescheduled via
  `addScheduledTask`), and `clearCooldowns()` is called from the main event handler
  (`BedwarsRuntime.resetMatchState`). Safe as-is; a brief comment documenting that
  invariant would prevent a future regression if anyone calls it off-thread.
- ModGuiFactory (clean): correct `IModGuiFactory` implementation; returning `null`
  from `runtimeGuiCategories`/`getHandlerFor` is acceptable for a config-only GUI.
- BedwarsMod event-bus registration (clean): `BedwarsRuntime` is registered exactly
  once in `init` (single `MinecraftForge.EVENT_BUS.register(runtime)` call, no
  duplicate registration, no double-registration of the command). The shutdown hook
  for `HypixelAPI.shutdown()` is reasonable. preInit/init ordering is correct
  (config loaded in preInit before runtime construction in init).
- Consider gating `ModConfig.init`/setters against a null `config` (currently
  `setApiKey` etc. would NPE if invoked before `init`); not currently reachable
  given the lifecycle, but a defensive null-check would harden the static API.
