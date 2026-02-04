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

@Mod(modid = ExampleMod.MODID, version = ExampleMod.VERSION)
public class ExampleMod {
    public static final String MODID = "bedwars";
    public static final String VERSION = "1.0";

    // Store recent player joins with timestamp for display
    private static final List<PlayerJoinEntry> recentJoins = new ArrayList<PlayerJoinEntry>();
    private static final long DISPLAY_DURATION = 10000; // 10 seconds display time

    // Flag to track if we're in a Bedwars lobby
    private static boolean inBedwarsLobby = false;

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
     */
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.message == null)
            return;

        String message = event.message.getUnformattedText();

        // Detect Bed Wars lobby message - matches the title shown in the screenshot
        if (message.contains("Bed Wars") && message.contains("Protect your bed")) {
            if (!inBedwarsLobby) {
                inBedwarsLobby = true;
                // Clear previous entries when entering a new lobby
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                System.out.println("[BedwarsStats] Entered Bedwars lobby - stat tracking activated!");
            }
        }

        // Detect when leaving the game (common end messages)
        if (message.contains("You left.") || message.contains("The game has ended!") ||
                message.contains("has won the game!") || message.contains("Sending you to")) {
            if (inBedwarsLobby) {
                inBedwarsLobby = false;
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                System.out.println("[BedwarsStats] Left Bedwars lobby - stat tracking deactivated.");
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
            return "/bwstats <setkey|lookup|clear> [args]";
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
                sendMessage(sender, "/bwstats clear - Clear the stats cache");
                return;
            }

            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("setkey")) {
                if (args.length < 2) {
                    sendMessage(sender, EnumChatFormatting.RED + "Usage: /bwstats setkey <apikey>");
                    return;
                }
                HypixelAPI.setApiKey(args[1]);
                sendMessage(sender, EnumChatFormatting.GREEN + "API key set successfully!");

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

            } else if (subCommand.equals("clear")) {
                HypixelAPI.clearCache();
                synchronized (recentJoins) {
                    recentJoins.clear();
                }
                sendMessage(sender, EnumChatFormatting.GREEN + "Cache cleared!");

            } else {
                sendMessage(sender, EnumChatFormatting.RED + "Unknown command. Use /bwstats for help.");
            }
        }

        private void sendMessage(ICommandSender sender, String message) {
            sender.addChatMessage(new ChatComponentText(message));
        }
    }
}
