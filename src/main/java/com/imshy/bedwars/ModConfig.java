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
    private static boolean audioAlertsEnabled = true;
    private static boolean invisibleAudioCueEnabled = true;
    private static boolean bedDangerAudioCueEnabled = true;
    private static boolean extremeJoinAudioCueEnabled = true;
    private static double audioCueVolume = 0.8;
    private static int audioCueCooldownMs = 1500;
    private static String autoplayMaxThreatLevel = "HIGH"; // HIGH or EXTREME

    // Invisible player detection settings
    private static boolean invisiblePlayerAlertsEnabled = true;
    private static int invisibleDetectionRange = 20; // blocks
    private static int invisibleWarningCooldown = 5000; // ms

    // Generator display settings
    private static boolean generatorDisplayEnabled = true;
    private static int generatorScanRange = 100; // blocks
    private static int generatorLabelRenderDistance = 256; // blocks

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

    public static String getAutoplayMaxThreatLevel() {
        return autoplayMaxThreatLevel;
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
}
