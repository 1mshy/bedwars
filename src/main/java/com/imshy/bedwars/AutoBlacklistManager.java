package com.imshy.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles automatic blacklist additions after repeated losses.
 */
public class AutoBlacklistManager {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /**
     * Evaluate current-loss opponents and auto-blacklist eligible players.
     */
    public static List<String> processLossOutcome(PlayerDatabase db) {
        List<String> autoAddedPlayers = new ArrayList<String>();
        if (db == null || !ModConfig.isAutoBlacklistEnabled()) {
            return autoAddedPlayers;
        }

        int lossThreshold = ModConfig.getAutoBlacklistLossThreshold();
        int lookbackDays = ModConfig.getAutoBlacklistLookbackDays();
        int cooldownDays = ModConfig.getAutoBlacklistCooldownDays();
        int expiryDays = ModConfig.getAutoBlacklistExpiryDays();

        if (lossThreshold <= 0 || lookbackDays <= 0) {
            return autoAddedPlayers;
        }

        long cooldownMs = cooldownDays > 0 ? cooldownDays * DAY_MS : 0L;
        long now = System.currentTimeMillis();

        Set<String> currentPlayers = db.getCurrentGamePlayersSnapshot();
        for (String playerKey : currentPlayers) {
            if (playerKey == null || playerKey.trim().isEmpty()) {
                continue;
            }

            if (db.isManualBlacklistEntry(playerKey)) {
                continue;
            }

            PlayerDatabase.BlacklistEntry existing = db.getBlacklistEntry(playerKey);
            if (existing != null && existing.isAuto() && cooldownMs > 0L) {
                long lastAutoAddAt = existing.lastAutoAddAt > 0 ? existing.lastAutoAddAt : existing.addedAt;
                if (lastAutoAddAt > 0 && (now - lastAutoAddAt) < cooldownMs) {
                    continue;
                }
            }

            int recentLosses = db.countRecentOutcomes(playerKey, PlayerDatabase.GameOutcome.LOSS, lookbackDays);
            if (recentLosses < lossThreshold) {
                continue;
            }

            String reason = String.format("Auto-blacklisted: %d losses in %d days", recentLosses, lookbackDays);
            if (db.addOrRefreshAutoBlacklist(playerKey, reason, expiryDays)) {
                autoAddedPlayers.add(playerKey + " (" + recentLosses + "L)");
            }
        }

        return autoAddedPlayers;
    }
}
