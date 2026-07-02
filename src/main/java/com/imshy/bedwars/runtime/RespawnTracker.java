package com.imshy.bedwars.runtime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks respawn timers for players killed by non-final deaths. Hypixel
 * Bedwars respawns a bed-alive player a fixed ~5.5s after the death message,
 * so a death line is enough to know how long the victim is out of play.
 *
 * <p>Deliberately Minecraft-free (strings + injected timestamps) so it is
 * unit-testable, mirroring {@link KillFeedTracker}. Timers are an estimate —
 * chat latency shifts them by a few hundred ms — which is why renderers
 * prefix the countdown with "~".
 */
public class RespawnTracker {

    /** Fixed Hypixel Bedwars respawn delay after the death message. */
    public static final long RESPAWN_DELAY_MS = 5_500L;

    /** Names are keyed lowercase; values are the absolute respawn timestamp. */
    private final Map<String, Long> respawnAtMs = new HashMap<String, Long>();

    /** Records a non-final death. Final kills never respawn — don't record them. */
    public void recordDeath(String victimName, long nowMs) {
        if (victimName == null || victimName.isEmpty()) {
            return;
        }
        respawnAtMs.put(victimName.toLowerCase(java.util.Locale.ROOT), nowMs + RESPAWN_DELAY_MS);
    }

    /**
     * Milliseconds until {@code victimName} respawns, or {@code -1} when no
     * timer is active. Expired timers are pruned opportunistically.
     */
    public long getRemainingMs(String victimName, long nowMs) {
        if (victimName == null) {
            return -1;
        }
        String key = victimName.toLowerCase(java.util.Locale.ROOT);
        Long at = respawnAtMs.get(key);
        if (at == null) {
            return -1;
        }
        long remaining = at - nowMs;
        if (remaining <= 0) {
            respawnAtMs.remove(key);
            return -1;
        }
        return remaining;
    }

    /** Whole seconds remaining, rounded up, or -1 when no timer is active. */
    public int getRemainingSeconds(String victimName, long nowMs) {
        long remaining = getRemainingMs(victimName, nowMs);
        return remaining < 0 ? -1 : (int) ((remaining + 999) / 1000);
    }

    /** Drops timers that have already expired (bounded growth between matches). */
    public void prune(long nowMs) {
        Iterator<Map.Entry<String, Long>> it = respawnAtMs.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= nowMs) {
                it.remove();
            }
        }
    }

    public boolean isEmpty() {
        return respawnAtMs.isEmpty();
    }

    public void clear() {
        respawnAtMs.clear();
    }
}
