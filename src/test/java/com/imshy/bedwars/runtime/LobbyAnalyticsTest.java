package com.imshy.bedwars.runtime;

import com.imshy.bedwars.runtime.LobbyAnalytics.LobbySummary;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link LobbyAnalytics#summarize}: averaging, tiering thresholds,
 * percentile math and the minimum-sample guard.
 */
public class LobbyAnalyticsTest {

    private static List<double[]> samples(double[]... entries) {
        return new ArrayList<double[]>(Arrays.asList(entries));
    }

    @Test
    public void averagesAndCounts() {
        LobbySummary s = LobbyAnalytics.summarize(
                samples(new double[]{100, 1.0}, new double[]{300, 3.0}), null, 8);
        assertNotNull(s);
        assertEquals(2, s.knownPlayers);
        assertEquals(8, s.totalPlayers);
        assertEquals(200.0, s.avgStars, 0.001);
        assertEquals(2.0, s.avgFkdr, 0.001);
        assertEquals(-1, s.ownPercentile);
    }

    @Test
    public void tierBoundaries() {
        assertEquals("Chill", LobbyAnalytics.summarize(
                samples(new double[]{50, 1.0}, new double[]{50, 1.0}), null, 2).tier);
        assertEquals("Average", LobbyAnalytics.summarize(
                samples(new double[]{50, 1.5}, new double[]{50, 1.5}), null, 2).tier);
        assertEquals("Sweaty", LobbyAnalytics.summarize(
                samples(new double[]{50, 3.5}, new double[]{50, 2.5}), null, 2).tier);
    }

    @Test
    public void percentileCountsPlayersBelowOwnFkdr() {
        double[] own = {200, 2.0};
        List<double[]> known = samples(
                new double[]{100, 1.0}, new double[]{150, 1.5},
                own, new double[]{400, 4.0});
        LobbySummary s = LobbyAnalytics.summarize(known, own, 4);
        assertEquals(50, s.ownPercentile); // 2 of 4 below own FKDR
    }

    @Test
    public void requiresAtLeastTwoKnownPlayers() {
        assertNull(LobbyAnalytics.summarize(samples(new double[]{100, 1.0}), null, 16));
        assertNull(LobbyAnalytics.summarize(samples(), null, 16));
    }
}
