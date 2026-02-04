package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.lwjgl.opengl.GL11;

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

        // Register the /bw command
        ClientCommandHandler.instance.registerCommand(new BedwarsCommand());

        System.out.println("[BedwarsStats] Mod initialized! Use /bw setkey <apikey> to set your Hypixel API key.");
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

        // Check encounter history and notify in chat
        if (db.hasPlayedBefore(playerName)) {
            entry.encounterCount = db.getEncounterCount(playerName);
            entry.winLossRecord = db.getWinLossRecord(playerName);

            // Show history notification in chat
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
     * Renders threat level above player nametags when they have cached stats
     */
    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Specials.Post event) {
        // Only render for players
        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.entity;
        Minecraft mc = Minecraft.getMinecraft();

        // Don't render for self
        if (mc.thePlayer != null && player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
            return;
        }

        // Check if we have cached stats for this player
        BedwarsStats stats = HypixelAPI.getCachedStats(player.getName());
        if (stats == null || !stats.isLoaded()) {
            return;
        }

        // Get threat level and color
        BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
        if (threat == BedwarsStats.ThreatLevel.UNKNOWN) {
            return;
        }

        // Create display text with threat info
        String threatText = stats.getThreatColor() + "[" + threat.name() + "] " +
                EnumChatFormatting.WHITE + stats.getStars() + "⭐ " +
                EnumChatFormatting.YELLOW + String.format("%.1f", stats.getFkdr()) + " FKDR";

        // Render the threat level above the nametag
        renderThreatLabel(player, threatText, event.x, event.y, event.z);
    }

    /**
     * Renders a label above the player's nametag
     */
    private void renderThreatLabel(EntityPlayer player, String text, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRendererObj;
        RenderManager renderManager = mc.getRenderManager();

        // Calculate distance for visibility check
        double distance = player.getDistanceSqToEntity(mc.thePlayer);
        if (distance > 4096.0D) { // 64 blocks squared
            return;
        }

        // Height offset - above the nametag (player height + nametag offset + extra)
        float heightOffset = player.height + 0.5F + 0.3F;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + heightOffset, (float) z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        float scale = 0.016666668F * 1.5F; // Slightly larger than nametag
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;

        // Draw background
        GlStateManager.disableTexture2D();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(-halfWidth - 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(-halfWidth - 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(halfWidth + 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(halfWidth + 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        // Draw text
        fontRenderer.drawString(text, -halfWidth, 0, 0xFFFFFFFF);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
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
     * Command handler for /bw
     */
    public static class BedwarsCommand extends CommandBase {

        @Override
        public String getCommandName() {
            return "bw";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/bw <setkey|lookup|all|info|blacklist|history|status|clear> [args]";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0; // Anyone can use
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 0) {
                sendMessage(sender, EnumChatFormatting.GOLD + "=== BedwarsStats Commands ===");
                sendMessage(sender, "/bw setkey <key> - Set your Hypixel API key");
                sendMessage(sender, "/bw lookup <player> - Look up a player's stats");
                sendMessage(sender, "/bw all - Check stats for everyone in the lobby");
                sendMessage(sender, "/bw info - Show threats and history players in lobby");
                sendMessage(sender, "/bw blacklist <add|remove|list> [player] [reason] - Manage blacklist");
                sendMessage(sender, "/bw history [player] - View encounter history");
                sendMessage(sender, "/bw status - Show cache and rate limit info");
                sendMessage(sender, "/bw clear - Clear the stats cache");
                return;
            }

            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("setkey")) {
                if (args.length < 2) {
                    sendMessage(sender, EnumChatFormatting.RED + "Usage: /bw setkey <apikey>");
                    return;
                }
                // Save API key to config file so it persists across restarts
                ModConfig.setApiKey(args[1]);
                sendMessage(sender, EnumChatFormatting.GREEN + "API key set and saved!");

            } else if (subCommand.equals("lookup")) {
                if (args.length < 2) {
                    sendMessage(sender, EnumChatFormatting.RED + "Usage: /bw lookup <playername>");
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
                    sendMessage(sender, EnumChatFormatting.RED + "No API key set. Use /bw setkey <key>");
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
                sendMessage(sender, "In Bedwars lobby: " + (inBedwarsLobby ? "Yes" : "No"));

            } else if (subCommand.equals("clear"))

            {
                HypixelAPI.clearCache();
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                sendMessage(sender, EnumChatFormatting.GREEN + "Cache cleared!");

            } else {
                sendMessage(sender, EnumChatFormatting.RED + "Unknown command. Use /bw for help.");
            }
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args,
                net.minecraft.util.BlockPos pos) {
            if (args.length == 1) {
                // Autocomplete subcommands
                return getListOfStringsMatchingLastWord(args, "setkey", "lookup", "all", "info", "blacklist", "history",
                        "status", "clear");
            }

            if (args.length == 2) {
                String subCommand = args[0].toLowerCase();

                // For lookup and history, suggest online player names
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

                // For blacklist, suggest add/remove/list
                if (subCommand.equals("blacklist")) {
                    return getListOfStringsMatchingLastWord(args, "add", "remove", "list");
                }
            }

            if (args.length == 3 && args[0].toLowerCase().equals("blacklist")) {
                // For blacklist add/remove, suggest player names
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

            sendMessage(sender, EnumChatFormatting.GOLD + "=== Lobby Info ===");

            for (EntityPlayer player : players) {
                // Skip yourself
                if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                    continue;
                }

                String playerName = player.getName();

                // Check for threats (cached stats with MEDIUM+ threat level)
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

                // Check for history players
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

            // Display threats
            if (!threats.isEmpty()) {
                sendMessage(sender, EnumChatFormatting.RED + "⚠ Threats (" + threats.size() + "):");
                for (String threat : threats) {
                    sendMessage(sender, "  " + threat);
                }
            } else {
                sendMessage(sender, EnumChatFormatting.GREEN + "✓ No threats detected");
            }

            // Display history players
            if (!historyPlayers.isEmpty()) {
                sendMessage(sender, EnumChatFormatting.AQUA + "📜 History Players (" + historyPlayers.size() + "):");
                for (String historyPlayer : historyPlayers) {
                    sendMessage(sender, "  " + historyPlayer);
                }
            } else {
                sendMessage(sender, EnumChatFormatting.GRAY + "No previously encountered players");
            }

            int total = players.size() - 1; // minus yourself
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
