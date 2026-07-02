package com.imshy.bedwars;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Configuration handler for BedwarsStats mod
 * Persists settings like API key across restarts
 */
public class ModConfig {

    private static final Logger LOGGER = LogManager.getLogger("BedwarsStats");

    private static Configuration config;

    // Settings values
    private static boolean modEnabled = true;
    private static String apiKey = "";
    private static int displayDuration = 16; // seconds
    private static int lowStarThreshold = 100;
    private static int mediumStarThreshold = 300;
    private static int highStarThreshold = 500;
    private static double lowFkdrThreshold = 2.0;
    private static double mediumFkdrThreshold = 4.0;
    private static double highFkdrThreshold = 6.0;
    private static boolean chatAlertsEnabled = true;
    private static boolean blacklistAlertsEnabled = true;
    private static boolean historyAlertsEnabled = true;
    private static boolean bedProximityAlertsEnabled = true;
    private static boolean mapAwareBedDetectionEnabled = true;
    private static int bedScanRange = 30; // blocks
    private static int bedScanRetrySeconds = 12; // seconds
    private static boolean teamDangerSummaryEnabled = true;
    private static boolean nickDetectionEnabled = true;
    private static boolean audioAlertsEnabled = true;
    private static boolean invisibleAudioCueEnabled = true;
    private static boolean bedDangerAudioCueEnabled = true;
    private static double audioCueVolume = 0.8;
    private static int audioCueCooldownMs = 1500;
    private static boolean rushPredictorEnabled = true;
    private static int rushWarningThresholdSeconds = 20;
    private static int rushRecheckIntervalSeconds = 2;
    private static boolean autoBlacklistEnabled = true;
    private static int autoBlacklistLossThreshold = 3;
    private static int autoBlacklistLookbackDays = 14;
    private static int autoBlacklistCooldownDays = 7;
    private static int autoBlacklistExpiryDays = 30;
    private static String autoplayMaxThreatLevel = "HIGH"; // HIGH or EXTREME
    private static boolean autoplayRequeueEnabled = false;
    private static int lobbyMaxPlayerCount = 8;

    // Invisible player detection settings
    private static boolean invisiblePlayerAlertsEnabled = true;
    private static int invisibleDetectionRange = 20; // blocks
    private static int invisibleWarningCooldown = 5000; // ms

    // Generator display settings
    private static boolean generatorDisplayEnabled = true;
    private static int generatorScanRange = 64; // blocks
    private static int generatorLabelRenderDistance = 256; // blocks

    // Lobby bait messages
    private static boolean lobbyBaitMessagesEnabled = true;

    // Enemy tracking settings
    private static boolean enemyTrackingEnabled = true;
    private static boolean enemyTrackingHudEnabled = true;
    private static double enemyTrackingPickupRange = 3.0;

    // Fireball detection settings
    private static boolean fireballDetectionEnabled = true;
    private static boolean fireballOverlayEnabled = true;
    private static boolean fireballAudioCueEnabled = true;
    private static double fireballAlertRadius = 3.0; // blocks
    private static double fireballBedAlertRadius = 4.0; // blocks
    private static int fireballMaxTraceDistance = 200; // blocks

    // HUD overlay settings
    private static boolean hudEnabled = true;
    private static boolean hudHighestThreatEnabled = true;
    private static boolean hudGeneratorCountsEnabled = true;
    private static boolean hudTeamSummaryEnabled = true;
    private static boolean hudGoodTeamsEnabled = true;
    private static boolean hudTeamStatusBoardEnabled = true;
    private static boolean hudChatDetectedEnabled = true;
    private static boolean hudResourceEnabled = true;
    private static int resourceAlertIronThreshold = 40;
    private static int resourceAlertGoldThreshold = 40;
    private static boolean threatProximityGlowEnabled = true;
    private static int threatProximityRange = 20;
    private static String hudPosition = "TOP_LEFT";
    private static double hudScale = 1.0;
    private static double hudBackgroundOpacity = 0.4;

    // Time-windowed FKDR settings
    private static boolean recentFkdrNametagEnabled = true;
    private static boolean recentFkdrHudEnabled = true;

    // Last-seen minimap arrow settings
    private static boolean lastSeenArrowEnabled = true;
    private static int lastSeenArrowFreshSeconds = 6;
    private static boolean lastSeenArrowOnlyThreats = true;

    // Bed defense build assistant settings
    private static boolean bedDefenseAssistEnabled = true;
    private static int bedDefenseProximityRange = 18;

    // Match summary card settings
    private static boolean matchSummaryCardEnabled = true;
    private static int matchSummaryCardDurationSeconds = 12;

    // Pre-game threat briefing settings
    private static boolean preGameBriefingEnabled = true;
    private static int preGameBriefingDurationSeconds = 6;

    // Enemy loadout row settings
    private static boolean enemyLoadoutNametagEnabled = true;
    private static boolean enemyLoadoutHudEnabled = true;

    // Final-kill context + ledger settings
    private static boolean finalKillContextEnabled = true;
    private static boolean finalKillLedgerHudEnabled = true;

    // Ender pearl tracking settings
    private static boolean enderPearlTrackingEnabled = true;
    private static boolean enderPearlOverlayEnabled = true;
    private static double enderPearlAlertRadius = 8.0;
    private static boolean enderPearlPreviewEnabled = true;

    // In-world nametag master toggle (threat label, recent-FKDR addon, loadout row, enemy-tracking label)
    private static boolean nameTagsEnabled = true;

    // Tab-list stat suffix (threat-colored stars + FKDR appended to tab display names)
    private static boolean tabStatsEnabled = true;

    // Clickable chat name links (click a player name in chat to run /bw lookup)
    private static boolean clickableChatEnabled = true;

    // Killfeed settings (standalone HUD element listing recent kills/deaths)
    private static boolean killfeedEnabled = true;
    private static String killfeedAnchor = "TOP_RIGHT";
    private static int killfeedOffsetX = 4;
    private static int killfeedOffsetY = 4;
    // Respawn countdown suffix on killfeed entries (~5.5s Hypixel respawn delay)
    private static boolean respawnCountdownEnabled = true;
    // Per-killer live finals badges on nametags/killfeed + one-shot carry warning
    private static boolean carryBadgesEnabled = true;
    private static int carryWarnThreshold = 4;
    // Sidebar-calibrated generator tier clock (HUD countdown + T-5s audio cue)
    private static boolean generatorTierClockEnabled = true;
    private static boolean generatorTierCueEnabled = true;
    // Passive block-change-packet consumers: bridge radar + bed tamper alarm
    private static boolean bridgeRadarEnabled = true;
    private static boolean bedTamperAlarmEnabled = true;
    private static double bedTamperRadius = 8.0;
    // Lobby sweat index: cached-stats aggregate + own percentile on the HUD
    private static boolean lobbySweatIndexEnabled = true;

    // HUD editor (/bw edithud): anchor + offset position overrides. While the
    // per-element custom flag is false the legacy position logic (hudPosition
    // corner, killfeedAnchor corner, centered cards) applies unchanged.
    // Offsets are signed pixels in unscaled ScaledResolution space, measured
    // from the screen anchor point to the same corner/edge of the element.
    private static boolean hudCustomPosition = false;
    private static String hudAnchorX = "LEFT";
    private static String hudAnchorY = "TOP";
    private static int hudAnchorOffsetX = 4;
    private static int hudAnchorOffsetY = 4;
    private static boolean killfeedCustomPosition = false;
    private static String killfeedAnchorX = "RIGHT";
    private static String killfeedAnchorY = "TOP";
    private static int killfeedAnchorOffsetX = -4;
    private static int killfeedAnchorOffsetY = 4;
    private static boolean matchSummaryCustomPosition = false;
    private static String matchSummaryAnchorX = "CENTER";
    private static String matchSummaryAnchorY = "CENTER";
    private static int matchSummaryAnchorOffsetX = 0;
    private static int matchSummaryAnchorOffsetY = 0;
    private static boolean preGameBriefingCustomPosition = false;
    private static String preGameBriefingAnchorX = "CENTER";
    private static String preGameBriefingAnchorY = "CENTER";
    private static int preGameBriefingAnchorOffsetX = 0;
    private static int preGameBriefingAnchorOffsetY = 0;

    // Self-building map registry (learn generator/bed layouts from played games)
    private static boolean mapLearningEnabled = true;

    // Anti-cheat / hacker detection settings
    private static boolean antiCheatEnabled = true;
    private static boolean antiCheatAutoBlockEnabled = true;
    private static boolean antiCheatScaffoldEnabled = true;
    private static boolean antiCheatCpsEnabled = true;
    private static int antiCheatFlagCooldownMs = 10000;

    // Forge config sub-categories used to organise the GUI config screen.
    public static final String CATEGORY_NEW_FEATURES = "newfeatures";

    /**
     * Initialize the configuration file
     */
    public static void init(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "bedwarsstats.cfg");
        config = new Configuration(configFile);
        loadConfig();
    }

    /**
     * Get the Configuration object (for GUI)
     */
    public static Configuration getConfig() {
        return config;
    }

    /**
     * Load configuration from file
     */
    public static void loadConfig() {
        try {
            config.load();
        } catch (Exception e) {
            LOGGER.error("Error loading config from disk: {}", e.getMessage());
        }
        syncFromConfig();
    }

    /**
     * Re-reads the in-memory {@link Configuration} into the static fields and persists
     * any changes. Unlike {@link #loadConfig()} this deliberately does NOT reload from
     * disk: the in-game GUI writes edits into the Property objects before firing
     * OnConfigChangedEvent, so reloading from disk here would discard those edits.
     */
    public static void syncFromConfig() {
        try {
            // API key stored in "general" category
            Property apiKeyProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "apiKey",
                    "",
                    "Your Hypixel API key. Get one from https://developer.hypixel.net/dashboard/");
            apiKey = apiKeyProp.getString();

            // Display settings
            Property displayDurationProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "displayDuration",
                    16,
                    "How long (seconds) to show player info on screen",
                    5, 60);
            displayDuration = displayDurationProp.getInt();

            // Threat level thresholds - Stars
            Property lowStarProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "lowStarThreshold",
                    100,
                    "Minimum stars for MEDIUM threat level",
                    0, 1000);
            lowStarThreshold = lowStarProp.getInt();

            Property mediumStarProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "mediumStarThreshold",
                    300,
                    "Minimum stars for HIGH threat level",
                    0, 2000);
            mediumStarThreshold = mediumStarProp.getInt();

            Property highStarProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "highStarThreshold",
                    500,
                    "Minimum stars for EXTREME threat level",
                    0, 5000);
            highStarThreshold = highStarProp.getInt();

            // Threat level thresholds - FKDR
            Property lowFkdrProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "lowFkdrThreshold",
                    2.0,
                    "Minimum FKDR for MEDIUM threat level",
                    0.0, 20.0);
            lowFkdrThreshold = lowFkdrProp.getDouble();

            Property mediumFkdrProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "mediumFkdrThreshold",
                    4.0,
                    "Minimum FKDR for HIGH threat level",
                    0.0, 20.0);
            mediumFkdrThreshold = mediumFkdrProp.getDouble();

            Property highFkdrProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "highFkdrThreshold",
                    6.0,
                    "Minimum FKDR for EXTREME threat level",
                    0.0, 20.0);
            highFkdrThreshold = highFkdrProp.getDouble();

            // Alert settings
            Property chatAlertsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "chatAlertsEnabled",
                    true,
                    "Show threat alerts in chat");
            chatAlertsEnabled = chatAlertsProp.getBoolean();

            Property blacklistAlertsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "blacklistAlertsEnabled",
                    true,
                    "Show alert when blacklisted player is detected");
            blacklistAlertsEnabled = blacklistAlertsProp.getBoolean();

            Property historyAlertsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "historyAlertsEnabled",
                    true,
                    "Show alert when you have played against a player before");
            historyAlertsEnabled = historyAlertsProp.getBoolean();

            Property bedProximityAlertsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "bedProximityAlertsEnabled",
                    true,
                    "Show warning when enemy player is within 15 blocks of your bed");
            bedProximityAlertsEnabled = bedProximityAlertsProp.getBoolean();

            Property mapAwareBedDetectionProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "mapAwareBedDetectionEnabled",
                    true,
                    "Scan around spawn to detect your actual bed block instead of only using spawn location");
            mapAwareBedDetectionEnabled = mapAwareBedDetectionProp.getBoolean();

            Property bedScanRangeProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "bedScanRange",
                    30,
                    "Horizontal scan range (blocks) for map-aware bed detection",
                    8, 64);
            bedScanRange = bedScanRangeProp.getInt();

            Property bedScanRetrySecondsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "bedScanRetrySeconds",
                    12,
                    "How long (seconds) to keep retrying bed detection before falling back to spawn",
                    3, 30);
            bedScanRetrySeconds = bedScanRetrySecondsProp.getInt();

            Property teamDangerSummaryProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "teamDangerSummaryEnabled",
                    true,
                    "Show per-team average danger summaries in HUD and /bw info");
            teamDangerSummaryEnabled = teamDangerSummaryProp.getBoolean();

            Property nickDetectionProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "nickDetectionEnabled",
                    true,
                    "Flag Hypixel-nicked players (no Mojang account, empty Bedwars profile) with a [NICK] tag");
            nickDetectionEnabled = nickDetectionProp.getBoolean();

            // Autoplay settings
            Property autoplayMaxThreatProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "autoplayMaxThreatLevel",
                    "HIGH",
                    "Maximum threat level to tolerate when autoplay is enabled. Use HIGH or EXTREME.");
            autoplayMaxThreatLevel = autoplayMaxThreatProp.getString();
            // Validate the value
            if (!autoplayMaxThreatLevel.equals("HIGH") && !autoplayMaxThreatLevel.equals("EXTREME")) {
                autoplayMaxThreatLevel = "HIGH";
            }

            Property autoplayRequeueProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "autoplayRequeueEnabled",
                    false,
                    "When true, autoplay will leave and requeue into a new game when threats are detected. When false, it only displays stats.");
            autoplayRequeueEnabled = autoplayRequeueProp.getBoolean();

            Property lobbyMaxPlayerCountProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "lobbyMaxPlayerCount",
                    8,
                    "Maximum number of players in a pre-game lobby before autoplay switches lobbies. Set to 16 to effectively disable.",
                    2, 16);
            lobbyMaxPlayerCount = lobbyMaxPlayerCountProp.getInt();

            // Invisible player detection settings
            Property invisibleAlertsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "invisiblePlayerAlertsEnabled",
                    true,
                    "Show warning when invisible player is detected nearby");
            invisiblePlayerAlertsEnabled = invisibleAlertsProp.getBoolean();

            Property invisibleRangeProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "invisibleDetectionRange",
                    20,
                    "Range (blocks) to detect invisible players",
                    5, 100);
            invisibleDetectionRange = invisibleRangeProp.getInt();

            Property invisibleCooldownProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "invisibleWarningCooldown",
                    5000,
                    "Cooldown (ms) between invisible player warnings",
                    1000, 30000);
            invisibleWarningCooldown = invisibleCooldownProp.getInt();

            // Audio cue settings
            Property audioAlertsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "audioAlertsEnabled",
                    true,
                    "Enable sound cues for critical events");
            audioAlertsEnabled = audioAlertsProp.getBoolean();

            Property invisAudioCueProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "invisibleAudioCueEnabled",
                    true,
                    "Play a sound when an invisible player warning triggers");
            invisibleAudioCueEnabled = invisAudioCueProp.getBoolean();

            Property bedAudioCueProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "bedDangerAudioCueEnabled",
                    true,
                    "Play a sound when bed danger warning triggers");
            bedDangerAudioCueEnabled = bedAudioCueProp.getBoolean();

            Property audioCueVolumeProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "audioCueVolume",
                    0.8,
                    "Volume for mod audio cues",
                    0.0, 1.0);
            audioCueVolume = audioCueVolumeProp.getDouble();

            Property audioCueCooldownProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "audioCueCooldownMs",
                    1500,
                    "Cooldown (ms) between repeated audio cues of the same type",
                    250, 10000);
            audioCueCooldownMs = audioCueCooldownProp.getInt();

            // Rush risk predictor settings
            Property rushPredictorProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "rushPredictorEnabled",
                    true,
                    "Estimate first-rush timing from map and nearby teams");
            rushPredictorEnabled = rushPredictorProp.getBoolean();

            Property rushWarnThresholdProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "rushWarningThresholdSeconds",
                    20,
                    "Warn when predicted first rush is within this many seconds",
                    8, 45);
            rushWarningThresholdSeconds = rushWarnThresholdProp.getInt();

            Property rushRecheckProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "rushRecheckIntervalSeconds",
                    2,
                    "How often to refresh rush prediction while match starts",
                    1, 10);
            rushRecheckIntervalSeconds = rushRecheckProp.getInt();

            // Auto-blacklist settings
            Property autoBlacklistProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "autoBlacklistEnabled",
                    true,
                    "Automatically blacklist players after repeated losses");
            autoBlacklistEnabled = autoBlacklistProp.getBoolean();

            Property autoBlacklistLossThresholdProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "autoBlacklistLossThreshold",
                    3,
                    "Losses required to auto-blacklist a player within lookback window",
                    2, 20);
            autoBlacklistLossThreshold = autoBlacklistLossThresholdProp.getInt();

            Property autoBlacklistLookbackProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "autoBlacklistLookbackDays",
                    14,
                    "Lookback window (days) for auto-blacklist loss counting",
                    1, 90);
            autoBlacklistLookbackDays = autoBlacklistLookbackProp.getInt();

            Property autoBlacklistCooldownProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "autoBlacklistCooldownDays",
                    7,
                    "Cooldown (days) before the same player can be auto-blacklisted again",
                    0, 60);
            autoBlacklistCooldownDays = autoBlacklistCooldownProp.getInt();

            Property autoBlacklistExpiryProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "autoBlacklistExpiryDays",
                    30,
                    "Days until auto-blacklist entries expire (0 = never expire)",
                    0, 180);
            autoBlacklistExpiryDays = autoBlacklistExpiryProp.getInt();

            // Generator display settings
            Property generatorDisplayProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "generatorDisplayEnabled",
                    true,
                    "Show resource count above diamond/emerald generators");
            generatorDisplayEnabled = generatorDisplayProp.getBoolean();

            Property generatorScanRangeProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "generatorScanRange",
                    64,
                    "Range (blocks) to scan for generators. 64 covers a player's reachable "
                            + "diamond/emerald gens; raise it for very large maps.",
                    50, 200);
            generatorScanRange = generatorScanRangeProp.getInt();

            Property generatorRenderDistProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "generatorLabelRenderDistance",
                    256,
                    "Distance (blocks) to render generator labels",
                    100, 500);
            generatorLabelRenderDistance = generatorRenderDistProp.getInt();

            // HUD overlay settings
            Property hudEnabledProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudEnabled",
                    true,
                    "Master toggle for the on-screen HUD overlay");
            hudEnabled = hudEnabledProp.getBoolean();

            Property hudHighestThreatProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudHighestThreatEnabled",
                    true,
                    "Show highest enemy team threat on the HUD");
            hudHighestThreatEnabled = hudHighestThreatProp.getBoolean();

            Property hudGeneratorCountsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudGeneratorCountsEnabled",
                    true,
                    "Show diamond/emerald generator resource counts on the HUD");
            hudGeneratorCountsEnabled = hudGeneratorCountsProp.getBoolean();

            Property hudTeamSummaryProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudTeamSummaryEnabled",
                    true,
                    "Show per-team danger summary on the HUD");
            hudTeamSummaryEnabled = hudTeamSummaryProp.getBoolean();

            Property hudGoodTeamsProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudGoodTeamsEnabled",
                    true,
                    "Show a GOOD TEAMS section listing teams whose average threat is HIGH or EXTREME");
            hudGoodTeamsEnabled = hudGoodTeamsProp.getBoolean();

            Property hudTeamStatusBoardProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudTeamStatusBoardEnabled",
                    true,
                    "Show a TEAMS status board (bed alive/gone, players left, finals lost) built from the sidebar; works without an API key.");
            hudTeamStatusBoardEnabled = hudTeamStatusBoardProp.getBoolean();

            Property hudChatDetectedProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudChatDetectedEnabled",
                    true,
                    "Show stats of players detected via in-game chat on the HUD");
            hudChatDetectedEnabled = hudChatDetectedProp.getBoolean();

            Property hudResourceProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudResourceEnabled",
                    true,
                    "Show your iron/gold/diamond/emerald inventory counts on the HUD");
            hudResourceEnabled = hudResourceProp.getBoolean();

            Property resourceAlertIronProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "resourceAlertIronThreshold",
                    40,
                    "Iron count to trigger TNT ready-to-buy alert",
                    1, 64);
            resourceAlertIronThreshold = resourceAlertIronProp.getInt();

            Property resourceAlertGoldProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "resourceAlertGoldThreshold",
                    40,
                    "Gold count to trigger Fireball ready-to-buy alert",
                    1, 64);
            resourceAlertGoldThreshold = resourceAlertGoldProp.getInt();

            Property threatProximityGlowProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "threatProximityGlowEnabled",
                    true,
                    "Pulse a red border on the HUD when an EXTREME threat player is within range");
            threatProximityGlowEnabled = threatProximityGlowProp.getBoolean();

            Property threatProximityRangeProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "threatProximityRange",
                    20,
                    "Distance (blocks) for the EXTREME threat proximity glow to activate",
                    5, 50);
            threatProximityRange = threatProximityRangeProp.getInt();

            // Lobby bait messages
            Property lobbyBaitProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "lobbyBaitMessagesEnabled",
                    true,
                    "Send bait messages in pre-lobby chat to provoke players into revealing their stats");
            lobbyBaitMessagesEnabled = lobbyBaitProp.getBoolean();

            // Enemy tracking settings
            Property enemyTrackingProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "enemyTrackingEnabled",
                    true,
                    "Track enemy diamond/emerald pickups, armor protection, and hotbar items");
            enemyTrackingEnabled = enemyTrackingProp.getBoolean();

            Property enemyTrackingHudProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "enemyTrackingHudEnabled",
                    true,
                    "Show detailed enemy tracking info on the HUD for the nearest enemy");
            enemyTrackingHudEnabled = enemyTrackingHudProp.getBoolean();

            Property enemyTrackingPickupRangeProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "enemyTrackingPickupRange",
                    3.0,
                    "Max distance (blocks) to attribute a resource pickup to an enemy player",
                    1.0, 8.0);
            enemyTrackingPickupRange = enemyTrackingPickupRangeProp.getDouble();

            // Fireball detection settings
            Property fireballDetectionProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "fireballDetectionEnabled",
                    true,
                    "Detect incoming fireballs in Bedwars matches and predict their impact point");
            fireballDetectionEnabled = fireballDetectionProp.getBoolean();

            Property fireballOverlayProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "fireballOverlayEnabled",
                    true,
                    "Draw the fireball trajectory line and impact marker in the world");
            fireballOverlayEnabled = fireballOverlayProp.getBoolean();

            Property fireballAudioCueProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "fireballAudioCueEnabled",
                    true,
                    "Play an audio cue when a fireball is heading toward you");
            fireballAudioCueEnabled = fireballAudioCueProp.getBoolean();

            Property fireballAlertRadiusProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "fireballAlertRadius",
                    3.0,
                    "Max distance (blocks) between the fireball's projected path and the player to count as threatening",
                    0.5, 10.0);
            fireballAlertRadius = fireballAlertRadiusProp.getDouble();

            Property fireballBedAlertRadiusProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "fireballBedAlertRadius",
                    4.0,
                    "Max distance (blocks) between a fireball's projected impact and your bed to fire the bed-attack warning",
                    1.0, 10.0);
            fireballBedAlertRadius = fireballBedAlertRadiusProp.getDouble();

            Property fireballMaxTraceProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "fireballMaxTraceDistance",
                    200,
                    "Maximum ray-trace distance (blocks) when projecting a fireball's impact point",
                    20, 500);
            fireballMaxTraceDistance = fireballMaxTraceProp.getInt();

            Property hudPositionProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudPosition",
                    "TOP_LEFT",
                    "Screen corner for the HUD overlay. TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, or BOTTOM_RIGHT.");
            hudPosition = hudPositionProp.getString();
            if (!hudPosition.equals("TOP_LEFT") && !hudPosition.equals("TOP_RIGHT")
                    && !hudPosition.equals("BOTTOM_LEFT") && !hudPosition.equals("BOTTOM_RIGHT")) {
                hudPosition = "TOP_LEFT";
            }

            Property hudScaleProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudScale",
                    1.0,
                    "Scale factor for the HUD overlay",
                    0.5, 2.0);
            hudScale = hudScaleProp.getDouble();

            Property hudBgOpacityProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudBackgroundOpacity",
                    0.4,
                    "Background opacity for the HUD panel",
                    0.0, 1.0);
            hudBackgroundOpacity = hudBgOpacityProp.getDouble();

            // ─── New feature toggles (separate category for the GUI screen) ───
            config.setCategoryComment(CATEGORY_NEW_FEATURES,
                    "Newer features: time-windowed FKDR, last-seen minimap arrow,\n" +
                            "bed defense build assistant, match summary card.");

            Property recentFkdrNametagProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "recentFkdrNametagEnabled",
                    true,
                    "Append the player's recent (monthly/weekly) FKDR + delta to the in-world threat nametag.");
            recentFkdrNametagEnabled = recentFkdrNametagProp.getBoolean();

            Property recentFkdrHudProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "recentFkdrHudEnabled",
                    true,
                    "Show recent (monthly/weekly) FKDR alongside career FKDR in HUD detected-player rows.");
            recentFkdrHudEnabled = recentFkdrHudProp.getBoolean();

            Property lastSeenArrowProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "lastSeenArrowEnabled",
                    true,
                    "Show a compass-style arrow at the screen edge pointing at recently sighted threat players.");
            lastSeenArrowEnabled = lastSeenArrowProp.getBoolean();

            Property lastSeenFreshProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "lastSeenArrowFreshSeconds",
                    6,
                    "How many seconds after losing sight of an enemy the arrow stays on screen.",
                    1, 30);
            lastSeenArrowFreshSeconds = lastSeenFreshProp.getInt();

            Property lastSeenOnlyThreatsProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "lastSeenArrowOnlyThreats",
                    true,
                    "When true, only HIGH/EXTREME threat players get an arrow. When false, all enemies do.");
            lastSeenArrowOnlyThreats = lastSeenOnlyThreatsProp.getBoolean();

            Property bedDefenseAssistProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "bedDefenseAssistEnabled",
                    true,
                    "Highlight exposed bed faces in red when an enemy is near your bed so you can patch them quickly.");
            bedDefenseAssistEnabled = bedDefenseAssistProp.getBoolean();

            Property bedDefenseRangeProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "bedDefenseProximityRange",
                    18,
                    "Distance (blocks) to your bed within which an enemy activates the defense highlight.",
                    6, 48);
            bedDefenseProximityRange = bedDefenseRangeProp.getInt();

            Property matchSummaryProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "matchSummaryCardEnabled",
                    true,
                    "Show a post-match summary card with top threats and suggested blacklist entries after each game.");
            matchSummaryCardEnabled = matchSummaryProp.getBoolean();

            Property matchSummaryDurProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "matchSummaryCardDurationSeconds",
                    12,
                    "How long (seconds) the post-match summary card stays on screen.",
                    3, 60);
            matchSummaryCardDurationSeconds = matchSummaryDurProp.getInt();

            Property preGameBriefingProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "preGameBriefingEnabled",
                    true,
                    "Show a pre-game scouting report summarising each enemy team's threat profile as a match starts.");
            preGameBriefingEnabled = preGameBriefingProp.getBoolean();

            Property preGameBriefingDurProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "preGameBriefingDurationSeconds",
                    6,
                    "How long (seconds) the pre-game scouting report stays on screen.",
                    2, 30);
            preGameBriefingDurationSeconds = preGameBriefingDurProp.getInt();

            Property enemyLoadoutNametagProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "enemyLoadoutNametagEnabled",
                    true,
                    "Show a compact loadout row (armor tier / best sword / ranged / utility) above enemy nametags.");
            enemyLoadoutNametagEnabled = enemyLoadoutNametagProp.getBoolean();

            Property enemyLoadoutHudProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "enemyLoadoutHudEnabled",
                    true,
                    "Show the compact enemy loadout string in the HUD nearest-enemy block.");
            enemyLoadoutHudEnabled = enemyLoadoutHudProp.getBoolean();

            Property finalKillContextProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "finalKillContextEnabled",
                    true,
                    "Append threat context (stars/FKDR of the victim) to FINAL KILL chat lines.");
            finalKillContextEnabled = finalKillContextProp.getBoolean();

            Property finalKillLedgerHudProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "finalKillLedgerHudEnabled",
                    true,
                    "Show a per-team final-kill tally on the HUD.");
            finalKillLedgerHudEnabled = finalKillLedgerHudProp.getBoolean();

            Property enderPearlTrackingProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "enderPearlTrackingEnabled",
                    true,
                    "Track ender pearls thrown by non-teammates and predict their landing point.");
            enderPearlTrackingEnabled = enderPearlTrackingProp.getBoolean();

            Property enderPearlOverlayProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "enderPearlOverlayEnabled",
                    true,
                    "Draw the predicted ender pearl arc and landing marker in the world.");
            enderPearlOverlayEnabled = enderPearlOverlayProp.getBoolean();

            Property enderPearlAlertRadiusProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "enderPearlAlertRadius",
                    8.0,
                    "Distance (blocks) from the local player or bed at which a pearl landing triggers an alert.",
                    1.0, 30.0);
            enderPearlAlertRadius = enderPearlAlertRadiusProp.getDouble();

            Property enderPearlPreviewProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "enderPearlPreviewEnabled",
                    true,
                    "Show a predicted arc and landing marker for your own ender pearls when holding one.");
            enderPearlPreviewEnabled = enderPearlPreviewProp.getBoolean();

            Property nameTagsEnabledProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "nameTagsEnabled",
                    true,
                    "Master toggle for in-world stat labels rendered above player heads (threat level, FKDR, loadout, enemy tracking).");
            nameTagsEnabled = nameTagsEnabledProp.getBoolean();

            Property tabStatsEnabledProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "tabStatsEnabled",
                    true,
                    "Append threat-colored stars + FKDR to player names in the tab list (uses cached stats only, never fetches).");
            tabStatsEnabled = tabStatsEnabledProp.getBoolean();

            Property clickableChatProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "clickableChatEnabled",
                    true,
                    "Rewrite player names in chat into clickable links that run /bw lookup on click.");
            clickableChatEnabled = clickableChatProp.getBoolean();

            Property killfeedEnabledProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedEnabled",
                    true,
                    "Show a compact killfeed HUD element listing recent kills and deaths (threat-colored killers).");
            killfeedEnabled = killfeedEnabledProp.getBoolean();

            Property respawnCountdownProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "respawnCountdownEnabled",
                    true,
                    "Append an estimated respawn countdown (~5s) to killfeed entries; final kills show a cross instead.");
            respawnCountdownEnabled = respawnCountdownProp.getBoolean();

            Property carryBadgesProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "carryBadgesEnabled",
                    true,
                    "Show live per-match final-kill badges on threat nametags and killfeed entries, plus a one-shot warning when an enemy reaches the carry threshold.");
            carryBadgesEnabled = carryBadgesProp.getBoolean();

            Property carryWarnThresholdProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "carryWarnThreshold",
                    4,
                    "Final kills in one match at which the carry warning fires.",
                    2, 16);
            carryWarnThreshold = carryWarnThresholdProp.getInt();

            Property generatorTierClockProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "generatorTierClockEnabled",
                    true,
                    "Show the server-exact next-event countdown (Diamond/Emerald tier upgrades) from the sidebar in the HUD generators section.");
            generatorTierClockEnabled = generatorTierClockProp.getBoolean();

            Property generatorTierCueProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "generatorTierCueEnabled",
                    true,
                    "Play a note cue 5 seconds before a diamond/emerald generator tier upgrade.");
            generatorTierCueEnabled = generatorTierCueProp.getBoolean();

            Property bridgeRadarProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "bridgeRadarEnabled",
                    true,
                    "Detect enemy bridges advancing toward your bed from block-placement packets and alert with direction, distance and ETA.");
            bridgeRadarEnabled = bridgeRadarProp.getBoolean();

            Property bedTamperAlarmProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "bedTamperAlarmEnabled",
                    true,
                    "Alarm the instant any block changes near your bed with no teammate nearby (packet-level, works behind terrain within loaded chunks).");
            bedTamperAlarmEnabled = bedTamperAlarmProp.getBoolean();

            Property bedTamperRadiusProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "bedTamperRadius",
                    8.0,
                    "Radius (blocks) around your bed watched by the tamper alarm.",
                    3.0, 16.0);
            bedTamperRadius = bedTamperRadiusProp.getDouble();

            Property lobbySweatIndexProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "lobbySweatIndexEnabled",
                    true,
                    "Show a LOBBY difficulty line (avg stars/FKDR, Chill/Average/Sweaty, your percentile) aggregated from already-cached stats.");
            lobbySweatIndexEnabled = lobbySweatIndexProp.getBoolean();

            Property killfeedAnchorProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedAnchor",
                    "TOP_RIGHT",
                    "Screen corner the killfeed is anchored to: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, or BOTTOM_RIGHT.");
            killfeedAnchor = killfeedAnchorProp.getString();
            if (!killfeedAnchor.equals("TOP_LEFT") && !killfeedAnchor.equals("TOP_RIGHT")
                    && !killfeedAnchor.equals("BOTTOM_LEFT") && !killfeedAnchor.equals("BOTTOM_RIGHT")) {
                killfeedAnchor = "TOP_RIGHT";
            }

            Property killfeedOffsetXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedOffsetX",
                    4,
                    "Horizontal offset (scaled pixels) of the killfeed from its anchored screen edge.",
                    0, 4096);
            killfeedOffsetX = killfeedOffsetXProp.getInt();

            Property killfeedOffsetYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedOffsetY",
                    4,
                    "Vertical offset (scaled pixels) of the killfeed from its anchored screen edge.",
                    0, 4096);
            killfeedOffsetY = killfeedOffsetYProp.getInt();

            // ─── HUD editor layout overrides (/bw edithud) ───
            Property hudCustomPositionProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "hudCustomPosition",
                    false,
                    "Use the HUD editor anchor+offset position for the main stats panel instead of hudPosition.");
            hudCustomPosition = hudCustomPositionProp.getBoolean();

            Property hudAnchorXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "hudAnchorX",
                    "LEFT",
                    "Horizontal anchor for the main stats panel: LEFT, CENTER, or RIGHT.");
            hudAnchorX = clampAnchorX(hudAnchorXProp.getString(), "LEFT");

            Property hudAnchorYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "hudAnchorY",
                    "TOP",
                    "Vertical anchor for the main stats panel: TOP, CENTER, or BOTTOM.");
            hudAnchorY = clampAnchorY(hudAnchorYProp.getString(), "TOP");

            Property hudAnchorOffsetXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "hudAnchorOffsetX",
                    4,
                    "Signed horizontal offset (unscaled pixels) of the main stats panel from its anchor.",
                    -4096, 4096);
            hudAnchorOffsetX = hudAnchorOffsetXProp.getInt();

            Property hudAnchorOffsetYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "hudAnchorOffsetY",
                    4,
                    "Signed vertical offset (unscaled pixels) of the main stats panel from its anchor.",
                    -4096, 4096);
            hudAnchorOffsetY = hudAnchorOffsetYProp.getInt();

            Property killfeedCustomPositionProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedCustomPosition",
                    false,
                    "Use the HUD editor anchor+offset position for the killfeed instead of killfeedAnchor.");
            killfeedCustomPosition = killfeedCustomPositionProp.getBoolean();

            Property killfeedAnchorXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedAnchorX",
                    "RIGHT",
                    "Horizontal anchor for the killfeed: LEFT, CENTER, or RIGHT.");
            killfeedAnchorX = clampAnchorX(killfeedAnchorXProp.getString(), "RIGHT");

            Property killfeedAnchorYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedAnchorY",
                    "TOP",
                    "Vertical anchor for the killfeed: TOP, CENTER, or BOTTOM.");
            killfeedAnchorY = clampAnchorY(killfeedAnchorYProp.getString(), "TOP");

            Property killfeedAnchorOffsetXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedAnchorOffsetX",
                    -4,
                    "Signed horizontal offset (unscaled pixels) of the killfeed from its anchor.",
                    -4096, 4096);
            killfeedAnchorOffsetX = killfeedAnchorOffsetXProp.getInt();

            Property killfeedAnchorOffsetYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "killfeedAnchorOffsetY",
                    4,
                    "Signed vertical offset (unscaled pixels) of the killfeed from its anchor.",
                    -4096, 4096);
            killfeedAnchorOffsetY = killfeedAnchorOffsetYProp.getInt();

            Property matchSummaryCustomPositionProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "matchSummaryCustomPosition",
                    false,
                    "Use the HUD editor anchor+offset position for the match summary card instead of the centered default.");
            matchSummaryCustomPosition = matchSummaryCustomPositionProp.getBoolean();

            Property matchSummaryAnchorXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "matchSummaryAnchorX",
                    "CENTER",
                    "Horizontal anchor for the match summary card: LEFT, CENTER, or RIGHT.");
            matchSummaryAnchorX = clampAnchorX(matchSummaryAnchorXProp.getString(), "CENTER");

            Property matchSummaryAnchorYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "matchSummaryAnchorY",
                    "CENTER",
                    "Vertical anchor for the match summary card: TOP, CENTER, or BOTTOM.");
            matchSummaryAnchorY = clampAnchorY(matchSummaryAnchorYProp.getString(), "CENTER");

            Property matchSummaryAnchorOffsetXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "matchSummaryAnchorOffsetX",
                    0,
                    "Signed horizontal offset (unscaled pixels) of the match summary card from its anchor.",
                    -4096, 4096);
            matchSummaryAnchorOffsetX = matchSummaryAnchorOffsetXProp.getInt();

            Property matchSummaryAnchorOffsetYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "matchSummaryAnchorOffsetY",
                    0,
                    "Signed vertical offset (unscaled pixels) of the match summary card from its anchor.",
                    -4096, 4096);
            matchSummaryAnchorOffsetY = matchSummaryAnchorOffsetYProp.getInt();

            Property preGameBriefingCustomPositionProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "preGameBriefingCustomPosition",
                    false,
                    "Use the HUD editor anchor+offset position for the pre-game briefing card instead of the centered default.");
            preGameBriefingCustomPosition = preGameBriefingCustomPositionProp.getBoolean();

            Property preGameBriefingAnchorXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "preGameBriefingAnchorX",
                    "CENTER",
                    "Horizontal anchor for the pre-game briefing card: LEFT, CENTER, or RIGHT.");
            preGameBriefingAnchorX = clampAnchorX(preGameBriefingAnchorXProp.getString(), "CENTER");

            Property preGameBriefingAnchorYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "preGameBriefingAnchorY",
                    "CENTER",
                    "Vertical anchor for the pre-game briefing card: TOP, CENTER, or BOTTOM.");
            preGameBriefingAnchorY = clampAnchorY(preGameBriefingAnchorYProp.getString(), "CENTER");

            Property preGameBriefingAnchorOffsetXProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "preGameBriefingAnchorOffsetX",
                    0,
                    "Signed horizontal offset (unscaled pixels) of the pre-game briefing card from its anchor.",
                    -4096, 4096);
            preGameBriefingAnchorOffsetX = preGameBriefingAnchorOffsetXProp.getInt();

            Property preGameBriefingAnchorOffsetYProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "preGameBriefingAnchorOffsetY",
                    0,
                    "Signed vertical offset (unscaled pixels) of the pre-game briefing card from its anchor.",
                    -4096, 4096);
            preGameBriefingAnchorOffsetY = preGameBriefingAnchorOffsetYProp.getInt();

            Property mapLearningProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "mapLearningEnabled",
                    true,
                    "Learn map layouts (generator and bed positions) from played games and persist them to learnedmaps.json (/bw maps).");
            mapLearningEnabled = mapLearningProp.getBoolean();

            // Anti-cheat / hacker detection
            Property antiCheatEnabledProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "antiCheatEnabled",
                    true,
                    "Master toggle for client-side hacker detection (autoblock, scaffold).");
            antiCheatEnabled = antiCheatEnabledProp.getBoolean();

            Property antiCheatAutoBlockProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "antiCheatAutoBlockEnabled",
                    true,
                    "Flag players that swing a sword while still in the blocking-use state (autoblock).");
            antiCheatAutoBlockEnabled = antiCheatAutoBlockProp.getBoolean();

            Property antiCheatScaffoldProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "antiCheatScaffoldEnabled",
                    true,
                    "Flag players sprint-bridging with sharp downward pitch or pitch-snap behaviour (scaffold).");
            antiCheatScaffoldEnabled = antiCheatScaffoldProp.getBoolean();

            Property antiCheatCpsProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "antiCheatCpsEnabled",
                    true,
                    "Track each player's clicks-per-second from swing packets and show it on their nametag (autoclicker indicator).");
            antiCheatCpsEnabled = antiCheatCpsProp.getBoolean();

            Property antiCheatCooldownProp = config.get(
                    CATEGORY_NEW_FEATURES,
                    "antiCheatFlagCooldownMs",
                    10000,
                    "Minimum milliseconds between repeat flag chat messages for the same player.",
                    1000, 600000);
            antiCheatFlagCooldownMs = antiCheatCooldownProp.getInt();

            // Master enable/disable toggle
            Property modEnabledProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "modEnabled",
                    true,
                    "Master toggle. When false, all automatic features (stat lookup, alerts, HUD, audio) are disabled.");
            modEnabled = modEnabledProp.getBoolean();

            // Apply the loaded API key to HypixelAPI
            if (apiKey != null && !apiKey.isEmpty()) {
                HypixelAPI.setApiKey(apiKey);
                LOGGER.info("API key loaded from config");
            }

        } catch (Exception e) {
            LOGGER.error("Error loading config: {}", e.getMessage());
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    /** Clamp a horizontal anchor string to LEFT/CENTER/RIGHT (mirrors the hudPosition clamp). */
    private static String clampAnchorX(String value, String fallback) {
        if ("LEFT".equals(value) || "CENTER".equals(value) || "RIGHT".equals(value)) {
            return value;
        }
        return fallback;
    }

    /** Clamp a vertical anchor string to TOP/CENTER/BOTTOM (mirrors the hudPosition clamp). */
    private static String clampAnchorY(String value, String fallback) {
        if ("TOP".equals(value) || "CENTER".equals(value) || "BOTTOM".equals(value)) {
            return value;
        }
        return fallback;
    }

    /**
     * Save the API key to configuration file
     */
    public static void setApiKey(String key) {
        apiKey = key;
        config.get(Configuration.CATEGORY_GENERAL, "apiKey", "").set(key);
        config.save();

        // Also update the HypixelAPI class
        HypixelAPI.setApiKey(key);
    }

    // Getters
    public static String getApiKey() {
        return apiKey;
    }

    public static int getDisplayDuration() {
        return displayDuration;
    }

    public static int getLowStarThreshold() {
        return lowStarThreshold;
    }

    public static int getMediumStarThreshold() {
        return mediumStarThreshold;
    }

    public static int getHighStarThreshold() {
        return highStarThreshold;
    }

    public static double getLowFkdrThreshold() {
        return lowFkdrThreshold;
    }

    public static double getMediumFkdrThreshold() {
        return mediumFkdrThreshold;
    }

    public static double getHighFkdrThreshold() {
        return highFkdrThreshold;
    }

    public static boolean isChatAlertsEnabled() {
        return chatAlertsEnabled;
    }

    public static boolean isBlacklistAlertsEnabled() {
        return blacklistAlertsEnabled;
    }

    public static boolean isHistoryAlertsEnabled() {
        return historyAlertsEnabled;
    }

    public static boolean isBedProximityAlertsEnabled() {
        return bedProximityAlertsEnabled;
    }

    public static boolean isMapAwareBedDetectionEnabled() {
        return mapAwareBedDetectionEnabled;
    }

    public static int getBedScanRange() {
        return bedScanRange;
    }

    public static int getBedScanRetrySeconds() {
        return bedScanRetrySeconds;
    }

    public static boolean isTeamDangerSummaryEnabled() {
        return teamDangerSummaryEnabled;
    }

    public static boolean isNickDetectionEnabled() {
        return nickDetectionEnabled;
    }

    public static boolean isAudioAlertsEnabled() {
        return audioAlertsEnabled;
    }

    public static boolean isInvisibleAudioCueEnabled() {
        return invisibleAudioCueEnabled;
    }

    public static boolean isBedDangerAudioCueEnabled() {
        return bedDangerAudioCueEnabled;
    }

    public static double getAudioCueVolume() {
        return audioCueVolume;
    }

    public static int getAudioCueCooldownMs() {
        return audioCueCooldownMs;
    }

    public static boolean isRushPredictorEnabled() {
        return rushPredictorEnabled;
    }

    public static int getRushWarningThresholdSeconds() {
        return rushWarningThresholdSeconds;
    }

    public static int getRushRecheckIntervalSeconds() {
        return rushRecheckIntervalSeconds;
    }

    public static boolean isAutoBlacklistEnabled() {
        return autoBlacklistEnabled;
    }

    public static int getAutoBlacklistLossThreshold() {
        return autoBlacklistLossThreshold;
    }

    public static int getAutoBlacklistLookbackDays() {
        return autoBlacklistLookbackDays;
    }

    public static int getAutoBlacklistCooldownDays() {
        return autoBlacklistCooldownDays;
    }

    public static int getAutoBlacklistExpiryDays() {
        return autoBlacklistExpiryDays;
    }

    public static String getAutoplayMaxThreatLevel() {
        return autoplayMaxThreatLevel;
    }

    public static boolean isAutoplayRequeueEnabled() {
        return autoplayRequeueEnabled;
    }

    public static void setAutoplayRequeueEnabled(boolean enabled) {
        autoplayRequeueEnabled = enabled;
        config.get(Configuration.CATEGORY_GENERAL, "autoplayRequeueEnabled", false).set(enabled);
        config.save();
    }

    public static int getLobbyMaxPlayerCount() {
        return lobbyMaxPlayerCount;
    }

    public static void setLobbyMaxPlayerCount(int count) {
        lobbyMaxPlayerCount = count;
        config.get(Configuration.CATEGORY_GENERAL, "lobbyMaxPlayerCount", 8).set(count);
        config.save();
    }

    // Invisible player detection getters
    public static boolean isInvisiblePlayerAlertsEnabled() {
        return invisiblePlayerAlertsEnabled;
    }

    public static int getInvisibleDetectionRange() {
        return invisibleDetectionRange;
    }

    public static int getInvisibleWarningCooldown() {
        return invisibleWarningCooldown;
    }

    // Generator display getters
    public static boolean isGeneratorDisplayEnabled() {
        return generatorDisplayEnabled;
    }

    public static int getGeneratorScanRange() {
        return generatorScanRange;
    }

    public static int getGeneratorLabelRenderDistance() {
        return generatorLabelRenderDistance;
    }

    // HUD overlay getters
    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static boolean isHudHighestThreatEnabled() {
        return hudHighestThreatEnabled;
    }

    public static boolean isHudGeneratorCountsEnabled() {
        return hudGeneratorCountsEnabled;
    }

    public static boolean isHudTeamSummaryEnabled() {
        return hudTeamSummaryEnabled;
    }

    public static boolean isHudGoodTeamsEnabled() {
        return hudGoodTeamsEnabled;
    }

    public static boolean isHudTeamStatusBoardEnabled() {
        return hudTeamStatusBoardEnabled;
    }

    public static boolean isHudChatDetectedEnabled() {
        return hudChatDetectedEnabled;
    }

    public static boolean isHudResourceEnabled() {
        return hudResourceEnabled;
    }

    public static int getResourceAlertIronThreshold() {
        return resourceAlertIronThreshold;
    }

    public static int getResourceAlertGoldThreshold() {
        return resourceAlertGoldThreshold;
    }

    public static boolean isThreatProximityGlowEnabled() {
        return threatProximityGlowEnabled;
    }

    public static int getThreatProximityRange() {
        return threatProximityRange;
    }

    public static String getHudPosition() {
        return hudPosition;
    }

    public static double getHudScale() {
        return hudScale;
    }

    public static double getHudBackgroundOpacity() {
        return hudBackgroundOpacity;
    }

    public static boolean isLobbyBaitMessagesEnabled() {
        return lobbyBaitMessagesEnabled;
    }

    public static boolean isEnemyTrackingEnabled() {
        return enemyTrackingEnabled;
    }

    public static boolean isEnemyTrackingHudEnabled() {
        return enemyTrackingHudEnabled;
    }

    public static double getEnemyTrackingPickupRange() {
        return enemyTrackingPickupRange;
    }

    public static boolean isFireballDetectionEnabled() {
        return fireballDetectionEnabled;
    }

    public static boolean isFireballOverlayEnabled() {
        return fireballOverlayEnabled;
    }

    public static boolean isFireballAudioCueEnabled() {
        return fireballAudioCueEnabled;
    }

    public static double getFireballAlertRadius() {
        return fireballAlertRadius;
    }

    public static double getFireballBedAlertRadius() {
        return fireballBedAlertRadius;
    }

    public static int getFireballMaxTraceDistance() {
        return fireballMaxTraceDistance;
    }

    public static boolean isRecentFkdrNametagEnabled() {
        return recentFkdrNametagEnabled;
    }

    public static boolean isRecentFkdrHudEnabled() {
        return recentFkdrHudEnabled;
    }

    public static boolean isLastSeenArrowEnabled() {
        return lastSeenArrowEnabled;
    }

    public static int getLastSeenArrowFreshSeconds() {
        return lastSeenArrowFreshSeconds;
    }

    public static boolean isLastSeenArrowOnlyThreats() {
        return lastSeenArrowOnlyThreats;
    }

    public static boolean isBedDefenseAssistEnabled() {
        return bedDefenseAssistEnabled;
    }

    public static int getBedDefenseProximityRange() {
        return bedDefenseProximityRange;
    }

    public static boolean isMatchSummaryCardEnabled() {
        return matchSummaryCardEnabled;
    }

    public static int getMatchSummaryCardDurationSeconds() {
        return matchSummaryCardDurationSeconds;
    }

    /**
     * Pre-game “scouting report” (center screen box) is disabled; the feature was noisy at match start.
     */
    public static boolean isPreGameBriefingEnabled() {
        return false;
    }

    public static int getPreGameBriefingDurationSeconds() {
        return preGameBriefingDurationSeconds;
    }

    public static boolean isEnemyLoadoutNametagEnabled() {
        return enemyLoadoutNametagEnabled;
    }

    public static boolean isEnemyLoadoutHudEnabled() {
        return enemyLoadoutHudEnabled;
    }

    public static boolean isFinalKillContextEnabled() {
        return finalKillContextEnabled;
    }

    public static boolean isFinalKillLedgerHudEnabled() {
        return finalKillLedgerHudEnabled;
    }

    public static boolean isEnderPearlTrackingEnabled() {
        return enderPearlTrackingEnabled;
    }

    public static boolean isEnderPearlOverlayEnabled() {
        return enderPearlOverlayEnabled;
    }

    public static double getEnderPearlAlertRadius() {
        return enderPearlAlertRadius;
    }

    public static boolean isEnderPearlPreviewEnabled() {
        return enderPearlPreviewEnabled;
    }

    public static void setEnderPearlPreviewEnabled(boolean enabled) {
        enderPearlPreviewEnabled = enabled;
        config.get(CATEGORY_NEW_FEATURES, "enderPearlPreviewEnabled", true).set(enabled);
        config.save();
    }

    public static boolean isNameTagsEnabled() {
        return nameTagsEnabled;
    }

    public static void setNameTagsEnabled(boolean enabled) {
        nameTagsEnabled = enabled;
        config.get(CATEGORY_NEW_FEATURES, "nameTagsEnabled", true).set(enabled);
        config.save();
    }

    public static boolean isTabStatsEnabled() {
        return tabStatsEnabled;
    }

    public static void setTabStatsEnabled(boolean enabled) {
        tabStatsEnabled = enabled;
        config.get(CATEGORY_NEW_FEATURES, "tabStatsEnabled", true).set(enabled);
        config.save();
    }

    public static boolean isClickableChatEnabled() {
        return clickableChatEnabled;
    }

    public static boolean isKillfeedEnabled() {
        return killfeedEnabled;
    }

    public static boolean isRespawnCountdownEnabled() {
        return respawnCountdownEnabled;
    }

    public static boolean isCarryBadgesEnabled() {
        return carryBadgesEnabled;
    }

    public static int getCarryWarnThreshold() {
        return carryWarnThreshold;
    }

    public static boolean isGeneratorTierClockEnabled() {
        return generatorTierClockEnabled;
    }

    public static boolean isGeneratorTierCueEnabled() {
        return generatorTierCueEnabled;
    }

    public static boolean isBridgeRadarEnabled() {
        return bridgeRadarEnabled;
    }

    public static boolean isBedTamperAlarmEnabled() {
        return bedTamperAlarmEnabled;
    }

    public static double getBedTamperRadius() {
        return bedTamperRadius;
    }

    public static boolean isLobbySweatIndexEnabled() {
        return lobbySweatIndexEnabled;
    }

    public static String getKillfeedAnchor() {
        return killfeedAnchor;
    }

    public static void setKillfeedAnchor(String anchor) {
        killfeedAnchor = anchor;
        config.get(CATEGORY_NEW_FEATURES, "killfeedAnchor", "TOP_RIGHT").set(anchor);
        config.save();
    }

    public static int getKillfeedOffsetX() {
        return killfeedOffsetX;
    }

    public static void setKillfeedOffsetX(int offsetX) {
        killfeedOffsetX = offsetX;
        config.get(CATEGORY_NEW_FEATURES, "killfeedOffsetX", 4).set(offsetX);
        config.save();
    }

    public static int getKillfeedOffsetY() {
        return killfeedOffsetY;
    }

    public static void setKillfeedOffsetY(int offsetY) {
        killfeedOffsetY = offsetY;
        config.get(CATEGORY_NEW_FEATURES, "killfeedOffsetY", 4).set(offsetY);
        config.save();
    }

    // ─── HUD editor layout (anchor + offset overrides) ───

    public static boolean isHudCustomPosition() {
        return hudCustomPosition;
    }

    public static String getHudAnchorX() {
        return hudAnchorX;
    }

    public static String getHudAnchorY() {
        return hudAnchorY;
    }

    public static int getHudAnchorOffsetX() {
        return hudAnchorOffsetX;
    }

    public static int getHudAnchorOffsetY() {
        return hudAnchorOffsetY;
    }

    public static boolean isKillfeedCustomPosition() {
        return killfeedCustomPosition;
    }

    public static String getKillfeedAnchorX() {
        return killfeedAnchorX;
    }

    public static String getKillfeedAnchorY() {
        return killfeedAnchorY;
    }

    public static int getKillfeedAnchorOffsetX() {
        return killfeedAnchorOffsetX;
    }

    public static int getKillfeedAnchorOffsetY() {
        return killfeedAnchorOffsetY;
    }

    public static boolean isMatchSummaryCustomPosition() {
        return matchSummaryCustomPosition;
    }

    public static String getMatchSummaryAnchorX() {
        return matchSummaryAnchorX;
    }

    public static String getMatchSummaryAnchorY() {
        return matchSummaryAnchorY;
    }

    public static int getMatchSummaryAnchorOffsetX() {
        return matchSummaryAnchorOffsetX;
    }

    public static int getMatchSummaryAnchorOffsetY() {
        return matchSummaryAnchorOffsetY;
    }

    public static boolean isPreGameBriefingCustomPosition() {
        return preGameBriefingCustomPosition;
    }

    public static String getPreGameBriefingAnchorX() {
        return preGameBriefingAnchorX;
    }

    public static String getPreGameBriefingAnchorY() {
        return preGameBriefingAnchorY;
    }

    public static int getPreGameBriefingAnchorOffsetX() {
        return preGameBriefingAnchorOffsetX;
    }

    public static int getPreGameBriefingAnchorOffsetY() {
        return preGameBriefingAnchorOffsetY;
    }

    /**
     * In-memory only: live-preview update from the HUD editor while dragging.
     * Nothing is written to disk until {@link #persistHudEditorLayout()}.
     */
    public static void applyHudLayoutLive(boolean custom, String anchorX, String anchorY,
                                          int offsetX, int offsetY) {
        hudCustomPosition = custom;
        hudAnchorX = clampAnchorX(anchorX, "LEFT");
        hudAnchorY = clampAnchorY(anchorY, "TOP");
        hudAnchorOffsetX = offsetX;
        hudAnchorOffsetY = offsetY;
    }

    /** In-memory only: live-preview update from the HUD editor while dragging. */
    public static void applyKillfeedLayoutLive(boolean custom, String anchorX, String anchorY,
                                               int offsetX, int offsetY) {
        killfeedCustomPosition = custom;
        killfeedAnchorX = clampAnchorX(anchorX, "RIGHT");
        killfeedAnchorY = clampAnchorY(anchorY, "TOP");
        killfeedAnchorOffsetX = offsetX;
        killfeedAnchorOffsetY = offsetY;
    }

    /** In-memory only: live-preview update from the HUD editor while dragging. */
    public static void applyMatchSummaryLayoutLive(boolean custom, String anchorX, String anchorY,
                                                   int offsetX, int offsetY) {
        matchSummaryCustomPosition = custom;
        matchSummaryAnchorX = clampAnchorX(anchorX, "CENTER");
        matchSummaryAnchorY = clampAnchorY(anchorY, "CENTER");
        matchSummaryAnchorOffsetX = offsetX;
        matchSummaryAnchorOffsetY = offsetY;
    }

    /** In-memory only: live-preview update from the HUD editor while dragging. */
    public static void applyPreGameBriefingLayoutLive(boolean custom, String anchorX, String anchorY,
                                                      int offsetX, int offsetY) {
        preGameBriefingCustomPosition = custom;
        preGameBriefingAnchorX = clampAnchorX(anchorX, "CENTER");
        preGameBriefingAnchorY = clampAnchorY(anchorY, "CENTER");
        preGameBriefingAnchorOffsetX = offsetX;
        preGameBriefingAnchorOffsetY = offsetY;
    }

    /** In-memory only: live hudScale preview from the HUD editor scroll wheel (0.5-2.0 clamp). */
    public static void applyHudScaleLive(double scale) {
        hudScale = Math.max(0.5, Math.min(2.0, scale));
    }

    /**
     * Persist every HUD-editor-managed property in one pass (Property.set +
     * a single config.save()), called when the editor screen closes.
     */
    public static void persistHudEditorLayout() {
        config.get(Configuration.CATEGORY_GENERAL, "hudScale", 1.0).set(hudScale);
        config.get(CATEGORY_NEW_FEATURES, "hudCustomPosition", false).set(hudCustomPosition);
        config.get(CATEGORY_NEW_FEATURES, "hudAnchorX", "LEFT").set(hudAnchorX);
        config.get(CATEGORY_NEW_FEATURES, "hudAnchorY", "TOP").set(hudAnchorY);
        config.get(CATEGORY_NEW_FEATURES, "hudAnchorOffsetX", 4).set(hudAnchorOffsetX);
        config.get(CATEGORY_NEW_FEATURES, "hudAnchorOffsetY", 4).set(hudAnchorOffsetY);
        config.get(CATEGORY_NEW_FEATURES, "killfeedCustomPosition", false).set(killfeedCustomPosition);
        config.get(CATEGORY_NEW_FEATURES, "killfeedAnchorX", "RIGHT").set(killfeedAnchorX);
        config.get(CATEGORY_NEW_FEATURES, "killfeedAnchorY", "TOP").set(killfeedAnchorY);
        config.get(CATEGORY_NEW_FEATURES, "killfeedAnchorOffsetX", -4).set(killfeedAnchorOffsetX);
        config.get(CATEGORY_NEW_FEATURES, "killfeedAnchorOffsetY", 4).set(killfeedAnchorOffsetY);
        config.get(CATEGORY_NEW_FEATURES, "matchSummaryCustomPosition", false).set(matchSummaryCustomPosition);
        config.get(CATEGORY_NEW_FEATURES, "matchSummaryAnchorX", "CENTER").set(matchSummaryAnchorX);
        config.get(CATEGORY_NEW_FEATURES, "matchSummaryAnchorY", "CENTER").set(matchSummaryAnchorY);
        config.get(CATEGORY_NEW_FEATURES, "matchSummaryAnchorOffsetX", 0).set(matchSummaryAnchorOffsetX);
        config.get(CATEGORY_NEW_FEATURES, "matchSummaryAnchorOffsetY", 0).set(matchSummaryAnchorOffsetY);
        config.get(CATEGORY_NEW_FEATURES, "preGameBriefingCustomPosition", false).set(preGameBriefingCustomPosition);
        config.get(CATEGORY_NEW_FEATURES, "preGameBriefingAnchorX", "CENTER").set(preGameBriefingAnchorX);
        config.get(CATEGORY_NEW_FEATURES, "preGameBriefingAnchorY", "CENTER").set(preGameBriefingAnchorY);
        config.get(CATEGORY_NEW_FEATURES, "preGameBriefingAnchorOffsetX", 0).set(preGameBriefingAnchorOffsetX);
        config.get(CATEGORY_NEW_FEATURES, "preGameBriefingAnchorOffsetY", 0).set(preGameBriefingAnchorOffsetY);
        config.save();
    }

    public static boolean isMapLearningEnabled() {
        return mapLearningEnabled;
    }

    public static void setMapLearningEnabled(boolean enabled) {
        mapLearningEnabled = enabled;
        config.get(CATEGORY_NEW_FEATURES, "mapLearningEnabled", true).set(enabled);
        config.save();
    }

    public static boolean isModEnabled() {
        return modEnabled;
    }

    public static void setModEnabled(boolean enabled) {
        modEnabled = enabled;
        config.get(Configuration.CATEGORY_GENERAL, "modEnabled", true).set(enabled);
        config.save();
    }

    public static boolean isAntiCheatEnabled() {
        return antiCheatEnabled;
    }

    public static boolean isAntiCheatAutoBlockEnabled() {
        return antiCheatAutoBlockEnabled;
    }

    public static boolean isAntiCheatScaffoldEnabled() {
        return antiCheatScaffoldEnabled;
    }

    public static boolean isAntiCheatCpsEnabled() {
        return antiCheatCpsEnabled;
    }

    public static int getAntiCheatFlagCooldownMs() {
        return antiCheatFlagCooldownMs;
    }
}
