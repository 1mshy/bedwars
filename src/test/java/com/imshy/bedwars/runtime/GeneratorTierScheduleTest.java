package com.imshy.bedwars.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Decision-table tests for {@link GeneratorTierSchedule}: tier boundaries at
 * 60s/180s, spawn intervals 30/20/10, the per-tier spawn countdown, the next
 * tier countdown (-1 once Tier III is reached) and duration formatting.
 */
public class GeneratorTierScheduleTest {

    @Test
    public void scheduleConstants() {
        assertEquals(60, GeneratorTierSchedule.TIER_II_START_SECONDS);
        assertEquals(180, GeneratorTierSchedule.TIER_III_START_SECONDS);
        assertEquals(30, GeneratorTierSchedule.TIER_I_INTERVAL_SECONDS);
        assertEquals(20, GeneratorTierSchedule.TIER_II_INTERVAL_SECONDS);
        assertEquals(10, GeneratorTierSchedule.TIER_III_INTERVAL_SECONDS);
    }

    @Test
    public void currentTierBoundaries() {
        assertEquals(1, GeneratorTierSchedule.currentTier(0));
        assertEquals(1, GeneratorTierSchedule.currentTier(59));
        assertEquals(2, GeneratorTierSchedule.currentTier(60));
        assertEquals(2, GeneratorTierSchedule.currentTier(179));
        assertEquals(3, GeneratorTierSchedule.currentTier(180));
        assertEquals(3, GeneratorTierSchedule.currentTier(3600));
    }

    @Test
    public void currentIntervalSecondsBoundaries() {
        assertEquals(30, GeneratorTierSchedule.currentIntervalSeconds(0));
        assertEquals(30, GeneratorTierSchedule.currentIntervalSeconds(59));
        assertEquals(20, GeneratorTierSchedule.currentIntervalSeconds(60));
        assertEquals(20, GeneratorTierSchedule.currentIntervalSeconds(179));
        assertEquals(10, GeneratorTierSchedule.currentIntervalSeconds(180));
    }

    @Test
    public void secondsUntilNextSpawnInTier1() {
        // Cycle restarts at the tier start; an exact multiple means a spawn
        // just happened, so a full interval remains.
        assertEquals(30, GeneratorTierSchedule.secondsUntilNextSpawn(0));
        assertEquals(29, GeneratorTierSchedule.secondsUntilNextSpawn(1));
        assertEquals(1, GeneratorTierSchedule.secondsUntilNextSpawn(29));
        assertEquals(30, GeneratorTierSchedule.secondsUntilNextSpawn(30));
        assertEquals(1, GeneratorTierSchedule.secondsUntilNextSpawn(59));
    }

    @Test
    public void secondsUntilNextSpawnInTier2() {
        // A brand-new Tier II does not immediately spawn — full 20s remain.
        assertEquals(20, GeneratorTierSchedule.secondsUntilNextSpawn(60));
        assertEquals(19, GeneratorTierSchedule.secondsUntilNextSpawn(61));
        assertEquals(1, GeneratorTierSchedule.secondsUntilNextSpawn(79));
        assertEquals(20, GeneratorTierSchedule.secondsUntilNextSpawn(80));
        assertEquals(1, GeneratorTierSchedule.secondsUntilNextSpawn(179));
    }

    @Test
    public void secondsUntilNextSpawnInTier3() {
        assertEquals(10, GeneratorTierSchedule.secondsUntilNextSpawn(180));
        assertEquals(5, GeneratorTierSchedule.secondsUntilNextSpawn(185));
        assertEquals(1, GeneratorTierSchedule.secondsUntilNextSpawn(189));
        assertEquals(10, GeneratorTierSchedule.secondsUntilNextSpawn(190));
    }

    @Test
    public void secondsUntilNextSpawnClampsNegativeInput() {
        assertEquals(30, GeneratorTierSchedule.secondsUntilNextSpawn(-5));
    }

    @Test
    public void secondsUntilNextTierBoundaries() {
        assertEquals(60, GeneratorTierSchedule.secondsUntilNextTier(0));
        assertEquals(1, GeneratorTierSchedule.secondsUntilNextTier(59));
        assertEquals(120, GeneratorTierSchedule.secondsUntilNextTier(60));
        assertEquals(1, GeneratorTierSchedule.secondsUntilNextTier(179));
        // No boundary past Tier III
        assertEquals(-1, GeneratorTierSchedule.secondsUntilNextTier(180));
        assertEquals(-1, GeneratorTierSchedule.secondsUntilNextTier(999));
    }

    @Test
    public void formatDurationFormats() {
        assertEquals("-", GeneratorTierSchedule.formatDuration(-1));
        assertEquals("0s", GeneratorTierSchedule.formatDuration(0));
        assertEquals("5s", GeneratorTierSchedule.formatDuration(5));
        assertEquals("59s", GeneratorTierSchedule.formatDuration(59));
        assertEquals("1:00", GeneratorTierSchedule.formatDuration(60));
        assertEquals("1:01", GeneratorTierSchedule.formatDuration(61));
        assertEquals("1:10", GeneratorTierSchedule.formatDuration(70));
        assertEquals("2:05", GeneratorTierSchedule.formatDuration(125));
        assertEquals("10:00", GeneratorTierSchedule.formatDuration(600));
        assertEquals("59:59", GeneratorTierSchedule.formatDuration(3599));
        assertEquals("60:00", GeneratorTierSchedule.formatDuration(3600));
    }

    @Test
    public void elapsedSecondsFromMatchStartGuards() {
        assertEquals(0, GeneratorTierSchedule.elapsedSecondsFromMatchStart(0L));
        assertEquals(0, GeneratorTierSchedule.elapsedSecondsFromMatchStart(-1L));
        // Future timestamps yield 0 rather than negative elapsed time
        assertEquals(0, GeneratorTierSchedule.elapsedSecondsFromMatchStart(
                System.currentTimeMillis() + 60_000L));
    }

    @Test
    public void elapsedSecondsFromMatchStartMeasuresWallClock() {
        // Wall-clock based, so assert with a tolerance window instead of equality.
        int elapsed = GeneratorTierSchedule.elapsedSecondsFromMatchStart(
                System.currentTimeMillis() - 10_200L);
        assertTrue("expected ~10s, got " + elapsed, elapsed >= 10 && elapsed <= 11);
    }
}
