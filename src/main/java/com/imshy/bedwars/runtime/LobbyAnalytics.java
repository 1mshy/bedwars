package com.imshy.bedwars.runtime;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Lobby difficulty as one number: aggregates the stats ALREADY sitting in
 * {@link HypixelAPI}'s cache for the tab-scanned lobby players — zero new
 * network calls — into an average stars/FKDR, a Chill/Average/Sweaty tier and
 * the local player's percentile within the lobby.
 *
 * <p>The known-player count is surfaced alongside the average because nicked
 * and not-yet-fetched players bias it: "avg 2.1 FKDR (12/16 known)" is honest,
 * "avg 2.1 FKDR" alone is not.
 */
public final class LobbyAnalytics {

    /** FKDR tier boundaries (community-typical). */
    static final double SWEATY_FKDR = 3.0;
    static final double AVERAGE_FKDR = 1.5;

    public static final class LobbySummary {
        public final int knownPlayers;
        public final int totalPlayers;
        public final double avgStars;
        public final double avgFkdr;
        /** "Chill", "Average" or "Sweaty". */
        public final String tier;
        /** Local player's FKDR percentile among known players (0-100), -1 unknown. */
        public final int ownPercentile;

        LobbySummary(int knownPlayers, int totalPlayers, double avgStars, double avgFkdr,
                String tier, int ownPercentile) {
            this.knownPlayers = knownPlayers;
            this.totalPlayers = totalPlayers;
            this.avgStars = avgStars;
            this.avgFkdr = avgFkdr;
            this.tier = tier;
            this.ownPercentile = ownPercentile;
        }
    }

    private LobbyAnalytics() {
    }

    /**
     * Scans the tab list and folds the cached stats. Client thread only (tab
     * scan + cache reads). Returns {@code null} when fewer than 2 lobby
     * players have cached stats — an average of one player is noise.
     */
    public static LobbySummary compute(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        List<TabListScanner.TabListPlayer> players = TabListScanner.scanAllPlayers(mc);
        if (players.isEmpty()) {
            return null;
        }
        String ownName = mc.thePlayer.getName();
        List<double[]> known = new ArrayList<double[]>();
        double[] own = null;
        int total = 0;
        for (TabListScanner.TabListPlayer p : players) {
            total++;
            BedwarsStats stats = HypixelAPI.getCachedStats(p.name);
            if (stats == null) {
                continue;
            }
            // Nicked/error placeholders are cached with zero stats — counting
            // them would dilute the average and inflate the percentile (same
            // filter TeamDangerAnalyzer applies).
            if (stats.isNicked() || !stats.isLoaded() || stats.hasError()) {
                continue;
            }
            double[] sample = {stats.getStars(), stats.getFkdr()};
            known.add(sample);
            if (p.name.equalsIgnoreCase(ownName)) {
                own = sample;
            }
        }
        return summarize(known, own, total);
    }

    /** Pure aggregation — unit-testable without Minecraft. */
    static LobbySummary summarize(List<double[]> knownStats, double[] ownStats, int totalPlayers) {
        if (knownStats.size() < 2) {
            return null;
        }
        double starSum = 0;
        double fkdrSum = 0;
        for (double[] s : knownStats) {
            starSum += s[0];
            fkdrSum += s[1];
        }
        double avgStars = starSum / knownStats.size();
        double avgFkdr = fkdrSum / knownStats.size();

        String tier = avgFkdr >= SWEATY_FKDR ? "Sweaty"
                : avgFkdr >= AVERAGE_FKDR ? "Average" : "Chill";

        int percentile = -1;
        if (ownStats != null) {
            int below = 0;
            for (double[] s : knownStats) {
                if (s[1] < ownStats[1]) {
                    below++;
                }
            }
            percentile = (int) Math.round(100.0 * below / knownStats.size());
        }
        return new LobbySummary(knownStats.size(), totalPlayers, avgStars, avgFkdr,
                tier, percentile);
    }
}
