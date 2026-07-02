package com.imshy.bedwars.command;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.ClientThread;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.PlayerDatabase;
import com.imshy.bedwars.runtime.BedwarsRuntime;
import com.imshy.bedwars.runtime.GamePhase;
import com.imshy.bedwars.runtime.MapLearningService;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class BedwarsCommand extends CommandBase {
    private final BedwarsRuntime runtime;

    public BedwarsCommand(BedwarsRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public String getCommandName() {
        return "bw";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bw <setkey|lookup|all|info|autoplay|afk|rejoin|blacklist|history|status|clear|reset|disable|enable|pearlpreview|nametags|maps|edithud> [args]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sendMessage(sender, EnumChatFormatting.GOLD + "=== BedwarsStats Commands ===");
            sendMessage(sender, "/bw setkey <key> - Set your Hypixel API key");
            sendMessage(sender, "/bw lookup <player> - Look up a player's stats");
            sendMessage(sender, "/bw all - Check stats for everyone in the lobby");
            sendMessage(sender, "/bw info - Show threats and history players in lobby");
            sendMessage(sender, "/bw autoplay <ones|twos|threes|fours|stop|requeue> - Auto-queue until safe lobby");
            sendMessage(sender, "/bw afk - Toggle anti-kick movement (strafe left/right every 60s)");
            sendMessage(sender, "/bw rejoin - Re-run game-start setup (bed tracking, generators, player scan)");
            sendMessage(sender, "/bw blacklist <add|remove|list> [player] [reason] - Manage blacklist");
            sendMessage(sender, "/bw history [player] - View encounter history");
            sendMessage(sender, "/bw status - Show cache and rate limit info");
            sendMessage(sender, "/bw clear - Clear the stats cache");
            sendMessage(sender, "/bw reset - Reset all HUD and runtime state (like a fresh boot)");
            sendMessage(sender, "/bw disable - Disable all automatic features (stat lookup, alerts, HUD, audio)");
            sendMessage(sender, "/bw enable - Re-enable all automatic features");
            sendMessage(sender, "/bw pearlpreview - Toggle ender pearl trajectory preview");
            sendMessage(sender, "/bw nametags - Toggle in-world nametags above player heads");
            sendMessage(sender, "/bw maps - List map layouts learned from played games");
            sendMessage(sender, "/bw edithud - Open the drag-and-drop HUD layout editor");
            sendMessage(sender, EnumChatFormatting.GRAY + "Tactical features (toggle in config GUI):");
            sendMessage(sender, EnumChatFormatting.GRAY + "  pre-game briefing, generator countdown, enemy loadout row,");
            sendMessage(sender, EnumChatFormatting.GRAY + "  final-kill feed, ender-pearl trajectory overlay");
            return;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("setkey")) {
            if (args.length < 2) {
                sendMessage(sender, EnumChatFormatting.RED + "Usage: /bw setkey <apikey>");
                return;
            }
            ModConfig.setApiKey(args[1]);
            sendMessage(sender, EnumChatFormatting.GREEN + "API key saved. Validating...");
            validateApiKey();

        } else if (subCommand.equals("lookup")) {
            if (args.length < 2) {
                sendMessage(sender, EnumChatFormatting.RED + "Usage: /bw lookup <playername>");
                return;
            }
            final String targetPlayer = args[1];
            sendMessage(sender, EnumChatFormatting.YELLOW + "Looking up " + targetPlayer + "...");

            HypixelAPI.fetchStatsAsync(targetPlayer, ClientThread.marshal(new HypixelAPI.StatsCallback() {
                @Override
                public void onStatsLoaded(BedwarsStats stats) {
                    // marshal(): this whole body runs on the client thread, so it is
                    // safe to touch the chat GUI directly.
                    Minecraft client = Minecraft.getMinecraft();
                    if (client.thePlayer != null) {
                        client.thePlayer.addChatMessage(new ChatComponentText(buildLookupReport(targetPlayer, stats)));
                    }
                }

                @Override
                public void onError(String error) {
                    Minecraft client = Minecraft.getMinecraft();
                    if (client.thePlayer != null) {
                        client.thePlayer.addChatMessage(new ChatComponentText(
                                EnumChatFormatting.RED + "Error: " + error));
                    }
                }
            }), HypixelAPI.FetchPriority.EXPLICIT);

        } else if (subCommand.equals("all")) {
            if (!HypixelAPI.hasApiKey()) {
                sendMessage(sender, EnumChatFormatting.RED + "No API key set. Use /bw setkey <key>");
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null) {
                sendMessage(sender, EnumChatFormatting.RED + "You must be in a world to use this command.");
                return;
            }

            List<EntityPlayer> players = mc.theWorld.playerEntities;
            int count = 0;

            sendMessage(sender, EnumChatFormatting.GOLD + "=== Checking all players in lobby ===");

            for (EntityPlayer player : players) {
                if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                    continue;
                }

                final String playerName = player.getName();
                final String playerUuid = player.getUniqueID().toString();
                count++;

                HypixelAPI.fetchStatsWithUuid(playerName, playerUuid, ClientThread.marshal(new HypixelAPI.StatsCallback() {

                    @Override
                    public void onStatsLoaded(BedwarsStats stats) {
                        // marshal(): on the client thread — safe to touch the chat GUI.
                        Minecraft client = Minecraft.getMinecraft();
                        if (client.thePlayer != null) {
                            client.thePlayer.addChatMessage(new ChatComponentText(
                                    stats.getThreatColor() + playerName + " " +
                                    EnumChatFormatting.WHITE + "[" + stats.getStars() + "⭐] " +
                                    EnumChatFormatting.YELLOW + BedwarsStats.formatRatio(stats.getFkdr())
                                    + " FKDR " +
                                    EnumChatFormatting.GRAY + "(" + stats.getThreatLevel().name() + ")"));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Minecraft client = Minecraft.getMinecraft();
                        if (client.thePlayer != null) {
                            client.thePlayer.addChatMessage(new ChatComponentText(
                                    EnumChatFormatting.RED + playerName + ": " + error));
                        }
                    }
                }), HypixelAPI.FetchPriority.EXPLICIT);
            }

            if (count == 0) {
                sendMessage(sender, EnumChatFormatting.YELLOW + "No other players found in the lobby.");
            } else {
                sendMessage(sender, EnumChatFormatting.YELLOW + "Looking up " + count + " players...");
            }

        } else if (subCommand.equals("info")) {
            handleInfoCommand(sender);

        } else if (subCommand.equals("blacklist")) {
            handleBlacklistCommand(sender, args);

        } else if (subCommand.equals("history")) {
            handleHistoryCommand(sender, args);

        } else if (subCommand.equals("status")) {
            sendMessage(sender, EnumChatFormatting.GOLD + "=== BedwarsStats Status ===");
            sendMessage(sender, HypixelAPI.getCacheStatus());

            PlayerDatabase db = PlayerDatabase.getInstance();

            sendMessage(sender, String.format("Blacklist: %d players", db.getBlacklistSize()));
            sendMessage(sender, String.format("History: %d unique players", db.getHistorySize()));
            sendMessage(sender, "Game phase: " + runtime.getGamePhase().name());
            sendMessage(sender, "Rush predictor: " + (ModConfig.isRushPredictorEnabled() ? "Enabled" : "Disabled"));
            sendMessage(sender, "Mod: " + (ModConfig.isModEnabled() ? EnumChatFormatting.GREEN + "Enabled" : EnumChatFormatting.RED + "Disabled"));
            if (ModConfig.isRushPredictorEnabled()) {
                int eta = runtime.getLastPredictedRushEtaSeconds();
                String etaText = eta > 0 ? (eta + "s") : "N/A";
                sendMessage(sender, "Predicted first rush: " + etaText + " | Map: " + runtime.getLastDetectedMapName());
            }

        } else if (subCommand.equals("clear")) {
            HypixelAPI.clearCache();
            runtime.clearRecentJoins();
            runtime.clearPendingTabListFetches();
            sendMessage(sender, EnumChatFormatting.GREEN + "Cache cleared!");

        } else if (subCommand.equals("reset")) {
            runtime.resetToBootState();
            sendMessage(sender, EnumChatFormatting.GREEN + "Mod state reset! HUD and runtime cleared.");

        } else if (subCommand.equals("disable")) {
            ModConfig.setModEnabled(false);
            sendMessage(sender, EnumChatFormatting.GREEN + "Mod disabled. All automatic features are off. Use /bw enable to re-enable.");

        } else if (subCommand.equals("enable")) {
            ModConfig.setModEnabled(true);
            sendMessage(sender, EnumChatFormatting.GREEN + "Mod enabled. All automatic features are active.");

        } else if (subCommand.equals("autoplay")) {
            handleAutoplayCommand(sender, args);

        } else if (subCommand.equals("afk")) {
            boolean enabled = runtime.toggleAfk();
            if (enabled) {
                sendMessage(sender, EnumChatFormatting.GOLD + "[BW] " +
                        EnumChatFormatting.GREEN + "AFK mode enabled — moving every 60 seconds.");
            } else {
                sendMessage(sender, EnumChatFormatting.GOLD + "[BW] " +
                        EnumChatFormatting.RED + "AFK mode disabled.");
            }

        } else if (subCommand.equals("rejoin")) {
            handleRejoinCommand(sender);

        } else if (subCommand.equals("pearlpreview")) {
            boolean enabled = !ModConfig.isEnderPearlPreviewEnabled();
            ModConfig.setEnderPearlPreviewEnabled(enabled);
            if (enabled) {
                sendMessage(sender, EnumChatFormatting.GOLD + "[BW] " +
                        EnumChatFormatting.GREEN + "Ender pearl trajectory preview enabled.");
            } else {
                sendMessage(sender, EnumChatFormatting.GOLD + "[BW] " +
                        EnumChatFormatting.RED + "Ender pearl trajectory preview disabled.");
            }

        } else if (subCommand.equals("nametags")) {
            boolean enabled = !ModConfig.isNameTagsEnabled();
            ModConfig.setNameTagsEnabled(enabled);
            if (enabled) {
                sendMessage(sender, EnumChatFormatting.GOLD + "[BW] " +
                        EnumChatFormatting.GREEN + "Nametags enabled.");
            } else {
                sendMessage(sender, EnumChatFormatting.GOLD + "[BW] " +
                        EnumChatFormatting.RED + "Nametags disabled.");
            }

        } else if (subCommand.equals("maps")) {
            handleMapsCommand(sender);

        } else if (subCommand.equals("edithud")) {
            // Vanilla closes the chat screen right after command execution
            // (and addScheduledTask runs synchronously on the client thread),
            // which would kill a screen opened here — defer to the tick loop.
            runtime.requestHudEditorOpen();
            sendMessage(sender, EnumChatFormatting.GOLD + "[BW] " +
                    EnumChatFormatting.GREEN + "Opening HUD editor...");

        } else {
            sendMessage(sender, EnumChatFormatting.RED + "Unknown command. Use /bw for help.");
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args,
            net.minecraft.util.BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "setkey", "lookup", "all", "info", "autoplay",
                    "afk", "rejoin", "blacklist", "history",
                    "status", "clear", "reset", "disable", "enable", "pearlpreview", "nametags", "maps",
                    "edithud");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("lookup") || subCommand.equals("history")) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld != null) {
                    List<String> playerNames = new ArrayList<String>();
                    for (EntityPlayer player : mc.theWorld.playerEntities) {
                        playerNames.add(player.getName());
                    }
                    return getListOfStringsMatchingLastWord(args, playerNames.toArray(new String[0]));
                }
            }

            if (subCommand.equals("blacklist")) {
                return getListOfStringsMatchingLastWord(args, "add", "remove", "list");
            }

            if (subCommand.equals("autoplay")) {
                return getListOfStringsMatchingLastWord(args, runtime.getAutoplayModeSuggestions());
            }
        }

        if (args.length == 3 && args[0].toLowerCase().equals("blacklist")) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null) {
                List<String> playerNames = new ArrayList<String>();
                for (EntityPlayer player : mc.theWorld.playerEntities) {
                    playerNames.add(player.getName());
                }
                return getListOfStringsMatchingLastWord(args, playerNames.toArray(new String[0]));
            }
        }

        return null;
    }

    private void handleInfoCommand(ICommandSender sender) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            sendMessage(sender, EnumChatFormatting.RED + "You must be in a world to use this command.");
            return;
        }

        PlayerDatabase db = PlayerDatabase.getInstance();
        List<EntityPlayer> players = mc.theWorld.playerEntities;

        List<String> threats = new ArrayList<String>();
        List<String> historyPlayers = new ArrayList<String>();
        List<String> teamDangerLines = ModConfig.isTeamDangerSummaryEnabled()
                ? runtime.buildTeamDangerLines(mc)
                : new ArrayList<String>();

        sendMessage(sender, EnumChatFormatting.GOLD + "=== Lobby Info ===");

        for (EntityPlayer player : players) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }

            String playerName = player.getName();

            BedwarsStats stats = HypixelAPI.getCachedStats(playerName);
            if (stats != null && stats.isLoaded()) {
                BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
                if (threat == BedwarsStats.ThreatLevel.MEDIUM ||
                        threat == BedwarsStats.ThreatLevel.HIGH ||
                        threat == BedwarsStats.ThreatLevel.EXTREME) {
                    String threatInfo = stats.getThreatColor() + playerName + " " +
                            EnumChatFormatting.WHITE + "[" + stats.getStars() + "⭐] " +
                            EnumChatFormatting.YELLOW + String.format("%.2f", stats.getFkdr()) + " FKDR " +
                            EnumChatFormatting.GRAY + "(" + threat.name() + ")";
                    threats.add(threatInfo);
                }
            }

            if (db.hasPlayedBefore(playerName)) {
                int[] record = db.getWinLossRecord(playerName);
                int wins = record[0];
                int losses = record[1];
                int encounters = db.getEncounterCount(playerName);

                String recordColor;
                if (wins > losses) {
                    recordColor = EnumChatFormatting.GREEN.toString();
                } else if (losses > wins) {
                    recordColor = EnumChatFormatting.RED.toString();
                } else {
                    recordColor = EnumChatFormatting.YELLOW.toString();
                }

                String historyInfo = EnumChatFormatting.AQUA + playerName +
                        EnumChatFormatting.GRAY + " - Played " + encounters + "x | Record: " +
                        recordColor + wins + "W-" + losses + "L";
                historyPlayers.add(historyInfo);
            }
        }

        if (!threats.isEmpty()) {
            sendMessage(sender, EnumChatFormatting.RED + "⚠ Threats (" + threats.size() + "):");
            for (String threat : threats) {
                sendMessage(sender, "  " + threat);
            }
        } else {
            sendMessage(sender, EnumChatFormatting.GREEN + "✓ No threats detected");
        }

        if (!teamDangerLines.isEmpty()) {
            sendMessage(sender, EnumChatFormatting.RED + "Team Danger:");
            for (String line : teamDangerLines) {
                sendMessage(sender, "  " + line);
            }
        } else {
            sendMessage(sender, EnumChatFormatting.GRAY + "Team danger summary unavailable (teams not assigned)");
        }

        if (!historyPlayers.isEmpty()) {
            sendMessage(sender, EnumChatFormatting.AQUA + "📜 History Players (" + historyPlayers.size() + "):");
            for (String historyPlayer : historyPlayers) {
                sendMessage(sender, "  " + historyPlayer);
            }
        } else {
            sendMessage(sender, EnumChatFormatting.GRAY + "No previously encountered players");
        }

        int total = players.size() - 1;
        sendMessage(sender, EnumChatFormatting.GRAY + "Total players in lobby: " + total);
    }

    private void handleBlacklistCommand(ICommandSender sender, String[] args) {
        PlayerDatabase db = PlayerDatabase.getInstance();

        if (args.length < 2) {
            sendMessage(sender,
                    EnumChatFormatting.RED + "Usage: /bw blacklist <add|remove|list> [player] [reason]");
            return;
        }

        String action = args[1].toLowerCase();

        if (action.equals("add")) {
            if (args.length < 3) {
                sendMessage(sender, EnumChatFormatting.RED + "Usage: /bw blacklist add <player> [reason]");
                return;
            }
            String playerName = args[2];
            String reason = args.length > 3 ? joinArgs(args, 3) : "No reason specified";
            db.addToBlacklist(playerName, reason);
            sendMessage(sender, EnumChatFormatting.GREEN + "Added " + playerName + " to blacklist: " + reason);

        } else if (action.equals("remove")) {
            if (args.length < 3) {
                sendMessage(sender, EnumChatFormatting.RED + "Usage: /bw blacklist remove <player>");
                return;
            }
            String playerName = args[2];
            if (db.removeFromBlacklist(playerName)) {
                sendMessage(sender, EnumChatFormatting.GREEN + "Removed " + playerName + " from blacklist");
            } else {
                sendMessage(sender, EnumChatFormatting.RED + playerName + " is not on the blacklist");
            }

        } else if (action.equals("list")) {
            java.util.Collection<PlayerDatabase.BlacklistEntry> entries = db.getBlacklistedPlayers();
            if (entries.isEmpty()) {
                sendMessage(sender, EnumChatFormatting.YELLOW + "Blacklist is empty");
            } else {
                sendMessage(sender,
                        EnumChatFormatting.GOLD + "=== Blacklisted Players (" + entries.size() + ") ===");
                long now = System.currentTimeMillis();
                for (PlayerDatabase.BlacklistEntry entry : entries) {
                    long daysAgo = (now - entry.addedAt) / (1000 * 60 * 60 * 24);
                    String sourceLabel = entry.isAuto() ? "AUTO" : "MANUAL";
                    String sourceColor = entry.isAuto()
                            ? EnumChatFormatting.LIGHT_PURPLE.toString()
                            : EnumChatFormatting.AQUA.toString();

                    String expiryInfo = "";
                    if (entry.isAuto() && entry.expiresAt > 0) {
                        long daysLeft = Math.max(0L, (entry.expiresAt - now) / (1000 * 60 * 60 * 24));
                        expiryInfo = EnumChatFormatting.GRAY + " | expires in " + daysLeft + "d";
                    }

                    sendMessage(sender, sourceColor + "[" + sourceLabel + "] " +
                            EnumChatFormatting.RED + entry.playerName +
                            EnumChatFormatting.GRAY + " - " + entry.reason +
                            " (" + daysAgo + " days ago)" + expiryInfo);
                }
            }

        } else {
            sendMessage(sender, EnumChatFormatting.RED + "Unknown blacklist action. Use: add, remove, list");
        }
    }

    private void handleHistoryCommand(ICommandSender sender, String[] args) {
        PlayerDatabase db = PlayerDatabase.getInstance();

        if (args.length < 2) {
            sendMessage(sender, EnumChatFormatting.GOLD + "=== Encounter History ===");
            sendMessage(sender, "Total unique players: " + db.getHistorySize());
            sendMessage(sender, "Use /bw history <player> for details");
            return;
        }

        String playerName = args[1];
        java.util.List<PlayerDatabase.EncounterEntry> encounters = db.getEncounterHistory(playerName);

        if (encounters.isEmpty()) {
            sendMessage(sender, EnumChatFormatting.YELLOW + "No encounter history for " + playerName);
            return;
        }

        int[] record = db.getWinLossRecord(playerName);
        sendMessage(sender, EnumChatFormatting.GOLD + "=== History: " + playerName + " ===");
        sendMessage(sender, String.format("Games: %d | Record: %d-%d (W-L)",
                encounters.size(), record[0], record[1]));

        int showCount = Math.min(5, encounters.size());
        sendMessage(sender, EnumChatFormatting.GRAY + "Last " + showCount + " games:");
        for (int i = encounters.size() - 1; i >= Math.max(0, encounters.size() - 5); i--) {
            PlayerDatabase.EncounterEntry e = encounters.get(i);
            long daysAgo = (System.currentTimeMillis() - e.timestamp) / (1000 * 60 * 60 * 24);
            String outcomeColor = e.outcome == PlayerDatabase.GameOutcome.WIN ? EnumChatFormatting.GREEN.toString()
                    : (e.outcome == PlayerDatabase.GameOutcome.LOSS ? EnumChatFormatting.RED.toString()
                            : EnumChatFormatting.GRAY.toString());
            sendMessage(sender,
                    outcomeColor + e.outcome.name() +
                            EnumChatFormatting.GRAY + " (" + daysAgo + " days ago)");
        }
    }

    private void handleAutoplayCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, EnumChatFormatting.RED + "Usage: /bw autoplay <ones|twos|threes|fours|stop|requeue>");
            sendMessage(sender,
                    EnumChatFormatting.GRAY + "Autoplay will auto-queue until finding a lobby without threats.");
            sendMessage(sender,
                    EnumChatFormatting.GRAY + "Current max threat level: " + ModConfig.getAutoplayMaxThreatLevel());
            sendMessage(sender,
                    EnumChatFormatting.GRAY + "Requeue on threats: " +
                            (ModConfig.isAutoplayRequeueEnabled()
                                    ? EnumChatFormatting.GREEN + "Enabled"
                                    : EnumChatFormatting.RED + "Disabled"));
            return;
        }

        String mode = args[1].toLowerCase();

        if (mode.equals("stop")) {
            if (runtime.isAutoplayEnabled()) {
                runtime.stopAutoplay();
                sendMessage(sender, EnumChatFormatting.GOLD + "[Autoplay] " +
                        EnumChatFormatting.RED + "Autoplay stopped.");
            } else {
                sendMessage(sender, EnumChatFormatting.YELLOW + "Autoplay is not currently running.");
            }
            return;
        }

        if (mode.equals("requeue")) {
            boolean newValue = !ModConfig.isAutoplayRequeueEnabled();
            ModConfig.setAutoplayRequeueEnabled(newValue);
            sendMessage(sender, EnumChatFormatting.GOLD + "[Autoplay] " +
                    EnumChatFormatting.YELLOW + "Requeue on threats: " +
                    (newValue ? EnumChatFormatting.GREEN + "Enabled" : EnumChatFormatting.RED + "Disabled"));
            return;
        }

        if (!runtime.isValidAutoplayMode(mode)) {
            sendMessage(sender, EnumChatFormatting.RED + "Invalid mode. Use: ones, twos, threes, fours, or stop");
            return;
        }

        if (!HypixelAPI.hasApiKey()) {
            sendMessage(sender, EnumChatFormatting.RED + "No API key set. Use /bw setkey <key> first.");
            return;
        }

        runtime.startAutoplay(mode);

        Minecraft mc = Minecraft.getMinecraft();
        runtime.requestPartyList(mc);

        String playCommand = runtime.getPlayCommand(mode);
        sendMessage(sender, EnumChatFormatting.GOLD + "[Autoplay] " +
                EnumChatFormatting.GREEN + "Started autoplay for " + mode + "!");
        sendMessage(sender, EnumChatFormatting.GRAY + "Will requeue if " +
                ModConfig.getAutoplayMaxThreatLevel() + "+ threat players detected.");
        sendMessage(sender, EnumChatFormatting.GRAY + "Use /bw autoplay stop to cancel.");

        if (mc.thePlayer != null && playCommand != null) {
            mc.thePlayer.sendChatMessage(playCommand);
        }
    }

    private void handleMapsCommand(ICommandSender sender) {
        List<MapLearningService.LearnedMapSummary> maps = runtime.getMapLearningService().getLearnedMaps();
        if (maps.isEmpty()) {
            sendMessage(sender, EnumChatFormatting.YELLOW
                    + "No learned maps yet. Finish a game with map learning enabled.");
            return;
        }

        sendMessage(sender, EnumChatFormatting.GOLD + "=== Learned Maps (" + maps.size() + ") ===");
        long now = System.currentTimeMillis();
        for (MapLearningService.LearnedMapSummary map : maps) {
            long daysAgo = (now - map.lastSeenEpochMs) / (1000L * 60 * 60 * 24);
            String lastSeen = daysAgo == 0 ? "today" : daysAgo + "d ago";
            sendMessage(sender,
                    EnumChatFormatting.AQUA + map.name +
                            EnumChatFormatting.GRAY + " - " + map.observationCount + " games | " +
                            EnumChatFormatting.YELLOW + map.consolidatedGenerators + " confirmed gens" +
                            EnumChatFormatting.GRAY + " | last seen " + lastSeen);
        }
    }

    private void handleRejoinCommand(ICommandSender sender) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            sendMessage(sender, EnumChatFormatting.RED + "You must be in a world to use this command.");
            return;
        }

        boolean wasTracking = runtime.rerunMatchStartup(mc);

        if (wasTracking) {
            sendMessage(sender, EnumChatFormatting.GREEN + "Re-ran Bedwars startup flow for this match.");
        } else {
            sendMessage(sender, EnumChatFormatting.GREEN + "Started Bedwars tracking as if you just joined.");
        }
    }

    /**
     * Format a recent-FKDR delta with arrow + sign + colour. Tiny deltas show
     * a flat line so we don't visually shout about noise.
     */
    private static String formatDelta(double delta) {
        if (Math.abs(delta) < 0.10) {
            return EnumChatFormatting.GRAY + "(\u2192 flat)";
        }
        if (delta > 0) {
            return EnumChatFormatting.RED + "(\u2191 +" + String.format("%.2f", delta) + ")";
        }
        return EnumChatFormatting.GREEN + "(\u2193 " + String.format("%.2f", delta) + ")";
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /**
     * Confirm a freshly-set API key actually works with one real lookup of the
     * local player. A 403 means the key was rejected; anything else that returns
     * means it is accepted. The report string is built off-thread (pure CPU) and
     * {@link ClientThread#modChat} marshals the chat send.
     */
    private static void validateApiKey() {
        Minecraft mc = Minecraft.getMinecraft();
        final String selfName = mc.thePlayer != null ? mc.thePlayer.getName() : null;
        if (selfName == null) {
            return;
        }
        HypixelAPI.fetchStatsAsync(selfName, new HypixelAPI.StatsCallback() {
            @Override
            public void onStatsLoaded(BedwarsStats stats) {
                ClientThread.modChat(EnumChatFormatting.GREEN + "API key is valid ✓");
            }

            @Override
            public void onError(String error) {
                if (error != null && error.contains("403")) {
                    ClientThread.modChat(EnumChatFormatting.RED
                            + "API key rejected (403). Get a valid key at developer.hypixel.net/dashboard.");
                } else {
                    ClientThread.modChat(EnumChatFormatting.YELLOW
                            + "Key saved, but validation couldn't finish: " + error);
                }
            }
        }, HypixelAPI.FetchPriority.EXPLICIT);
    }

    /** Full multi-line /bw lookup report, including the per-mode breakdown. */
    private static String buildLookupReport(String name, BedwarsStats stats) {
        if (stats.getThreatLevel() == BedwarsStats.ThreatLevel.NICKED) {
            return EnumChatFormatting.GOLD + "=== " + name + " ===\n"
                    + EnumChatFormatting.LIGHT_PURPLE + "Likely NICK — no Bedwars stats found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(EnumChatFormatting.GOLD).append("=== ").append(name).append(" ===\n");
        sb.append(EnumChatFormatting.WHITE).append("Stars: ")
          .append(EnumChatFormatting.YELLOW).append(stats.getStars()).append("\n");
        sb.append(EnumChatFormatting.WHITE).append("FKDR: ")
          .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatio(stats.getFkdr()))
          .append(" (").append(stats.getFinalKills()).append("/").append(stats.getFinalDeaths()).append(")\n");

        BedwarsStats.RecentWindow window = stats.getRecentWindow();
        if (window != BedwarsStats.RecentWindow.NONE) {
            double recent = stats.getRecentFkdr();
            double delta = stats.getRecentFkdrDelta();
            String deltaText = formatDelta(delta);
            sb.append(EnumChatFormatting.WHITE).append("Recent FKDR: ")
              .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatio(recent))
              .append(EnumChatFormatting.GRAY).append(" [").append(stats.getRecentWindowLabel()).append("] ")
              .append(deltaText).append("\n");
        }

        sb.append(EnumChatFormatting.WHITE).append("WLR: ")
          .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatio(stats.getWlr()))
          .append(" (").append(stats.getWins()).append("/").append(stats.getLosses()).append(")\n");
        sb.append(EnumChatFormatting.WHITE).append("Beds: ")
          .append(EnumChatFormatting.YELLOW).append(stats.getBedsBroken()).append("\n");

        appendModeBreakdown(sb, stats);

        sb.append(EnumChatFormatting.WHITE).append("Threat: ")
          .append(stats.getThreatColor()).append(stats.getThreatLevel().name());
        return sb.toString();
    }

    /**
     * Per-mode FKDR breakdown, each row colored by that mode's OWN threat tier so
     * a fours stacker padding a solos lobby is obvious at a glance. Modes with no
     * games are omitted; the player's most-played mode is starred.
     */
    private static void appendModeBreakdown(StringBuilder sb, BedwarsStats stats) {
        BedwarsStats.Mode primary = stats.getPrimaryMode();
        if (primary == null) {
            return;
        }
        sb.append(EnumChatFormatting.GRAY).append("Modes:\n");
        for (BedwarsStats.Mode mode : BedwarsStats.Mode.values()) {
            if (stats.getModeGames(mode) == 0) {
                continue;
            }
            sb.append(EnumChatFormatting.GRAY).append("  ").append(mode.label).append(": ")
              .append(stats.getModeThreatColor(mode)).append(BedwarsStats.formatRatio(stats.getModeFkdr(mode)))
              .append(EnumChatFormatting.DARK_GRAY).append(" (").append(stats.getModeFinalKills(mode))
              .append("/").append(stats.getModeFinalDeaths(mode)).append(") ")
              .append(EnumChatFormatting.GRAY).append(stats.getModeWins(mode)).append("W");
            if (mode == primary) {
                sb.append(EnumChatFormatting.GOLD).append(" ★");
            }
            sb.append("\n");
        }
    }

    private void sendMessage(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }
}
