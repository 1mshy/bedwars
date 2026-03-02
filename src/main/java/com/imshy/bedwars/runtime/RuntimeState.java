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
    static final long SPAM_RETRY_DELAY = 7000;
    static final long INITIAL_STAT_DISPLAY_MS = 15000;

    // --- Phase tracking ---
    GamePhase gamePhase = GamePhase.IDLE;
    boolean disconnectedFromGame = false;
    long disconnectTime = 0;
    long matchStartTime = 0;
    long clientTickCounter = 0;

    // --- Pre-game state ---
    long joinBurstTickStamp = -1;
    int joinMessageBurstCount = 0;
    boolean joinBurstContainsMainUser = false;
    final List<String> joinBurstNames = new ArrayList<String>();
    long lastPartyWarningTickStamp = -1;
    long lastPartyAutoplaySwapTickStamp = -1;
    final List<ChatDetectedPlayer> chatDetectedPlayers = new ArrayList<ChatDetectedPlayer>();
    long chatDetectedStartTime = 0;
    boolean lobbyBaitActive = false;
    long lobbyBaitFirstSentTime = 0;
    boolean lobbyBaitRetrySent = false;
    int lobbyBaitMessageIndex = -1;

    // --- In-game state ---
    final List<LobbyTrackerService.PlayerJoinEntry> recentJoins = new ArrayList<LobbyTrackerService.PlayerJoinEntry>();
    final List<BlockPos> playerBedBlocks = new ArrayList<BlockPos>();
    BlockPos fallbackBedPosition = null;
    boolean usingBedFallback = false;
    boolean bedDetectionPending = false;
    long bedDetectionStartTime = 0;
    long lastBedDetectionAttempt = 0;
    final Map<String, Long> lastBedWarningTime = new HashMap<String, Long>();
    final Set<String> inferredTeammateUuids = new HashSet<String>();
    final Map<String, Long> invisiblePlayerWarnings = new HashMap<String, Long>();
    final Map<BlockPos, WorldScanService.GeneratorEntry> trackedGenerators = new HashMap<BlockPos, WorldScanService.GeneratorEntry>();
    long lastGeneratorScan = 0;
    long lastRushPredictorCheck = 0;
    boolean rushRiskWarningSent = false;
    int lastPredictedRushEtaSeconds = -1;
    String lastDetectedMapName = "Unknown";

    // --- Enemy tracking state ---
    final Map<String, TrackedEnemy> trackedEnemies = new HashMap<String, TrackedEnemy>();
    final Map<Integer, double[]> trackedResourceItems = new HashMap<Integer, double[]>(); // entityId -> [posX, posY, posZ, isDiamond(1/0), stackSize]
    long lastArmorHeldItemScan = 0;

    // --- Cross-phase state ---
    boolean autoplayEnabled = false;
    String autoplayMode = "ones";
    long autoplayCheckTime = 0;
    boolean autoplayPendingCheck = false;
    boolean autoplaySpamBlocked = false;
    long autoplaySpamBlockedTime = 0;
    long lastRequeueTime = 0;
    final Set<String> partyMemberNames = new HashSet<String>();
    boolean partyListPending = false;
    long partyListRequestTime = 0;

    void reset() {
        // Phase tracking
        gamePhase = GamePhase.IDLE;
        disconnectedFromGame = false;
        disconnectTime = 0;
        matchStartTime = 0;
        clientTickCounter = 0;

        // Pre-game state
        joinBurstTickStamp = -1;
        joinMessageBurstCount = 0;
        joinBurstContainsMainUser = false;
        joinBurstNames.clear();
        lastPartyWarningTickStamp = -1;
        lastPartyAutoplaySwapTickStamp = -1;
        synchronized (chatDetectedPlayers) {
            chatDetectedPlayers.clear();
        }
        chatDetectedStartTime = 0;
        lobbyBaitActive = false;
        lobbyBaitFirstSentTime = 0;
        lobbyBaitRetrySent = false;
        lobbyBaitMessageIndex = -1;

        // In-game state
        recentJoins.clear();
        playerBedBlocks.clear();
        fallbackBedPosition = null;
        usingBedFallback = false;
        bedDetectionPending = false;
        bedDetectionStartTime = 0;
        lastBedDetectionAttempt = 0;
        lastBedWarningTime.clear();
        inferredTeammateUuids.clear();
        invisiblePlayerWarnings.clear();
        trackedGenerators.clear();
        lastGeneratorScan = 0;
        lastRushPredictorCheck = 0;
        rushRiskWarningSent = false;
        lastPredictedRushEtaSeconds = -1;
        lastDetectedMapName = "Unknown";

        // Enemy tracking state
        trackedEnemies.clear();
        trackedResourceItems.clear();
        lastArmorHeldItemScan = 0;

        // Cross-phase state
        autoplayEnabled = false;
        autoplayMode = "ones";
        autoplayCheckTime = 0;
        autoplayPendingCheck = false;
        autoplaySpamBlocked = false;
        autoplaySpamBlockedTime = 0;
        lastRequeueTime = 0;
        partyMemberNames.clear();
        partyListPending = false;
        partyListRequestTime = 0;
    }
}
