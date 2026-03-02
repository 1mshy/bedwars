package com.imshy.bedwars;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * Configuration handler for BedwarsStats mod
 * Persists settings like API key across restarts
 */
public class ModConfig {

    private static Configuration config;

    // Settings values
    private static boolean modEnabled = true;
    private static String apiKey = "";
    private static int displayDuration = 10; // seconds
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
    private static boolean audioAlertsEnabled = true;
    private static boolean invisibleAudioCueEnabled = true;
    private static boolean bedDangerAudioCueEnabled = true;
    private static boolean extremeJoinAudioCueEnabled = true;
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
    private static int generatorScanRange = 100; // blocks
    private static int generatorLabelRenderDistance = 256; // blocks

    // Lobby bait messages
    private static boolean lobbyBaitMessagesEnabled = true;

    // Enemy tracking settings
    private static boolean enemyTrackingEnabled = true;
    private static boolean enemyTrackingHudEnabled = true;
    private static double enemyTrackingPickupRange = 3.0;

    // HUD overlay settings
    private static boolean hudEnabled = true;
    private static boolean hudHighestThreatEnabled = true;
    private static boolean hudGeneratorCountsEnabled = true;
    private static boolean hudTeamSummaryEnabled = true;
    private static boolean hudChatDetectedEnabled = true;
    private static String hudPosition = "TOP_LEFT";
    private static double hudScale = 1.0;
    private static double hudBackgroundOpacity = 0.4;

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
                    10,
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

            Property extremeJoinCueProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "extremeJoinAudioCueEnabled",
                    true,
                    "Play a sound when an EXTREME threat player is identified joining");
            extremeJoinAudioCueEnabled = extremeJoinCueProp.getBoolean();

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
                    100,
                    "Range (blocks) to scan for generators",
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

            Property hudChatDetectedProp = config.get(
                    Configuration.CATEGORY_GENERAL,
                    "hudChatDetectedEnabled",
                    true,
                    "Show stats of players detected via in-game chat on the HUD");
            hudChatDetectedEnabled = hudChatDetectedProp.getBoolean();

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
                System.out.println("[BedwarsStats] API key loaded from config!");
            }

        } catch (Exception e) {
            System.out.println("[BedwarsStats] Error loading config: " + e.getMessage());
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
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

    public static boolean isAudioAlertsEnabled() {
        return audioAlertsEnabled;
    }

    public static boolean isInvisibleAudioCueEnabled() {
        return invisibleAudioCueEnabled;
    }

    public static boolean isBedDangerAudioCueEnabled() {
        return bedDangerAudioCueEnabled;
    }

    public static boolean isExtremeJoinAudioCueEnabled() {
        return extremeJoinAudioCueEnabled;
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

    public static boolean isHudChatDetectedEnabled() {
        return hudChatDetectedEnabled;
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

    public static boolean isModEnabled() {
        return modEnabled;
    }

    public static void setModEnabled(boolean enabled) {
        modEnabled = enabled;
        config.get(Configuration.CATEGORY_GENERAL, "modEnabled", true).set(enabled);
        config.save();
    }
}
