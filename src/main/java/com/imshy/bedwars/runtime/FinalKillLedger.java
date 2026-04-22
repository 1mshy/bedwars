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

    private final Map<String, TeamTally> tallies = new LinkedHashMap<String, TeamTally>();

    /**
     * Record a final kill. {@code victimTeamName} and {@code victimTeamColor} may
     * be {@code null} if the victim's team couldn't be resolved — the entry is
     * still added under a synthetic "UNKNOWN" bucket so the total count stays
     * accurate.
     */
    public void recordFinalKill(String victimName, String victimTeamName, String victimTeamColor) {
        String key = victimTeamName != null ? victimTeamName : "UNKNOWN";
        TeamTally tally = tallies.get(key);
        if (tally == null) {
            tally = new TeamTally(key, victimTeamColor != null ? victimTeamColor : "\u00a77");
            tallies.put(key, tally);
        }
        tally.finalKills++;
        tally.lastVictim = victimName;
        tally.lastKillTime = System.currentTimeMillis();
    }

    public Map<String, TeamTally> getTallies() {
        return tallies;
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
    }
}
