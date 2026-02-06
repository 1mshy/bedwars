package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.BedLocator;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.RushRiskPredictor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class MatchThreatService {
    private static final int BED_PROXIMITY_WARNING_DISTANCE = 15;
    private static final long BED_WARNING_COOLDOWN = 5000;
    private static final long BED_WARNING_START_DELAY = 10000;
    private static final long BED_DETECTION_ATTEMPT_INTERVAL = 1000;
    private static final int BED_SCAN_VERTICAL_RANGE = 8;

    private static final double TEAMMATE_SPAWN_RADIUS_BLOCKS = 10.0;
    private static final long TEAMMATE_SPAWN_CAPTURE_WINDOW_MS = 2000;

    private static final long RUSH_PREDICTOR_ACTIVE_WINDOW_MS = 90_000;

    private final RuntimeState state;
    private final TeamDangerAnalyzer teamDangerAnalyzer;

    MatchThreatService(RuntimeState state, TeamDangerAnalyzer teamDangerAnalyzer) {
        this.state = state;
        this.teamDangerAnalyzer = teamDangerAnalyzer;
    }

    public void startBedTracking(Minecraft mc, long currentTime) {
        if (mc.thePlayer == null) {
            return;
        }

        clearBedTrackingState();
        state.fallbackBedPosition = mc.thePlayer.getPosition();

        if (!ModConfig.isMapAwareBedDetectionEnabled()) {
            state.playerBedBlocks.add(state.fallbackBedPosition);
            state.usingBedFallback = true;
            System.out.println("[BedwarsStats] Map-aware bed detection disabled. Using spawn fallback: "
                    + state.fallbackBedPosition);
            return;
        }

        state.bedDetectionPending = true;
        state.bedDetectionStartTime = currentTime;
        attemptBedDetection(mc, currentTime);
    }

    public void maybeRetryBedDetection(Minecraft mc, long currentTime) {
        if (!state.bedDetectionPending || !ModConfig.isMapAwareBedDetectionEnabled()) {
            return;
        }
        if (currentTime - state.lastBedDetectionAttempt >= BED_DETECTION_ATTEMPT_INTERVAL) {
            attemptBedDetection(mc, currentTime);
        }
    }

    public void attemptBedDetection(Minecraft mc, long currentTime) {
        if (!state.bedDetectionPending || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        state.lastBedDetectionAttempt = currentTime;

        BedLocator.BedLocation bedLocation = BedLocator.locateNearestBed(
                mc.theWorld,
                mc.thePlayer.getPosition(),
                ModConfig.getBedScanRange(),
                BED_SCAN_VERTICAL_RANGE);

        if (bedLocation != null && !bedLocation.getBedBlocks().isEmpty()) {
            state.playerBedBlocks.clear();
            state.playerBedBlocks.addAll(bedLocation.getBedBlocks());
            state.usingBedFallback = false;
            state.bedDetectionPending = false;
            System.out.println("[BedwarsStats] Map-aware bed detected: " + state.playerBedBlocks);
            return;
        }

        long retryWindowMs = ModConfig.getBedScanRetrySeconds() * 1000L;
        if (currentTime - state.bedDetectionStartTime < retryWindowMs) {
            return;
        }

        state.bedDetectionPending = false;
        state.playerBedBlocks.clear();
        if (state.fallbackBedPosition != null) {
            state.playerBedBlocks.add(state.fallbackBedPosition);
            state.usingBedFallback = true;
            System.out.println("[BedwarsStats] Bed scan timed out. Using spawn fallback: " + state.fallbackBedPosition);
        }
    }

    public void clearBedTrackingState() {
        state.playerBedBlocks.clear();
        state.fallbackBedPosition = null;
        state.usingBedFallback = false;
        state.bedDetectionPending = false;
        state.bedDetectionStartTime = 0;
        state.lastBedDetectionAttempt = 0;
        state.lastBedWarningTime.clear();
        state.inferredTeammateUuids.clear();
        resetRushPredictorState();
    }

    public void captureEarlySpawnTeammates(Minecraft mc, long currentTime) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (!isWithinSpawnTeammateWindow(currentTime)) {
            return;
        }

        double teammateRadiusSq = TEAMMATE_SPAWN_RADIUS_BLOCKS * TEAMMATE_SPAWN_RADIUS_BLOCKS;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }
            if (isWatchdogBotName(player.getName())) {
                continue;
            }

            if (mc.thePlayer.getDistanceSqToEntity(player) <= teammateRadiusSq) {
                state.inferredTeammateUuids.add(player.getUniqueID().toString());
            }
        }
    }

    public void checkRushRiskPredictor(Minecraft mc, long currentTime) {
        if (state.gameStartTime <= 0 || (currentTime - state.gameStartTime) > RUSH_PREDICTOR_ACTIVE_WINDOW_MS) {
            return;
        }

        long intervalMs = Math.max(1, ModConfig.getRushRecheckIntervalSeconds()) * 1000L;
        if (currentTime - state.lastRushPredictorCheck < intervalMs) {
            return;
        }
        state.lastRushPredictorCheck = currentTime;

        String detectedMap = com.imshy.bedwars.MapMetadataRegistry.detectCurrentMapName(mc);
        if (detectedMap != null && !detectedMap.trim().isEmpty()) {
            state.lastDetectedMapName = detectedMap.trim();
        }

        int baseRushSeconds = com.imshy.bedwars.MapMetadataRegistry.getBaseRushSeconds(state.lastDetectedMapName);
        double highestEnemyTeamThreat = teamDangerAnalyzer.getHighestEnemyTeamThreatAverage(mc);
        double nearestEnemyDistance = getNearestEnemyDistanceToBed(mc);

        RushRiskPredictor.Estimate estimate = RushRiskPredictor.estimateFirstRush(
                baseRushSeconds,
                highestEnemyTeamThreat,
                nearestEnemyDistance);

        state.lastPredictedRushEtaSeconds = estimate.etaSeconds;

        long predictedRushTimestamp = state.gameStartTime + (estimate.etaSeconds * 1000L);
        long secondsUntilRush = (predictedRushTimestamp - currentTime + 999L) / 1000L;

        if (state.rushRiskWarningSent || secondsUntilRush <= 0) {
            return;
        }

        if (secondsUntilRush <= ModConfig.getRushWarningThresholdSeconds()) {
            String mapLabel = "Unknown".equals(state.lastDetectedMapName)
                    ? "unknown map"
                    : state.lastDetectedMapName;

            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[Rush Risk] " +
                            riskLevelColor(estimate.riskLevel) + estimate.riskLevel + " " +
                            EnumChatFormatting.YELLOW + "first rush likely in ~" + secondsUntilRush + "s " +
                            EnumChatFormatting.GRAY + "(" + mapLabel + ", base " + baseRushSeconds + "s)"));

            AudioCueManager.playCue(mc, AudioCueManager.CueType.BED_DANGER);
            state.rushRiskWarningSent = true;
        }
    }

    public void checkBedProximityWarnings(Minecraft mc, long currentTime) {
        if (state.playerBedBlocks.isEmpty()) {
            return;
        }

        if (!ModConfig.isBedProximityAlertsEnabled()) {
            return;
        }

        if (currentTime - state.gameStartTime < BED_WARNING_START_DELAY) {
            return;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }

            String playerName = player.getName();
            if (isWatchdogBotName(playerName)) {
                continue;
            }

            if (isTeammate(mc, mc.thePlayer, player)) {
                continue;
            }

            double distance = getDistanceToNearestTrackedBed(player.getPosition());
            if (distance < 0) {
                continue;
            }

            if (distance <= BED_PROXIMITY_WARNING_DISTANCE) {
                Long lastWarning = state.lastBedWarningTime.get(playerName);
                if (lastWarning == null || (currentTime - lastWarning) >= BED_WARNING_COOLDOWN) {
                    String warningMessage = EnumChatFormatting.DARK_RED + "⚠ BED WARNING: " +
                            EnumChatFormatting.RED + playerName +
                            EnumChatFormatting.YELLOW + " is " + (int) distance + " blocks from your bed!";
                    mc.thePlayer.addChatMessage(new ChatComponentText(warningMessage));
                    AudioCueManager.playCue(mc, AudioCueManager.CueType.BED_DANGER);

                    state.lastBedWarningTime.put(playerName, currentTime);
                }
            }
        }
    }

    public boolean isWatchdogBotName(String playerName) {
        if (playerName == null || playerName.length() != 10) {
            return false;
        }

        for (int i = 0; i < playerName.length(); i++) {
            char c = playerName.charAt(i);
            boolean isDigit = c >= '0' && c <= '9';
            boolean isLowercaseLetter = c >= 'a' && c <= 'z';
            if (!isDigit && !isLowercaseLetter) {
                return false;
            }
        }
        return true;
    }

    private void resetRushPredictorState() {
        state.lastRushPredictorCheck = 0;
        state.rushRiskWarningSent = false;
        state.lastPredictedRushEtaSeconds = -1;
        state.lastDetectedMapName = "Unknown";
    }

    private double getDistanceToNearestTrackedBed(BlockPos position) {
        if (state.playerBedBlocks.isEmpty() || position == null) {
            return -1;
        }

        double closest = Double.MAX_VALUE;
        for (BlockPos bedBlock : state.playerBedBlocks) {
            double distance = Math.sqrt(position.distanceSq(bedBlock));
            if (distance < closest) {
                closest = distance;
            }
        }

        return closest == Double.MAX_VALUE ? -1 : closest;
    }

    private double getNearestEnemyDistanceToBed(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return -1.0;
        }

        BlockPos referencePos = state.fallbackBedPosition;
        if (!state.playerBedBlocks.isEmpty()) {
            referencePos = state.playerBedBlocks.get(0);
        } else if (referencePos == null) {
            referencePos = mc.thePlayer.getPosition();
        }

        double nearestDistance = Double.MAX_VALUE;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }

            if (isWatchdogBotName(player.getName())) {
                continue;
            }

            if (isTeammate(mc, mc.thePlayer, player)) {
                continue;
            }

            double distance = Math.sqrt(player.getPosition().distanceSq(referencePos));
            if (distance < nearestDistance) {
                nearestDistance = distance;
            }
        }

        return nearestDistance == Double.MAX_VALUE ? -1.0 : nearestDistance;
    }

    private boolean isTeammate(Minecraft mc, EntityPlayer self, EntityPlayer other) {
        if (self == null || other == null) {
            return false;
        }

        if (self.getUniqueID().equals(other.getUniqueID())) {
            return true;
        }

        if (state.inferredTeammateUuids.contains(other.getUniqueID().toString())) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        if (isWithinSpawnTeammateWindow(currentTime)) {
            double teammateRadiusSq = TEAMMATE_SPAWN_RADIUS_BLOCKS * TEAMMATE_SPAWN_RADIUS_BLOCKS;
            if (self.getDistanceSqToEntity(other) <= teammateRadiusSq) {
                state.inferredTeammateUuids.add(other.getUniqueID().toString());
                return true;
            }
        }

        Character selfTabColor = getTabNamePrimaryColorCode(mc, self);
        Character otherTabColor = getTabNamePrimaryColorCode(mc, other);
        if (selfTabColor != null && otherTabColor != null) {
            return selfTabColor.charValue() == otherTabColor.charValue();
        }

        if (self.isOnSameTeam(other)) {
            return true;
        }

        String selfTeamKey = getPlayerTeamKey(self);
        String otherTeamKey = getPlayerTeamKey(other);
        return selfTeamKey != null && selfTeamKey.equals(otherTeamKey);
    }

    private boolean isWithinSpawnTeammateWindow(long currentTime) {
        return state.inBedwarsLobby &&
                state.gameStartTime > 0 &&
                (currentTime - state.gameStartTime) <= TEAMMATE_SPAWN_CAPTURE_WINDOW_MS;
    }

    private static String getPlayerTeamKey(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        Team team = player.getTeam();
        if (team instanceof ScorePlayerTeam) {
            return ((ScorePlayerTeam) team).getRegisteredName();
        }
        return null;
    }

    private Character getTabNamePrimaryColorCode(Minecraft mc, EntityPlayer player) {
        String formattedName = getFormattedTabName(mc, player);
        if (formattedName == null || formattedName.isEmpty()) {
            return null;
        }

        Character activeColorCode = null;
        for (int i = 0; i < formattedName.length() - 1; i++) {
            if (formattedName.charAt(i) != '\u00A7') {
                continue;
            }

            char code = Character.toLowerCase(formattedName.charAt(i + 1));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                activeColorCode = Character.valueOf(code);
            }
        }
        return activeColorCode;
    }

    private String getFormattedTabName(Minecraft mc, EntityPlayer player) {
        if (mc == null || mc.getNetHandler() == null || player == null) {
            return null;
        }

        NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (playerInfo == null) {
            return null;
        }

        if (playerInfo.getDisplayName() != null) {
            return playerInfo.getDisplayName().getFormattedText();
        }

        Team team = player.getTeam();
        if (team instanceof ScorePlayerTeam) {
            return ScorePlayerTeam.formatPlayerName((ScorePlayerTeam) team, player.getName());
        }
        return player.getName();
    }

    private static String riskLevelColor(String riskLevel) {
        if ("HIGH".equals(riskLevel)) {
            return EnumChatFormatting.RED.toString();
        }
        if ("MEDIUM".equals(riskLevel)) {
            return EnumChatFormatting.YELLOW.toString();
        }
        return EnumChatFormatting.GREEN.toString();
    }
}
