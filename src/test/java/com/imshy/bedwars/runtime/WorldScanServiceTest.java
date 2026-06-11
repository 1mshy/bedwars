package com.imshy.bedwars.runtime;

import net.minecraft.util.BlockPos;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the pure radius-suppression helpers in {@link WorldScanService}: among same-type
 * generator anchors within GENERATOR_MERGE_RADIUS blocks (Chebyshev), exactly one — the
 * lex-greatest (x, then z, then y) — survives, deterministically. This is the rule that keeps
 * a decorated generator (same-type blocks at another Y, or with a 1-block gap) from rendering
 * two nametags and double-counting in the HUD summary.
 */
public class WorldScanServiceTest {

    // ---- chebyshevDistance ----

    @Test
    public void chebyshevDistanceIsZeroForSamePos() {
        assertEquals(0, WorldScanService.chebyshevDistance(
                new BlockPos(10, 65, -20), new BlockPos(10, 65, -20)));
    }

    @Test
    public void chebyshevDistanceTakesMaxAxis() {
        assertEquals(3, WorldScanService.chebyshevDistance(
                new BlockPos(0, 65, 0), new BlockPos(1, 67, 3)));
        assertEquals(5, WorldScanService.chebyshevDistance(
                new BlockPos(0, 70, 0), new BlockPos(-5, 68, 2)));
    }

    @Test
    public void chebyshevDistanceCountsVerticalOffset() {
        assertEquals(2, WorldScanService.chebyshevDistance(
                new BlockPos(100, 65, 200), new BlockPos(100, 67, 200)));
    }

    // ---- resolveSuppressionWinner ----

    @Test
    public void isolatedAnchorWinsItself() {
        BlockPos anchor = new BlockPos(100, 65, 200);
        assertEquals(anchor, WorldScanService.resolveSuppressionWinner(
                anchor, Collections.singletonList(anchor)));
    }

    @Test
    public void farApartAnchorsAllWinThemselves() {
        // Realistic map layout: four diamond generators on distant islands.
        BlockPos a = new BlockPos(80, 65, 0);
        BlockPos b = new BlockPos(-80, 65, 0);
        BlockPos c = new BlockPos(0, 65, 80);
        BlockPos d = new BlockPos(0, 65, -80);
        List<BlockPos> pool = Arrays.asList(a, b, c, d);
        assertEquals(a, WorldScanService.resolveSuppressionWinner(a, pool));
        assertEquals(b, WorldScanService.resolveSuppressionWinner(b, pool));
        assertEquals(c, WorldScanService.resolveSuppressionWinner(c, pool));
        assertEquals(d, WorldScanService.resolveSuppressionWinner(d, pool));
    }

    @Test
    public void duplicateAtDifferentYIsSuppressed() {
        // The reported bug: a same-type decorative block above/below the pad is not reachable
        // by the same-Y cluster BFS but sits within the merge radius. Same x/z, so the
        // lex-greatest y wins.
        BlockPos pad = new BlockPos(100, 65, 200);
        BlockPos decoration = new BlockPos(100, 67, 200);
        List<BlockPos> pool = Arrays.asList(pad, decoration);
        assertEquals(decoration, WorldScanService.resolveSuppressionWinner(pad, pool));
        assertEquals(decoration, WorldScanService.resolveSuppressionWinner(decoration, pool));
    }

    @Test
    public void duplicateAcrossOneBlockGapIsSuppressed() {
        // A 1-block air gap breaks the adjacency BFS, but both anchors are within the radius.
        BlockPos a = new BlockPos(100, 65, 200);
        BlockPos b = new BlockPos(102, 65, 200);
        List<BlockPos> pool = Arrays.asList(a, b);
        assertEquals(b, WorldScanService.resolveSuppressionWinner(a, pool));
        assertEquals(b, WorldScanService.resolveSuppressionWinner(b, pool));
    }

    @Test
    public void radiusBoundaryIsInclusiveAtThreeBlocks() {
        BlockPos a = new BlockPos(0, 65, 0);
        BlockPos b = new BlockPos(WorldScanService.GENERATOR_MERGE_RADIUS, 65, 0);
        List<BlockPos> pool = Arrays.asList(a, b);
        assertEquals(b, WorldScanService.resolveSuppressionWinner(a, pool));
    }

    @Test
    public void anchorsFourBlocksApartBothSurvive() {
        BlockPos a = new BlockPos(0, 65, 0);
        BlockPos b = new BlockPos(4, 65, 0);
        List<BlockPos> pool = Arrays.asList(a, b);
        assertEquals(a, WorldScanService.resolveSuppressionWinner(a, pool));
        assertEquals(b, WorldScanService.resolveSuppressionWinner(b, pool));
    }

    @Test
    public void radiusIsChebyshevNotEuclidean() {
        // (3,3,3) offset: Chebyshev distance 3 (suppressed) even though Euclidean is ~5.2.
        BlockPos a = new BlockPos(0, 65, 0);
        BlockPos b = new BlockPos(3, 68, 3);
        List<BlockPos> pool = Arrays.asList(a, b);
        assertEquals(b, WorldScanService.resolveSuppressionWinner(a, pool));
        assertEquals(b, WorldScanService.resolveSuppressionWinner(b, pool));
    }

    @Test
    public void lexOrderPrefersXThenZThenY() {
        // x dominates regardless of z/y (pairs kept within the merge radius).
        BlockPos lowX = new BlockPos(5, 66, 9);
        BlockPos highX = new BlockPos(6, 64, 7);
        assertEquals(highX, WorldScanService.resolveSuppressionWinner(
                lowX, Arrays.asList(lowX, highX)));

        // Equal x: z decides.
        BlockPos lowZ = new BlockPos(5, 67, 3);
        BlockPos highZ = new BlockPos(5, 65, 4);
        assertEquals(highZ, WorldScanService.resolveSuppressionWinner(
                lowZ, Arrays.asList(lowZ, highZ)));

        // Equal x and z: y decides.
        BlockPos lowY = new BlockPos(5, 64, 3);
        BlockPos highY = new BlockPos(5, 66, 3);
        assertEquals(highY, WorldScanService.resolveSuppressionWinner(
                lowY, Arrays.asList(lowY, highY)));
    }

    @Test
    public void chainedSuppressionResolvesToFinalSurvivor() {
        // a is within 3 of b, b within 3 of c, but a is 6 from c. Resolution hops the chain:
        // every anchor maps to c, the only survivor, so merged state never lands on an entry
        // that is itself about to be suppressed.
        BlockPos a = new BlockPos(0, 65, 0);
        BlockPos b = new BlockPos(3, 65, 0);
        BlockPos c = new BlockPos(6, 65, 0);
        List<BlockPos> pool = Arrays.asList(a, b, c);
        assertEquals(c, WorldScanService.resolveSuppressionWinner(a, pool));
        assertEquals(c, WorldScanService.resolveSuppressionWinner(b, pool));
        assertEquals(c, WorldScanService.resolveSuppressionWinner(c, pool));
    }

    @Test
    public void winnerIsIndependentOfPoolIterationOrder() {
        // The previous label-flapping regression came from add/evict disagreement; the winner
        // must not depend on how the anchor pool happens to be ordered.
        BlockPos a = new BlockPos(0, 65, 0);
        BlockPos b = new BlockPos(2, 67, 1);
        BlockPos c = new BlockPos(3, 64, -2);
        List<BlockPos> pool = new ArrayList<BlockPos>(Arrays.asList(a, b, c));

        BlockPos expectedForA = WorldScanService.resolveSuppressionWinner(a, pool);
        BlockPos expectedForB = WorldScanService.resolveSuppressionWinner(b, pool);

        Collections.reverse(pool);
        assertEquals(expectedForA, WorldScanService.resolveSuppressionWinner(a, pool));
        assertEquals(expectedForB, WorldScanService.resolveSuppressionWinner(b, pool));

        Collections.swap(pool, 0, 1);
        assertEquals(expectedForA, WorldScanService.resolveSuppressionWinner(a, pool));
        assertEquals(expectedForB, WorldScanService.resolveSuppressionWinner(b, pool));
    }

    @Test
    public void poolNotContainingPosStillResolvesToNearbyWinner() {
        // Eviction can resolve a tracked anchor against a pool built from a fresh scan.
        BlockPos tracked = new BlockPos(0, 65, 0);
        BlockPos observed = new BlockPos(2, 65, 0);
        assertEquals(observed, WorldScanService.resolveSuppressionWinner(
                tracked, Collections.singletonList(observed)));
    }
}
