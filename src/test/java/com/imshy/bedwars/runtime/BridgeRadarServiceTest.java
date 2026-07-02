package com.imshy.bedwars.runtime;

import com.imshy.bedwars.runtime.BridgeRadarService.BridgeAlert;
import com.imshy.bedwars.runtime.BridgeRadarService.Cluster;

import net.minecraft.util.BlockPos;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the pure geometry of {@link BridgeRadarService}: cluster
 * analysis (straightness, extent, approach) and compass octants.
 */
public class BridgeRadarServiceTest {

    private static final long T0 = 1_000_000L;

    /** A straight bridge along +X, one block per 250ms, starting at origin. */
    private static Cluster straightBridge(int length) {
        Cluster c = new Cluster();
        for (int i = 0; i < length; i++) {
            c.points.add(new BlockChangeFeed.BlockChangeEvent(
                    new BlockPos(i, 80, 0), false, T0 + i * 250L));
        }
        c.lastAt = T0 + (length - 1) * 250L;
        return c;
    }

    @Test
    public void detectsStraightRunAimedAtTarget() {
        Cluster c = straightBridge(12);
        // Target 30 blocks ahead of the bridge head, dead on the line.
        BridgeAlert alert = BridgeRadarService.analyzeCluster(c, 41.5, 0.5);
        assertNotNull(alert);
        assertTrue(alert.distanceBlocks > 25 && alert.distanceBlocks < 35);
    }

    @Test
    public void directionIsFromDefendedPointTowardBridgeHead() {
        Cluster c = straightBridge(12);
        BridgeAlert alert = BridgeRadarService.analyzeCluster(c, 41.5, 0.5);
        assertNotNull(alert);
        // Head at x=11, target at x=41.5: the bridge approaches from the West.
        assertEquals("W", alert.direction);
    }

    @Test
    public void ignoresShortRuns() {
        assertNull(BridgeRadarService.analyzeCluster(straightBridge(4), 40, 0));
    }

    @Test
    public void ignoresRunsAimedAway() {
        Cluster c = straightBridge(12);
        // Target BEHIND the run (negative X).
        assertNull(BridgeRadarService.analyzeCluster(c, -30, 0));
    }

    @Test
    public void ignoresRunsMissingTheTarget() {
        Cluster c = straightBridge(12);
        // Target far to the side of the line.
        assertNull(BridgeRadarService.analyzeCluster(c, 41.5, 40));
    }

    @Test
    public void ignoresScatteredNoise() {
        Cluster c = new Cluster();
        int[][] pts = {{0, 0}, {2, 3}, {1, -3}, {4, 2}, {3, -4}, {6, 3}, {5, -2}, {8, 4}};
        for (int i = 0; i < pts.length; i++) {
            c.points.add(new BlockChangeFeed.BlockChangeEvent(
                    new BlockPos(pts[i][0], 80, pts[i][1]), false, T0 + i * 250L));
        }
        assertNull(BridgeRadarService.analyzeCluster(c, 40, 0));
    }

    @Test
    public void etaComesFromObservedBuildSpeed() {
        Cluster c = straightBridge(12); // 11 blocks in 2750ms = 4 blocks/s
        BridgeAlert alert = BridgeRadarService.analyzeCluster(c, 41.5, 0.5);
        assertNotNull(alert);
        // ~30 blocks at 4 blocks/s ≈ 7-8s.
        assertTrue("eta was " + alert.etaSeconds, alert.etaSeconds >= 6 && alert.etaSeconds <= 9);
    }

    @Test
    public void octants() {
        assertEquals("N", BridgeRadarService.octantFrom(0, 0, 0, -10));
        assertEquals("S", BridgeRadarService.octantFrom(0, 0, 0, 10));
        assertEquals("E", BridgeRadarService.octantFrom(0, 0, 10, 0));
        assertEquals("W", BridgeRadarService.octantFrom(0, 0, -10, 0));
        assertEquals("NE", BridgeRadarService.octantFrom(0, 0, 10, -10));
        assertEquals("SW", BridgeRadarService.octantFrom(0, 0, -10, 10));
        assertEquals("", BridgeRadarService.octantFrom(0, 0, 0, 0));
    }
}
