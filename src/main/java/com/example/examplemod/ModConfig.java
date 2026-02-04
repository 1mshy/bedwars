package com.example.examplemod;

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
}
