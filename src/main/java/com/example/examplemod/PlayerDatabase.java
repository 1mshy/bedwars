package com.example.examplemod;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Persistent storage for player data including blacklist and encounter history.
 * Data is stored as JSON in config/bedwarsstats/playerdata.json
 */
public class PlayerDatabase {

    private static final String DATA_DIR = "config/bedwarsstats";
    private static final String DATA_FILE = DATA_DIR + "/playerdata.json";

    // Singleton instance
    private static PlayerDatabase instance;

    // Blacklist: playerName (lowercase) -> BlacklistEntry
    private Map<String, BlacklistEntry> blacklist = new HashMap<String, BlacklistEntry>();

    // Encounter history: playerName (lowercase) -> List of encounters
    private Map<String, List<EncounterEntry>> history = new HashMap<String, List<EncounterEntry>>();

    // Players in current game session (for recording encounters on game end)
    private Set<String> currentGamePlayers = new HashSet<String>();

    private Gson gson;

    /**
     * Blacklist entry data
     */
    public static class BlacklistEntry {
        public String playerName; // Original case
        public String reason;
        public long addedAt;

        public BlacklistEntry(String playerName, String reason) {
            this.playerName = playerName;
            this.reason = reason;
            this.addedAt = System.currentTimeMillis();
        }
    }

    /**
     * Game encounter entry
     */
    public static class EncounterEntry {
        public long timestamp;
        public String uuid;
        public GameOutcome outcome;

        public EncounterEntry(String uuid, GameOutcome outcome) {
            this.timestamp = System.currentTimeMillis();
            this.uuid = uuid;
            this.outcome = outcome;
        }
    }

    /**
     * Game outcome enum
     */
    public enum GameOutcome {
        WIN,
        LOSS,
        UNKNOWN // Game ended but outcome unclear
    }

    private PlayerDatabase() {
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        load();
    }

    /**
     * Get singleton instance
     */
    public static PlayerDatabase getInstance() {
        if (instance == null) {
            instance = new PlayerDatabase();
        }
        return instance;
    }

    // ==================== BLACKLIST METHODS ====================

    /**
     * Add a player to the blacklist
     */
    public void addToBlacklist(String playerName, String reason) {
        String key = playerName.toLowerCase();
        blacklist.put(key, new BlacklistEntry(playerName, reason));
        save();
        System.out.println("[BedwarsStats] Added " + playerName + " to blacklist: " + reason);
    }

    /**
     * Remove a player from the blacklist
     */
    public boolean removeFromBlacklist(String playerName) {
        String key = playerName.toLowerCase();
        if (blacklist.remove(key) != null) {
            save();
            System.out.println("[BedwarsStats] Removed " + playerName + " from blacklist");
            return true;
        }
        return false;
    }

    /**
     * Check if a player is blacklisted
     */
    public boolean isBlacklisted(String playerName) {
        return blacklist.containsKey(playerName.toLowerCase());
    }

    /**
     * Get blacklist entry for a player
     */
    public BlacklistEntry getBlacklistEntry(String playerName) {
        return blacklist.get(playerName.toLowerCase());
    }

    /**
     * Get all blacklisted players
     */
    public Collection<BlacklistEntry> getBlacklistedPlayers() {
        return blacklist.values();
    }

    /**
     * Get blacklist size
     */
    public int getBlacklistSize() {
        return blacklist.size();
    }

    // ==================== HISTORY METHODS ====================

    /**
     * Add a player to the current game session
     */
    public void addToCurrentGame(String playerName) {
        currentGamePlayers.add(playerName.toLowerCase());
    }

    /**
     * Clear current game players (when leaving lobby)
     */
    public void clearCurrentGame() {
        currentGamePlayers.clear();
    }

    /**
     * Record encounter for all players in current game
     */
    public void recordGameEnd(GameOutcome outcome) {
        for (String playerKey : currentGamePlayers) {
            recordEncounter(playerKey, null, outcome);
        }
        save();
        System.out.println("[BedwarsStats] Recorded " + outcome + " against " + currentGamePlayers.size() + " players");
    }

    /**
     * Record a single encounter
     */
    public void recordEncounter(String playerName, String uuid, GameOutcome outcome) {
        String key = playerName.toLowerCase();
        if (!history.containsKey(key)) {
            history.put(key, new ArrayList<EncounterEntry>());
        }
        history.get(key).add(new EncounterEntry(uuid, outcome));
    }

    /**
     * Get encounter history for a player
     */
    public List<EncounterEntry> getEncounterHistory(String playerName) {
        String key = playerName.toLowerCase();
        if (history.containsKey(key)) {
            return history.get(key);
        }
        return new ArrayList<EncounterEntry>();
    }

    /**
     * Check if we've played against this player before
     */
    public boolean hasPlayedBefore(String playerName) {
        return history.containsKey(playerName.toLowerCase());
    }

    /**
     * Get encounter count for a player
     */
    public int getEncounterCount(String playerName) {
        List<EncounterEntry> encounters = getEncounterHistory(playerName);
        return encounters.size();
    }

    /**
     * Get win/loss record against a player
     */
    public int[] getWinLossRecord(String playerName) {
        List<EncounterEntry> encounters = getEncounterHistory(playerName);
        int wins = 0;
        int losses = 0;
        for (EncounterEntry e : encounters) {
            if (e.outcome == GameOutcome.WIN)
                wins++;
            else if (e.outcome == GameOutcome.LOSS)
                losses++;
        }
        return new int[] { wins, losses };
    }

    /**
     * Get total history size (unique players)
     */
    public int getHistorySize() {
        return history.size();
    }

    // ==================== PERSISTENCE ====================

    /**
     * Save data to JSON file
     */
    public void save() {
        try {
            // Create directory if needed
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Create data object
            JsonObject root = new JsonObject();

            // Serialize blacklist
            root.add("blacklist", gson.toJsonTree(blacklist));

            // Serialize history
            root.add("history", gson.toJsonTree(history));

            // Write to file
            FileWriter writer = new FileWriter(DATA_FILE);
            gson.toJson(root, writer);
            writer.close();

            System.out.println("[BedwarsStats] Saved player database");

        } catch (Exception e) {
            System.out.println("[BedwarsStats] Error saving player database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load data from JSON file
     */
    public void load() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            System.out.println("[BedwarsStats] No player database found, starting fresh");
            return;
        }

        try {
            FileReader reader = new FileReader(file);
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            reader.close();

            // Deserialize blacklist
            if (root.has("blacklist")) {
                Type blacklistType = new TypeToken<Map<String, BlacklistEntry>>() {
                }.getType();
                blacklist = gson.fromJson(root.get("blacklist"), blacklistType);
                if (blacklist == null) {
                    blacklist = new HashMap<String, BlacklistEntry>();
                }
            }

            // Deserialize history
            if (root.has("history")) {
                Type historyType = new TypeToken<Map<String, List<EncounterEntry>>>() {
                }.getType();
                history = gson.fromJson(root.get("history"), historyType);
                if (history == null) {
                    history = new HashMap<String, List<EncounterEntry>>();
                }
            }

            System.out.println("[BedwarsStats] Loaded player database: " +
                    blacklist.size() + " blacklisted, " +
                    history.size() + " in history");

        } catch (Exception e) {
            System.out.println("[BedwarsStats] Error loading player database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
