package com.imshy.bedwars.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Mode-agnostic detection of an in-progress Bedwars match by inspecting the
 * sidebar scoreboard. The chat-based GAME_START trigger only handles standard
 * modes; this detector covers Lucky Block, Rush, Castle, Voidless, etc. by
 * looking for per-team status rows that only appear once a match has begun
 * (e.g. "Yellow: ✓", "Pink: ✗", "Blue: 0 YOU").
 */
public final class ScoreboardGameStateDetector {

    private ScoreboardGameStateDetector() {}

    /**
     * Sidebar rows for active teams use the form "&lt;TeamName&gt;: &lt;status&gt;".
     * The status is one of:
     *   - ✓ / ✔  bed alive
     *   - ✗ / ✘  team eliminated
     *   - a single digit   bed gone, that many players still alive
     * An optional " YOU" suffix marks the local player's team. Pre-lobby and
     * mode-select sidebars never produce rows that match this pattern.
     */
    private static final Pattern TEAM_STATUS_ROW = Pattern.compile(
            "^(?:Red|Blue|Green|Yellow|Aqua|White|Pink|Gray)\\s*:\\s*(?:[✓✔✗✘]|\\d)(?:\\s+YOU)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** Returns true iff the sidebar contains at least one in-game team-status row. */
    public static boolean isMatchInProgress(Minecraft mc) {
        if (mc == null || mc.theWorld == null) {
            return false;
        }

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return false;
        }

        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return false;
        }

        Collection<Score> scores = scoreboard.getSortedScores(objective);
        for (Score score : scores) {
            String playerName = score.getPlayerName();
            if (playerName == null || playerName.startsWith("#")) {
                continue;
            }

            ScorePlayerTeam team = scoreboard.getPlayersTeam(playerName);
            String formatted = ScorePlayerTeam.formatPlayerName(team, playerName);
            String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(formatted);
            if (stripped == null) {
                continue;
            }

            if (TEAM_STATUS_ROW.matcher(stripped.trim()).matches()) {
                return true;
            }
        }

        return false;
    }
}
