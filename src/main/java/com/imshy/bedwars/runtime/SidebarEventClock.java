package com.imshy.bedwars.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Hypixel sidebar's upcoming-event row ("Diamond II in 3:45",
 * "Emerald III in 0:12", "Bed Gone In 5:00", ...) into an absolute deadline.
 *
 * <p>Hypixel shows exactly one upcoming event at a time, so the server-exact
 * countdown for the NEXT event is always on screen; this replaces the guessed
 * {@link GeneratorTierSchedule} numbers with the value the server literally
 * prints. Parsing is pure ({@link #parseRow}) for unit tests; only
 * {@link #scan} touches Minecraft.
 */
public final class SidebarEventClock {

    /**
     * "<label> in M:SS" — the label is kept verbatim for display; generator
     * tier rows ("Diamond II", "Emerald III") are additionally classified so
     * consumers can fire tier-specific cues.
     */
    private static final Pattern EVENT_ROW = Pattern.compile(
            "^([A-Za-z][A-Za-z ]{1,30}?)\\s+[Ii]n\\s+(\\d{1,2}):(\\d{2})$");

    private static final Pattern GENERATOR_LABEL = Pattern.compile(
            "^(Diamond|Emerald)\\s+(I{1,3}|IV)$", Pattern.CASE_INSENSITIVE);

    /** One parsed upcoming event with an absolute deadline. */
    public static final class NextEvent {
        /** Verbatim sidebar label, e.g. "Diamond II". */
        public final String label;
        /** Absolute wall-clock deadline derived from the sidebar countdown. */
        public final long deadlineMs;
        /** True when the label is a diamond/emerald tier upgrade. */
        public final boolean isGeneratorTier;

        NextEvent(String label, long deadlineMs, boolean isGeneratorTier) {
            this.label = label;
            this.deadlineMs = deadlineMs;
            this.isGeneratorTier = isGeneratorTier;
        }

        public int secondsRemaining(long nowMs) {
            long remaining = deadlineMs - nowMs;
            return remaining <= 0 ? 0 : (int) ((remaining + 999) / 1000);
        }
    }

    private SidebarEventClock() {
    }

    /**
     * Parses one stripped sidebar row. Returns {@code null} when the row is
     * not an upcoming-event row.
     */
    public static NextEvent parseRow(String strippedRow, long nowMs) {
        if (strippedRow == null) {
            return null;
        }
        Matcher m = EVENT_ROW.matcher(strippedRow.trim());
        if (!m.matches()) {
            return null;
        }
        String label = m.group(1).trim();
        int minutes = Integer.parseInt(m.group(2));
        int seconds = Integer.parseInt(m.group(3));
        if (seconds >= 60) {
            return null;
        }
        long deadline = nowMs + (minutes * 60L + seconds) * 1000L;
        boolean generatorTier = GENERATOR_LABEL.matcher(label).matches();
        return new NextEvent(label, deadline, generatorTier);
    }

    /**
     * Scans the visible sidebar for the upcoming-event row. Returns the first
     * match (Hypixel only shows one), or {@code null} when absent.
     */
    public static NextEvent scan(Minecraft mc, long nowMs) {
        if (mc == null || mc.theWorld == null) {
            return null;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return null;
        }
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return null;
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
            NextEvent event = parseRow(stripped, nowMs);
            if (event != null) {
                return event;
            }
        }
        return null;
    }
}
