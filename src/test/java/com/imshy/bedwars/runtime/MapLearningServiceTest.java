package com.imshy.bedwars.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.imshy.bedwars.runtime.MapLearningService.BedPoint;
import com.imshy.bedwars.runtime.MapLearningService.ConsolidatedGenerator;
import com.imshy.bedwars.runtime.MapLearningService.GeneratorPoint;
import com.imshy.bedwars.runtime.MapLearningService.MapObservation;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the pure consolidation core, the observation-bounding policy, and
 * the JSON round-trip of the MapLearningService model classes. No Minecraft
 * types are involved — the core deliberately works on int triples / long keys.
 */
public class MapLearningServiceTest {

    private static MapObservation observation(GeneratorPoint... points) {
        return new MapObservation(1000L, new ArrayList<GeneratorPoint>(Arrays.asList(points)), null);
    }

    // ==================== consolidate ====================

    @Test
    public void consolidateOfNullReturnsEmpty() {
        assertTrue(MapLearningService.consolidate(null).isEmpty());
    }

    @Test
    public void consolidateOfEmptyListReturnsEmpty() {
        assertTrue(MapLearningService.consolidate(new ArrayList<MapObservation>()).isEmpty());
    }

    @Test
    public void singleObservationNeverConsolidates() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(new GeneratorPoint(10, 70, -5, true)));

        assertTrue(MapLearningService.consolidate(observations).isEmpty());
    }

    @Test
    public void pointInTwoObservationsConsolidatesWithAgreementTwo() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(new GeneratorPoint(10, 70, -5, true)));
        observations.add(observation(new GeneratorPoint(10, 70, -5, true)));

        List<ConsolidatedGenerator> result = MapLearningService.consolidate(observations);
        assertEquals(1, result.size());
        assertEquals(10, result.get(0).x);
        assertEquals(70, result.get(0).y);
        assertEquals(-5, result.get(0).z);
        assertTrue(result.get(0).diamond);
        assertEquals(2, result.get(0).agreement);
    }

    @Test
    public void duplicatePointWithinOneObservationCountsOnce() {
        GeneratorPoint point = new GeneratorPoint(3, 64, 3, false);
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(point, new GeneratorPoint(3, 64, 3, false)));

        // Two copies inside a single observation must not fake agreement.
        assertTrue(MapLearningService.consolidate(observations).isEmpty());
    }

    @Test
    public void generatorTypeIsPartOfIdentity() {
        // Same coordinates, conflicting types across observations: no agreement.
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(new GeneratorPoint(0, 80, 0, true)));
        observations.add(observation(new GeneratorPoint(0, 80, 0, false)));

        assertTrue(MapLearningService.consolidate(observations).isEmpty());
    }

    @Test
    public void exactCoordinateEqualityRequired() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(new GeneratorPoint(10, 70, -5, true)));
        observations.add(observation(new GeneratorPoint(10, 70, -6, true)));

        // Off by one block: not the same position, no consolidation.
        assertTrue(MapLearningService.consolidate(observations).isEmpty());
    }

    @Test
    public void negativeCoordinatesConsolidate() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(new GeneratorPoint(-120, 65, -340, false)));
        observations.add(observation(new GeneratorPoint(-120, 65, -340, false)));

        List<ConsolidatedGenerator> result = MapLearningService.consolidate(observations);
        assertEquals(1, result.size());
        assertEquals(-120, result.get(0).x);
        assertEquals(-340, result.get(0).z);
        assertFalse(result.get(0).diamond);
    }

    @Test
    public void agreementAccumulatesAcrossObservations() {
        GeneratorPoint stable = new GeneratorPoint(50, 78, 50, true);
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(new GeneratorPoint(50, 78, 50, true)));
        observations.add(observation(new GeneratorPoint(50, 78, 50, true), new GeneratorPoint(1, 2, 3, false)));
        observations.add(observation(new GeneratorPoint(50, 78, 50, true)));
        observations.add(observation(stable, new GeneratorPoint(9, 9, 9, false)));

        List<ConsolidatedGenerator> result = MapLearningService.consolidate(observations);
        assertEquals(1, result.size());
        assertEquals(4, result.get(0).agreement);
    }

    @Test
    public void onlyAgreedPositionsSurviveMixedObservations() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(
                new GeneratorPoint(10, 70, 10, true),
                new GeneratorPoint(20, 70, 20, false)));
        observations.add(observation(
                new GeneratorPoint(10, 70, 10, true),
                new GeneratorPoint(99, 70, 99, false)));

        List<ConsolidatedGenerator> result = MapLearningService.consolidate(observations);
        assertEquals(1, result.size());
        assertEquals(10, result.get(0).x);
        assertEquals(2, result.get(0).agreement);
    }

    @Test
    public void consolidateToleratesNullObservationsAndNullLists() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(null);
        observations.add(new MapObservation(0L, null, null));
        observations.add(observation(new GeneratorPoint(1, 2, 3, true), null));
        observations.add(observation(new GeneratorPoint(1, 2, 3, true)));

        List<ConsolidatedGenerator> result = MapLearningService.consolidate(observations);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).agreement);
    }

    @Test
    public void consolidatedOutputPreservesFirstSeenOrder() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        observations.add(observation(
                new GeneratorPoint(1, 70, 1, true),
                new GeneratorPoint(2, 70, 2, false)));
        observations.add(observation(
                new GeneratorPoint(2, 70, 2, false),
                new GeneratorPoint(1, 70, 1, true)));

        List<ConsolidatedGenerator> result = MapLearningService.consolidate(observations);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).x);
        assertEquals(2, result.get(1).x);
    }

    // ==================== packKey ====================

    @Test
    public void packKeyDistinguishesCoordinatesAndType() {
        Set<Long> keys = new HashSet<Long>();
        assertTrue(keys.add(MapLearningService.packKey(1, 2, 3, true)));
        assertTrue(keys.add(MapLearningService.packKey(1, 2, 3, false)));
        assertTrue(keys.add(MapLearningService.packKey(-1, 2, 3, true)));
        assertTrue(keys.add(MapLearningService.packKey(1, 3, 3, true)));
        assertTrue(keys.add(MapLearningService.packKey(1, 2, -3, true)));
        assertTrue(keys.add(MapLearningService.packKey(3, 2, 1, true)));
        assertEquals(6, keys.size());
    }

    @Test
    public void packKeyIsStableForEqualInputs() {
        assertEquals(
                MapLearningService.packKey(-120, 65, -340, true),
                MapLearningService.packKey(-120, 65, -340, true));
        assertNotEquals(
                MapLearningService.packKey(-120, 65, -340, true),
                MapLearningService.packKey(-120, 65, -340, false));
    }

    // ==================== appendBounded ====================

    @Test
    public void appendBoundedKeepsAllUnderTheBound() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        for (int i = 0; i < 5; i++) {
            MapLearningService.appendBounded(observations,
                    new MapObservation(i, new ArrayList<GeneratorPoint>(), null), 10);
        }
        assertEquals(5, observations.size());
        assertEquals(0L, observations.get(0).epochMs);
        assertEquals(4L, observations.get(4).epochMs);
    }

    @Test
    public void appendBoundedDropsOldestBeyondTheBound() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        for (int i = 0; i < 13; i++) {
            MapLearningService.appendBounded(observations,
                    new MapObservation(i, new ArrayList<GeneratorPoint>(), null), 10);
        }
        assertEquals(10, observations.size());
        // Oldest three (epochMs 0..2) were dropped from the front.
        assertEquals(3L, observations.get(0).epochMs);
        assertEquals(12L, observations.get(9).epochMs);
    }

    @Test
    public void appendBoundedWithBoundOfOneKeepsOnlyNewest() {
        List<MapObservation> observations = new ArrayList<MapObservation>();
        MapLearningService.appendBounded(observations, new MapObservation(1, null, null), 1);
        MapLearningService.appendBounded(observations, new MapObservation(2, null, null), 1);
        assertEquals(1, observations.size());
        assertEquals(2L, observations.get(0).epochMs);
    }

    @Test
    public void defaultBoundIsTenAndMinAgreementIsTwo() {
        // The persistence brief pins these policies; fail loudly if they drift.
        assertEquals(10, MapLearningService.MAX_OBSERVATIONS_PER_MAP);
        assertEquals(2, MapLearningService.MIN_AGREEMENT);
    }

    // ==================== JSON round-trip (gson 2.2.4 APIs) ====================

    @Test
    public void jsonRoundTripPreservesObservations() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Map<String, List<MapObservation>> maps = new HashMap<String, List<MapObservation>>();
        List<MapObservation> observations = new ArrayList<MapObservation>();
        List<GeneratorPoint> generators = new ArrayList<GeneratorPoint>();
        generators.add(new GeneratorPoint(10, 70, -5, true));
        generators.add(new GeneratorPoint(-120, 65, -340, false));
        observations.add(new MapObservation(1717171717000L, generators, new BedPoint(4, 66, 4, "c")));
        // Second observation without a bed — the field is optional.
        List<GeneratorPoint> generators2 = new ArrayList<GeneratorPoint>();
        generators2.add(new GeneratorPoint(10, 70, -5, true));
        observations.add(new MapObservation(1717171999000L, generators2, null));
        maps.put("airshow", observations);

        JsonElement tree = gson.toJsonTree(maps);
        Type mapsType = new TypeToken<Map<String, List<MapObservation>>>() {
        }.getType();
        Map<String, List<MapObservation>> reloaded = gson.fromJson(tree, mapsType);

        assertEquals(1, reloaded.size());
        List<MapObservation> reloadedObservations = reloaded.get("airshow");
        assertEquals(2, reloadedObservations.size());

        MapObservation first = reloadedObservations.get(0);
        assertEquals(1717171717000L, first.epochMs);
        assertEquals(2, first.generators.size());
        assertEquals(10, first.generators.get(0).x);
        assertEquals(70, first.generators.get(0).y);
        assertEquals(-5, first.generators.get(0).z);
        assertTrue(first.generators.get(0).diamond);
        assertEquals(-120, first.generators.get(1).x);
        assertFalse(first.generators.get(1).diamond);
        assertEquals(4, first.bed.x);
        assertEquals(66, first.bed.y);
        assertEquals(4, first.bed.z);
        assertEquals("c", first.bed.team);

        MapObservation second = reloadedObservations.get(1);
        assertNull(second.bed);
        assertEquals(1, second.generators.size());

        // The reloaded data consolidates exactly like the original.
        List<ConsolidatedGenerator> consolidated = MapLearningService.consolidate(reloadedObservations);
        assertEquals(1, consolidated.size());
        assertEquals(10, consolidated.get(0).x);
        assertEquals(2, consolidated.get(0).agreement);
    }

    @Test
    public void jsonRoundTripThroughStringForm() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        List<GeneratorPoint> generators = new ArrayList<GeneratorPoint>();
        generators.add(new GeneratorPoint(0, 100, 0, true));
        MapObservation original = new MapObservation(42L, generators, new BedPoint(-8, 65, 12, null));

        MapObservation reloaded = gson.fromJson(gson.toJson(original), MapObservation.class);
        assertEquals(42L, reloaded.epochMs);
        assertEquals(1, reloaded.generators.size());
        assertEquals(100, reloaded.generators.get(0).y);
        assertTrue(reloaded.generators.get(0).diamond);
        assertEquals(-8, reloaded.bed.x);
        assertNull(reloaded.bed.team);
    }
}
