# Bedwars Stats — Hypixel Bedwars Utility Mod

A **client-side** Minecraft Forge **1.8.9** mod for Hypixel Bedwars. It looks up
players' stats in real time and turns them into on-screen threat assessment:
lobby stat panels, in-world nametags, per-team danger summaries, a kill feed,
bed/generator tracking, ender-pearl trajectory prediction, and optional autoplay.

It is a lookup/overlay tool that talks to the public Hypixel API and reads
in-game chat, tab list, and the world — it does not modify gameplay or automate
combat.

## Features

- **Player stats & threat tiering** — stars, FKDR, WLR, beds, recent (monthly/weekly)
  form, and a LOW → EXTREME threat level. Nick (alias) detection included.
- **Per-mode splits** — solo / doubles / 3s / 4s FKDR so a fours stacker is easy to
  tell apart from a solos main at the same star (see `/bw lookup`).
- **Lobby HUD** — auto stat panel for players who join your lobby, with a
  drag-and-drop layout editor (`/bw edithud`).
- **Team danger summary** — per-team average threat from the tab list, with a
  world-scan fallback early in a game.
- **In-world overlays** — threat-colored nametags, enemy last-seen tracking,
  generator ESP, final-kill feed.
- **Tactical extras** — rush-risk prediction, bed-proximity alerts, ender-pearl
  trajectory preview, configurable audio cues, auto-blacklist.
- **Persistence** — encounter history and a blacklist saved across sessions.

## Getting started

1. **Install** the built JAR into your `mods/` folder (Forge 1.8.9 required).
2. **Get a Hypixel API key** — log in at
   [developer.hypixel.net/dashboard](https://developer.hypixel.net/dashboard) and
   create a key. (On first join without a key, the mod prints a clickable prompt.)
3. **Set it in game:**
   ```
   /bw setkey <your-key>
   ```
   The mod does a quick self-lookup and confirms whether the key is valid.
4. **Use it** — join a Bedwars lobby and stats populate automatically, or run
   `/bw lookup <player>` / `/bw all`.

> Without a valid key, all stat features are silently inactive — the key is the
> one required setup step.

## Commands

All commands are client-side (`/bw`), never sent to the server.

| Command | Description |
| --- | --- |
| `/bw setkey <key>` | Set (and validate) your Hypixel API key |
| `/bw lookup <player>` | Look up one player's stats, incl. per-mode breakdown |
| `/bw all` | Look up every player in the current lobby |
| `/bw info` | Show threats and history for players in the lobby |
| `/bw autoplay <ones\|twos\|threes\|fours\|stop\|requeue>` | Auto-queue until a safe lobby |
| `/bw afk` | Toggle anti-kick movement |
| `/bw rejoin` | Re-run game-start setup (bed tracking, generators, scan) |
| `/bw blacklist <add\|remove\|list> [player] [reason]` | Manage the blacklist |
| `/bw history [player]` | View encounter history |
| `/bw status` | Cache and rate-limit info |
| `/bw clear` | Clear the stats cache |
| `/bw reset` | Reset all HUD/runtime state (like a fresh boot) |
| `/bw disable` / `/bw enable` | Toggle all automatic features |
| `/bw pearlpreview` | Toggle ender-pearl trajectory preview |
| `/bw nametags` | Toggle in-world nametags |
| `/bw maps` | List map layouts learned from played games |
| `/bw edithud` | Open the drag-and-drop HUD layout editor |

Additional tactical overlays (pre-game briefing, generator countdown, enemy
loadout row, final-kill feed, ender-pearl overlay) are toggled in the config GUI
(Mods → Bedwars Stats → Config).

## Configuration

Settings persist to `config/bedwarsstats.cfg` and can be edited in-game via the
Forge config GUI. Encounter history / blacklist / learned maps live under
`config/bedwarsstats/`.

---

## Building from source

**Requires Java JDK 8** — Forge 1.8.9 will not build on newer JDKs.

```bash
# macOS — activate Java 8
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)

# First-time setup (downloads + decompiles Minecraft; takes a few minutes)
./gradlew setupDecompWorkspace

# Build the JAR (output in build/libs/)
./gradlew build

# Run the client with the mod loaded
./gradlew runClient

# Run the unit tests
./gradlew test
```

Generate IDE run configs with `./gradlew genIntellijRuns` (IntelliJ) or
`./gradlew eclipse` (Eclipse).

### Troubleshooting

- **Wrong Java version:** `java -version` should report `1.8.0_xxx`. Point
  `JAVA_HOME` at a JDK 8.
- **Dependency resolution fails:** `./gradlew --refresh-dependencies`.
- **Clean rebuild:** `./gradlew clean setupDecompWorkspace build`.

## Project layout

```
src/main/java/com/imshy/bedwars/   # mod source
  runtime/                         # event handling, game-state, tracking services
  render/                          # HUD + in-world overlays
  command/                         # /bw command
  gui/                             # HUD editor
src/test/java/                     # JUnit tests (./gradlew test)
docs/review/                       # code-review / audit reports
```
