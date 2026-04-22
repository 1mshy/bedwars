package com.imshy.bedwars.runtime;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.PlayerDatabase;
import com.imshy.bedwars.runtime.TeamDangerAnalyzer.TeamDangerEntry;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Snapshot of the "scouting report" shown to the player right as a Bedwars
 * match begins. Built once from the lobby-populated Hypixel stat cache so it
 * stays stable and doesn't thrash as new tab-list data arrives mid-render.
 */
public class PreGameBriefing {

    public static class TeamBriefing {
        public final String teamName;
        public final String teamColor;
        public final boolean isOwnTeam;
        public final int totalPlayers;
        public final int knownPlayers;
        public final int nickedPlayers;
        public final int blacklistedPlayers;
        public final double averageThreatScore;
        public final int combinedStars;
        public final String topThreatName;
        public final int topThreatStars;
        public final double topThreatFkdr;
        public final BedwarsStats.ThreatLevel topThreatLevel;

        TeamBriefing(String teamName, String teamColor, boolean isOwnTeam,
                     int totalPlayers, int knownPlayers, int nickedPlayers, int blacklistedPlayers,
                     double averageThreatScore, int combinedStars,
                     String topThreatName, int topThreatStars, double topThreatFkdr,
                     BedwarsStats.ThreatLevel topThreatLevel) {
            this.teamName = teamName;
            this.teamColor = teamColor;
            this.isOwnTeam = isOwnTeam;
            this.totalPlayers = totalPlayers;
            this.knownPlayers = knownPlayers;
            this.nickedPlayers = nickedPlayers;
            this.blacklistedPlayers = blacklistedPlayers;
            this.averageThreatScore = averageThreatScore;
            this.combinedStars = combinedStars;
            this.topThreatName = topThreatName;
            this.topThreatStars = topThreatStars;
            this.topThreatFkdr = topThreatFkdr;
            this.topThreatLevel = topThreatLevel;
        }
    }

    public final long createdAt;
    public final String mapName;
    public final List<TeamBriefing> teams;
    public final String focusTargetName;
    public final BedwarsStats.ThreatLevel focusTargetThreat;

    private PreGameBriefing(String mapName, List<TeamBriefing> teams,
                            String focusTargetName, BedwarsStats.ThreatLevel focusTargetThreat) {
        this.createdAt = System.currentTimeMillis();
        this.mapName = mapName;
        this.teams = teams;
        this.focusTargetName = focusTargetName;
        this.focusTargetThreat = focusTargetThreat;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }

    public boolean isExpired(long ttlMs) {
        return getAgeMs() > ttlMs;
    }

    /**
     * Build a briefing from the team-danger breakdown plus the current
     * Hypixel stat cache. Returns {@code null} when not enough team data is
     * available (e.g. the tab list hasn't populated yet — caller can retry).
     */
    public static PreGameBriefing build(Minecraft mc, TeamDangerAnalyzer analyzer,
                                         String mapName) {
        List<TeamDangerEntry> summaries = analyzer.buildTeamDangerSummary(mc);
        if (summaries.isEmpty()) {
            return null;
        }

        List<TabListScanner.TabListPlayer> tabPlayers = TabListScanner.scanAllPlayers(mc);

        PlayerDatabase db = PlayerDatabase.getInstance();
        List<TeamBriefing> teamBriefings = new ArrayList<TeamBriefing>();
        TeamBriefing enemyFocusTeam = null;

        for (TeamDangerEntry entry : summaries) {
            String topName = null;
            int topStars = 0;
            double topFkdr = 0.0;
            BedwarsStats.ThreatLevel topLevel = BedwarsStats.ThreatLevel.UNKNOWN;
            int combinedStars = 0;
            int knownPlayers = 0;
            int blacklisted = 0;

            for (TabListScanner.TabListPlayer tp : tabPlayers) {
                if (tp.teamColorCode == null) {
                    continue;
                }
                if (!tp.teamName.equalsIgnoreCase(entry.teamName)) {
                    continue;
                }

                if (db.isBlacklisted(tp.name)) {
                    blacklisted++;
                }

                BedwarsStats stats = HypixelAPI.getCachedStats(tp.name);
                if (stats == null || !stats.isLoaded()) {
                    continue;
                }
                knownPlayers++;
                combinedStars += stats.getStars();

                int rankScore = threatRank(stats.getThreatLevel());
                int existingRank = threatRank(topLevel);
                if (rankScore > existingRank
                        || (rankScore == existingRank && stats.getStars() > topStars)) {
                    topName = tp.name;
                    topStars = stats.getStars();
                    topFkdr = stats.getFkdr();
                    topLevel = stats.getThreatLevel();
                }
            }

            TeamBriefing tb = new TeamBriefing(
                    entry.teamName, entry.teamColor, entry.isOwnTeam,
                    entry.totalPlayers,
                    knownPlayers > 0 ? knownPlayers : entry.playersWithKnownThreat,
                    entry.nickedPlayers, blacklisted,
                    entry.getAverageThreatScore(), combinedStars,
                    topName, topStars, topFkdr, topLevel);
            teamBriefings.add(tb);

            if (!entry.isOwnTeam) {
                if (enemyFocusTeam == null
                        || tb.averageThreatScore > enemyFocusTeam.averageThreatScore) {
                    enemyFocusTeam = tb;
                }
            }
        }

        Collections.sort(teamBriefings, new Comparator<TeamBriefing>() {
            @Override
            public int compare(TeamBriefing a, TeamBriefing b) {
                if (a.isOwnTeam != b.isOwnTeam) {
                    return a.isOwnTeam ? -1 : 1;
                }
                return Double.compare(b.averageThreatScore, a.averageThreatScore);
            }
        });

        String focusName = enemyFocusTeam != null ? enemyFocusTeam.topThreatName : null;
        BedwarsStats.ThreatLevel focusLevel = enemyFocusTeam != null
                ? enemyFocusTeam.topThreatLevel
                : BedwarsStats.ThreatLevel.UNKNOWN;

        return new PreGameBriefing(mapName, teamBriefings, focusName, focusLevel);
    }

    private static int threatRank(BedwarsStats.ThreatLevel level) {
        if (level == null) return 0;
        switch (level) {
            case EXTREME: return 5;
            case HIGH: return 4;
            case NICKED: return 3;
            case MEDIUM: return 2;
            case LOW: return 1;
            default: return 0;
        }
    }
}
