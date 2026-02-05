package com.imshy.bedwars;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Map metadata and sidebar parsing for Bedwars rush predictions.
 */
public class MapMetadataRegistry {

    private static final int DEFAULT_BASE_RUSH_SECONDS = 36;

    private static final Map<String, Integer> BASE_RUSH_SECONDS_BY_MAP = new HashMap<String, Integer>();
    static {
        // Smaller/fast maps
        BASE_RUSH_SECONDS_BY_MAP.put("lighthouse", 24);
        BASE_RUSH_SECONDS_BY_MAP.put("speedway", 24);
        BASE_RUSH_SECONDS_BY_MAP.put("acropolis", 26);
        BASE_RUSH_SECONDS_BY_MAP.put("crypt", 26);

        // Medium maps
        BASE_RUSH_SECONDS_BY_MAP.put("airshow", 28);
        BASE_RUSH_SECONDS_BY_MAP.put("orbit", 28);
        BASE_RUSH_SECONDS_BY_MAP.put("solace", 28);
        BASE_RUSH_SECONDS_BY_MAP.put("lectus", 30);
        BASE_RUSH_SECONDS_BY_MAP.put("pernicious", 30);

        // Slower maps
        BASE_RUSH_SECONDS_BY_MAP.put("dreamgrove", 34);
        BASE_RUSH_SECONDS_BY_MAP.put("ashfire", 34);
        BASE_RUSH_SECONDS_BY_MAP.put("zarzul", 36);
    }

    /**
     * Tries to detect current map name from the Bedwars sidebar.
     */
    public static String detectCurrentMapName(Minecraft mc) {
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
            if (stripped == null) {
                continue;
            }

            String trimmed = stripped.trim();
            String lower = trimmed.toLowerCase();
            if (lower.startsWith("map:")) {
                String mapName = trimmed.substring(4).trim();
                if (!mapName.isEmpty()) {
                    return mapName;
                }
            }
        }

        return null;
    }

    public static int getBaseRushSeconds(String mapName) {
        if (mapName == null || mapName.trim().isEmpty()) {
            return DEFAULT_BASE_RUSH_SECONDS;
        }

        String key = mapName.trim().toLowerCase();
        Integer value = BASE_RUSH_SECONDS_BY_MAP.get(key);
        return value != null ? value.intValue() : DEFAULT_BASE_RUSH_SECONDS;
    }
}
