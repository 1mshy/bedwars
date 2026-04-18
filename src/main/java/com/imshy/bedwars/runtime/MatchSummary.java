package com.imshy.bedwars.runtime;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.PlayerDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Snapshot of a finished Bedwars match. Built once on game-end so the HUD
 * card and chat printout can render a stable, deterministic recap even after
 * underlying caches start to expire.
 */
public class MatchSummary {

    public enum Outcome {
        WIN,
        LOSS,
        UNKNOWN
    }

    public static class TopThreat {
        public final String name;
        public final int stars;
        public final double careerFkdr;
        public final double recentFkdr;
        public final BedwarsStats.RecentWindow recentWindow;
        public final BedwarsStats.ThreatLevel threat;

        TopThreat(String name, int stars, double careerFkdr, double recentFkdr,
                  BedwarsStats.RecentWindow recentWindow, BedwarsStats.ThreatLevel threat) {
            this.name = name;
            this.stars = stars;
            this.careerFkdr = careerFkdr;
            this.recentFkdr = recentFkdr;
            this.recentWindow = recentWindow;
            this.threat = threat;
        }
    }

    public final Outcome outcome;
    public final long createdAt;
    public final long durationMs;
    public final String mapName;
    public final int playersTracked;
    public final int extremeThreatCount;
    public final int highThreatCount;
    public final int blacklistedFaced;
    public final List<TopThreat> topThreats;
    /** Names of EXTREME-threat enemies the user might want to blacklist after this loss. */
    public final List<String> suggestedBlacklist;

    private MatchSummary(Outcome outcome, long durationMs, String mapName, int playersTracked,
                          int extremeThreatCount, int highThreatCount, int blacklistedFaced,
                          List<TopThreat> topThreats, List<String> suggestedBlacklist) {
        this.outcome = outcome;
        this.createdAt = System.currentTimeMillis();
        this.durationMs = durationMs;
        this.mapName = mapName;
        this.playersTracked = playersTracked;
        this.extremeThreatCount = extremeThreatCount;
        this.highThreatCount = highThreatCount;
        this.blacklistedFaced = blacklistedFaced;
        this.topThreats = topThreats;
        this.suggestedBlacklist = suggestedBlacklist;
    }

    /**
     * Build a MatchSummary from {@link RuntimeState} + the {@link PlayerDatabase}'s
     * snapshot of who we faced. Stats are pulled from the {@link HypixelAPI}
     * cache so we don't make new network calls during the end-of-game frame.
     */
    public static MatchSummary build(Outcome outcome, long matchStartTime, String mapName,
                                      Set<String> playerNames) {
        long now = System.currentTimeMillis();
        long durationMs = matchStartTime > 0 ? Math.max(0L, now - matchStartTime) : 0L;

        List<TopThreat> rankedThreats = new ArrayList<TopThreat>();
        List<String> suggestedBlacklist = new ArrayList<String>();
        int extremeThreatCount = 0;
        int highThreatCount = 0;
        int blacklistedFaced = 0;

        PlayerDatabase db = PlayerDatabase.getInstance();

        for (String name : playerNames) {
            if (db.isBlacklisted(name)) {
                blacklistedFaced++;
            }

            BedwarsStats stats = HypixelAPI.getCachedStats(name);
            if (stats == null || !stats.isLoaded()) {
                continue;
            }

            BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
            if (threat == BedwarsStats.ThreatLevel.EXTREME) {
                extremeThreatCount++;
                if (outcome == Outcome.LOSS && !db.isBlacklisted(name)) {
                    suggestedBlacklist.add(name);
                }
            } else if (threat == BedwarsStats.ThreatLevel.HIGH) {
                highThreatCount++;
            }

            if (threat == BedwarsStats.ThreatLevel.HIGH || threat == BedwarsStats.ThreatLevel.EXTREME) {
                rankedThreats.add(new TopThreat(name, stats.getStars(), stats.getFkdr(),
                        stats.getRecentFkdr(), stats.getRecentWindow(), threat));
            }
        }

        Collections.sort(rankedThreats, new Comparator<TopThreat>() {
            @Override
            public int compare(TopThreat a, TopThreat b) {
                int byThreat = Integer.compare(threatRank(b.threat), threatRank(a.threat));
                if (byThreat != 0) return byThreat;
                int byStars = Integer.compare(b.stars, a.stars);
                if (byStars != 0) return byStars;
                return Double.compare(b.careerFkdr, a.careerFkdr);
            }
        });

        // Cap suggested blacklist + top threats so the card stays compact.
        if (rankedThreats.size() > 5) {
            rankedThreats = new ArrayList<TopThreat>(rankedThreats.subList(0, 5));
        }
        if (suggestedBlacklist.size() > 3) {
            suggestedBlacklist = new ArrayList<String>(suggestedBlacklist.subList(0, 3));
        }

        return new MatchSummary(outcome, durationMs, mapName, playerNames.size(),
                extremeThreatCount, highThreatCount, blacklistedFaced,
                rankedThreats, suggestedBlacklist);
    }

    private static int threatRank(BedwarsStats.ThreatLevel level) {
        if (level == BedwarsStats.ThreatLevel.EXTREME) return 4;
        if (level == BedwarsStats.ThreatLevel.HIGH) return 3;
        if (level == BedwarsStats.ThreatLevel.MEDIUM) return 2;
        if (level == BedwarsStats.ThreatLevel.LOW) return 1;
        return 0;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }

    public boolean isExpired(long ttlMs) {
        return getAgeMs() > ttlMs;
    }

    public String getDurationLabel() {
        long totalSec = durationMs / 1000L;
        long mins = totalSec / 60L;
        long secs = totalSec % 60L;
        return String.format("%d:%02d", mins, secs);
    }
}
