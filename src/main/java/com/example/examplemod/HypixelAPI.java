package com.example.examplemod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class for Hypixel API communication
 */
public class HypixelAPI {

    private static final String HYPIXEL_API_URL = "https://api.hypixel.net/player";
    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";

    // Your Hypixel API key - get one from https://developer.hypixel.net/dashboard/
    // TODO: Move to config file for production
    private static String API_KEY = "";

    // Cache to avoid repeat API calls
    private static final Map<String, BedwarsStats> statsCache = new HashMap<String, BedwarsStats>();
    private static final Map<String, String> uuidCache = new HashMap<String, String>();

    // Thread pool for async API calls
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    // Callback interface for async stats fetching
    public interface StatsCallback {
        void onStatsLoaded(BedwarsStats stats);

        void onError(String error);
    }

    /**
     * Set the API key
     */
    public static void setApiKey(String key) {
        API_KEY = key;
    }

    /**
     * Check if API key is set
     */
    public static boolean hasApiKey() {
        return API_KEY != null && !API_KEY.isEmpty();
    }

    /**
     * Fetch stats asynchronously using username (Mojang API lookup)
     */
    public static void fetchStatsAsync(final String playerName, final StatsCallback callback) {
        // Check cache first
        if (statsCache.containsKey(playerName.toLowerCase())) {
            callback.onStatsLoaded(statsCache.get(playerName.toLowerCase()));
            return;
        }

        // Check if API key is set
        if (!hasApiKey()) {
            callback.onError("No API key set. Use /bwstats setkey <key>");
            return;
        }

        // Run API call on background thread
        executor.submit(() -> {
            try {
                // Step 1: Get UUID from Mojang
                System.out.println("[BedwarsStats] Looking up UUID for: " + playerName);
                String uuid = getUUID(playerName);
                if (uuid == null) {
                    callback.onError("Could not get UUID for " + playerName + " - player may not exist");
                    return;
                }

                // Step 2: Fetch stats from Hypixel
                System.out.println("[BedwarsStats] Fetching Hypixel stats for UUID: " + uuid);
                String response = fetchHypixelStats(uuid);
                if (response == null) {
                    callback.onError("Could not fetch Hypixel stats for " + playerName);
                    return;
                }

                // Step 3: Parse stats
                BedwarsStats stats = new BedwarsStats(playerName, uuid);
                stats.parseFromJson(response);

                // Cache the result
                statsCache.put(playerName.toLowerCase(), stats);

                callback.onStatsLoaded(stats);

            } catch (Exception e) {
                System.out.println("[BedwarsStats] Exception: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Error fetching stats: " + e.getMessage());
            }
        });
    }

    /**
     * Fetch stats asynchronously using pre-known UUID (skips Mojang lookup)
     */
    public static void fetchStatsWithUuid(final String playerName, final String uuid, final StatsCallback callback) {
        // Check cache first
        if (statsCache.containsKey(playerName.toLowerCase())) {
            callback.onStatsLoaded(statsCache.get(playerName.toLowerCase()));
            return;
        }

        // Check if API key is set
        if (!hasApiKey()) {
            callback.onError("No API key set. Use /bwstats setkey <key>");
            return;
        }

        // Cache the UUID
        uuidCache.put(playerName.toLowerCase(), uuid);

        // Run API call on background thread
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // Fetch stats from Hypixel directly (UUID already known)
                    System.out.println("[BedwarsStats] Fetching stats for " + playerName + " (UUID: " + uuid + ")");
                    String response = fetchHypixelStats(uuid);
                    if (response == null) {
                        callback.onError("Could not fetch Hypixel stats for " + playerName);
                        return;
                    }

                    // Parse stats
                    BedwarsStats stats = new BedwarsStats(playerName, uuid);
                    stats.parseFromJson(response);

                    // Cache the result
                    statsCache.put(playerName.toLowerCase(), stats);

                    callback.onStatsLoaded(stats);

                } catch (Exception e) {
                    System.out.println("[BedwarsStats] Exception: " + e.getMessage());
                    e.printStackTrace();
                    callback.onError("Error fetching stats: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Get UUID from Mojang API
     */
    private static String getUUID(String playerName) {
        // Check cache
        if (uuidCache.containsKey(playerName.toLowerCase())) {
            return uuidCache.get(playerName.toLowerCase());
        }

        try {
            URL url = new URL(MOJANG_API_URL + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse UUID from response
            String json = response.toString();
            String uuid = extractString(json, "id");

            if (uuid != null) {
                // Format UUID with dashes
                uuid = formatUUID(uuid);
                uuidCache.put(playerName.toLowerCase(), uuid);
            }

            return uuid;

        } catch (Exception e) {
            System.out.println("[BedwarsStats] Error getting UUID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetch player stats from Hypixel API
     */
    private static String fetchHypixelStats(String uuid) {
        try {
            String urlString = HYPIXEL_API_URL + "?key=" + API_KEY + "&uuid=" + uuid;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("[BedwarsStats] Hypixel API returned: " + responseCode);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();

        } catch (Exception e) {
            System.out.println("[BedwarsStats] Error fetching Hypixel stats: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract string value from JSON
     */
    private static String extractString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1)
            return null;

        int valueStart = keyIndex + searchKey.length();
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd == -1)
            return null;

        return json.substring(valueStart, valueEnd);
    }

    /**
     * Format UUID without dashes to UUID with dashes
     */
    private static String formatUUID(String uuid) {
        if (uuid.length() != 32)
            return uuid;
        return uuid.substring(0, 8) + "-" +
                uuid.substring(8, 12) + "-" +
                uuid.substring(12, 16) + "-" +
                uuid.substring(16, 20) + "-" +
                uuid.substring(20);
    }

    /**
     * Clear the stats cache
     */
    public static void clearCache() {
        statsCache.clear();
        uuidCache.clear();
    }

    /**
     * Get cached stats for a player (or null if not cached)
     */
    public static BedwarsStats getCachedStats(String playerName) {
        return statsCache.get(playerName.toLowerCase());
    }
}
