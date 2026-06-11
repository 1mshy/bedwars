package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.PlayerDatabase;
import com.imshy.bedwars.runtime.TabListScanner.TabListPlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.List;

public class LobbyTrackerService {
    private static final Logger LOGGER = LogManager.getLogger("BedwarsStats");

    private final RuntimeState state;
    private final MatchThreatService matchThreatService;

    LobbyTrackerService(RuntimeState state, MatchThreatService matchThreatService) {
        this.state = state;
        this.matchThreatService = matchThreatService;
    }

    public void activateMatchTracking(Minecraft mc) {
        state.gamePhase = GamePhase.IN_GAME;
        state.disconnectedFromGame = false;
        state.disconnectTime = 0;
        if (mc != null && mc.theWorld != null) {
            // Baseline the world we're tracking so a respawn within the same
            // world isn't mistaken for a disconnect by onEntityJoinWorld.
            state.lastTrackedWorld =
                    new java.lang.ref.WeakReference<net.minecraft.world.World>(mc.theWorld);
        }
        state.trackedGenerators.clear();
        state.lastGeneratorScan = 0;
        state.chatDetectedPlayers.clear();
        state.chatDetectedStartTime = 0;

        // Reset lobby bait state
        state.lobbyBaitActive = false;
        state.lobbyBaitFirstSentTime = 0;
        state.lobbyBaitRetrySent = false;
        state.lobbyBaitMessageIndex = -1;

        clearRecentJoins();

        state.matchStartTime = System.currentTimeMillis();
        matchThreatService.clearBedTrackingState();

        if (mc != null && mc.thePlayer != null) {
            matchThreatService.startBedTracking(mc, state.matchStartTime);
        }

        PlayerDatabase db = PlayerDatabase.getInstance();
        db.cleanupExpiredAutoBlacklistEntries();
        db.clearCurrentGame();

        if (mc != null) {
            scanExistingPlayers(mc);
        }

        state.tabListScanPending = true;
        state.tabListScanScheduledTime = System.currentTimeMillis() + 3000;

        // Schedule the pre-game briefing after the tab list scan completes so
        // team-danger summaries have cached stats to read from.
        state.lastPreGameBriefing = null;
        state.preGameBriefingPending = ModConfig.isPreGameBriefingEnabled();
        state.preGameBriefingScheduledTime = System.currentTimeMillis() + 3500;

        LOGGER.info("Bedwars match started - stat tracking activated");

        if (state.autoplayEnabled) {
            state.autoplayCheckTime = System.currentTimeMillis() + RuntimeState.AUTOPLAY_CHECK_DELAY;
            state.autoplayPendingCheck = true;
            if (mc != null && mc.thePlayer != null) {
                state.partyMemberNames.clear();
                state.partyListPending = true;
                state.partyListRequestTime = System.currentTimeMillis();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000);
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc.thePlayer != null && state.autoplayEnabled) {
                                mc.thePlayer.sendChatMessage("/p list");
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        }
    }

    public void scanExistingPlayers(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }
            trackPlayerJoin(mc, player);
        }
    }

    public void trackPlayerJoin(Minecraft mc, EntityPlayer player) {
        if (mc == null || player == null) {
            return;
        }

        if (!ModConfig.isModEnabled()) {
            return;
        }

        final String playerName = player.getName();

        synchronized (state.recentJoins) {
            for (PlayerJoinEntry entry : state.recentJoins) {
                if (entry.playerName.equals(playerName)) {
                    entry.timestamp = System.currentTimeMillis();
                    return;
                }
            }
        }

        final PlayerJoinEntry entry = new PlayerJoinEntry(playerName, System.currentTimeMillis());

        synchronized (state.recentJoins) {
            state.recentJoins.add(entry);
        }

        final String playerUuid = player.getUniqueID().toString();

        PlayerDatabase.getInstance().addToCurrentGame(playerName);

        final PlayerDatabase db = PlayerDatabase.getInstance();
        if (db.isBlacklisted(playerName)) {
            PlayerDatabase.BlacklistEntry blEntry = db.getBlacklistEntry(playerName);
            if (ModConfig.isBlacklistAlertsEnabled() && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.DARK_RED + "⚠ BLACKLISTED: " +
                                EnumChatFormatting.RED + playerName +
                                EnumChatFormatting.GRAY + " (" + blEntry.reason + ")"));
            }
            entry.isBlacklisted = true;
        }

        if (db.hasPlayedBefore(playerName)) {
            entry.encounterCount = db.getEncounterCount(playerName);
            entry.winLossRecord = db.getWinLossRecord(playerName);
        }

        if (HypixelAPI.hasApiKey()) {
            // Players we know nothing about decide threat fastest — bump them.
            // Computed before the fetch call, which seeds the UUID cache itself.
            HypixelAPI.FetchPriority priority = (HypixelAPI.getCachedStats(playerName) == null
                    && !HypixelAPI.hasKnownUuid(playerName))
                            ? HypixelAPI.FetchPriority.UNKNOWN_PLAYER
                            : HypixelAPI.FetchPriority.NORMAL;
            HypixelAPI.fetchStatsWithUuid(playerName, playerUuid, new HypixelAPI.StatsCallback() {
                @Override
                public void onStatsLoaded(BedwarsStats stats) {
                    entry.stats = stats;
                }

                @Override
                public void onError(String error) {
                    LOGGER.warn("Stat lookup error for {}: {}", playerName, error);
                }
            }, priority);
        } else {
            LOGGER.debug("Player joined: {} (no API key set)", playerName);
        }
    }

    public void scanTabListPlayers(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || !HypixelAPI.hasApiKey()) {
            return;
        }

        List<TabListPlayer> allPlayers = TabListScanner.scanAllPlayers(mc);
        for (TabListPlayer tp : allPlayers) {
            if (tp.isLocalPlayer) {
                continue;
            }

            if (HypixelAPI.getCachedStats(tp.name) != null) {
                continue;
            }

            if (state.pendingTabListFetches.contains(tp.name)) {
                continue;
            }

            // Pause requeueing entirely while rate limited — retrying at scan
            // cadence would churn the queue without ever succeeding.
            if (HypixelAPI.isRateLimitBackoffActive()) {
                continue;
            }

            final String tabName = tp.name;
            state.pendingTabListFetches.add(tabName);
            HypixelAPI.StatsCallback callback = new HypixelAPI.StatsCallback() {
                @Override
                public void onStatsLoaded(BedwarsStats stats) { }

                @Override
                public void onError(String error) {
                    // Make the name eligible for a refetch, but only after transient
                    // failures (local/Hypixel rate limit, timeouts) — permanent ones
                    // like a bad API key would otherwise retry on every scan.
                    // pendingTabListFetches is client-thread-only state, so marshal
                    // the removal back like the chat path does.
                    if (!HypixelAPI.isRetryableError(error)) {
                        return;
                    }
                    Minecraft.getMinecraft().addScheduledTask(
                            () -> state.pendingTabListFetches.remove(tabName));
                }
            };

            HypixelAPI.FetchPriority priority = TabListScanner.resolveFetchPriority(tp);
            if (tp.uuid != null) {
                // Tab list carries the real GameProfile UUID — skip the Mojang round-trip.
                HypixelAPI.fetchStatsWithUuid(tabName, tp.uuid, callback, priority);
            } else {
                HypixelAPI.fetchStatsAsync(tabName, callback, priority);
            }
        }

        LOGGER.debug("Tab list scan: queued stats for {} players", allPlayers.size());
    }

    public void trimRecentJoins() {
        long currentTime = System.currentTimeMillis();
        synchronized (state.recentJoins) {
            Iterator<PlayerJoinEntry> iterator = state.recentJoins.iterator();
            while (iterator.hasNext()) {
                if (currentTime - iterator.next().timestamp > RuntimeState.DISPLAY_DURATION) {
                    iterator.remove();
                }
            }
        }
    }

    public void clearRecentJoins() {
        synchronized (state.recentJoins) {
            state.recentJoins.clear();
        }
    }

    static class PlayerJoinEntry {
        String playerName;
        long timestamp;
        // volatile: written on the HypixelAPI executor pool, read on the render thread.
        volatile BedwarsStats stats;
        boolean isBlacklisted = false;
        int encounterCount = 0;
        int[] winLossRecord = null;

        PlayerJoinEntry(String name, long time) {
            this.playerName = name;
            this.timestamp = time;
            this.stats = null;
        }
    }
}
