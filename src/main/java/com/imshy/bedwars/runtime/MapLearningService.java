package com.imshy.bedwars.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.imshy.bedwars.JsonFileUtil;
import com.imshy.bedwars.MapMetadataRegistry;
import com.imshy.bedwars.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Self-building map registry: learns generator (and own-bed) layouts from the
 * matches the user actually plays and persists them to JSON.
 *
 * LEARN + PERSIST + EXPOSE only — learned coordinates are deliberately NOT fed
 * back into live generator tracking, because Hypixel may offset/rotate map
 * instances and absolute-coordinate replay is unverified. Consumers read the
 * consolidated data (e.g. {@code /bw maps}) instead.
 *
 * Timing is the critical constraint: {@code state.trackedGenerators} is wiped
 * on every client tick where the phase is not IN_GAME, so the final snapshot
 * of a match must be captured inside the WIN/LOSS/leave chat handlers (and
 * {@code resetToBootState}) BEFORE the phase flips and the next tick clears
 * the map. Disk writes happen only at those phase transitions, on the client
 * thread — never per-scan (mirrors the PlayerDatabase persistence pattern).
 */
public class MapLearningService {

    private static final String DATA_DIR = "config/bedwarsstats";
    private static final String DATA_FILE = DATA_DIR + "/learnedmaps.json";
    private static final Logger LOGGER = LogManager.getLogger("BedwarsStats");

    /** Oldest observations are dropped once a map has this many. */
    static final int MAX_OBSERVATIONS_PER_MAP = 10;

    /** A position must appear in at least this many observations to be consolidated. */
    static final int MIN_AGREEMENT = 2;

    /** Sidebar map-name detection retry cadence while the name is still unknown. */
    static final long MAP_NAME_RETRY_INTERVAL_MS = 5000;

    /** In-memory generator/bed snapshot cadence during IN_GAME. */
    static final long SNAPSHOT_INTERVAL_MS = 30000;

    private final RuntimeState state;
    private final Gson gson;

    /** Learned data keyed by trim().toLowerCase() map name (the registry convention). */
    private Map<String, List<MapObservation>> learnedMaps = new HashMap<String, List<MapObservation>>();
    private boolean loaded = false;

    // --- Per-match working state (discarded at match start, committed at match end) ---
    private String pendingMapName = null;
    private MapObservation pendingObservation = null;
    private long lastMapNameAttempt = 0;
    private long lastSnapshotTime = 0;

    MapLearningService(RuntimeState state) {
        this.state = state;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    // ==================== MATCH LIFECYCLE ====================

    /**
     * Discards any leftover per-match state (e.g. from a match abandoned via
     * a mid-game disconnect that never produced an end-of-match chat trigger).
     */
    public void onMatchStart() {
        clearMatchState();
    }

    /**
     * Timer-driven entry point, called from BedwarsRuntime.onClientTick while
     * IN_GAME and connected. Retries sidebar map-name detection every ~5s
     * until it succeeds, and snapshots the tracked generators every ~30s.
     */
    public void onInGameTick(Minecraft mc, long currentTime) {
        if (!ModConfig.isModEnabled() || !ModConfig.isMapLearningEnabled()) {
            return;
        }

        // Slow retry: the sidebar's "Map: ..." row can take a while to appear.
        // Mirrors MatchThreatService.checkRushRiskPredictor's write so the
        // existing state.lastDetectedMapName semantics are preserved.
        if (pendingMapName == null && currentTime - lastMapNameAttempt >= MAP_NAME_RETRY_INTERVAL_MS) {
            lastMapNameAttempt = currentTime;
            String detectedMap = MapMetadataRegistry.detectCurrentMapName(mc);
            if (detectedMap != null && !detectedMap.trim().isEmpty()) {
                pendingMapName = detectedMap.trim();
                state.lastDetectedMapName = detectedMap.trim();
            }
        }

        // Periodic in-memory snapshot. The first one waits a full interval so
        // the generator scan has had time to populate. The richest snapshot of
        // the match is kept: trackedGenerators evicts entries the player walks
        // away from, so a later, sparser snapshot must not overwrite a fuller
        // earlier one (the layout itself never changes mid-match).
        if (lastSnapshotTime == 0) {
            lastSnapshotTime = currentTime;
        } else if (currentTime - lastSnapshotTime >= SNAPSHOT_INTERVAL_MS) {
            lastSnapshotTime = currentTime;
            MapObservation snapshot = snapshotCurrentMatch(mc);
            if (pendingObservation == null
                    || snapshot.generators.size() > pendingObservation.generators.size()) {
                pendingObservation = snapshot;
            }
        }
    }

    /**
     * Captures the final snapshot and persists the match's observation. MUST
     * be invoked at the phase-transition moment (WIN/LOSS/leave handlers,
     * resetToBootState) while state.trackedGenerators is still populated —
     * the next non-IN_GAME client tick wipes it.
     */
    public void onMatchEnd(Minecraft mc) {
        if (!ModConfig.isMapLearningEnabled()) {
            clearMatchState();
            return;
        }

        MapObservation finalSnapshot = snapshotCurrentMatch(mc);

        // Same richest-wins rule as the periodic pass: distance eviction may
        // have thinned trackedGenerators by the time the match ends.
        MapObservation observation = finalSnapshot;
        if (pendingObservation != null
                && pendingObservation.generators.size() > finalSnapshot.generators.size()) {
            observation = pendingObservation;
        }

        String mapName = pendingMapName;
        if (mapName == null
                && state.lastDetectedMapName != null
                && !"Unknown".equals(state.lastDetectedMapName)) {
            // The rush predictor may have detected the map before we did.
            mapName = state.lastDetectedMapName;
        }

        clearMatchState();

        // Zero-generator observations carry no layout information — skip.
        if (mapName == null || mapName.trim().isEmpty() || observation.generators.isEmpty()) {
            return;
        }

        ensureLoaded();

        String key = mapName.trim().toLowerCase();
        List<MapObservation> observations = learnedMaps.get(key);
        if (observations == null) {
            observations = new ArrayList<MapObservation>();
            learnedMaps.put(key, observations);
        }
        appendBounded(observations, observation, MAX_OBSERVATIONS_PER_MAP);
        save();

        LOGGER.info("Learned map observation for '{}': {} generators ({} observations total)",
                key, observation.generators.size(), observations.size());
    }

    /**
     * Builds an observation from the live runtime state: tracked generator
     * anchors plus the own-team bed (real located bed blocks only — the
     * spawn-based fallback position is not an actual bed and is never stored).
     */
    private MapObservation snapshotCurrentMatch(Minecraft mc) {
        MapObservation observation = new MapObservation();
        observation.epochMs = System.currentTimeMillis();
        observation.generators = new ArrayList<GeneratorPoint>();

        for (Map.Entry<BlockPos, WorldScanService.GeneratorEntry> entry : state.trackedGenerators.entrySet()) {
            BlockPos pos = entry.getKey();
            observation.generators.add(new GeneratorPoint(
                    pos.getX(), pos.getY(), pos.getZ(), entry.getValue().isDiamond));
        }

        if (!state.playerBedBlocks.isEmpty()) {
            BlockPos bedPos = state.playerBedBlocks.get(0);
            BedPoint bed = new BedPoint(bedPos.getX(), bedPos.getY(), bedPos.getZ(), null);
            Character colorCode = TabListScanner.getLocalPlayerColorCode(mc);
            if (colorCode != null) {
                bed.team = colorCode.toString();
            }
            observation.bed = bed;
        }

        return observation;
    }

    private void clearMatchState() {
        pendingMapName = null;
        pendingObservation = null;
        lastMapNameAttempt = 0;
        lastSnapshotTime = 0;
    }

    // ==================== QUERY API ====================

    /**
     * Summaries of all learned maps, sorted by name.
     */
    public List<LearnedMapSummary> getLearnedMaps() {
        ensureLoaded();

        List<LearnedMapSummary> summaries = new ArrayList<LearnedMapSummary>();
        for (Map.Entry<String, List<MapObservation>> entry : learnedMaps.entrySet()) {
            List<MapObservation> observations = entry.getValue();
            long lastSeen = 0;
            for (MapObservation observation : observations) {
                if (observation.epochMs > lastSeen) {
                    lastSeen = observation.epochMs;
                }
            }
            summaries.add(new LearnedMapSummary(
                    entry.getKey(),
                    observations.size(),
                    consolidate(observations).size(),
                    lastSeen));
        }

        Collections.sort(summaries, new Comparator<LearnedMapSummary>() {
            @Override
            public int compare(LearnedMapSummary a, LearnedMapSummary b) {
                return a.name.compareTo(b.name);
            }
        });
        return summaries;
    }

    /**
     * Consolidated generator positions for a map: only positions confirmed by
     * agreement across at least {@link #MIN_AGREEMENT} stored observations.
     */
    public List<ConsolidatedGenerator> getConsolidated(String mapName) {
        ensureLoaded();

        if (mapName == null) {
            return new ArrayList<ConsolidatedGenerator>();
        }
        List<MapObservation> observations = learnedMaps.get(mapName.trim().toLowerCase());
        return consolidate(observations);
    }

    // ==================== PURE CONSOLIDATION CORE ====================
    // Deliberately MC-type-free (int triples / long keys) so it is unit-testable.

    /**
     * Returns the generator positions that appear — by exact coordinate AND
     * type equality — in at least {@link #MIN_AGREEMENT} of the given
     * observations. A position is counted at most once per observation.
     * Output preserves first-seen order; agreement is the confidence signal.
     */
    static List<ConsolidatedGenerator> consolidate(List<MapObservation> observations) {
        List<ConsolidatedGenerator> result = new ArrayList<ConsolidatedGenerator>();
        if (observations == null) {
            return result;
        }

        Map<Long, ConsolidatedGenerator> byKey = new LinkedHashMap<Long, ConsolidatedGenerator>();
        for (MapObservation observation : observations) {
            if (observation == null || observation.generators == null) {
                continue;
            }
            Set<Long> seenThisObservation = new HashSet<Long>();
            for (GeneratorPoint point : observation.generators) {
                if (point == null) {
                    continue;
                }
                long key = packKey(point.x, point.y, point.z, point.diamond);
                if (!seenThisObservation.add(key)) {
                    continue; // duplicate within one observation — count once
                }
                ConsolidatedGenerator consolidated = byKey.get(key);
                if (consolidated == null) {
                    consolidated = new ConsolidatedGenerator(point.x, point.y, point.z, point.diamond);
                    byKey.put(key, consolidated);
                }
                consolidated.agreement++;
            }
        }

        for (ConsolidatedGenerator consolidated : byKey.values()) {
            if (consolidated.agreement >= MIN_AGREEMENT) {
                result.add(consolidated);
            }
        }
        return result;
    }

    /**
     * Packs position + generator type into one long for agreement counting:
     * x and z get 24 bits each, y gets 15, the type flag gets bit 0. Injective
     * for |x|,|z| &lt; 2^23 and 0 &lt;= y &lt; 2^15 — far beyond any Hypixel
     * Bedwars build area. Never unpacked; coordinates ride in the value object.
     */
    static long packKey(int x, int y, int z, boolean diamond) {
        return ((long) (x & 0xFFFFFF) << 40)
                | ((long) (z & 0xFFFFFF) << 16)
                | ((long) (y & 0x7FFF) << 1)
                | (diamond ? 1L : 0L);
    }

    /**
     * Appends an observation and drops the oldest (front of the list) until
     * the bound holds.
     */
    static void appendBounded(List<MapObservation> observations, MapObservation observation, int max) {
        observations.add(observation);
        while (observations.size() > max) {
            observations.remove(0);
        }
    }

    // ==================== PERSISTENCE ====================

    /** Load on first access — boot stays I/O-free until the feature is touched. */
    private void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    private void load() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            LOGGER.info("No learned map data found, starting fresh");
            return;
        }

        try {
            FileReader reader = new FileReader(file);
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            reader.close();

            if (root.has("maps")) {
                Type mapsType = new TypeToken<Map<String, List<MapObservation>>>() {
                }.getType();
                learnedMaps = gson.fromJson(root.get("maps"), mapsType);
                if (learnedMaps == null) {
                    learnedMaps = new HashMap<String, List<MapObservation>>();
                }
            }

            normalizeLoadedData();

            LOGGER.info("Loaded learned map data: {} maps", learnedMaps.size());

        } catch (Exception e) {
            LOGGER.error("Error loading learned map data: {}", e.getMessage());
            File quarantined = JsonFileUtil.quarantineCorrupt(file);
            if (quarantined != null) {
                LOGGER.error("Corrupt learned map data moved to {} — starting fresh", quarantined.getName());
            }
        }
    }

    private void save() {
        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            JsonObject root = new JsonObject();
            root.add("maps", gson.toJsonTree(learnedMaps));

            // Temp-file + atomic move so a crash mid-write can never truncate
            // the data file (the old delete-then-rename left a no-file window).
            // Pretty printing is kept deliberately: the file is small (bounded
            // observations) and meant to be human-editable.
            JsonFileUtil.writeAtomic(new File(DATA_FILE), gson, root);

            LOGGER.debug("Saved learned map data");

        } catch (Exception e) {
            LOGGER.error("Error saving learned map data: {}", e.getMessage());
        }
    }

    /** Drops malformed entries from a hand-edited/corrupt file and re-applies the bound. */
    private void normalizeLoadedData() {
        Iterator<Map.Entry<String, List<MapObservation>>> mapIterator = learnedMaps.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<String, List<MapObservation>> entry = mapIterator.next();
            List<MapObservation> observations = entry.getValue();
            if (entry.getKey() == null || entry.getKey().trim().isEmpty() || observations == null) {
                mapIterator.remove();
                continue;
            }

            Iterator<MapObservation> observationIterator = observations.iterator();
            while (observationIterator.hasNext()) {
                MapObservation observation = observationIterator.next();
                if (observation == null || observation.generators == null
                        || observation.generators.isEmpty()) {
                    observationIterator.remove();
                }
            }
            while (observations.size() > MAX_OBSERVATIONS_PER_MAP) {
                observations.remove(0);
            }

            if (observations.isEmpty()) {
                mapIterator.remove();
            }
        }
    }

    // ==================== MODEL CLASSES (pure — no MC types) ====================

    /** One persisted match observation: generator anchors + optional own bed. */
    public static class MapObservation {
        public long epochMs;
        public List<GeneratorPoint> generators;
        public BedPoint bed;

        public MapObservation() {
        }

        public MapObservation(long epochMs, List<GeneratorPoint> generators, BedPoint bed) {
            this.epochMs = epochMs;
            this.generators = generators;
            this.bed = bed;
        }
    }

    /** A tracked generator anchor position. */
    public static class GeneratorPoint {
        public int x;
        public int y;
        public int z;
        public boolean diamond;

        public GeneratorPoint() {
        }

        public GeneratorPoint(int x, int y, int z, boolean diamond) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.diamond = diamond;
        }
    }

    /** Own-team bed block position with the local team colour code, when known. */
    public static class BedPoint {
        public int x;
        public int y;
        public int z;
        public String team;

        public BedPoint() {
        }

        public BedPoint(int x, int y, int z, String team) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.team = team;
        }
    }

    /** A generator position confirmed by cross-observation agreement. */
    public static class ConsolidatedGenerator {
        public final int x;
        public final int y;
        public final int z;
        public final boolean diamond;
        /** Number of observations the position appeared in (>= MIN_AGREEMENT). */
        public int agreement;

        ConsolidatedGenerator(int x, int y, int z, boolean diamond) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.diamond = diamond;
            this.agreement = 0;
        }
    }

    /** Per-map roll-up for the {@code /bw maps} listing. */
    public static class LearnedMapSummary {
        public final String name;
        public final int observationCount;
        public final int consolidatedGenerators;
        public final long lastSeenEpochMs;

        LearnedMapSummary(String name, int observationCount, int consolidatedGenerators, long lastSeenEpochMs) {
            this.name = name;
            this.observationCount = observationCount;
            this.consolidatedGenerators = consolidatedGenerators;
            this.lastSeenEpochMs = lastSeenEpochMs;
        }
    }
}
