package com.imshy.bedwars;

import com.imshy.bedwars.RushRiskPredictor.Estimate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Decision-table tests for {@link RushRiskPredictor#estimateFirstRush}:
 * threat adjustment tiers (≥3.2→8s, ≥2.5→5s, ≥1.5→2s), distance adjustment
 * tiers (≤28→6s, ≤40→4s, ≤56→2s, only when distance > 0), the [12, 70] ETA
 * clamp and the HIGH/MEDIUM/LOW risk boundaries at 20s/30s.
 */
public class RushRiskPredictorTest {

    private static void assertEstimate(int expectedEta, String expectedRisk, Estimate actual) {
        assertEquals("etaSeconds", expectedEta, actual.etaSeconds);
        assertEquals("riskLevel", expectedRisk, actual.riskLevel);
    }

    @Test
    public void baselinePassesThroughWithNoAdjustments() {
        assertEstimate(40, "LOW", RushRiskPredictor.estimateFirstRush(40, 0.0, 0.0));
        assertEstimate(30, "MEDIUM", RushRiskPredictor.estimateFirstRush(30, 0.0, 0.0));
        assertEstimate(20, "HIGH", RushRiskPredictor.estimateFirstRush(20, 0.0, 0.0));
    }

    @Test
    public void threatAdjustmentTiers() {
        // base 50, no distance — isolate the threat tiers
        assertEstimate(42, "LOW", RushRiskPredictor.estimateFirstRush(50, 4.0, 0.0));  // >= 3.2 → -8
        assertEstimate(42, "LOW", RushRiskPredictor.estimateFirstRush(50, 3.2, 0.0));  // boundary
        assertEstimate(45, "LOW", RushRiskPredictor.estimateFirstRush(50, 3.19, 0.0)); // >= 2.5 → -5
        assertEstimate(45, "LOW", RushRiskPredictor.estimateFirstRush(50, 2.5, 0.0));  // boundary
        assertEstimate(48, "LOW", RushRiskPredictor.estimateFirstRush(50, 2.49, 0.0)); // >= 1.5 → -2
        assertEstimate(48, "LOW", RushRiskPredictor.estimateFirstRush(50, 1.5, 0.0));  // boundary
        assertEstimate(50, "LOW", RushRiskPredictor.estimateFirstRush(50, 1.49, 0.0)); // below → 0
    }

    @Test
    public void distanceAdjustmentTiers() {
        // base 50, no threat — isolate the distance tiers
        assertEstimate(44, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 1.0));   // <= 28 → -6
        assertEstimate(44, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 28.0));  // boundary
        assertEstimate(46, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 28.5));  // <= 40 → -4
        assertEstimate(46, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 40.0));  // boundary
        assertEstimate(48, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 40.5));  // <= 56 → -2
        assertEstimate(48, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 56.0));  // boundary
        assertEstimate(50, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 56.5));  // beyond → 0
    }

    @Test
    public void zeroOrNegativeDistanceIsIgnored() {
        // "no enemy seen" sentinel — no distance adjustment at all
        assertEstimate(50, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, 0.0));
        assertEstimate(50, "LOW", RushRiskPredictor.estimateFirstRush(50, 0.0, -10.0));
    }

    @Test
    public void etaIsClampedToFloor12() {
        assertEstimate(12, "HIGH", RushRiskPredictor.estimateFirstRush(10, 0.0, 0.0));
        // 20 - 8 - 6 = 6 → clamped up to 12
        assertEstimate(12, "HIGH", RushRiskPredictor.estimateFirstRush(20, 3.2, 28.0));
    }

    @Test
    public void etaIsClampedToCeiling70() {
        assertEstimate(70, "LOW", RushRiskPredictor.estimateFirstRush(100, 0.0, 0.0));
        // 200 - 2 - 2 = 196 → clamped down to 70
        assertEstimate(70, "LOW", RushRiskPredictor.estimateFirstRush(200, 1.5, 56.0));
    }

    @Test
    public void riskLevelBoundaries() {
        assertEstimate(20, "HIGH", RushRiskPredictor.estimateFirstRush(20, 0.0, 0.0));
        assertEstimate(21, "MEDIUM", RushRiskPredictor.estimateFirstRush(21, 0.0, 0.0));
        assertEstimate(30, "MEDIUM", RushRiskPredictor.estimateFirstRush(30, 0.0, 0.0));
        assertEstimate(31, "LOW", RushRiskPredictor.estimateFirstRush(31, 0.0, 0.0));
    }

    @Test
    public void threatAndDistanceAdjustmentsCombine() {
        // 45 - 5 (threat 2.5) - 4 (distance 40) = 36
        assertEstimate(36, "LOW", RushRiskPredictor.estimateFirstRush(45, 2.5, 40.0));
        // 35 - 8 - 6 = 21
        assertEstimate(21, "MEDIUM", RushRiskPredictor.estimateFirstRush(35, 3.2, 28.0));
        // 30 - 8 - 6 = 16
        assertEstimate(16, "HIGH", RushRiskPredictor.estimateFirstRush(30, 3.5, 20.0));
    }
}
