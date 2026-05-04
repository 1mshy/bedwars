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
     * Sidebar rows for active teams use the form "<TeamName>: <status>".
     * The status is one of:
     *   - ✓ / ✔  bed alive
     *   - ✗ / ✘  team eliminated
     *   - a single digit   bed gone, that many players still alive
     * An optional " YOU" suffix marks the local player's team. Pre-lobby and
     * mode-select sidebars never produce rows that match this pattern.
     *
     * Group 1 — team colour name (e.g. "Red")
     * Group 2 — status char (✓ ✔ ✗ ✘ or a digit)
     * Group 3 — non-null when " YOU" suffix present
     */
    private static final Pattern TEAM_STATUS_ROW = Pattern.compile(
            "^(Red|Blue|Green|Yellow|Aqua|White|Pink|Gray)\\s*:\\s*([✓✔✗✘]|\\d)(\\s+YOU)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    // -----------------------------------------------------------------------
    // Public data type
    // -----------------------------------------------------------------------

    /**
     * Parsed snapshot of a single team's scoreboard row.
     */
    public static final class TeamStatus {
        /** Colour name as it appears on the scoreboard, e.g. "Red", "Blue". */
        public final String teamName;
        /**
         * Status classification:<br>
         * {@code 'B'} — bed alive (✓/✔)<br>
         * {@code 'D'} — bed destroyed, players still alive (digit row)<br>
         * {@code 'E'} — team fully eliminated (✗/✘)
         */
        public final char statusType;
        /** Number of players still alive when {@code statusType == 'D'}, otherwise 0. */
        public final int bedGoneCount;
        /** True when this row carries the " YOU" suffix — i.e. it is the local player's team. */
        public final boolean isOwnTeam;
        /**
         * The leading Minecraft colour code for this row (e.g. {@code "§c"} for Red),
         * or an empty string if the row had no colour formatting.
         */
        public final String colorCode;

        TeamStatus(String teamName, char statusType, int bedGoneCount,
                   boolean isOwnTeam, String colorCode) {
            this.teamName    = teamName;
            this.statusType  = statusType;
            this.bedGoneCount = bedGoneCount;
            this.isOwnTeam   = isOwnTeam;
            this.colorCode   = colorCode;
        }

        @Override
        public String toString() {
            return teamName + ":" + statusType
                    + (statusType == 'D' ? bedGoneCount : "")
                    + (isOwnTeam ? "(YOU)" : "");
        }
    }

    // -----------------------------------------------------------------------
    // Core parsing
    // -----------------------------------------------------------------------

    /**
     * Parses every team-status row currently visible in the sidebar and returns
     * one {@link TeamStatus} per matched row.  Returns an empty list when the
     * sidebar is absent or contains no in-game team rows.
     */
    public static java.util.List<TeamStatus> parseTeamStatuses(Minecraft mc) {
        java.util.List<TeamStatus> result = new java.util.ArrayList<TeamStatus>();

        if (mc == null || mc.theWorld == null) {
            return result;
        }

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return result;
        }

        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return result;
        }

        Collection<Score> scores = scoreboard.getSortedScores(objective);
        for (Score score : scores) {
            String playerName = score.getPlayerName();
            if (playerName == null || playerName.startsWith("#")) {
                continue;
            }

            ScorePlayerTeam team = scoreboard.getPlayersTeam(playerName);
            String formatted = ScorePlayerTeam.formatPlayerName(team, playerName);
            String stripped  = EnumChatFormatting.getTextWithoutFormattingCodes(formatted);
            if (stripped == null) {
                continue;
            }

            java.util.regex.Matcher m = TEAM_STATUS_ROW.matcher(stripped.trim());
            if (!m.matches()) {
                continue;
            }

            String teamName  = m.group(1);
            String statusStr = m.group(2);
            boolean isOwnTeam = m.group(3) != null;

            char statusType;
            int bedGoneCount = 0;
            if (statusStr.equals("✓") || statusStr.equals("✔")) {
                statusType = 'B';
            } else if (statusStr.equals("✗") || statusStr.equals("✘")) {
                statusType = 'E';
            } else {
                statusType = 'D';
                bedGoneCount = Character.getNumericValue(statusStr.charAt(0));
            }

            // Extract the leading colour code from the formatted (un-stripped) name
            String colorCode = extractLeadingColorCode(formatted);

            result.add(new TeamStatus(teamName, statusType, bedGoneCount, isOwnTeam, colorCode));
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Convenience helpers
    // -----------------------------------------------------------------------

    /** Returns true iff the sidebar contains at least one in-game team-status row. */
    public static boolean isMatchInProgress(Minecraft mc) {
        return !parseTeamStatuses(mc).isEmpty();
    }

    /**
     * Returns the Minecraft colour code string (e.g. {@code "§c"}) for the local
     * player's own team as reported by the " YOU" suffix on the scoreboard row,
     * or {@code null} if the sidebar is absent or no row carries the suffix.
     */
    public static String getOwnTeamColorCode(Minecraft mc) {
        for (TeamStatus ts : parseTeamStatuses(mc)) {
            if (ts.isOwnTeam && !ts.colorCode.isEmpty()) {
                return ts.colorCode;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Scans a formatted (§-coded) string and returns the last colour code found
     * before any non-formatting characters, as a two-character string like "§c".
     * Returns an empty string if no colour code is found.
     */
    private static String extractLeadingColorCode(String formatted) {
        if (formatted == null) {
            return "";
        }
        String lastColor = "";
        for (int i = 0; i < formatted.length() - 1; i++) {
            if (formatted.charAt(i) != '\u00A7') {
                continue;
            }
            char code = Character.toLowerCase(formatted.charAt(i + 1));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                lastColor = "\u00A7" + code;
            } else if (code == 'r') {
                lastColor = "";
            }
            i++; // skip the code char
        }
        return lastColor;
    }
}
