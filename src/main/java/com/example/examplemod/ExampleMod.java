package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod(modid = ExampleMod.MODID, version = ExampleMod.VERSION, guiFactory = "com.example.examplemod.ModGuiFactory")
public class ExampleMod {
    public static final String MODID = "bedwars";
    public static final String VERSION = "1.0";

    // Store recent player joins with timestamp for display
    private static final List<PlayerJoinEntry> recentJoins = new ArrayList<PlayerJoinEntry>();
    private static final long DISPLAY_DURATION = 10000; // 10 seconds display time

    // Flag to track if we're in a Bedwars lobby
    private static boolean inBedwarsLobby = false;

    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent event) {
        // Load configuration (including saved API key)
        ModConfig.init(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register this class as an event handler
        MinecraftForge.EVENT_BUS.register(this);

        // Register the /bwstats command
        ClientCommandHandler.instance.registerCommand(new BedwarsCommand());

        System.out.println("[BedwarsStats] Mod initialized! Use /bwstats setkey <apikey> to set your Hypixel API key.");
    }

    /**
     * Detects Bed Wars lobby chat message to activate stat tracking
     * Also detects game end for win/loss recording
     */
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.message == null)
            return;

        String message = event.message.getUnformattedText();
        Minecraft mc = Minecraft.getMinecraft();

        // Detect Bed Wars lobby message - matches the title shown in the screenshot
        if (message.contains("Protect your bed and destroy the enemy beds.")) {
            if (!inBedwarsLobby) {
                inBedwarsLobby = true;
                // Clear previous entries when entering a new lobby
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                PlayerDatabase.getInstance().clearCurrentGame();
                System.out.println("[BedwarsStats] Entered Bedwars lobby - stat tracking activated!");
            }
        }

        // Detect WIN - look for victory messages
        if (inBedwarsLobby && mc.thePlayer != null) {
            String playerName = mc.thePlayer.getName();

            // Victory detection
            if (message.contains("VICTORY!") ||
                    (message.contains("1st Killer") && message.contains(playerName)) ||
                    message.contains("You won!") ||
                    (message.contains("Winner") && message.contains(playerName))) {

                System.out.println("[BedwarsStats] WIN detected!");
                PlayerDatabase.getInstance().recordGameEnd(PlayerDatabase.GameOutcome.WIN);
                PlayerDatabase.getInstance().clearCurrentGame();
                inBedwarsLobby = false;
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                return;
            }

            // Loss detection - bed destroyed + final killed
            if (message.contains("GAME OVER!") ||
                    (message.contains("disconnected") && message.contains("BED WARS")) ||
                    (message.contains("You died!") && message.contains("FINAL KILL")) ||
                    message.contains("You have been eliminated!")) {

                System.out.println("[BedwarsStats] LOSS detected!");
                PlayerDatabase.getInstance().recordGameEnd(PlayerDatabase.GameOutcome.LOSS);
                PlayerDatabase.getInstance().clearCurrentGame();
                inBedwarsLobby = false;
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                return;
            }
        }

        // Detect when leaving the game (unknown outcome)
        if (message.contains("You left.") || message.contains("Sending you to")) {
            if (inBedwarsLobby) {
                inBedwarsLobby = false;
                PlayerDatabase.getInstance().recordGameEnd(PlayerDatabase.GameOutcome.UNKNOWN);
                PlayerDatabase.getInstance().clearCurrentGame();
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                System.out.println("[BedwarsStats] Left Bedwars game - unknown outcome.");
            }
        }
    }

    /**
     * Detects when any player entity joins the world (client-side)
     */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        // Only handle on client side
        if (!event.world.isRemote)
            return;

        // Only track stats when in a Bedwars lobby
        if (!inBedwarsLobby)
            return;

        // Only handle player entities
        if (!(event.entity instanceof EntityPlayer))
            return;

        EntityPlayer player = (EntityPlayer) event.entity;
        Minecraft mc = Minecraft.getMinecraft();

        // Ignore the local player (yourself)
        if (mc.thePlayer != null && player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
            return;
        }

        final String playerName = player.getName();

        // Check if already in list
        synchronized (recentJoins) {
            for (PlayerJoinEntry entry : recentJoins) {
                if (entry.playerName.equals(playerName)) {
                    entry.timestamp = System.currentTimeMillis();
                    return;
                }
            }
        }

        // Create new entry
        final PlayerJoinEntry entry = new PlayerJoinEntry(playerName, System.currentTimeMillis());

        synchronized (recentJoins) {
            recentJoins.add(entry);
        }

        // Get UUID directly from the player entity (no Mojang API needed!)
        final String playerUuid = player.getUniqueID().toString();

        // Add to current game for history tracking
        PlayerDatabase.getInstance().addToCurrentGame(playerName);

        // Check if player is blacklisted
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

        // Check encounter history
        if (db.hasPlayedBefore(playerName)) {
            entry.encounterCount = db.getEncounterCount(playerName);
            entry.winLossRecord = db.getWinLossRecord(playerName);
        }

        // Fetch stats asynchronously
        if (HypixelAPI.hasApiKey()) {
            // Use UUID directly since we have the player entity
            HypixelAPI.fetchStatsWithUuid(playerName, playerUuid, new HypixelAPI.StatsCallback() {
                @Override
                public void onStatsLoaded(BedwarsStats stats) {
                    entry.stats = stats;

                    // Only show players with MEDIUM or higher threat level
                    BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
                    if (threat == BedwarsStats.ThreatLevel.MEDIUM ||
                            threat == BedwarsStats.ThreatLevel.HIGH ||
                            threat == BedwarsStats.ThreatLevel.EXTREME) {

                        // Print to chat
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
            // No API key - log to console only, don't spam chat
            System.out.println("[BedwarsStats] Player joined: " + playerName + " (No API key set)");
        }
    }

    /**
     * Renders the player names on screen
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT)
            return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null)
            return;

        // Clean up expired entries
        long currentTime = System.currentTimeMillis();
        synchronized (recentJoins) {
            Iterator<PlayerJoinEntry> iterator = recentJoins.iterator();
            while (iterator.hasNext()) {
                if (currentTime - iterator.next().timestamp > DISPLAY_DURATION) {
                    iterator.remove();
                }
            }
        }

        if (recentJoins.isEmpty())
            return;

        FontRenderer fontRenderer = mc.fontRendererObj;

        int x = 5;
        int y = 50;
        int lineHeight = 12;

        // Draw header
        fontRenderer.drawStringWithShadow(
                EnumChatFormatting.GOLD + "Recent Players:",
                x, y, 0xFFFFFF);
        y += lineHeight + 2;

        // Draw player names with stats
        synchronized (recentJoins) {
            for (PlayerJoinEntry entry : recentJoins) {
                String displayText;

                if (entry.stats != null && entry.stats.isLoaded()) {
                    displayText = entry.stats.getThreatColor() + entry.playerName + " " +
                            entry.stats.getDisplayString();
                } else if (entry.stats != null && entry.stats.hasError()) {
                    displayText = EnumChatFormatting.GRAY + entry.playerName + " [Error]";
                } else {
                    displayText = EnumChatFormatting.WHITE + entry.playerName +
                            EnumChatFormatting.GRAY + " [Loading...]";
                }

                fontRenderer.drawStringWithShadow(displayText, x, y, 0xFFFFFF);
                y += lineHeight;
            }
        }
    }

    /**
     * Helper class to store player join information
     */
    private static class PlayerJoinEntry {
        String playerName;
        long timestamp;
        BedwarsStats stats;
        boolean isBlacklisted = false;
        int encounterCount = 0;
        int[] winLossRecord = null; // [wins, losses]

        PlayerJoinEntry(String name, long time) {
            this.playerName = name;
            this.timestamp = time;
            this.stats = null;
        }
    }

    /**
     * Command handler for /bwstats
     */
    public static class BedwarsCommand extends CommandBase {

        @Override
        public String getCommandName() {
            return "bwstats";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/bwstats <setkey|lookup|all|blacklist|history|status|clear> [args]";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0; // Anyone can use
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                sendMessage(sender, EnumChatFormatting.GOLD + "=== BedwarsStats Commands ===");
                sendMessage(sender, "/bwstats setkey <key> - Set your Hypixel API key");
                sendMessage(sender, "/bwstats lookup <player> - Look up a player's stats");
                sendMessage(sender, "/bwstats all - Check stats for everyone in the lobby");
                sendMessage(sender, "/bwstats blacklist <add|remove|list> [player] [reason] - Manage blacklist");
                sendMessage(sender, "/bwstats history [player] - View encounter history");
                sendMessage(sender, "/bwstats status - Show cache and rate limit info");
                sendMessage(sender, "/bwstats clear - Clear the stats cache");
                return;
            }

            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("setkey")) {
                if (args.length < 2) {
                    sendMessage(sender, EnumChatFormatting.RED + "Usage: /bwstats setkey <apikey>");
                    return;
                }
                // Save API key to config file so it persists across restarts
                ModConfig.setApiKey(args[1]);
                sendMessage(sender, EnumChatFormatting.GREEN + "API key set and saved!");

            } else if (subCommand.equals("lookup")) {
                if (args.length < 2) {
                    sendMessage(sender, EnumChatFormatting.RED + "Usage: /bwstats lookup <playername>");
                    return;
                }
                final String targetPlayer = args[1];
                sendMessage(sender, EnumChatFormatting.YELLOW + "Looking up " + targetPlayer + "...");

                HypixelAPI.fetchStatsAsync(targetPlayer, new HypixelAPI.StatsCallback() {
                    @Override
                    public void onStatsLoaded(BedwarsStats stats) {
                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc.thePlayer != null) {
                            mc.thePlayer.addChatMessage(new ChatComponentText(
                                    EnumChatFormatting.GOLD + "=== " + targetPlayer + " ===\n" +
                                            EnumChatFormatting.WHITE + "Stars: " + EnumChatFormatting.YELLOW
                                            + stats.getStars() + "\n" +
                                            EnumChatFormatting.WHITE + "FKDR: " + EnumChatFormatting.YELLOW
                                            + String.format("%.2f", stats.getFkdr()) +
                                            " (" + stats.getFinalKills() + "/" + stats.getFinalDeaths() + ")\n" +
                                            EnumChatFormatting.WHITE + "WLR: " + EnumChatFormatting.YELLOW
                                            + String.format("%.2f", stats.getWlr()) +
                                            " (" + stats.getWins() + "/" + stats.getLosses() + ")\n" +
                                            EnumChatFormatting.WHITE + "Beds: " + EnumChatFormatting.YELLOW
                                            + stats.getBedsBroken() + "\n" +
                                            EnumChatFormatting.WHITE + "Threat: " + stats.getThreatColor()
                                            + stats.getThreatLevel().name()));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc.thePlayer != null) {
                            mc.thePlayer.addChatMessage(new ChatComponentText(
                                    EnumChatFormatting.RED + "Error: " + error));
                        }
                    }
                });

            } else if (subCommand.equals("all")) {
                if (!HypixelAPI.hasApiKey()) {
                    sendMessage(sender, EnumChatFormatting.RED + "No API key set. Use /bwstats setkey <key>");
                    return;
                }

                Minecraft mc = Minecraft.getMinecraft();
                if (mc.theWorld == null || mc.thePlayer == null) {
                    sendMessage(sender, EnumChatFormatting.RED + "You must be in a world to use this command.");
                    return;
                }

                // Get all players in the world
                List<EntityPlayer> players = mc.theWorld.playerEntities;
                int count = 0;

                sendMessage(sender, EnumChatFormatting.GOLD + "=== Checking all players in lobby ===");

                for (EntityPlayer player : players) {
                    // Skip yourself
                    if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                        continue;
                    }

                    final String playerName = player.getName();
                    final String playerUuid = player.getUniqueID().toString();
                    count++;

                    // Fetch stats for each player
                    HypixelAPI.fetchStatsWithUuid(playerName, playerUuid, new HypixelAPI.StatsCallback() {

                        @Override
                        public void onStatsLoaded(BedwarsStats stats) {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc.thePlayer != null) {
                                mc.thePlayer.addChatMessage(new ChatComponentText(
                                        stats.getThreatColor() + playerName + " " +
                                                EnumChatFormatting.WHITE + "[" + stats.getStars() + "⭐] " +
                                                EnumChatFormatting.YELLOW + String.format("%.2f", stats.getFkdr())
                                                + " FKDR " +
                                                EnumChatFormatting.GRAY + "(" + stats.getThreatLevel().name() + ")"));
                            }
                        }

                        @Override
                        public void onError(String error) {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc.thePlayer != null) {
                                mc.thePlayer.addChatMessage(new ChatComponentText(
                                        EnumChatFormatting.RED + playerName + ": " + error));
                            }
                        }
                    });
                }

                if (count == 0) {
                    sendMessage(sender, EnumChatFormatting.YELLOW + "No other players found in the lobby.");
                } else {
                    sendMessage(sender, EnumChatFormatting.YELLOW + "Looking up " + count + " players...");
                }

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
                sendMessage(sender, "In Bedwars lobby: " + (inBedwarsLobby ? "Yes" : "No"));

            } else if (subCommand.equals("clear"))

            {
                HypixelAPI.clearCache();
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                sendMessage(sender, EnumChatFormatting.GREEN + "Cache cleared!");

            } else {
                sendMessage(sender, EnumChatFormatting.RED + "Unknown command. Use /bwstats for help.");
            }
        }

        private void handleBlacklistCommand(ICommandSender sender, String[] args) {
            PlayerDatabase db = PlayerDatabase.getInstance();

            if (args.length < 2) {
                sendMessage(sender,
                        EnumChatFormatting.RED + "Usage: /bwstats blacklist <add|remove|list> [player] [reason]");
                return;
            }

            String action = args[1].toLowerCase();

            if (action.equals("add")) {
                if (args.length < 3) {
                    sendMessage(sender, EnumChatFormatting.RED + "Usage: /bwstats blacklist add <player> [reason]");
                    return;
                }
                String playerName = args[2];
                String reason = args.length > 3 ? joinArgs(args, 3) : "No reason specified";
                db.addToBlacklist(playerName, reason);
                sendMessage(sender, EnumChatFormatting.GREEN + "Added " + playerName + " to blacklist: " + reason);

            } else if (action.equals("remove")) {
                if (args.length < 3) {
                    sendMessage(sender, EnumChatFormatting.RED + "Usage: /bwstats blacklist remove <player>");
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
                    for (PlayerDatabase.BlacklistEntry entry : entries) {
                        long daysAgo = (System.currentTimeMillis() - entry.addedAt) / (1000 * 60 * 60 * 24);
                        sendMessage(sender, EnumChatFormatting.RED + entry.playerName +
                                EnumChatFormatting.GRAY + " - " + entry.reason +
                                " (" + daysAgo + " days ago)");
                    }
                }

            } else {
                sendMessage(sender, EnumChatFormatting.RED + "Unknown blacklist action. Use: add, remove, list");
            }
        }

        private void handleHistoryCommand(ICommandSender sender, String[] args) {
            PlayerDatabase db = PlayerDatabase.getInstance();

            if (args.length < 2) {
                // Show overall history summary
                sendMessage(sender, EnumChatFormatting.GOLD + "=== Encounter History ===");
                sendMessage(sender, "Total unique players: " + db.getHistorySize());
                sendMessage(sender, "Use /bwstats history <player> for details");
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

            // Show last 5 encounters
            int showCount = Math.min(5, encounters.size());
            sendMessage(sender, EnumChatFormatting.GRAY + "Last " + showCount + " games:");
            for (int i = encounters.size() - 1; i >= Math.max(0, encounters.size() - 5); i--) {
                PlayerDatabase.EncounterEntry e = encounters.get(i);
                long daysAgo = (System.currentTimeMillis() - e.timestamp) / (1000 * 60 * 60 * 24);
                String outcomeColor = e.outcome == PlayerDatabase.GameOutcome.WIN ? EnumChatFormatting.GREEN.toString()
                        : (e.outcome == PlayerDatabase.GameOutcome.LOSS ? EnumChatFormatting.RED.toString()
                                : EnumChatFormatting.GRAY.toString());
                sendMessage(sender, outcomeColor + e.outcome.name() +
                        EnumChatFormatting.GRAY + " (" + daysAgo + " days ago)");
            }
        }

        private String joinArgs(String[] args, int startIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = startIndex; i < args.length; i++) {
                if (i > startIndex)
                    sb.append(" ");
                sb.append(args[i]);
            }
            return sb.toString();
        }

        private void sendMessage(ICommandSender sender, String message) {
            sender.addChatMessage(new ChatComponentText(message));
        }
    }
}
