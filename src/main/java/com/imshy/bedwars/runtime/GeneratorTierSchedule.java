package com.imshy.bedwars.runtime;

/**
 * Static helper describing Hypixel Bedwars' standard diamond/emerald generator
 * tier schedule. The numbers are the widely-used community baselines — they
 * match stock Hypixel timings closely enough to surface actionable ETAs even
 * though the real server tweaks them occasionally.
 *
 * Schedule:
 *   Tier I:   0s – 60s,   one spawn every 30s
 *   Tier II:  60s – 180s, one spawn every 20s
 *   Tier III: 180s+,      one spawn every 10s
 */
public final class GeneratorTierSchedule {

    public static final int TIER_II_START_SECONDS = 60;
    public static final int TIER_III_START_SECONDS = 180;

    public static final int TIER_I_INTERVAL_SECONDS = 30;
    public static final int TIER_II_INTERVAL_SECONDS = 20;
    public static final int TIER_III_INTERVAL_SECONDS = 10;

    private GeneratorTierSchedule() {
    }

    /**
     * Tier index (1, 2, or 3) for the given match elapsed time in seconds.
     */
    public static int currentTier(int elapsedSeconds) {
        if (elapsedSeconds < TIER_II_START_SECONDS) {
            return 1;
        }
        if (elapsedSeconds < TIER_III_START_SECONDS) {
            return 2;
        }
        return 3;
    }

    /**
     * Returns the spawn interval (seconds) for the current tier at {@code elapsedSeconds}.
     */
    public static int currentIntervalSeconds(int elapsedSeconds) {
        switch (currentTier(elapsedSeconds)) {
            case 1:  return TIER_I_INTERVAL_SECONDS;
            case 2:  return TIER_II_INTERVAL_SECONDS;
            default: return TIER_III_INTERVAL_SECONDS;
        }
    }

    /**
     * Seconds until the next spawn at the current tier. Each tier's cycle is
     * considered to restart at its boundary (so a brand-new Tier II does not
     * immediately spawn a new resource).
     */
    public static int secondsUntilNextSpawn(int elapsedSeconds) {
        if (elapsedSeconds < 0) {
            elapsedSeconds = 0;
        }
        int tier = currentTier(elapsedSeconds);
        int tierStart;
        int interval;
        switch (tier) {
            case 1:
                tierStart = 0;
                interval = TIER_I_INTERVAL_SECONDS;
                break;
            case 2:
                tierStart = TIER_II_START_SECONDS;
                interval = TIER_II_INTERVAL_SECONDS;
                break;
            default:
                tierStart = TIER_III_START_SECONDS;
                interval = TIER_III_INTERVAL_SECONDS;
                break;
        }
        int elapsedInTier = elapsedSeconds - tierStart;
        int remainder = elapsedInTier % interval;
        int untilNext = interval - remainder;
        if (untilNext <= 0) {
            untilNext = interval;
        }
        return untilNext;
    }

    /**
     * Seconds until the next tier starts. Returns -1 once the match has
     * reached Tier III (no further tier boundary).
     */
    public static int secondsUntilNextTier(int elapsedSeconds) {
        if (elapsedSeconds < TIER_II_START_SECONDS) {
            return TIER_II_START_SECONDS - elapsedSeconds;
        }
        if (elapsedSeconds < TIER_III_START_SECONDS) {
            return TIER_III_START_SECONDS - elapsedSeconds;
        }
        return -1;
    }

    /**
     * Formats a second count as {@code "Xs"} when < 60 and {@code "M:SS"} otherwise.
     */
    public static String formatDuration(int seconds) {
        if (seconds < 0) {
            return "-";
        }
        if (seconds < 60) {
            return seconds + "s";
        }
        int mins = seconds / 60;
        int rem = seconds % 60;
        return mins + ":" + (rem < 10 ? "0" + rem : Integer.toString(rem));
    }

    /**
     * Derives elapsed seconds from the {@code matchStartTime} timestamp in
     * {@link RuntimeState} (millis since epoch). Returns 0 if not started.
     */
    public static int elapsedSecondsFromMatchStart(long matchStartTimeMs) {
        if (matchStartTimeMs <= 0L) {
            return 0;
        }
        long elapsedMs = System.currentTimeMillis() - matchStartTimeMs;
        if (elapsedMs <= 0L) {
            return 0;
        }
        return (int) (elapsedMs / 1000L);
    }
}
