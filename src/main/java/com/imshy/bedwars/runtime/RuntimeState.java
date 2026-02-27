package com.imshy.bedwars.runtime;

import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RuntimeState {
    static final long DISPLAY_DURATION = 10000;
    static final long AUTOPLAY_CHECK_DELAY = 5000;
    static final long GENERATOR_SCAN_INTERVAL = 1000;

    final List<LobbyTrackerService.PlayerJoinEntry> recentJoins = new ArrayList<LobbyTrackerService.PlayerJoinEntry>();

    boolean inBedwarsLobby = false;
    long clientTickCounter = 0;
    long joinMessageBurstTick = -1;
    int joinMessageBurstCount = 0;
    long lastPartyWarningTick = -1;
    long lastPartyAutoplaySwapTick = -1;

    final List<BlockPos> playerBedBlocks = new ArrayList<BlockPos>();
    BlockPos fallbackBedPosition = null;
    boolean usingBedFallback = false;
    boolean bedDetectionPending = false;
    long bedDetectionStartTime = 0;
    long lastBedDetectionAttempt = 0;
    long gameStartTime = 0;
    final Map<String, Long> lastBedWarningTime = new HashMap<String, Long>();
    final Set<String> inferredTeammateUuids = new HashSet<String>();

    boolean autoplayEnabled = false;
    String autoplayMode = "ones";
    long autoplayCheckTime = 0;
    boolean autoplayPendingCheck = false;

    final Map<String, Long> invisiblePlayerWarnings = new HashMap<String, Long>();

    final Map<BlockPos, WorldScanService.GeneratorEntry> trackedGenerators =
            new HashMap<BlockPos, WorldScanService.GeneratorEntry>();
    long lastGeneratorScan = 0;

    long lastRushPredictorCheck = 0;
    boolean rushRiskWarningSent = false;
    int lastPredictedRushEtaSeconds = -1;
    String lastDetectedMapName = "Unknown";
}
