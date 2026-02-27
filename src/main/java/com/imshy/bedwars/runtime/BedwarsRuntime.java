package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AutoBlacklistManager;
import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.PlayerDatabase;
import com.imshy.bedwars.render.BedwarsOverlayRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BedwarsRuntime {
    private static final Pattern LOBBY_JOIN_MESSAGE_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{1,16} has joined \\(\\d+/\\d+\\)[.!]?$");
    private static final Pattern CHAT_MESSAGE_PATTERN =
            Pattern.compile("^(?:\\[[A-Z+]+\\] )?([A-Za-z0-9_]{1,16}): .+$");
    private static final int PARTY_JOIN_WARNING_THRESHOLD = 4;

    private final RuntimeState state;
    private final TeamDangerAnalyzer teamDangerAnalyzer;
    private final MatchThreatService matchThreatService;
    private final LobbyTrackerService lobbyTrackerService;
    private final WorldScanService worldScanService;
    private final BedwarsOverlayRenderer overlayRenderer;

    public BedwarsRuntime() {
        this.state = new RuntimeState();
        this.teamDangerAnalyzer = new TeamDangerAnalyzer(state);
        this.matchThreatService = new MatchThreatService(state, teamDangerAnalyzer);
        this.lobbyTrackerService = new LobbyTrackerService(state, matchThreatService);
        this.worldScanService = new WorldScanService(state);
        this.overlayRenderer = new BedwarsOverlayRenderer();
    }

    public boolean isInBedwarsLobby() {
        return state.inBedwarsLobby;
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

    public boolean rerunLobbyStartup(Minecraft mc) {
        boolean wasTracking = state.inBedwarsLobby;
        lobbyTrackerService.activateBedwarsLobbyTracking(mc);
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

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.message == null) {
            return;
        }

        String message = event.message.getUnformattedText();
        Minecraft mc = Minecraft.getMinecraft();

        trackJoinMessageBurst(mc, message);
        handleChatMessageStatLookup(mc, message);

        if (message.contains("Protect your bed and destroy the enemy beds.")) {
            if (!state.inBedwarsLobby) {
                lobbyTrackerService.activateBedwarsLobbyTracking(mc);
            }
        }

        if (state.inBedwarsLobby && mc.thePlayer != null) {
            String playerName = mc.thePlayer.getName();

            if (message.contains("VICTORY!") ||
                    (message.contains("1st Killer") && message.contains(playerName)) ||
                    message.contains("You won!") ||
                    (message.contains("Winner") && message.contains(playerName))) {

                System.out.println("[BedwarsStats] WIN detected!");
                PlayerDatabase.getInstance().recordGameEnd(PlayerDatabase.GameOutcome.WIN);
                PlayerDatabase.getInstance().clearCurrentGame();
                state.inBedwarsLobby = false;
                matchThreatService.clearBedTrackingState();
                lobbyTrackerService.clearRecentJoins();
                return;
            }

            if (message.contains("GAME OVER!") ||
                    (message.contains("disconnected") && message.contains("BED WARS")) ||
                    (message.contains("You died!") && message.contains("FINAL KILL")) ||
                    message.contains("You have been eliminated!")) {

                System.out.println("[BedwarsStats] LOSS detected!");
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
                state.inBedwarsLobby = false;
                matchThreatService.clearBedTrackingState();
                lobbyTrackerService.clearRecentJoins();
                return;
            }
        }

        if (message.contains("You left.") || message.contains("Sending you to")) {
            if (state.inBedwarsLobby) {
                state.inBedwarsLobby = false;
                matchThreatService.clearBedTrackingState();
                PlayerDatabase.getInstance().recordGameEnd(PlayerDatabase.GameOutcome.UNKNOWN);
                PlayerDatabase.getInstance().clearCurrentGame();
                lobbyTrackerService.clearRecentJoins();
                System.out.println("[BedwarsStats] Left Bedwars game - unknown outcome.");
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote) {
            return;
        }

        if (!state.inBedwarsLobby) {
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
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        state.clientTickCounter++;

        Minecraft mc = Minecraft.getMinecraft();

        if (state.autoplayEnabled && state.autoplayPendingCheck && state.inBedwarsLobby) {
            if (System.currentTimeMillis() >= state.autoplayCheckTime) {
                state.autoplayPendingCheck = false;
                worldScanService.performAutoplayCheck(mc);
            }
        }

        if (!state.inBedwarsLobby) {
            worldScanService.clearTrackedGenerators();
            return;
        }

        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

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
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Specials.Post event) {
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
            return;
        }

        BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
        if (threat == BedwarsStats.ThreatLevel.UNKNOWN) {
            return;
        }

        String threatText = stats.getThreatColor() + "[" + threat.name() + "] " +
                EnumChatFormatting.WHITE + stats.getStars() + "⭐ " +
                EnumChatFormatting.YELLOW + String.format("%.1f", stats.getFkdr()) + " FKDR";

        overlayRenderer.renderThreatLabel(player, threatText, event.x, event.y, event.z);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!state.inBedwarsLobby || !ModConfig.isGeneratorDisplayEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        worldScanService.renderTrackedGenerators(overlayRenderer, event.partialTicks);

        if (ModConfig.isInvisiblePlayerAlertsEnabled()) {
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player.isInvisible() && !player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                    overlayRenderer.renderInvisiblePlayerIndicator(player, event.partialTicks);
                }
            }
        }
    }

    private void trackJoinMessageBurst(Minecraft mc, String message) {
        if (mc == null || mc.thePlayer == null || message == null) {
            return;
        }

        if (!LOBBY_JOIN_MESSAGE_PATTERN.matcher(message).matches()) {
            return;
        }

        if (state.joinMessageBurstTick != state.clientTickCounter) {
            state.joinMessageBurstTick = state.clientTickCounter;
            state.joinMessageBurstCount = 0;
            state.joinBurstContainsMainUser = false;
        }

        String joinerName = message.split(" has joined")[0];
        if (joinerName.equals(mc.thePlayer.getName())) {
            state.joinBurstContainsMainUser = true;
        }

        state.joinMessageBurstCount++;

        if (state.joinMessageBurstCount >= PARTY_JOIN_WARNING_THRESHOLD &&
                !state.joinBurstContainsMainUser &&
                state.lastPartyWarningTick != state.clientTickCounter) {
            state.lastPartyWarningTick = state.clientTickCounter;
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
                state.lastPartyAutoplaySwapTick != state.clientTickCounter) {
            state.lastPartyAutoplaySwapTick = state.clientTickCounter;
            String burstMessage = EnumChatFormatting.RED + "Party burst detected: " +
                    EnumChatFormatting.YELLOW + state.joinMessageBurstCount +
                    EnumChatFormatting.GRAY + " joins in the same tick.";
            if (ModConfig.isAutoplayRequeueEnabled()) {
                worldScanService.requeueAutoplay(mc, burstMessage);
            } else {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[Autoplay] " + burstMessage));
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[Autoplay] " +
                                EnumChatFormatting.GRAY + "Requeue is disabled. Staying in lobby."));
                state.autoplayEnabled = false;
            }
        }
    }

    private void handleChatMessageStatLookup(Minecraft mc, String message) {
        if (mc == null || mc.thePlayer == null || message == null || !state.inBedwarsLobby) {
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

        if (HypixelAPI.getCachedStats(chatterName) != null) {
            return;
        }

        if (!HypixelAPI.hasApiKey()) {
            return;
        }

        HypixelAPI.fetchStatsAsync(chatterName, new HypixelAPI.StatsCallback() {
            @Override
            public void onStatsLoaded(BedwarsStats stats) {
                BedwarsStats.ThreatLevel threat = stats.getThreatLevel();

                if (threat == BedwarsStats.ThreatLevel.MEDIUM ||
                        threat == BedwarsStats.ThreatLevel.HIGH ||
                        threat == BedwarsStats.ThreatLevel.EXTREME) {

                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc.thePlayer != null) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(
                                EnumChatFormatting.GREEN + "[BW] " +
                                        stats.getThreatColor() + chatterName + " " +
                                        stats.getDisplayString()));
                    }
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
                        Minecraft mcInner = Minecraft.getMinecraft();
                        String threatMessage = EnumChatFormatting.RED + "Chat threat detected: " +
                                EnumChatFormatting.YELLOW + chatterName +
                                " (" + threat.name() + ")";
                        if (ModConfig.isAutoplayRequeueEnabled()) {
                            worldScanService.requeueAutoplay(mcInner, threatMessage);
                        } else if (mcInner.thePlayer != null) {
                            mcInner.thePlayer.addChatMessage(new ChatComponentText(
                                    EnumChatFormatting.GOLD + "[Autoplay] " + threatMessage));
                            mcInner.thePlayer.addChatMessage(new ChatComponentText(
                                    EnumChatFormatting.GOLD + "[Autoplay] " +
                                            EnumChatFormatting.GRAY + "Requeue is disabled. Staying in lobby."));
                            state.autoplayEnabled = false;
                        }
                    }
                }
            }

            @Override
            public void onError(String error) {
                System.out.println("[BedwarsStats] Chat lookup error for " + chatterName + ": " + error);
            }
        });
    }
}
