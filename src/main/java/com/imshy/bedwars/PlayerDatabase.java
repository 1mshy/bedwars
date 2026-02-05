package com.imshy.bedwars;

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
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

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
        public String source; // MANUAL or AUTO
        public long expiresAt; // 0 = never
        public long lastAutoAddAt; // 0 = never auto-added

        public BlacklistEntry(String playerName, String reason) {
            this(playerName, reason, "MANUAL", 0L, 0L);
        }

        public BlacklistEntry(String playerName, String reason, String source, long expiresAt, long lastAutoAddAt) {
            this.playerName = playerName;
            this.reason = reason;
            this.addedAt = System.currentTimeMillis();
            this.source = source;
            this.expiresAt = expiresAt;
            this.lastAutoAddAt = lastAutoAddAt;
        }

        public boolean isAuto() {
            return "AUTO".equalsIgnoreCase(source);
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
        blacklist.put(key, new BlacklistEntry(playerName, reason, "MANUAL", 0L, 0L));
        save();
        System.out.println("[BedwarsStats] Added " + playerName + " to blacklist: " + reason);
    }

    /**
     * Add or refresh an auto-blacklist entry.
     */
    public boolean addOrRefreshAutoBlacklist(String playerName, String reason, int expiryDays) {
        String key = playerName.toLowerCase();
        BlacklistEntry existing = blacklist.get(key);

        // Never overwrite manual blacklist entries.
        if (existing != null && !existing.isAuto()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long expiresAt = expiryDays > 0 ? now + (expiryDays * DAY_MS) : 0L;

        if (existing == null) {
            blacklist.put(key, new BlacklistEntry(playerName, reason, "AUTO", expiresAt, now));
        } else {
            existing.playerName = playerName;
            existing.reason = reason;
            existing.source = "AUTO";
            existing.addedAt = now;
            existing.expiresAt = expiresAt;
            existing.lastAutoAddAt = now;
        }

        save();
        return true;
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
        String key = playerName.toLowerCase();
        BlacklistEntry entry = blacklist.get(key);
        if (entry == null) {
            return false;
        }

        if (isBlacklistEntryExpired(entry, System.currentTimeMillis())) {
            blacklist.remove(key);
            save();
            return false;
        }

        return true;
    }

    /**
     * Get blacklist entry for a player
     */
    public BlacklistEntry getBlacklistEntry(String playerName) {
        String key = playerName.toLowerCase();
        BlacklistEntry entry = blacklist.get(key);
        if (entry == null) {
            return null;
        }

        if (isBlacklistEntryExpired(entry, System.currentTimeMillis())) {
            blacklist.remove(key);
            save();
            return null;
        }

        return entry;
    }

    /**
     * True if player is manually blacklisted.
     */
    public boolean isManualBlacklistEntry(String playerName) {
        BlacklistEntry entry = getBlacklistEntry(playerName);
        return entry != null && !entry.isAuto();
    }

    /**
     * Get all blacklisted players
     */
    public Collection<BlacklistEntry> getBlacklistedPlayers() {
        purgeExpiredBlacklistEntriesIfNeeded();
        return blacklist.values();
    }

    /**
     * Get blacklist size
     */
    public int getBlacklistSize() {
        purgeExpiredBlacklistEntriesIfNeeded();
        return blacklist.size();
    }

    /**
     * Remove expired AUTO entries.
     */
    public int cleanupExpiredAutoBlacklistEntries() {
        int removed = 0;
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, BlacklistEntry>> iterator = blacklist.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BlacklistEntry> entry = iterator.next();
            if (isBlacklistEntryExpired(entry.getValue(), now)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            save();
        }

        return removed;
    }

    // ==================== HISTORY METHODS ====================

    /**
     * Add a player to the current game session
     */
    public void addToCurrentGame(String playerName) {
        currentGamePlayers.add(playerName);
    }

    /**
     * Clear current game players (when leaving lobby)
     */
    public void clearCurrentGame() {
        currentGamePlayers.clear();
    }

    /**
     * Snapshot of players from the current game session.
     */
    public Set<String> getCurrentGamePlayersSnapshot() {
        return new HashSet<String>(currentGamePlayers);
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
     * Count recent encounters by outcome within lookback window.
     */
    public int countRecentOutcomes(String playerName, GameOutcome outcome, int lookbackDays) {
        if (lookbackDays <= 0) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long lookbackMs = lookbackDays * DAY_MS;
        int count = 0;

        List<EncounterEntry> encounters = getEncounterHistory(playerName);
        for (EncounterEntry encounter : encounters) {
            if (encounter.outcome != outcome) {
                continue;
            }
            if (now - encounter.timestamp <= lookbackMs) {
                count++;
            }
        }

        return count;
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

    private boolean isBlacklistEntryExpired(BlacklistEntry entry, long now) {
        return entry != null && entry.isAuto() && entry.expiresAt > 0 && entry.expiresAt <= now;
    }

    private void purgeExpiredBlacklistEntriesIfNeeded() {
        boolean changed = false;
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, BlacklistEntry>> iterator = blacklist.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BlacklistEntry> entry = iterator.next();
            if (isBlacklistEntryExpired(entry.getValue(), now)) {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    private void normalizeBlacklistEntries() {
        for (Map.Entry<String, BlacklistEntry> mapEntry : blacklist.entrySet()) {
            String key = mapEntry.getKey();
            BlacklistEntry entry = mapEntry.getValue();
            if (entry == null) {
                continue;
            }

            if (entry.playerName == null || entry.playerName.trim().isEmpty()) {
                entry.playerName = key;
            }

            if (entry.source == null || entry.source.trim().isEmpty()) {
                entry.source = "MANUAL";
            }

            if (!entry.isAuto()) {
                entry.expiresAt = 0L;
                entry.lastAutoAddAt = 0L;
            }
        }
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

            normalizeBlacklistEntries();
            cleanupExpiredAutoBlacklistEntries();

            System.out.println("[BedwarsStats] Loaded player database: " +
                    blacklist.size() + " blacklisted, " +
                    history.size() + " in history");

        } catch (Exception e) {
            System.out.println("[BedwarsStats] Error loading player database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
