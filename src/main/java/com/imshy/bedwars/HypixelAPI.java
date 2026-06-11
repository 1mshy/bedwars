package com.imshy.bedwars;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class for Hypixel API communication
 */
public class HypixelAPI {

    private static final String HYPIXEL_API_URL = "https://api.hypixel.net/v2/player";
    private static final String MOJANG_API_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";

    // Cache configuration
    private static final long CACHE_EXPIRATION_MS = 60 * 60 * 1000; // 60 minutes

    // Rate limiting configuration (Hypixel limit is 120 req/min)
    private static final int RATE_LIMIT_MAX = 120;
    private static final long RATE_LIMIT_WINDOW_MS = 60 * 1000; // 1 minute

    // Your Hypixel API key - get one from https://developer.hypixel.net/dashboard/
    private static String API_KEY = "";

    // Cache wrapper class with timestamp
    private static class CachedStats {
        BedwarsStats stats;
        long timestamp;

        CachedStats(BedwarsStats stats) {
            this.stats = stats;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }

        long getAgeMinutes() {
            return (System.currentTimeMillis() - timestamp) / (60 * 1000);
        }
    }

    // Cache to avoid repeat API calls (now with expiration). Read on the client thread
    // (getCachedStats / status) and written on the executor pool, so use concurrent maps.
    private static final java.util.Map<String, CachedStats> statsCache =
            new java.util.concurrent.ConcurrentHashMap<String, CachedStats>();
    private static final java.util.Map<String, String> uuidCache =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    // Rate limiting tracking. requestTimestamps does a compound read-modify-write in
    // checkRateLimit and is also read from /bw status, so every access synchronizes on it.
    private static final java.util.List<Long> requestTimestamps = new java.util.ArrayList<Long>();
    private static volatile int rateLimitedRequests = 0;
    private static volatile String lastFetchError = null;

    private static final Logger LOGGER = LogManager.getLogger("BedwarsStats");

    // Persistent UUID cache (config/bedwarsstats/uuidcache.json, same convention as
    // PlayerDatabase). Saves a Mojang round-trip per known name across sessions.
    private static final String UUID_CACHE_DIR = "config/bedwarsstats";
    private static final String UUID_CACHE_FILE = "config/bedwarsstats/uuidcache.json";
    private static final int UUID_CACHE_SCHEMA_VERSION = 2;
    private static final int UUID_CACHE_MAX_ENTRIES = 5000;
    private static final long UUID_CACHE_FLUSH_INTERVAL_MS = 60 * 1000;
    // Mojang's name-change cooldown is 30 days: a persisted name->UUID pair older
    // than this may legitimately point at a different account, so drop it at load.
    private static final long UUID_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000;

    // While rate limited, tab-scan paths pause requeueing for this long instead
    // of retrying at their scan cadence (the team-danger path runs at 1 Hz).
    private static final long RATE_LIMIT_BACKOFF_MS = 30 * 1000;
    private static volatile long rateLimitBackoffUntil = 0;

    /**
     * Name -> Mojang-confirmed UUID + confirmation time. Only entries in this
     * map are ever persisted, and the persisted value is taken from HERE — the
     * overwritable uuidCache may hold a tab-seeded UUID (which Hypixel spoofs
     * for nicked players) under the same name, and that one must stay
     * in-memory for the session only.
     */
    private static final java.util.Map<String, ConfirmedUuid> confirmedUuids =
            new java.util.concurrent.ConcurrentHashMap<String, ConfirmedUuid>();

    private static final class ConfirmedUuid {
        final String uuid;
        final long recordedAt;

        ConfirmedUuid(String uuid, long recordedAt) {
            this.uuid = uuid;
            this.recordedAt = recordedAt;
        }
    }

    private static volatile boolean uuidCacheDirty = false;
    private static volatile long lastUuidCacheFlush = 0;
    // Guards the file write itself; the map is already concurrent.
    private static final Object uuidCacheFileLock = new Object();

    // Thread pool for async API calls. The PriorityBlockingQueue lets high-value
    // fetches (user lookups, unknown players) jump ahead of background refreshes.
    // Tasks MUST go through execute() wrapped in PrioritizedFetchTask — submit()
    // would wrap them in a non-Comparable FutureTask and the priority queue would
    // throw ClassCastException at runtime.
    private static final java.util.concurrent.ThreadPoolExecutor executor =
            new java.util.concurrent.ThreadPoolExecutor(3, 3, 0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.PriorityBlockingQueue<Runnable>());

    static {
        loadUuidCache();
    }

    /**
     * Priority of a queued stat fetch. Declaration order is dispatch order:
     * lower ordinal runs first, FIFO within the same level.
     */
    public enum FetchPriority {
        /** User-initiated (/bw lookup, /bw all) — always first. */
        EXPLICIT,
        /** No cached stats and no known UUID — these decide threat fastest. */
        UNKNOWN_PLAYER,
        /** Tab list hinted a high-star player (pre-game "[123✫]" prefix). */
        TAB_HIGH_STAR,
        /** Everything else. */
        NORMAL,
        /** Refreshes and retries (render-path re-requests). */
        BACKGROUND
    }

    /**
     * Runnable wrapper that makes fetch tasks orderable in the priority queue.
     * A monotonic sequence number breaks ties so tasks within one priority level
     * keep FIFO submission order. Package-private for unit tests.
     */
    static class PrioritizedFetchTask implements Runnable, Comparable<PrioritizedFetchTask> {
        private static final java.util.concurrent.atomic.AtomicLong SEQUENCE =
                new java.util.concurrent.atomic.AtomicLong();

        final FetchPriority priority;
        final long sequence;
        private final Runnable delegate;

        PrioritizedFetchTask(FetchPriority priority, Runnable delegate) {
            this(priority, SEQUENCE.getAndIncrement(), delegate);
        }

        PrioritizedFetchTask(FetchPriority priority, long sequence, Runnable delegate) {
            this.priority = priority != null ? priority : FetchPriority.NORMAL;
            this.sequence = sequence;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                // Pool threads are the only place disk flushes may happen.
                maybeFlushUuidCache();
            }
        }

        @Override
        public int compareTo(PrioritizedFetchTask other) {
            int byPriority = Integer.compare(priority.ordinal(), other.priority.ordinal());
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(sequence, other.sequence);
        }
    }

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
        fetchStatsAsync(playerName, callback, FetchPriority.NORMAL);
    }

    /**
     * Fetch stats asynchronously using username (Mojang API lookup) at the
     * given queue priority.
     */
    public static void fetchStatsAsync(final String playerName, final StatsCallback callback,
            FetchPriority priority) {
        // Check cache first (with expiration)
        CachedStats cached = statsCache.get(playerName.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            LOGGER.debug("Using cached stats for {} ({} min old)", playerName, cached.getAgeMinutes());
            callback.onStatsLoaded(cached.stats);
            return;
        }

        // Check if API key is set
        if (!hasApiKey()) {
            callback.onError("No API key set. Use /bw setkey <key>");
            return;
        }

        // Run API call on background thread
        executor.execute(new PrioritizedFetchTask(priority, () -> {
            try {
                // Step 1: Get UUID from Mojang
                LOGGER.debug("Looking up UUID for: {}", playerName);
                UuidLookupResult uuidResult = resolveUUID(playerName);
                if (uuidResult.notFound) {
                    // Mojang returned HTTP 404 — the name does not resolve to any
                    // Minecraft account, which is the signature of a Hypixel nick.
                    BedwarsStats nickedStats = BedwarsStats.createNicked(playerName);
                    statsCache.put(playerName.toLowerCase(), new CachedStats(nickedStats));
                    callback.onStatsLoaded(nickedStats);
                    return;
                }
                if (uuidResult.uuid == null) {
                    callback.onError("Could not get UUID for " + playerName + " - lookup failed");
                    return;
                }
                String uuid = uuidResult.uuid;

                // Step 2: Fetch stats from Hypixel
                LOGGER.debug("Fetching Hypixel stats for UUID: {}", uuid);
                String response = fetchHypixelStats(uuid);
                if (response == null) {
                    String reason = lastFetchError != null ? lastFetchError : "unknown error";
                    callback.onError("Could not fetch Hypixel stats for " + playerName + " (" + reason + ")");
                    return;
                }

                // Step 3: Parse stats
                BedwarsStats stats = new BedwarsStats(playerName, uuid);
                stats.parseFromJson(response);

                // Cache the result with timestamp
                statsCache.put(playerName.toLowerCase(), new CachedStats(stats));

                callback.onStatsLoaded(stats);

            } catch (Exception e) {
                LOGGER.error("Exception fetching stats for {}: {}", playerName, e.getMessage(), e);
                callback.onError("Error fetching stats: " + e.getMessage());
            }
        }));
    }

    /**
     * Fetch stats asynchronously using pre-known UUID (skips Mojang lookup)
     */
    public static void fetchStatsWithUuid(final String playerName, final String uuid, final StatsCallback callback) {
        fetchStatsWithUuid(playerName, uuid, callback, FetchPriority.NORMAL);
    }

    /**
     * Fetch stats asynchronously using pre-known UUID (skips Mojang lookup) at
     * the given queue priority.
     */
    public static void fetchStatsWithUuid(final String playerName, final String uuid, final StatsCallback callback,
            FetchPriority priority) {
        // Check cache first (with expiration)
        CachedStats cached = statsCache.get(playerName.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            LOGGER.debug("Using cached stats for {} ({} min old)", playerName, cached.getAgeMinutes());
            callback.onStatsLoaded(cached.stats);
            return;
        }

        // Check if API key is set
        if (!hasApiKey()) {
            callback.onError("No API key set. Use /bw setkey <key>");
            return;
        }

        // Cache the UUID (skip nulls — ConcurrentHashMap forbids null values).
        // Not Mojang-confirmed: tab-list UUIDs are spoofed for nicked players.
        if (uuid != null) {
            recordUuidMapping(playerName, uuid, false);
        }

        // Run API call on background thread
        executor.execute(new PrioritizedFetchTask(priority, new Runnable() {
            @Override
            public void run() {
                try {
                    // Fetch stats from Hypixel directly (UUID already known)
                    LOGGER.debug("Fetching stats for {} (UUID: {})", playerName, uuid);
                    String response = fetchHypixelStats(uuid);
                    if (response == null) {
                        String reason = lastFetchError != null ? lastFetchError : "unknown error";
                        callback.onError("Could not fetch Hypixel stats for " + playerName + " (" + reason + ")");
                        return;
                    }

                    // Parse stats
                    BedwarsStats stats = new BedwarsStats(playerName, uuid);
                    stats.parseFromJson(response);

                    // Cache the result with timestamp
                    statsCache.put(playerName.toLowerCase(), new CachedStats(stats));

                    callback.onStatsLoaded(stats);

                } catch (Exception e) {
                    LOGGER.error("Exception fetching stats for {}: {}", playerName, e.getMessage(), e);
                    callback.onError("Error fetching stats: " + e.getMessage());
                }
            }
        }));
    }

    /**
     * Result of a Mojang UUID lookup. {@code notFound == true} means Mojang
     * explicitly reported the name as unknown (HTTP 404) — a definitive nick
     * signal. Other failures (timeouts, parse errors) leave {@code notFound}
     * false so transient errors do not get mistaken for nicks.
     */
    private static class UuidLookupResult {
        final String uuid;
        final boolean notFound;

        UuidLookupResult(String uuid, boolean notFound) {
            this.uuid = uuid;
            this.notFound = notFound;
        }
    }

    /**
     * Get UUID from Mojang API, distinguishing HTTP 404 from other failures.
     */
    private static UuidLookupResult resolveUUID(String playerName) {
        // Check cache
        if (uuidCache.containsKey(playerName.toLowerCase())) {
            return new UuidLookupResult(uuidCache.get(playerName.toLowerCase()), false);
        }

        try {
            URL url = new URL(MOJANG_API_URL + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                return new UuidLookupResult(null, true);
            }
            if (responseCode != 200) {
                return new UuidLookupResult(null, false);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse UUID from response using Gson
            String json = response.toString();
            JsonObject jsonObj = new JsonParser().parse(json).getAsJsonObject();

            if (!jsonObj.has("id")) {
                return new UuidLookupResult(null, false);
            }

            String uuid = jsonObj.get("id").getAsString();
            // Format UUID with dashes
            uuid = formatUUID(uuid);
            recordUuidMapping(playerName, uuid, true);

            return new UuidLookupResult(uuid, false);

        } catch (Exception e) {
            LOGGER.warn("Error getting UUID for {}: {}", playerName, e.getMessage());
            return new UuidLookupResult(null, false);
        }
    }

    /**
     * Check and update rate limit before making a request
     * Returns true if request is allowed, false if rate limited
     */
    private static boolean checkRateLimit() {
        long now = System.currentTimeMillis();

        synchronized (requestTimestamps) {
            // Remove old timestamps outside the window
            java.util.Iterator<Long> iter = requestTimestamps.iterator();
            while (iter.hasNext()) {
                if (now - iter.next() > RATE_LIMIT_WINDOW_MS) {
                    iter.remove();
                }
            }

            // Check if we're at the limit
            if (requestTimestamps.size() >= RATE_LIMIT_MAX) {
                rateLimitedRequests++;
                return false;
            }

            // Record this request
            requestTimestamps.add(now);
            return true;
        }
    }

    /**
     * Fetch player stats from Hypixel API
     */
    private static String fetchHypixelStats(String uuid) {
        if (!checkRateLimit()) {
            LOGGER.warn("Rate limited — request dropped (local throttle)");
            lastFetchError = "rate limited (local throttle)";
            rateLimitBackoffUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MS;
            return null;
        }

        try {
            String urlString = HYPIXEL_API_URL + "?uuid=" + uuid;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("API-Key", API_KEY);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 429) {
                LOGGER.warn("Hypixel API rate limit reached (429)");
                rateLimitedRequests++;
                lastFetchError = "rate limited by Hypixel (429)";
                rateLimitBackoffUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MS;
                return null;
            }
            if (responseCode == 403) {
                LOGGER.error("Hypixel API returned 403 — invalid API key. Use /bw setkey <key> to set a valid key.");
                lastFetchError = "invalid API key (403)";
                return null;
            }
            if (responseCode != 200) {
                LOGGER.warn("Hypixel API returned unexpected status: {}", responseCode);
                lastFetchError = "HTTP " + responseCode;
                return null;
            }

            lastFetchError = null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            return response.toString();

        } catch (Exception e) {
            LOGGER.error("Error fetching Hypixel stats: {}", e.getMessage());
            lastFetchError = e.getMessage();
            return null;
        }
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
     * Record a resolved name -> UUID mapping. Cheap enough for any thread — disk
     * flushes only ever happen on the executor pool (see maybeFlushUuidCache).
     * Only Mojang-confirmed mappings are stamped for persistence; UUIDs seeded
     * from the tab list can be Hypixel nick fakes and must not outlive the session.
     */
    private static void recordUuidMapping(String playerName, String uuid, boolean mojangConfirmed) {
        String key = playerName.toLowerCase();
        uuidCache.put(key, uuid);
        if (mojangConfirmed) {
            ConfirmedUuid previous = confirmedUuids.get(key);
            if (previous == null || !previous.uuid.equals(uuid)) {
                confirmedUuids.put(key, new ConfirmedUuid(uuid, System.currentTimeMillis()));
                uuidCacheDirty = true;
            }
        }
    }

    /**
     * True when the UUID for this name is already known (cached in memory or
     * loaded from disk), i.e. no Mojang round-trip is needed to fetch stats.
     */
    public static boolean hasKnownUuid(String playerName) {
        return uuidCache.containsKey(playerName.toLowerCase());
    }

    /**
     * Load the persisted UUID cache once at class init. Format is a versioned
     * root ({"version": 2, "entries": {lowercased-name: {"uuid": dashed-uuid,
     * "t": epochMs}}}); v1 bare-string entries are accepted and re-stamped as if
     * recorded now. Entries older than UUID_CACHE_TTL_MS are dropped — the name
     * may have changed hands. Anything malformed is skipped — a broken file just
     * means a cold cache.
     */
    private static void loadUuidCache() {
        try {
            File file = new File(UUID_CACHE_FILE);
            if (!file.exists()) {
                return;
            }

            FileReader reader = new FileReader(file);
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            reader.close();

            if (!root.has("entries") || !root.get("entries").isJsonObject()) {
                return;
            }

            long now = System.currentTimeMillis();
            int loaded = 0;
            for (java.util.Map.Entry<String, JsonElement> entry : root.getAsJsonObject("entries").entrySet()) {
                if (loaded >= UUID_CACHE_MAX_ENTRIES) {
                    break;
                }
                String uuid = null;
                long recordedAt = now;
                if (entry.getValue().isJsonPrimitive()) {
                    // Schema v1: bare uuid string, age unknown — expires 30 days from now.
                    uuid = entry.getValue().getAsString();
                } else if (entry.getValue().isJsonObject()) {
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    if (obj.has("uuid")) {
                        uuid = obj.get("uuid").getAsString();
                        if (obj.has("t")) {
                            recordedAt = obj.get("t").getAsLong();
                        }
                    }
                }
                if (uuid == null || now - recordedAt > UUID_CACHE_TTL_MS) {
                    continue;
                }
                String key = entry.getKey().toLowerCase();
                uuidCache.put(key, uuid);
                confirmedUuids.put(key, new ConfirmedUuid(uuid, recordedAt));
                loaded++;
            }
            LOGGER.info("Loaded {} cached UUID mappings", loaded);

        } catch (Exception e) {
            LOGGER.warn("Error loading UUID cache: {}", e.getMessage());
        }
    }

    /**
     * Debounced flush: writes at most once per UUID_CACHE_FLUSH_INTERVAL_MS and
     * only when new mappings arrived. Called from executor worker threads after
     * each fetch task and from shutdown() — never from the render thread.
     */
    static void maybeFlushUuidCache() {
        if (!uuidCacheDirty) {
            return;
        }
        if (System.currentTimeMillis() - lastUuidCacheFlush < UUID_CACHE_FLUSH_INTERVAL_MS) {
            return;
        }
        flushUuidCacheNow();
    }

    /**
     * Write the UUID cache to disk immediately (PlayerDatabase pattern: mkdirs +
     * pretty Gson). Serialized under a private lock so two pool threads never
     * interleave writes; the write goes to a temp file that is renamed into
     * place so a JVM halt mid-write can never leave a truncated uuidcache.json.
     * Only Mojang-confirmed entries (confirmedUuids) are persisted. Overflow
     * policy: past UUID_CACHE_MAX_ENTRIES, only the first N entries in
     * (arbitrary) iteration order are persisted — the simplest honest cap. In
     * practice the map is bounded by the players actually seen, so overflow is
     * a non-event.
     */
    private static void flushUuidCacheNow() {
        synchronized (uuidCacheFileLock) {
            try {
                // Clear the dirty flag before serializing: an insert racing the
                // write re-marks it and gets picked up by the next flush.
                uuidCacheDirty = false;

                File dir = new File(UUID_CACHE_DIR);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                JsonObject entries = new JsonObject();
                int written = 0;
                for (java.util.Map.Entry<String, ConfirmedUuid> entry : confirmedUuids.entrySet()) {
                    if (written >= UUID_CACHE_MAX_ENTRIES) {
                        break;
                    }
                    JsonObject obj = new JsonObject();
                    obj.addProperty("uuid", entry.getValue().uuid);
                    obj.addProperty("t", entry.getValue().recordedAt);
                    entries.add(entry.getKey(), obj);
                    written++;
                }

                JsonObject root = new JsonObject();
                root.addProperty("version", UUID_CACHE_SCHEMA_VERSION);
                root.add("entries", entries);

                File tmp = new File(UUID_CACHE_FILE + ".tmp");
                FileWriter writer = new FileWriter(tmp);
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
                writer.close();
                File target = new File(UUID_CACHE_FILE);
                if (target.exists() && !target.delete()) {
                    LOGGER.warn("Could not delete old UUID cache file before rename");
                }
                if (!tmp.renameTo(target)) {
                    LOGGER.warn("Could not move new UUID cache file into place");
                }

                lastUuidCacheFlush = System.currentTimeMillis();
                LOGGER.debug("Saved UUID cache ({} entries)", written);

            } catch (Exception e) {
                LOGGER.error("Error saving UUID cache: {}", e.getMessage());
            }
        }
    }

    /**
     * Shut down the background thread pool. Call this when the mod unloads.
     */
    public static void shutdown() {
        executor.shutdown();
        // Let in-flight fetches finish before the final flush, so a pool thread
        // can neither record a mapping after the flush (lost at JVM halt) nor be
        // mid-write inside flushUuidCacheNow when the halt kills it.
        try {
            executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Final flush so mappings learned in the last <60s are not lost.
        if (uuidCacheDirty) {
            flushUuidCacheNow();
        }
    }

    /**
     * True when a fetch error is transient (rate limiting, timeouts) and the
     * caller may sensibly re-queue the same name later. Permanent failures —
     * missing or invalid API key, malformed responses — return false so
     * one-attempt fetch paths stay one-attempt instead of retrying every scan.
     */
    public static boolean isRetryableError(String error) {
        if (error == null) {
            return false;
        }
        String lower = error.toLowerCase();
        return lower.contains("rate limited") || lower.contains("timed out");
    }

    /**
     * True while a recent rate-limit drop (local throttle or Hypixel 429) is
     * being backed off. Tab-scan paths use this to pause requeueing instead of
     * retrying at their scan cadence; explicit user lookups (/bw lookup, /bw
     * all) deliberately ignore it.
     */
    public static boolean isRateLimitBackoffActive() {
        return System.currentTimeMillis() < rateLimitBackoffUntil;
    }

    /** Test seam: the live executor queue, to pin the priority-queue wiring. */
    static java.util.concurrent.BlockingQueue<Runnable> executorQueueForTests() {
        return executor.getQueue();
    }

    /**
     * Clear the stats cache
     */
    public static void clearCache() {
        statsCache.clear();
        uuidCache.clear();
        confirmedUuids.clear();
        rateLimitedRequests = 0;
        // /bw clear is an explicit user action — rewrite the persisted UUID cache
        // right away so cleared entries do not resurrect next launch (PlayerDatabase
        // precedent: synchronous writes on the client thread for direct mutations).
        flushUuidCacheNow();
    }

    /**
     * Get cached stats for a player (or null if not cached/expired)
     */
    public static BedwarsStats getCachedStats(String playerName) {
        CachedStats cached = statsCache.get(playerName.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            return cached.stats;
        }
        return null;
    }

    /**
     * Get cache and rate limit status for /bw status command
     */
    public static String getCacheStatus() {
        int cacheSize = statsCache.size();
        int validCacheEntries = 0;
        long oldestAge = 0;

        for (CachedStats cached : statsCache.values()) {
            if (!cached.isExpired()) {
                validCacheEntries++;
            }
            long age = cached.getAgeMinutes();
            if (age > oldestAge) {
                oldestAge = age;
            }
        }

        // Clean old rate limit timestamps
        long now = System.currentTimeMillis();
        int recentRequests = 0;
        synchronized (requestTimestamps) {
            for (Long ts : requestTimestamps) {
                if (now - ts <= RATE_LIMIT_WINDOW_MS) {
                    recentRequests++;
                }
            }
        }

        return String.format(
                "Cache: %d entries (%d valid, oldest %d min)\n" +
                        "Rate: %d/%d requests in last minute\n" +
                        "Rate limited: %d requests blocked",
                cacheSize, validCacheEntries, oldestAge,
                recentRequests, RATE_LIMIT_MAX,
                rateLimitedRequests);
    }
}
