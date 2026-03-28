package com.imshy.bedwars.runtime;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;

import com.imshy.bedwars.runtime.TabListScanner.TabListPlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamDangerAnalyzer {
    private static final int TEAM_DANGER_MIN_TEAMS = 2;

    private final RuntimeState state;

    TeamDangerAnalyzer(RuntimeState state) {
        this.state = state;
    }

    public double getHighestEnemyTeamThreatAverage(Minecraft mc) {
        List<TeamDangerEntry> summaries = buildTeamDangerSummary(mc);
        double highest = 0.0;
        for (TeamDangerEntry summary : summaries) {
            if (summary.isOwnTeam || summary.playersWithKnownThreat == 0) {
                continue;
            }
            highest = Math.max(highest, summary.getAverageThreatScore());
        }
        return highest;
    }

    public List<String> buildTeamDangerLines(Minecraft mc) {
        List<TeamDangerEntry> summaries = buildTeamDangerSummary(mc);
        List<String> lines = new ArrayList<String>();
        for (TeamDangerEntry summary : summaries) {
            lines.add(formatTeamDangerLine(summary));
        }
        return lines;
    }

    public List<TeamDangerEntry> buildTeamDangerSummary(Minecraft mc) {
        if (state.gamePhase != GamePhase.IN_GAME || mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return new ArrayList<TeamDangerEntry>();
        }

        List<TabListPlayer> allPlayers = TabListScanner.scanAllPlayers(mc);
        Character ownColorCode = TabListScanner.getLocalPlayerColorCode(mc);

        Map<Character, TeamDangerEntry> byColor = new HashMap<Character, TeamDangerEntry>();
        for (TabListPlayer tp : allPlayers) {
            if (tp.teamColorCode == null) {
                continue;
            }

            TeamDangerEntry summary = byColor.get(tp.teamColorCode);
            if (summary == null) {
                boolean isOwn = ownColorCode != null && tp.teamColorCode.equals(ownColorCode);
                summary = new TeamDangerEntry(tp.teamName, tp.teamColorPrefix, isOwn);
                byColor.put(tp.teamColorCode, summary);
            }

            summary.totalPlayers++;

            BedwarsStats stats = HypixelAPI.getCachedStats(tp.name);
            if (stats == null || !stats.isLoaded()) {
                if (!state.pendingTabListFetches.contains(tp.name) && HypixelAPI.hasApiKey()) {
                    state.pendingTabListFetches.add(tp.name);
                    HypixelAPI.fetchStatsAsync(tp.name, new HypixelAPI.StatsCallback() {
                        @Override
                        public void onStatsLoaded(BedwarsStats s) { }
                        @Override
                        public void onError(String error) { }
                    });
                }
                continue;
            }

            double score = threatToScore(stats.getThreatLevel());
            if (score <= 0.0) {
                continue;
            }

            summary.playersWithKnownThreat++;
            summary.totalThreatScore += score;
        }

        if (byColor.size() < TEAM_DANGER_MIN_TEAMS) {
            return new ArrayList<TeamDangerEntry>();
        }

        List<TeamDangerEntry> summaries = new ArrayList<TeamDangerEntry>(byColor.values());
        Collections.sort(summaries, new Comparator<TeamDangerEntry>() {
            @Override
            public int compare(TeamDangerEntry a, TeamDangerEntry b) {
                double avgA = a.getAverageThreatScore();
                double avgB = b.getAverageThreatScore();
                if (avgA != avgB) {
                    return Double.compare(avgB, avgA);
                }
                return a.teamName.compareToIgnoreCase(b.teamName);
            }
        });

        return summaries;
    }

    private static String formatTeamDangerLine(TeamDangerEntry summary) {
        String dangerLabel = summary.playersWithKnownThreat > 0
                ? averageThreatLabel(summary.getAverageThreatScore())
                : "UNKNOWN";
        String dangerColor = dangerLabelColor(dangerLabel);

        String ownTeamSuffix = summary.isOwnTeam
                ? EnumChatFormatting.AQUA + " (You)"
                : "";

        return summary.teamColor + summary.teamName +
                ownTeamSuffix +
                EnumChatFormatting.GRAY + " - " +
                dangerColor + "Avg " + dangerLabel +
                EnumChatFormatting.GRAY + " (" +
                summary.playersWithKnownThreat + "/" + summary.totalPlayers + " known)";
    }

    private static double threatToScore(BedwarsStats.ThreatLevel threatLevel) {
        switch (threatLevel) {
            case LOW:
                return 1.0;
            case MEDIUM:
                return 2.0;
            case HIGH:
                return 3.0;
            case EXTREME:
                return 4.0;
            default:
                return 0.0;
        }
    }

    public static String averageThreatLabel(double averageScore) {
        if (averageScore >= 3.5) {
            return "EXTREME";
        }
        if (averageScore >= 2.5) {
            return "HIGH";
        }
        if (averageScore >= 1.5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public static String dangerLabelColor(String dangerLabel) {
        if ("EXTREME".equals(dangerLabel)) {
            return EnumChatFormatting.DARK_RED.toString();
        }
        if ("HIGH".equals(dangerLabel)) {
            return EnumChatFormatting.RED.toString();
        }
        if ("MEDIUM".equals(dangerLabel)) {
            return EnumChatFormatting.YELLOW.toString();
        }
        if ("LOW".equals(dangerLabel)) {
            return EnumChatFormatting.GREEN.toString();
        }
        return EnumChatFormatting.GRAY.toString();
    }

    public static class TeamDangerEntry {
        public final String teamName;
        public final String teamColor;
        public final boolean isOwnTeam;
        public int totalPlayers;
        public int playersWithKnownThreat;
        public double totalThreatScore;

        TeamDangerEntry(String teamName, String teamColor, boolean isOwnTeam) {
            this.teamName = teamName;
            this.teamColor = teamColor;
            this.isOwnTeam = isOwnTeam;
        }

        public double getAverageThreatScore() {
            if (playersWithKnownThreat == 0) {
                return 0.0;
            }
            return totalThreatScore / playersWithKnownThreat;
        }
    }
}
