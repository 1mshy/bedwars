package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.PlayerDatabase;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Iterator;

public class LobbyTrackerService {
    private final RuntimeState state;
    private final MatchThreatService matchThreatService;

    LobbyTrackerService(RuntimeState state, MatchThreatService matchThreatService) {
        this.state = state;
        this.matchThreatService = matchThreatService;
    }

    public void activateBedwarsLobbyTracking(Minecraft mc) {
        state.inBedwarsLobby = true;
        state.disconnectedFromGame = false;
        state.disconnectTime = 0;
        state.trackedGenerators.clear();
        state.lastGeneratorScan = 0;
        state.chatDetectedPlayers.clear();

        clearRecentJoins();

        state.gameStartTime = System.currentTimeMillis();
        matchThreatService.clearBedTrackingState();

        if (mc != null && mc.thePlayer != null) {
            matchThreatService.startBedTracking(mc, state.gameStartTime);
        }

        PlayerDatabase db = PlayerDatabase.getInstance();
        db.cleanupExpiredAutoBlacklistEntries();
        db.clearCurrentGame();

        if (mc != null) {
            scanExistingPlayers(mc);
        }

        System.out.println("[BedwarsStats] Entered Bedwars lobby - stat tracking activated!");

        if (state.autoplayEnabled) {
            state.autoplayCheckTime = System.currentTimeMillis() + RuntimeState.AUTOPLAY_CHECK_DELAY;
            state.autoplayPendingCheck = true;
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[Autoplay] " +
                                EnumChatFormatting.YELLOW + "Checking lobby for threats in 5 seconds..."));

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

            if (ModConfig.isHistoryAlertsEnabled() && mc.thePlayer != null) {
                int wins = entry.winLossRecord[0];
                int losses = entry.winLossRecord[1];
                String recordColor;
                if (wins > losses) {
                    recordColor = EnumChatFormatting.GREEN.toString();
                } else if (losses > wins) {
                    recordColor = EnumChatFormatting.RED.toString();
                } else {
                    recordColor = EnumChatFormatting.YELLOW.toString();
                }

                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.AQUA + "📜 HISTORY: " +
                                EnumChatFormatting.WHITE + playerName +
                                EnumChatFormatting.GRAY + " - Played " + entry.encounterCount + "x | Record: " +
                                recordColor + wins + "W-" + losses + "L"));
            }
        }

        if (HypixelAPI.hasApiKey()) {
            HypixelAPI.fetchStatsWithUuid(playerName, playerUuid, new HypixelAPI.StatsCallback() {
                @Override
                public void onStatsLoaded(BedwarsStats stats) {
                    entry.stats = stats;

                    BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
                    if (threat == BedwarsStats.ThreatLevel.EXTREME) {
                        AudioCueManager.playCue(Minecraft.getMinecraft(), AudioCueManager.CueType.EXTREME_PLAYER_JOIN);
                    }

                    if (threat == BedwarsStats.ThreatLevel.MEDIUM ||
                            threat == BedwarsStats.ThreatLevel.HIGH ||
                            threat == BedwarsStats.ThreatLevel.EXTREME) {

                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc.thePlayer != null) {
                            String threatColor = stats.getThreatColor();
                            mc.thePlayer.addChatMessage(new ChatComponentText(
                                    EnumChatFormatting.GREEN + "[BW] " +
                                            threatColor + playerName + " " +
                                            stats.getDisplayString()));
                        }
                    }
                }

                @Override
                public void onError(String error) {
                    System.out.println("[BedwarsStats] Error: " + error);
                }
            });
        } else {
            System.out.println("[BedwarsStats] Player joined: " + playerName + " (No API key set)");
        }
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
        BedwarsStats stats;
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
