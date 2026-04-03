# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A Minecraft Forge 1.8.9 client-side mod for Hypixel Bedwars. It provides real-time player stat lookups, threat assessment, HUD overlays, autoplay, bed tracking, and team danger analysis — all via the Hypixel API and in-game chat/world parsing.

## Build & Run

**Requires Java JDK 8.** Newer JDKs will not work with Forge 1.8.9.

```bash
# macOS — ensure Java 8 is active
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)

# First-time setup (downloads and decompiles Minecraft)
./gradlew setupDecompWorkspace

# Build JAR (output in build/libs/)
./gradlew build

# Run the game client with the mod loaded
./gradlew runClient

# IntelliJ run configs
./gradlew genIntellijRuns
```

No test suite exists. Verification is done by running the client (`./gradlew runClient`) and testing in-game on Hypixel.

## Architecture

### Entry Point & Lifecycle

`BedwarsMod` — the `@Mod` entry point. Registers `BedwarsRuntime` on the Forge event bus and the `/bw` command. Loads config in `preInit`, wires everything in `init`.

### Runtime Core (`runtime/` package)

`BedwarsRuntime` is the central event handler. It receives all Forge events (`@SubscribeEvent`) and delegates to specialized services:

- **`RuntimeState`** — mutable state bag holding game phase, timers, player lists, autoplay flags. Shared across all services via the runtime.
- **`GamePhase`** — `IDLE → PRE_GAME → IN_GAME` state machine driven by Hypixel chat messages.
- **`LobbyTrackerService`** — detects lobby joins, triggers stat lookups for new players, manages party detection via join-message burst analysis.
- **`EnemyTrackingService`** / `TrackedEnemy` — tracks enemies seen in-game with last-known positions and timestamps.
- **`MatchThreatService`** — bed proximity alerts, bed destruction tracking during matches.
- **`TeamDangerAnalyzer`** — builds per-team threat summaries from tab list data (with world-scan fallback when tab list data is sparse early in a game).
- **`WorldScanService`** — scans for diamond/emerald generators, handles autoplay requeueing, and play commands.
- **`TabListScanner`** — parses the player tab list for team/player data.
- **`ArmorColorTeamDetector`** — detects team membership by armor color when scoreboard data is unavailable.

### API & Stats

- **`HypixelAPI`** — async stat fetching with Mojang UUID resolution, in-memory cache (60min TTL), and client-side rate limiting (120 req/min). Uses a 3-thread pool.
- **`BedwarsStats`** — parses Hypixel JSON into stars, FKDR, win rate, etc. Computes `ThreatLevel` (LOW/MEDIUM/HIGH/EXTREME) used across the mod.
- **`PlayerDatabase`** — SQLite-backed persistent storage for player history and game outcomes. Tracks blacklisted players and historical encounters.

### Rendering (`render/` package)

- **`BedwarsHudRenderer`** — draws the on-screen HUD (lobby stats panel, team danger summary, match timer).
- **`BedwarsOverlayRenderer`** — renders in-world overlays (nametag threat labels, enemy tracking indicators, generator ESP).
- **`NameTagManager`** — manages custom nametag rendering with stat info above players.

### Supporting Classes

- **`ModConfig`** — Forge `Configuration`-backed settings with many toggles (HUD, alerts, thresholds, audio). Persisted to `config/bedwars.cfg`.
- **`BedwarsCommand`** (`command/`) — the `/bw` client command with subcommands: `setkey`, `lookup`, `all`, `info`, `autoplay`, `rejoin`, `blacklist`, `history`, `status`, `clear`, `reset`, `disable`, `enable`.
- **`MapMetadataRegistry`** — hardcoded map data (names, generator locations, team positions) for known Bedwars maps.
- **`BedLocator`** — finds bed block positions by scanning nearby chunks.
- **`RushRiskPredictor`** — estimates rush timing based on map metadata and game elapsed time.
- **`AudioCueManager`** — plays alert sounds with configurable cooldowns.
- **`AutoBlacklistManager`** — auto-blacklists players based on configurable threat thresholds.
- **`HypixelMessages`** — string constants for Hypixel chat message patterns (game start, win, loss, etc.).

## Key Patterns

- **All game state transitions** are driven by parsing chat messages in `BedwarsRuntime.onChatReceived()` against patterns in `HypixelMessages`.
- **Stat lookups are async** — `HypixelAPI.fetchStatsAsync()` runs on a background thread pool and delivers results via `StatsCallback`.
- **Thread safety** — `chatDetectedPlayers` and other shared lists use `synchronized` blocks. The Hypixel API cache is accessed from both the main thread and the executor pool.
- **Config values** are accessed via static methods on `ModConfig` (e.g., `ModConfig.isHudEnabled()`).
