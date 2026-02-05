# SPEC.md — Project Specification

> **Status**: `FINALIZED`

## Vision
To create the ultimate "Legit" utility mod for Hypixel Bedwars that functions as a "Smart Co-pilot". Instead of playing the game for you (which is bannable), it processes available information to give you perfect situational awareness. It answers "Can I afford this?", "Where is my base?", and "How is the game going?" instantly, allowing the player to focus purely on mechanics and strategy.

## Goals
1.  **Resource Intelligence HUD**: Real-time tracking of inventory resources and "Ready to Buy" alerts for continuous shop awareness.
2.  **Navigational Awareness**: A "Base Radar" to instantly orient the player towards their own bed/island, critical during frantic bridging or escaping.
3.  **Match State Awareness**: Enhanced scoreboard parsing to show a cleaner, more visual representation of broken beds and team statuses.

## Non-Goals (Out of Scope)
-   **Combat Hacks**: No KillAura, Reach, Velocity, or Click Macros.
-   **Automation**: No auto-buy or auto-bridge.
-   **Server-Side Tracking**: No tracking players outside render distance (ESP).
-   **Input Manipulation**: No aim assist.

## Users
-   **Hypixel Bedwars Players**: Competitive players who want an edge through information superiority.
-   **Solo Queuers**: Players who need to rely on themselves for game awareness.

## Constraints
-   **Hypixel Watchdog Compliance**: Features must rely on visual/client-side data only. No sending disallowed packets.
-   **Minecraft 1.8.9 Forge**: Must integrate with the existing codebase structure.
-   **Performance**: Minimal FPS impact during rendering.

## Success Criteria
-   [ ] **Smart HUD** accurately displays inventory count for Iron, Gold, Diamonds, Emeralds.
-   [ ] **Safe Shop Calculator** alerts when player has enough for Key items (TNT, Fireball, Gapple, Shears).
-   [ ] **Base Radar** renders a visible, non-obtrusive arrow/indicator towards the team base.
-   [ ] **Scoreboard Parser** accurately detects bed breaks and updates an "Alive Teams" HUD.
