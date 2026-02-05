package com.imshy.bedwars;

/**
 * Estimates first-rush timing based on map baseline, enemy threat, and proximity.
 */
public class RushRiskPredictor {

    public static class Estimate {
        public final int etaSeconds;
        public final String riskLevel;

        Estimate(int etaSeconds, String riskLevel) {
            this.etaSeconds = etaSeconds;
            this.riskLevel = riskLevel;
        }
    }

    /**
     * Returns an ETA for first rush.
     */
    public static Estimate estimateFirstRush(int baseRushSeconds, double highestEnemyThreatAverage,
            double nearestEnemyDistance) {
        int threatAdjustment = 0;
        if (highestEnemyThreatAverage >= 3.2) {
            threatAdjustment = 8;
        } else if (highestEnemyThreatAverage >= 2.5) {
            threatAdjustment = 5;
        } else if (highestEnemyThreatAverage >= 1.5) {
            threatAdjustment = 2;
        }

        int distanceAdjustment = 0;
        if (nearestEnemyDistance > 0) {
            if (nearestEnemyDistance <= 28.0) {
                distanceAdjustment = 6;
            } else if (nearestEnemyDistance <= 40.0) {
                distanceAdjustment = 4;
            } else if (nearestEnemyDistance <= 56.0) {
                distanceAdjustment = 2;
            }
        }

        int etaSeconds = baseRushSeconds - threatAdjustment - distanceAdjustment;
        etaSeconds = Math.max(12, Math.min(70, etaSeconds));

        String riskLevel;
        if (etaSeconds <= 20) {
            riskLevel = "HIGH";
        } else if (etaSeconds <= 30) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        return new Estimate(etaSeconds, riskLevel);
    }
}
