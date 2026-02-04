package com.example.examplemod;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * Configuration handler for BedwarsStats mod
 * Persists settings like API key across restarts
 */
public class ModConfig {

    private static Configuration config;
    private static String apiKey = "";

    /**
     * Initialize the configuration file
     */
    public static void init(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "bedwarsstats.cfg");
        config = new Configuration(configFile);
        loadConfig();
    }

    /**
     * Load configuration from file
     */
    public static void loadConfig() {
        try {
            config.load();

            // API key stored in "general" category
            apiKey = config.getString(
                    "apiKey",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Your Hypixel API key. Get one from https://developer.hypixel.net/dashboard/");

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

    /**
     * Get the stored API key
     */
    public static String getApiKey() {
        return apiKey;
    }
}
