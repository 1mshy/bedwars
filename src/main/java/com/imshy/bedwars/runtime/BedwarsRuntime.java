package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.AutoBlacklistManager;
import com.imshy.bedwars.BedwarsMod;
import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.HypixelMessages;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.PlayerDatabase;
import com.imshy.bedwars.render.BedwarsHudRenderer;
import com.imshy.bedwars.render.BedwarsOverlayRenderer;
import com.imshy.bedwars.render.LastSeenArrowRenderer;
import com.imshy.bedwars.render.MatchSummaryRenderer;
import com.imshy.bedwars.render.PreGameBriefingRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BedwarsRuntime {
    private static final Pattern LOBBY_JOIN_MESSAGE_PATTERN = Pattern
            .compile("^[A-Za-z0-9_]{1,16} has joined \\((\\d+)/\\d+\\)[.!]?$");
    private static final Pattern CHAT_MESSAGE_PATTERN = Pattern
            .compile("^(?:\\[[^\\]]+\\] )*([A-Za-z0-9_]{1,16}): .+$");
    private static final Pattern RECONNECT_MESSAGE_PATTERN = Pattern.compile("^([A-Za-z0-9_]{1,16}) reconnected\\.$");
    private static final int PARTY_JOIN_WARNING_THRESHOLD = 4;
    private static final Pattern PARTY_LINE_PATTERN = Pattern.compile("^Party (?:Leader|Members|Moderators): (.+)$");
    private static final Pattern PARTY_MEMBER_NAME_PATTERN = Pattern
            .compile("(?:\\[[A-Za-z0-9+]+\\] )?([A-Za-z0-9_]{1,16})");
    private static final Pattern DEATH_MESSAGE_PATTERN = Pattern
            .compile("^([A-Za-z0-9_]{1,16}) (?:was |died|fell |disconnected|burned |walked |suffocated|drowned)");
    private static final long PARTY_LIST_TIMEOUT_MS = 3000;
    private static final long LOBBY_BAIT_RETRY_DELAY_MS = 4000;
    private static final String[] LOBBY_BAIT_MESSAGES = {
            "Is anyone good in this lobby?",
            "Any sweats in here?",
            "This lobby looks easy ngl",
            "Anyone wanna party up?",
            "I need a challenge lol",
            "Any tryhards here?",
            "Who's the best player here?",
            "This lobby is gonna be quick"
    };
    private static final Logger LOGGER = LogManager.getLogger("BedwarsStats");
    private static final Random baitRandom = new Random();
    private static final long RENDER_FETCH_COOLDOWN_MS = 60_000L;
    private final java.util.Map<String, Long> renderFetchRequests = new java.util.concurrent.ConcurrentHashMap<String, Long>();

    private final RuntimeState state;
    private final TeamDangerAnalyzer teamDangerAnalyzer;
    private final MatchThreatService matchThreatService;
    private final LobbyTrackerService lobbyTrackerService;
    private final WorldScanService worldScanService;
    private final EnemyTrackingService enemyTrackingService;
    private final FireballTrackingService fireballTrackingService;
    private final ProjectileTrackingService projectileTrackingService;
    private final EnderPearlPredictionService enderPearlPredictionService;
    private final FinalKillLedger finalKillLedger;
    private final AntiCheatService antiCheatService;
    private final BedwarsOverlayRenderer overlayRenderer;
    private final BedwarsHudRenderer hudRenderer;
    private final LastSeenArrowRenderer lastSeenArrowRenderer;
    private final MatchSummaryRenderer matchSummaryRenderer;
    private final PreGameBriefingRenderer preGameBriefingRenderer;

    public BedwarsRuntime() {
        this.state = new RuntimeState();
        this.teamDangerAnalyzer = new TeamDangerAnalyzer(state);
        this.matchThreatService = new MatchThreatService(state, teamDangerAnalyzer);
        this.lobbyTrackerService = new LobbyTrackerService(state, matchThreatService);
        this.worldScanService = new WorldScanService(state, matchThreatService);
        this.enemyTrackingService = new EnemyTrackingService(state, matchThreatService);
        this.fireballTrackingService = new FireballTrackingService();
        this.projectileTrackingService = new ProjectileTrackingService();
        this.enderPearlPredictionService = new EnderPearlPredictionService();
        this.finalKillLedger = new FinalKillLedger();
        this.antiCheatService = new AntiCheatService();
        this.overlayRenderer = new BedwarsOverlayRenderer();
        this.hudRenderer = new BedwarsHudRenderer();
        this.lastSeenArrowRenderer = new LastSeenArrowRenderer();
        this.matchSummaryRenderer = new MatchSummaryRenderer();
        this.preGameBriefingRenderer = new PreGameBriefingRenderer();
    }

    public boolean isInMatch() {
        return state.gamePhase == GamePhase.IN_GAME;
    }

    public GamePhase getGamePhase() {
        return state.gamePhase;
    }

    public boolean isDisconnectedFromGame() {
        return state.disconnectedFromGame;
    }

    public int getLastPredictedRushEtaSeconds() {
        return state.lastPredictedRushEtaSeconds;
    }

    public String getLastDetectedMapName() {
        return state.lastDetectedMapName;
    }

    public void clearRecentJoins() {
        lobbyTrackerService.clearRecentJoins();
    }

    public void resetToBootState() {
        state.reset();
        matchThreatService.clearBedTrackingState();
        enemyTrackingService.clearAll();
        fireballTrackingService.clearAll();
        projectileTrackingService.clearAll();
        finalKillLedger.clear();
        PlayerDatabase.getInstance().clearCurrentGame();
        AudioCueManager.clearCooldowns();
    }

    public boolean rerunMatchStartup(Minecraft mc) {
        boolean wasTracking = state.gamePhase == GamePhase.IN_GAME;
        lobbyTrackerService.activateMatchTracking(mc);
        return wasTracking;
    }

    public List<String> buildTeamDangerLines(Minecraft mc) {
        return teamDangerAnalyzer.buildTeamDangerLines(mc);
    }

    public void startAutoplay(String mode) {
        state.autoplayEnabled = true;
        state.autoplayMode = mode;
        state.autoplayPendingCheck = false;
    }

    public void stopAutoplay() {
        state.autoplayEnabled = false;
        state.autoplayPendingCheck = false;
    }

    public boolean isAutoplayEnabled() {
        return state.autoplayEnabled;
    }

    public boolean isValidAutoplayMode(String mode) {
        return worldScanService.isValidAutoplayMode(mode);
    }

    public String[] getAutoplayModeSuggestions() {
        return worldScanService.getAutoplayModeSuggestions();
    }

    public String getPlayCommand(String mode) {
        return worldScanService.getPlayCommand(mode);
    }

    public void requestPartyList(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        state.partyMemberNames.clear();
        state.partyListPending = true;
        state.partyListRequestTime = System.currentTimeMillis();
        mc.thePlayer.sendChatMessage("/p list");
    }

    private void parsePartyListResponse(Minecraft mc, String message) {
        if (!state.partyListPending) {
            return;
        }

        if (System.currentTimeMillis() - state.partyListRequestTime > PARTY_LIST_TIMEOUT_MS) {
            state.partyListPending = false;
            return;
        }

        if (message.contains(HypixelMessages.NOT_IN_PARTY)) {
            state.partyListPending = false;
            return;
        }

        Matcher lineMatcher = PARTY_LINE_PATTERN.matcher(message);
        if (!lineMatcher.matches()) {
            return;
        }

        String membersPart = lineMatcher.group(1);
        Matcher nameMatcher = PARTY_MEMBER_NAME_PATTERN.matcher(membersPart);
        String selfName = mc.thePlayer != null ? mc.thePlayer.getName() : "";

        while (nameMatcher.find()) {
            String name = nameMatcher.group(1);
            if (name != null && !name.equals(selfName)) {
                state.partyMemberNames.add(name);
            }
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        // Fired on MinecraftForge.EVENT_BUS by GuiConfig when the user saves the in-game
        // settings screen. Re-read the (already in-memory, GUI-edited) Configuration into
        // ModConfig's fields and persist — without this, GUI edits never take effect.
        if (BedwarsMod.MODID.equals(event.modID)) {
            ModConfig.syncFromConfig();
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.message == null) {
            return;
        }

        String message = event.message.getUnformattedText().replaceAll("\u00a7.", "");
        Minecraft mc = Minecraft.getMinecraft();

        parsePartyListResponse(mc, message);
        trackJoinMessageBurst(mc, message);
        checkLobbyBaitResponse(mc, message);
        handleChatMessageStatLookup(mc, message);
        handleReconnectMessage(mc, message);

        if (message.contains(HypixelMessages.GAME_START)) {
            if (state.gamePhase != GamePhase.IN_GAME) {
                finalKillLedger.clear();
                lobbyTrackerService.activateMatchTracking(mc);
            }
        }

        if (state.gamePhase == GamePhase.IN_GAME && mc.thePlayer != null) {
            String playerName = mc.thePlayer.getName();
            String trimmedMessage = message.trim();

            // WIN detection: VICTORY! is a distinctive ASCII-art header so a plain
            // contains() is safe. The other markers can appear inside player chat
            // so they require trimmed-equality or startsWith plus a name check.
            if (message.contains(HypixelMessages.WIN_VICTORY) ||
                    trimmedMessage.equals(HypixelMessages.WIN_YOU_WON) ||
                    (trimmedMessage.startsWith(HypixelMessages.WIN_1ST_KILLER) && trimmedMessage.contains(playerName)) ||
                    (trimmedMessage.startsWith("Winners: ") && trimmedMessage.contains(playerName))) {

                LOGGER.info("WIN detected");
                captureMatchSummary(MatchSummary.Outcome.WIN);
                PlayerDatabase.getInstance().recordGameEnd(PlayerDatabase.GameOutcome.WIN);
                PlayerDatabase.getInstance().clearCurrentGame();
                state.gamePhase = GamePhase.IDLE;
                state.disconnectedFromGame = false;
                synchronized (state.chatDetectedPlayers) {
                    state.chatDetectedPlayers.clear();
                }
                state.chatDetectedStartTime = 0;
                state.lobbyBaitActive = false;
                matchThreatService.clearBedTrackingState();
                lobbyTrackerService.clearRecentJoins();
                enemyTrackingService.clearAll();
                fireballTrackingService.clearAll();
                projectileTrackingService.clearAll();
                finalKillLedger.clear();
                return;
            }

            if (trimmedMessage.equals(HypixelMessages.LOSS_GAME_OVER) ||
                    trimmedMessage.equals(HypixelMessages.LOSS_ELIMINATED)) {

                LOGGER.info("LOSS detected");
                captureMatchSummary(MatchSummary.Outcome.LOSS);
                PlayerDatabase db = PlayerDatabase.getInstance();
                db.recordGameEnd(PlayerDatabase.GameOutcome.LOSS);

                List<String> autoBlacklisted = AutoBlacklistManager.processLossOutcome(db);
                if (!autoBlacklisted.isEmpty() && mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                            EnumChatFormatting.GOLD + "[AutoBlacklist] " +
                                    EnumChatFormatting.RED + "Added: " +
                                    EnumChatFormatting.YELLOW + String.join(", ", autoBlacklisted)));
                }

                db.clearCurrentGame();
                state.gamePhase = GamePhase.IDLE;
                state.disconnectedFromGame = false;
                synchronized (state.chatDetectedPlayers) {
                    state.chatDetectedPlayers.clear();
                }
                state.chatDetectedStartTime = 0;
                state.lobbyBaitActive = false;
                matchThreatService.clearBedTrackingState();
                lobbyTrackerService.clearRecentJoins();
                enemyTrackingService.clearAll();
                fireballTrackingService.clearAll();
                projectileTrackingService.clearAll();
                finalKillLedger.clear();
                return;
            }

            // Detect player death messages for enemy tracking
            if (ModConfig.isEnemyTrackingEnabled()) {
                Matcher deathMatcher = DEATH_MESSAGE_PATTERN.matcher(message);
                if (deathMatcher.find()) {
                    String deadPlayer = deathMatcher.group(1);
                    enemyTrackingService.handleDeathMessage(deadPlayer);
                }
            }

            if (message.contains(HypixelMessages.FINAL_KILL_SUFFIX)) {
                handleFinalKill(mc, message);
            }
        }

        if (state.autoplayEnabled && message.contains(HypixelMessages.AUTOPLAY_RATE_LIMIT)) {
            state.autoplaySpamBlocked = true;
            state.autoplaySpamBlockedTime = System.currentTimeMillis();
        }

        // Hypixel sends these as bare system messages (no rank/name prefix), so the
        // trimmed form is either exactly PLAYER_LEFT or starts with PLAYER_SENDING
        // followed by a server name. Substring matches against the raw message were
        // ending the game whenever another player typed "You left." or
        // "Sending you to..." in chat.
        String trimmedLeave = message.trim();
        if (trimmedLeave.equals(HypixelMessages.PLAYER_LEFT)
                || trimmedLeave.startsWith(HypixelMessages.PLAYER_SENDING)) {
            synchronized (state.chatDetectedPlayers) {
                state.chatDetectedPlayers.clear();
            }
            state.chatDetectedStartTime = 0;
            state.lobbyBaitActive = false;
            if (state.gamePhase == GamePhase.IN_GAME) {
                state.disconnectedFromGame = false;
                captureMatchSummary(MatchSummary.Outcome.UNKNOWN);
                matchThreatService.clearBedTrackingState();
                PlayerDatabase.getInstance().recordGameEnd(PlayerDatabase.GameOutcome.UNKNOWN);
                PlayerDatabase.getInstance().clearCurrentGame();
                lobbyTrackerService.clearRecentJoins();
                enemyTrackingService.clearAll();
                fireballTrackingService.clearAll();
                projectileTrackingService.clearAll();
                finalKillLedger.clear();
                LOGGER.info("Left Bedwars game - unknown outcome");
            }
            state.gamePhase = GamePhase.IDLE;
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote) {
            return;
        }

        if (state.gamePhase != GamePhase.IN_GAME) {
            return;
        }

        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.entity;
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.thePlayer != null && player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
            // Forge fires EntityJoinWorldEvent for the local player on respawn and
            // certain re-syncs too, not only on a real server change. If the joined
            // world is the same World instance we were already tracking, treat it
            // as a respawn / re-sync and don't pause tracking.
            net.minecraft.world.World previous =
                    state.lastTrackedWorld != null ? state.lastTrackedWorld.get() : null;
            if (previous != null && previous == event.world) {
                return;
            }
            if (previous == null) {
                // No baseline yet — record this world as the tracked one and skip.
                state.lastTrackedWorld = new java.lang.ref.WeakReference<net.minecraft.world.World>(event.world);
                return;
            }
            // Different World instance → genuine server transition (disconnect).
            if (!state.disconnectedFromGame) {
                state.disconnectedFromGame = true;
                state.disconnectTime = System.currentTimeMillis();
                LOGGER.info("Disconnected from game — tracking paused");
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[BW] " +
                                EnumChatFormatting.RED + "\u26A0 Disconnected from game \u2014 tracking paused."));
            }
            return;
        }

        // Skip stat lookups for other players while disconnected
        if (state.disconnectedFromGame) {
            return;
        }

        lobbyTrackerService.trackPlayerJoin(mc, player);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        lobbyTrackerService.trimRecentJoins();

        if (ModConfig.isHudEnabled()) {
            // Auto-expire detected players after the configured display duration.
            if (state.chatDetectedStartTime > 0
                    && System.currentTimeMillis() - state.chatDetectedStartTime
                            > ModConfig.getDisplayDuration() * 1000L) {
                synchronized (state.chatDetectedPlayers) {
                    state.chatDetectedPlayers.clear();
                }
                state.chatDetectedStartTime = 0;
            }

            boolean hasDetectedPlayers;
            synchronized (state.chatDetectedPlayers) {
                hasDetectedPlayers = !state.chatDetectedPlayers.isEmpty();
            }
            if (state.gamePhase == GamePhase.IN_GAME || hasDetectedPlayers) {
                ScaledResolution resolution = new ScaledResolution(mc);
                hudRenderer.render(resolution, mc, teamDangerAnalyzer, worldScanService, enemyTrackingService,
                        finalKillLedger, state.matchStartTime, state.chatDetectedPlayers);

                if (state.gamePhase == GamePhase.IN_GAME) {
                    lastSeenArrowRenderer.render(resolution, mc, enemyTrackingService, event.partialTicks);
                }
            }

            if (state.lastPreGameBriefing != null && ModConfig.isPreGameBriefingEnabled()) {
                long briefingTtl = ModConfig.getPreGameBriefingDurationSeconds() * 1000L;
                if (state.lastPreGameBriefing.isExpired(briefingTtl)) {
                    state.lastPreGameBriefing = null;
                } else {
                    ScaledResolution resolution = new ScaledResolution(mc);
                    preGameBriefingRenderer.render(resolution, mc, state.lastPreGameBriefing);
                }
            }

            if (state.lastMatchSummary != null && ModConfig.isMatchSummaryCardEnabled()) {
                long ttlMs = ModConfig.getMatchSummaryCardDurationSeconds() * 1000L;
                if (state.lastMatchSummary.isExpired(ttlMs)) {
                    state.lastMatchSummary = null;
                } else {
                    ScaledResolution resolution = new ScaledResolution(mc);
                    matchSummaryRenderer.render(resolution, mc, state.lastMatchSummary);
                }
            }
        }
    }

    /**
     * Snapshot the match into a {@link MatchSummary} so the post-game card has
     * stable data even after the player db's current-game set is cleared.
     */
    private void captureMatchSummary(MatchSummary.Outcome outcome) {
        if (!ModConfig.isMatchSummaryCardEnabled()) {
            return;
        }
        try {
            java.util.Set<String> facedPlayers = PlayerDatabase.getInstance().getCurrentGamePlayersSnapshot();
            state.lastMatchSummary = MatchSummary.build(outcome, state.matchStartTime,
                    state.lastDetectedMapName, facedPlayers);
        } catch (Exception e) {
            LOGGER.warn("Failed to build match summary: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        state.clientTickCounter++;

        Minecraft mc = Minecraft.getMinecraft();

        antiCheatService.onClientTick(mc);

        // AFK anti-kick: strafe left then right every 60 seconds
        if (state.afkEnabled && mc.thePlayer != null) {
            tickAfkMovement(mc);
        }

        if (state.autoplayEnabled && state.autoplayPendingCheck && state.gamePhase == GamePhase.IN_GAME) {
            if (System.currentTimeMillis() >= state.autoplayCheckTime) {
                state.autoplayPendingCheck = false;
                worldScanService.performAutoplayCheck(mc);
            }
        }

        // Autoplay spam retry: re-send /play command after Hypixel rate limit expires
        if (state.autoplayEnabled && state.autoplaySpamBlocked && mc.thePlayer != null) {
            if (System.currentTimeMillis() - state.autoplaySpamBlockedTime >= RuntimeState.SPAM_RETRY_DELAY) {
                state.autoplaySpamBlocked = false;
                String playCommand = worldScanService.getPlayCommand(state.autoplayMode);
                if (playCommand != null) {
                    mc.thePlayer.sendChatMessage(playCommand);
                }
            }
        }

        // Lobby bait retry (PRE_GAME only)
        if (state.gamePhase == GamePhase.PRE_GAME && mc.theWorld != null && mc.thePlayer != null) {
            long currentTime = System.currentTimeMillis();
            if (state.lobbyBaitActive && !state.lobbyBaitRetrySent
                    && state.lobbyBaitFirstSentTime > 0
                    && currentTime - state.lobbyBaitFirstSentTime >= LOBBY_BAIT_RETRY_DELAY_MS) {
                int newIndex;
                do {
                    newIndex = baitRandom.nextInt(LOBBY_BAIT_MESSAGES.length);
                } while (newIndex == state.lobbyBaitMessageIndex && LOBBY_BAIT_MESSAGES.length > 1);
                mc.thePlayer.sendChatMessage(LOBBY_BAIT_MESSAGES[newIndex]);
                state.lobbyBaitRetrySent = true;
            }
            if (state.lobbyBaitActive && state.lobbyBaitRetrySent
                    && currentTime - state.lobbyBaitFirstSentTime >= LOBBY_BAIT_RETRY_DELAY_MS * 2) {
                state.lobbyBaitActive = false;
            }
        }

        // Scoreboard-based game-start detection (covers alt modes whose intro
        // chat doesn't match HypixelMessages.GAME_START). Only kicks in when
        // we're already in PRE_GAME — never escalates from IDLE, since the
        // mode-select lobby also has "BED WARS" in its sidebar.
        if (state.gamePhase == GamePhase.PRE_GAME
                && state.clientTickCounter - state.lastScoreboardPhaseScanTick >= 20) {
            state.lastScoreboardPhaseScanTick = state.clientTickCounter;
            if (ScoreboardGameStateDetector.isMatchInProgress(mc)) {
                LOGGER.info("Bedwars match start detected via scoreboard (chat trigger missed)");
                finalKillLedger.clear();
                lobbyTrackerService.activateMatchTracking(mc);
            }
        }

        // Scoreboard-based in-game polling: keep the team-status snapshot fresh
        // for downstream services (HUD, TeamDangerAnalyzer, etc.). Also acts as
        // the passive recovery signal for disconnectedFromGame — a non-empty
        // snapshot is positive evidence that tracking should resume.
        //
        // We do NOT end the match from this signal anymore: chat triggers
        // (LOSS_GAME_OVER, LOSS_ELIMINATED, WIN_*, PLAYER_LEFT) cover every
        // realistic end condition, and the old miss-count heuristic was the
        // dominant source of spurious "Game Over" popups when Hypixel briefly
        // mutated the sidebar (event banners, "Bed Self-Destruct in...", etc.).
        if (state.gamePhase == GamePhase.IN_GAME
                && state.clientTickCounter - state.lastScoreboardPhaseScanTick >= 10) {
            state.lastScoreboardPhaseScanTick = state.clientTickCounter;

            java.util.List<ScoreboardGameStateDetector.TeamStatus> statuses =
                    ScoreboardGameStateDetector.parseTeamStatuses(mc);

            // Refresh the team-status map
            state.scoreboardTeamStatuses.clear();
            for (ScoreboardGameStateDetector.TeamStatus ts : statuses) {
                state.scoreboardTeamStatuses.put(ts.teamName, ts);
            }

            // Track the active world so onEntityJoinWorld can distinguish a real
            // server change from a respawn / re-sync within the same world.
            if (mc.theWorld != null) {
                net.minecraft.world.World previous =
                        state.lastTrackedWorld != null ? state.lastTrackedWorld.get() : null;
                if (previous != mc.theWorld) {
                    state.lastTrackedWorld = new java.lang.ref.WeakReference<net.minecraft.world.World>(mc.theWorld);
                }
            }

            // Passive recovery: if we are flagged disconnected but the sidebar
            // shows active team rows, we are clearly back in a working match —
            // clear the flag without waiting for the reconnect chat pattern.
            if (state.disconnectedFromGame && !statuses.isEmpty() && mc.thePlayer != null) {
                state.disconnectedFromGame = false;
                state.disconnectTime = 0;
                LOGGER.info("Auto-resumed tracking — scoreboard shows active match");
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[BW] " +
                                EnumChatFormatting.GREEN + "✓ Tracking resumed."));
            }
        }

        if (state.gamePhase != GamePhase.IN_GAME) {
            worldScanService.clearTrackedGenerators();
            return;
        }

        // Skip all game-awareness processing while disconnected
        if (state.disconnectedFromGame) {
            return;
        }

        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (state.tabListScanPending && currentTime >= state.tabListScanScheduledTime) {
            state.tabListScanPending = false;
            lobbyTrackerService.scanTabListPlayers(mc);
        }

        if (state.preGameBriefingPending && currentTime >= state.preGameBriefingScheduledTime) {
            state.preGameBriefingPending = false;
            PreGameBriefing briefing = PreGameBriefing.build(mc, teamDangerAnalyzer,
                    state.lastDetectedMapName);
            if (briefing != null) {
                state.lastPreGameBriefing = briefing;
            }
        }

        matchThreatService.captureEarlySpawnTeammates(mc, currentTime);

        if (state.fallbackBedPosition == null) {
            matchThreatService.startBedTracking(mc, currentTime);
        }

        matchThreatService.maybeRetryBedDetection(mc, currentTime);

        if (ModConfig.isRushPredictorEnabled()) {
            matchThreatService.checkRushRiskPredictor(mc, currentTime);
        }

        if (ModConfig.isInvisiblePlayerAlertsEnabled()) {
            worldScanService.checkForInvisiblePlayers(mc, currentTime);
        }

        if (ModConfig.isGeneratorDisplayEnabled()) {
            if (currentTime - state.lastGeneratorScan >= RuntimeState.GENERATOR_SCAN_INTERVAL) {
                worldScanService.scanForGenerators(mc);
                state.lastGeneratorScan = currentTime;
            }
        }

        matchThreatService.checkBedProximityWarnings(mc, currentTime);

        // Enemy tracking: scan for item pickups and armor/held items
        if (ModConfig.isEnemyTrackingEnabled()) {
            enemyTrackingService.scanItemPickups(mc);
            enemyTrackingService.scanArmorAndHeldItems(mc, currentTime);
        }

        // Fireball detection: scan for incoming EntityLargeFireball projectiles
        if (ModConfig.isFireballDetectionEnabled()) {
            fireballTrackingService.scanFireballs(mc);
        } else if (!fireballTrackingService.getTracked().isEmpty()) {
            fireballTrackingService.clearAll();
        }

        // Ender pearl tracking (gravity-aware)
        if (ModConfig.isEnderPearlTrackingEnabled()) {
            projectileTrackingService.scanProjectiles(mc, matchThreatService, state.playerBedBlocks);
        } else if (!projectileTrackingService.getTracked().isEmpty()) {
            projectileTrackingService.clearAll();
        }
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Specials.Post event) {
        if (!ModConfig.isModEnabled()) {
            return;
        }

        if (!ModConfig.isNameTagsEnabled()) {
            return;
        }

        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.entity;
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.thePlayer != null && player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
            return;
        }

        BedwarsStats stats = HypixelAPI.getCachedStats(player.getName());
        if (stats == null || !stats.isLoaded()) {
            maybeRequestRenderFetch(player.getName());
            return;
        }

        BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
        if (threat == BedwarsStats.ThreatLevel.UNKNOWN) {
            return;
        }

        String threatText;
        if (threat == BedwarsStats.ThreatLevel.NICKED) {
            if (state.gamePhase == GamePhase.IDLE || !ModConfig.isNickDetectionEnabled()) {
                return;
            }
            threatText = stats.getThreatColor() + "[NICK]";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(stats.getThreatColor()).append("[").append(threat.name()).append("] ")
              .append(EnumChatFormatting.WHITE).append(stats.getStars()).append("⭐ ")
              .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatioShort(stats.getFkdr()))
              .append(" FKDR");

            if (ModConfig.isRecentFkdrNametagEnabled()
                    && stats.getRecentWindow() != BedwarsStats.RecentWindow.NONE) {
                double delta = stats.getRecentFkdrDelta();
                String arrow;
                EnumChatFormatting arrowColor;
                if (delta >= 0.10) {
                    arrow = "\u2191"; arrowColor = EnumChatFormatting.RED;
                } else if (delta <= -0.10) {
                    arrow = "\u2193"; arrowColor = EnumChatFormatting.GREEN;
                } else {
                    arrow = "\u2192"; arrowColor = EnumChatFormatting.GRAY;
                }
                sb.append(EnumChatFormatting.GRAY).append("  ")
                  .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatioShort(stats.getRecentFkdr()))
                  .append(EnumChatFormatting.GRAY).append(" ").append(stats.getRecentWindowLabel()).append(" ")
                  .append(arrowColor).append(arrow);
            }

            threatText = sb.toString();
        }

        // Append real-time clicks-per-second next to the threat level (autoclicker indicator).
        if (ModConfig.isAntiCheatEnabled() && ModConfig.isAntiCheatCpsEnabled()) {
            int cps = antiCheatService.getCps(player.getUniqueID());
            if (cps > 0) {
                EnumChatFormatting cpsColor;
                if (cps >= 14) {
                    cpsColor = EnumChatFormatting.RED;
                } else if (cps >= 10) {
                    cpsColor = EnumChatFormatting.YELLOW;
                } else {
                    cpsColor = EnumChatFormatting.GRAY;
                }
                threatText = threatText + " " + cpsColor + cps + " CPS";
            }
        }

        overlayRenderer.renderThreatLabel(player, threatText, event.x, event.y, event.z);

        // Render enemy tracking label below the threat label
        if (state.gamePhase == GamePhase.IN_GAME && ModConfig.isEnemyTrackingEnabled()) {
            TrackedEnemy tracked = enemyTrackingService.getTrackedEnemy(player.getName());
            if (tracked != null && tracked.hasAnyData()) {
                overlayRenderer.renderEnemyTrackingLabel(player, tracked, event.x, event.y, event.z);
                if (ModConfig.isEnemyLoadoutNametagEnabled()) {
                    overlayRenderer.renderEnemyLoadoutLabel(player, tracked, event.x, event.y, event.z);
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ModConfig.isModEnabled()) {
            return;
        }

        if (state.gamePhase != GamePhase.IN_GAME) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (ModConfig.isGeneratorDisplayEnabled()) {
            worldScanService.renderTrackedGenerators(overlayRenderer, event.partialTicks);

            if (ModConfig.isInvisiblePlayerAlertsEnabled()) {
                for (EntityPlayer player : mc.theWorld.playerEntities) {
                    if (player.isInvisible() && !player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                        overlayRenderer.renderInvisiblePlayerIndicator(player, event.partialTicks);
                    }
                }
            }
        }

        if (ModConfig.isFireballDetectionEnabled() && ModConfig.isFireballOverlayEnabled()) {
            overlayRenderer.renderFireballTrajectories(fireballTrackingService.getTracked(), event.partialTicks);
        }

        if (ModConfig.isEnderPearlTrackingEnabled() && ModConfig.isEnderPearlOverlayEnabled()) {
            overlayRenderer.renderProjectileTrajectories(projectileTrackingService.getTracked(), event.partialTicks);
        }

        if (ModConfig.isEnderPearlPreviewEnabled() && isHoldingEnderPearl(mc.thePlayer)) {
            TrackedProjectile preview = enderPearlPredictionService.predict(mc);
            if (preview != null) {
                overlayRenderer.renderPreThrowArc(preview, event.partialTicks);
            }
        }

        if (ModConfig.isBedDefenseAssistEnabled()
                && !state.playerBedBlocks.isEmpty()
                && isEnemyNearOwnBed(mc)) {
            overlayRenderer.renderBedDefenseAssist(state.playerBedBlocks, event.partialTicks);
        }
    }

    private static boolean isHoldingEnderPearl(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        net.minecraft.item.ItemStack held = player.getHeldItem();
        return held != null && held.getItem() == net.minecraft.init.Items.ender_pearl;
    }

    /**
     * Returns true when at least one non-teammate player is within the
     * configured bed-defense range of any of the local player's bed blocks.
     * Cheap check: scans the world's loaded players, no chunk lookups.
     */
    private boolean isEnemyNearOwnBed(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        if (state.playerBedBlocks.isEmpty()) {
            return false;
        }
        double range = ModConfig.getBedDefenseProximityRange();
        double rangeSq = range * range;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            if (player.isInvisible()) continue;
            if (matchThreatService.isTeammate(mc, mc.thePlayer, player)) continue;
            for (net.minecraft.util.BlockPos bedPos : state.playerBedBlocks) {
                double dx = player.posX - (bedPos.getX() + 0.5);
                double dy = player.posY - (bedPos.getY() + 0.5);
                double dz = player.posZ - (bedPos.getZ() + 0.5);
                if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                    return true;
                }
            }
        }
        return false;
    }

    private void trackJoinMessageBurst(Minecraft mc, String message) {
        if (mc == null || mc.thePlayer == null || message == null) {
            return;
        }

        Matcher lobbyJoinMatcher = LOBBY_JOIN_MESSAGE_PATTERN.matcher(message);
        if (!lobbyJoinMatcher.matches()) {
            return;
        }

        if (state.joinBurstTickStamp != state.clientTickCounter) {
            state.joinBurstTickStamp = state.clientTickCounter;
            state.joinMessageBurstCount = 0;
            state.joinBurstContainsMainUser = false;
            state.joinBurstNames.clear();
        }

        String joinerName = message.split(" has joined")[0];
        state.joinBurstNames.add(joinerName);
        if (joinerName.equals(mc.thePlayer.getName())) {
            state.joinBurstContainsMainUser = true;
            // Only requeue if the lobby was already over max when WE joined
            if (state.autoplayEnabled) {
                int currentPlayers = Integer.parseInt(lobbyJoinMatcher.group(1));
                int maxPlayers = ModConfig.getLobbyMaxPlayerCount();
                if (currentPlayers > maxPlayers) {
                    String reason = EnumChatFormatting.RED + "Lobby player count (" +
                            EnumChatFormatting.YELLOW + "" + currentPlayers +
                            EnumChatFormatting.RED + ") exceeded max (" +
                            EnumChatFormatting.YELLOW + "" + maxPlayers +
                            EnumChatFormatting.RED + ")";
                    worldScanService.requeueAutoplay(mc, reason);
                    return;
                }
            }
            // Transition to PRE_GAME when local player joins a bedwars pre-lobby
            if (state.gamePhase == GamePhase.IDLE) {
                state.gamePhase = GamePhase.PRE_GAME;
            }
            // Arm lobby bait messages when the local player joins the pre-lobby
            if (ModConfig.isLobbyBaitMessagesEnabled() && !state.lobbyBaitActive) {
                state.lobbyBaitActive = true;
                state.lobbyBaitRetrySent = false;
                state.lobbyBaitMessageIndex = baitRandom.nextInt(LOBBY_BAIT_MESSAGES.length);
                mc.thePlayer.sendChatMessage(LOBBY_BAIT_MESSAGES[state.lobbyBaitMessageIndex]);
                state.lobbyBaitFirstSentTime = System.currentTimeMillis();
            }
        }

        if (state.joinBurstContainsMainUser) {
            for (String burstName : state.joinBurstNames) {
                if (!burstName.equals(mc.thePlayer.getName())) {
                    state.partyMemberNames.add(burstName);
                }
            }
        }

        state.joinMessageBurstCount++;

        if (state.joinMessageBurstCount >= PARTY_JOIN_WARNING_THRESHOLD &&
                !state.joinBurstContainsMainUser &&
                state.lastPartyWarningTickStamp != state.clientTickCounter) {
            state.lastPartyWarningTickStamp = state.clientTickCounter;
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[BW] " +
                            EnumChatFormatting.RED + "Party warning: " +
                            EnumChatFormatting.YELLOW + state.joinMessageBurstCount +
                            EnumChatFormatting.GRAY + " players joined in the same tick."));
        }

        if (state.joinMessageBurstCount >= PARTY_JOIN_WARNING_THRESHOLD &&
                !state.joinBurstContainsMainUser &&
                state.autoplayEnabled &&
                "fours".equals(state.autoplayMode) &&
                state.lastPartyAutoplaySwapTickStamp != state.clientTickCounter) {
            state.lastPartyAutoplaySwapTickStamp = state.clientTickCounter;
            String burstMessage = EnumChatFormatting.RED + "Party burst detected: " +
                    EnumChatFormatting.YELLOW + state.joinMessageBurstCount +
                    EnumChatFormatting.GRAY + " joins in the same tick.";
            if (ModConfig.isAutoplayRequeueEnabled() || state.gamePhase != GamePhase.IN_GAME) {
                worldScanService.requeueAutoplay(mc, burstMessage);
            } else {
                state.autoplayEnabled = false;
            }
        }
    }

    private void handleChatMessageStatLookup(Minecraft mc, String message) {
        if (mc == null || mc.thePlayer == null || message == null
                || state.disconnectedFromGame) {
            return;
        }

        Matcher matcher = CHAT_MESSAGE_PATTERN.matcher(message);
        if (!matcher.matches()) {
            return;
        }

        final String chatterName = matcher.group(1);

        if (chatterName.equals(mc.thePlayer.getName())) {
            return;
        }

        if (state.partyMemberNames.contains(chatterName)) {
            return;
        }

        BedwarsStats cachedStats = HypixelAPI.getCachedStats(chatterName);
        if (cachedStats != null) {
            addToChatDetectedIfEligible(chatterName, cachedStats);
            return;
        }

        if (!HypixelAPI.hasApiKey()) {
            return;
        }

        HypixelAPI.fetchStatsAsync(chatterName, new HypixelAPI.StatsCallback() {
            @Override
            public void onStatsLoaded(BedwarsStats stats) {
                // This callback may run on the HypixelAPI executor pool. Marshal all
                // world iteration, chat mutation, and shared-state writes onto the
                // client thread, where those operations are safe in 1.8.9.
                Minecraft.getMinecraft().addScheduledTask(
                        () -> applyChatStatLookup(chatterName, stats));
            }

            @Override
            public void onError(String error) {
                LOGGER.warn("Chat lookup error for {}: {}", chatterName, error);
            }
        });
    }

    /**
     * Applies a resolved chat-detected stat lookup: updates the HUD list, optionally
     * prints a fallback chat line, and drives autoplay threat handling. Always invoked
     * on the client thread (scheduled from the {@link HypixelAPI.StatsCallback}, which
     * may fire on the executor pool), so the world iteration, chat mutation, and shared
     * state writes here are all main-thread-safe.
     */
    private void applyChatStatLookup(String chatterName, BedwarsStats stats) {
        Minecraft mc = Minecraft.getMinecraft();
        // The scheduled task can run up to a tick after the lookup resolved; re-check
        // the preconditions that handleChatMessageStatLookup verified at entry.
        if (mc == null || mc.thePlayer == null || state.disconnectedFromGame) {
            return;
        }

        BedwarsStats.ThreatLevel threat = stats.getThreatLevel();

        // Add to persistent HUD list (deduplicate)
        synchronized (state.chatDetectedPlayers) {
            boolean alreadyTracked = false;
            for (ChatDetectedPlayer cdp : state.chatDetectedPlayers) {
                if (cdp.name.equals(chatterName)) {
                    alreadyTracked = true;
                    break;
                }
            }
            if (!alreadyTracked) {
                state.chatDetectedPlayers.add(
                        new ChatDetectedPlayer(chatterName, stats));
                if (state.chatDetectedStartTime == 0) {
                    state.chatDetectedStartTime = System.currentTimeMillis();
                }
            }
        }

        // Fallback to chat message when HUD is disabled
        if (!ModConfig.isHudEnabled() || !ModConfig.isHudChatDetectedEnabled()) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GREEN + "[BW] " +
                            stats.getThreatColor() + chatterName + " " +
                            stats.getDisplayString()));
        }

        if (state.autoplayEnabled) {
            String maxThreatLevel = ModConfig.getAutoplayMaxThreatLevel();
            boolean isThreat = false;

            if (maxThreatLevel.equals("HIGH")) {
                isThreat = (threat == BedwarsStats.ThreatLevel.HIGH ||
                        threat == BedwarsStats.ThreatLevel.EXTREME);
            } else if (maxThreatLevel.equals("EXTREME")) {
                isThreat = (threat == BedwarsStats.ThreatLevel.EXTREME);
            }

            if (isThreat) {
                boolean isTeammate = false;
                if (mc.theWorld != null) {
                    for (EntityPlayer p : mc.theWorld.playerEntities) {
                        if (p.getName().equals(chatterName)) {
                            isTeammate = matchThreatService.isTeammate(
                                    mc, mc.thePlayer, p);
                            break;
                        }
                    }
                }

                if (!isTeammate) {
                    String threatMessage = EnumChatFormatting.RED + "Chat threat detected: " +
                            EnumChatFormatting.YELLOW + chatterName +
                            " (" + threat.name() + ")";
                    if (ModConfig.isAutoplayRequeueEnabled() || state.gamePhase != GamePhase.IN_GAME) {
                        worldScanService.requeueAutoplay(mc, threatMessage);
                    } else {
                        state.autoplayEnabled = false;
                    }
                }
            }
        }
    }

    private void maybeRequestRenderFetch(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        if (!HypixelAPI.hasApiKey()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = renderFetchRequests.get(playerName);
        if (prev != null && now - prev < RENDER_FETCH_COOLDOWN_MS) {
            return;
        }
        renderFetchRequests.put(playerName, now);
        HypixelAPI.fetchStatsAsync(playerName, new HypixelAPI.StatsCallback() {
            @Override
            public void onStatsLoaded(BedwarsStats stats) {
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void addToChatDetectedIfEligible(String playerName, BedwarsStats stats) {
        synchronized (state.chatDetectedPlayers) {
            for (ChatDetectedPlayer cdp : state.chatDetectedPlayers) {
                if (cdp.name.equals(playerName)) {
                    return;
                }
            }
            state.chatDetectedPlayers.add(new ChatDetectedPlayer(playerName, stats));
            if (state.chatDetectedStartTime == 0) {
                state.chatDetectedStartTime = System.currentTimeMillis();
            }
        }
    }

    private void checkLobbyBaitResponse(Minecraft mc, String message) {
        if (!state.lobbyBaitActive || mc == null || mc.thePlayer == null) {
            return;
        }
        Matcher matcher = CHAT_MESSAGE_PATTERN.matcher(message);
        if (matcher.matches() && !matcher.group(1).equals(mc.thePlayer.getName())) {
            state.lobbyBaitActive = false;
        }
    }

    private void handleFinalKill(Minecraft mc, String message) {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        Matcher m = HypixelMessages.FINAL_KILL_PATTERN.matcher(message);
        if (!m.find()) {
            return;
        }
        String victim = m.group(1);
        if (victim == null || victim.equals(mc.thePlayer.getName())) {
            // Don't tally self-deaths — local player FINAL KILL already triggers match-end paths.
            return;
        }

        // Resolve the victim's team (color + name) from the world scoreboard, if possible.
        String teamName = null;
        String teamColor = EnumChatFormatting.GRAY.toString();
        if (mc.theWorld != null) {
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                if (!p.getName().equals(victim)) continue;
                net.minecraft.scoreboard.Team t = p.getTeam();
                if (t instanceof net.minecraft.scoreboard.ScorePlayerTeam) {
                    net.minecraft.scoreboard.ScorePlayerTeam spt = (net.minecraft.scoreboard.ScorePlayerTeam) t;
                    teamName = spt.getRegisteredName();
                    String prefix = spt.getColorPrefix();
                    if (prefix != null && !prefix.isEmpty()) {
                        for (int i = 0; i < prefix.length() - 1; i++) {
                            if (prefix.charAt(i) == '\u00a7') {
                                char code = prefix.charAt(i + 1);
                                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                                    teamColor = "\u00a7" + code;
                                    break;
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
        finalKillLedger.recordFinalKill(victim, teamName, teamColor);

        // Final-kill chat context disabled \u2014 HUD's FINAL KILL TALLY section conveys the same info.
    }

    private void handleReconnectMessage(Minecraft mc, String message) {
        if (state.gamePhase != GamePhase.IN_GAME || !state.disconnectedFromGame) {
            return;
        }

        if (mc == null || mc.thePlayer == null) {
            return;
        }

        String playerName = mc.thePlayer.getName();
        Matcher matcher = RECONNECT_MESSAGE_PATTERN.matcher(message);
        if (matcher.matches() && matcher.group(1).equals(playerName)) {
            state.disconnectedFromGame = false;
            state.disconnectTime = 0;
            LOGGER.info("Reconnected to game — tracking resumed");
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[BW] " +
                            EnumChatFormatting.GREEN + "\u2713 Reconnected \u2014 tracking resumed."));
        }
    }

    // ── AFK anti-kick ──────────────────────────────────────────────

    private static final int AFK_STRAFE_TICKS = 5;
    private static final long AFK_INTERVAL_MS = 60_000;

    private void tickAfkMovement(Minecraft mc) {
        if (state.afkMovePhase == 0) {
            // Waiting for next cycle
            if (System.currentTimeMillis() - state.afkLastMoveTime >= AFK_INTERVAL_MS) {
                state.afkMovePhase = 1;
                state.afkMoveTicks = 0;
            }
            return;
        }

        state.afkMoveTicks++;

        if (state.afkMovePhase == 1) {
            // Strafe left
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), true);
            if (state.afkMoveTicks >= AFK_STRAFE_TICKS) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                state.afkMovePhase = 2;
                state.afkMoveTicks = 0;
            }
        } else if (state.afkMovePhase == 2) {
            // Strafe right (back to original position)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), true);
            if (state.afkMoveTicks >= AFK_STRAFE_TICKS) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                state.afkMovePhase = 0;
                state.afkMoveTicks = 0;
                state.afkLastMoveTime = System.currentTimeMillis();
            }
        }
    }

    public boolean toggleAfk() {
        state.afkEnabled = !state.afkEnabled;
        if (state.afkEnabled) {
            state.afkLastMoveTime = System.currentTimeMillis();
            state.afkMovePhase = 0;
            state.afkMoveTicks = 0;
        } else {
            // Release any held keys
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.gameSettings != null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
            }
            state.afkMovePhase = 0;
            state.afkMoveTicks = 0;
        }
        return state.afkEnabled;
    }
}
