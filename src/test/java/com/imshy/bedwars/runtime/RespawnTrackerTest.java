package com.imshy.bedwars.runtime;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link RespawnTracker}: countdown math, rounding, case
 * insensitivity, expiry pruning, and clear.
 */
public class RespawnTrackerTest {

    private static final long T0 = 1_000_000L;

    private RespawnTracker tracker;

    @Before
    public void setUp() {
        tracker = new RespawnTracker();
    }

    @Test
    public void recordedDeathCountsDownFromFullDelay() {
        tracker.recordDeath("Steve", T0);
        assertEquals(RespawnTracker.RESPAWN_DELAY_MS, tracker.getRemainingMs("Steve", T0));
        assertEquals(6, tracker.getRemainingSeconds("Steve", T0)); // 5500ms rounds up
        assertEquals(3, tracker.getRemainingSeconds("Steve", T0 + 2_600));
        assertEquals(1, tracker.getRemainingSeconds("Steve", T0 + 5_400));
    }

    @Test
    public void lookupIsCaseInsensitive() {
        tracker.recordDeath("Steve", T0);
        assertTrue(tracker.getRemainingMs("sTeVe", T0 + 100) > 0);
    }

    @Test
    public void expiredTimerReportsAbsentAndIsPruned() {
        tracker.recordDeath("Steve", T0);
        assertEquals(-1, tracker.getRemainingMs("Steve", T0 + RespawnTracker.RESPAWN_DELAY_MS));
        assertTrue(tracker.isEmpty());
    }

    @Test
    public void unknownAndNullNamesReportAbsent() {
        assertEquals(-1, tracker.getRemainingMs("Nobody", T0));
        assertEquals(-1, tracker.getRemainingMs(null, T0));
        assertEquals(-1, tracker.getRemainingSeconds("Nobody", T0));
    }

    @Test
    public void rerecordedDeathRestartsTheTimer() {
        tracker.recordDeath("Steve", T0);
        tracker.recordDeath("Steve", T0 + 3_000);
        assertEquals(RespawnTracker.RESPAWN_DELAY_MS,
                tracker.getRemainingMs("Steve", T0 + 3_000));
    }

    @Test
    public void pruneDropsOnlyExpiredTimers() {
        tracker.recordDeath("Old", T0);
        tracker.recordDeath("Fresh", T0 + 5_000);
        tracker.prune(T0 + RespawnTracker.RESPAWN_DELAY_MS);
        assertFalse(tracker.isEmpty());
        assertEquals(-1, tracker.getRemainingMs("Old", T0 + RespawnTracker.RESPAWN_DELAY_MS));
        assertTrue(tracker.getRemainingMs("Fresh", T0 + RespawnTracker.RESPAWN_DELAY_MS) > 0);
    }

    @Test
    public void nullAndEmptyDeathsAreIgnored() {
        tracker.recordDeath(null, T0);
        tracker.recordDeath("", T0);
        assertTrue(tracker.isEmpty());
    }

    @Test
    public void clearDropsEverything() {
        tracker.recordDeath("Steve", T0);
        tracker.clear();
        assertTrue(tracker.isEmpty());
        assertEquals(-1, tracker.getRemainingMs("Steve", T0));
    }
}
