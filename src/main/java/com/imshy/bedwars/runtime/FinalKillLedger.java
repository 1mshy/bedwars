package com.imshy.bedwars.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-match ledger of final-kill events, grouped by the victim's team.
 *
 * <p>This is intentionally a simple in-memory accumulator: it's populated from
 * {@link com.imshy.bedwars.runtime.BedwarsRuntime}'s chat handler and read by
 * the HUD renderer. The runtime clears it on match end.
 */
public class FinalKillLedger {

    /** Max ms between kills on the same team that still counts as "hot streak". */
    private static final long STREAK_WINDOW_MS = 30_000L;

    public static class TeamTally {
        public final String teamName;
        public final String teamColor;
        public int finalKills;
        public String lastVictim;
        public long lastKillTime;

        TeamTally(String teamName, String teamColor) {
            this.teamName = teamName;
            this.teamColor = teamColor;
        }

        public boolean isOnStreak(long now) {
            return lastKillTime > 0 && (now - lastKillTime) <= STREAK_WINDOW_MS;
        }
    }

    /** Per-killer running tally of final kills within the current match. */
    public static class KillerTally {
        public final String killerName;
        public int finalKills;
        public long lastKillTime;

        KillerTally(String killerName) {
            this.killerName = killerName;
        }

        public boolean isOnStreak(long now) {
            return lastKillTime > 0 && (now - lastKillTime) <= STREAK_WINDOW_MS;
        }
    }

    private final Map<String, TeamTally> tallies = new LinkedHashMap<String, TeamTally>();
    /** Keyed by lowercased killer name; the display name is kept on the tally. */
    private final Map<String, KillerTally> killerTallies = new LinkedHashMap<String, KillerTally>();

    /**
     * Record a final kill. {@code victimTeamName} and {@code victimTeamColor} may
     * be {@code null} if the victim's team couldn't be resolved — the entry is
     * still added under a synthetic "UNKNOWN" bucket so the total count stays
     * accurate.
     */
    public void recordFinalKill(String victimName, String victimTeamName, String victimTeamColor) {
        recordFinalKill(victimName, victimTeamName, victimTeamColor, null);
    }

    /**
     * Record a final kill with the killer attached. {@code killerName} may be
     * {@code null} (environmental/possessive phrasings capture no killer), in
     * which case only the victim-team tally is updated.
     */
    public void recordFinalKill(String victimName, String victimTeamName, String victimTeamColor,
            String killerName) {
        String key = victimTeamName != null ? victimTeamName : "UNKNOWN";
        TeamTally tally = tallies.get(key);
        if (tally == null) {
            tally = new TeamTally(key, victimTeamColor != null ? victimTeamColor : "\u00a77");
            tallies.put(key, tally);
        }
        tally.finalKills++;
        tally.lastVictim = victimName;
        tally.lastKillTime = System.currentTimeMillis();

        if (killerName != null && !killerName.isEmpty()) {
            String killerKey = killerName.toLowerCase(java.util.Locale.ROOT);
            KillerTally killer = killerTallies.get(killerKey);
            if (killer == null) {
                killer = new KillerTally(killerName);
                killerTallies.put(killerKey, killer);
            }
            killer.finalKills++;
            killer.lastKillTime = tally.lastKillTime;
        }
    }

    public Map<String, TeamTally> getTallies() {
        return tallies;
    }

    /** Final kills credited to {@code playerName} this match (0 when none). */
    public int getKillerFinals(String playerName) {
        if (playerName == null) {
            return 0;
        }
        KillerTally tally = killerTallies.get(playerName.toLowerCase(java.util.Locale.ROOT));
        return tally == null ? 0 : tally.finalKills;
    }

    /** The match's top fragger, or {@code null} when no killer is attributed yet. */
    public KillerTally getTopKiller() {
        KillerTally top = null;
        for (KillerTally tally : killerTallies.values()) {
            if (top == null || tally.finalKills > top.finalKills) {
                top = tally;
            }
        }
        return top;
    }

    public int getTotalFinalKills() {
        int total = 0;
        for (TeamTally t : tallies.values()) {
            total += t.finalKills;
        }
        return total;
    }

    public void clear() {
        tallies.clear();
        killerTallies.clear();
    }
}
