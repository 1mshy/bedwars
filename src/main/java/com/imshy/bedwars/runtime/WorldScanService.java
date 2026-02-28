package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.render.BedwarsOverlayRenderer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class WorldScanService {
    private static final Map<String, String> GAME_MODE_COMMANDS = new HashMap<String, String>();
    private static final String[] AUTOPLAY_MODE_SUGGESTIONS = {
            "ones", "twos", "threes", "fours", "stop", "requeue"
    };

    static {
        GAME_MODE_COMMANDS.put("ones", "/play bedwars_eight_one");
        GAME_MODE_COMMANDS.put("twos", "/play bedwars_eight_two");
        GAME_MODE_COMMANDS.put("threes", "/play bedwars_four_three");
        GAME_MODE_COMMANDS.put("fours", "/play bedwars_four_four");
    }

    private final RuntimeState state;
    private final MatchThreatService matchThreatService;

    WorldScanService(RuntimeState state, MatchThreatService matchThreatService) {
        this.state = state;
        this.matchThreatService = matchThreatService;
    }

    public void clearTrackedGenerators() {
        state.trackedGenerators.clear();
    }

    public void renderTrackedGenerators(BedwarsOverlayRenderer renderer, float partialTicks) {
        for (GeneratorEntry generator : state.trackedGenerators.values()) {
            renderer.renderGeneratorLabel(
                    generator.position,
                    generator.isDiamond,
                    generator.resourceCount,
                    generator.hasDesignatedIngotOnTop,
                    partialTicks);
        }
    }

    public GeneratorSummary getGeneratorSummary() {
        int totalDiamonds = 0;
        int totalEmeralds = 0;
        int diamondGenerators = 0;
        int emeraldGenerators = 0;
        for (GeneratorEntry gen : state.trackedGenerators.values()) {
            if (gen.isDiamond) {
                diamondGenerators++;
                totalDiamonds += gen.resourceCount;
            } else {
                emeraldGenerators++;
                totalEmeralds += gen.resourceCount;
            }
        }
        return new GeneratorSummary(totalDiamonds, totalEmeralds, diamondGenerators, emeraldGenerators);
    }

    public static class GeneratorSummary {
        public final int totalDiamonds;
        public final int totalEmeralds;
        public final int diamondGenerators;
        public final int emeraldGenerators;

        GeneratorSummary(int totalDiamonds, int totalEmeralds, int diamondGenerators, int emeraldGenerators) {
            this.totalDiamonds = totalDiamonds;
            this.totalEmeralds = totalEmeralds;
            this.diamondGenerators = diamondGenerators;
            this.emeraldGenerators = emeraldGenerators;
        }
    }

    public boolean isValidAutoplayMode(String mode) {
        return GAME_MODE_COMMANDS.containsKey(mode);
    }

    public String[] getAutoplayModeSuggestions() {
        return AUTOPLAY_MODE_SUGGESTIONS.clone();
    }

    public String getPlayCommand(String mode) {
        return GAME_MODE_COMMANDS.get(mode);
    }

    public void requeueAutoplay(Minecraft mc, String reasonMessage) {
        if (mc == null || mc.thePlayer == null || !state.autoplayEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - state.lastRequeueTime < RuntimeState.SPAM_RETRY_DELAY) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[Autoplay] " +
                            EnumChatFormatting.GRAY + "Waiting for cooldown..."));
            return;
        }
        state.lastRequeueTime = now;

        if (reasonMessage != null && !reasonMessage.isEmpty()) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[Autoplay] " + reasonMessage));
        }

        mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.GOLD + "[Autoplay] " +
                        EnumChatFormatting.YELLOW + "Requeuing..."));

        state.gamePhase = GamePhase.IDLE;
        state.autoplayPendingCheck = false;
        synchronized (state.recentJoins) {
            state.recentJoins.clear();
        }

        mc.thePlayer.sendChatMessage("/l");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500);
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc.thePlayer == null || !state.autoplayEnabled)
                        return;

                    mc.thePlayer.sendChatMessage("/p warp");
                    Thread.sleep(500);

                    if (mc.thePlayer == null || !state.autoplayEnabled)
                        return;
                    mc.thePlayer.sendChatMessage("/p warp");
                    Thread.sleep(1500);

                    if (mc.thePlayer == null || !state.autoplayEnabled)
                        return;
                    String playCommand = GAME_MODE_COMMANDS.get(state.autoplayMode);
                    if (playCommand != null) {
                        mc.thePlayer.sendChatMessage(playCommand);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void checkForInvisiblePlayers(Minecraft mc, long currentTime) {
        int detectionRange = ModConfig.getInvisibleDetectionRange();
        int cooldown = ModConfig.getInvisibleWarningCooldown();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }

            if (player.isInvisible()) {
                double distance = player.getDistanceToEntity(mc.thePlayer);

                if (distance <= detectionRange) {
                    String playerName = player.getName();

                    Long lastWarning = state.invisiblePlayerWarnings.get(playerName);
                    if (lastWarning == null || (currentTime - lastWarning) >= cooldown) {
                        String warningMessage = EnumChatFormatting.LIGHT_PURPLE + "👁 INVISIBLE PLAYER: " +
                                EnumChatFormatting.WHITE + playerName +
                                EnumChatFormatting.GRAY + " detected " + (int) distance + " blocks away!";
                        mc.thePlayer.addChatMessage(new ChatComponentText(warningMessage));
                        AudioCueManager.playCue(mc, AudioCueManager.CueType.INVISIBLE_NEARBY);

                        state.invisiblePlayerWarnings.put(playerName, currentTime);
                    }
                }
            }
        }
    }

    public void scanForGenerators(Minecraft mc) {
        int scanRange = ModConfig.getGeneratorScanRange();
        BlockPos playerPos = mc.thePlayer.getPosition();
        Set<BlockPos> visibleGenerators = new HashSet<BlockPos>();

        for (int x = -scanRange; x <= scanRange; x++) {
            for (int y = -20; y <= 20; y++) {
                for (int z = -scanRange; z <= scanRange; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    if (!mc.theWorld.isBlockLoaded(checkPos)) {
                        continue;
                    }

                    if (!mc.theWorld.isAirBlock(checkPos.up())) {
                        continue;
                    }

                    Block block = mc.theWorld.getBlockState(checkPos).getBlock();

                    boolean isDiamond = (block == Blocks.diamond_block);
                    boolean isEmerald = (block == Blocks.emerald_block);

                    if (isDiamond || isEmerald) {
                        visibleGenerators.add(checkPos);

                        GeneratorEntry existing = state.trackedGenerators.get(checkPos);
                        if (existing == null) {
                            existing = new GeneratorEntry(checkPos, isDiamond);
                            state.trackedGenerators.put(checkPos, existing);
                        }

                        GeneratorResourceScan scan = scanGeneratorResources(mc, checkPos, isDiamond);
                        existing.resourceCount = scan.resourceCount;
                        existing.hasDesignatedIngotOnTop = scan.hasDesignatedIngotOnTop;
                        existing.lastUpdate = System.currentTimeMillis();
                    }
                }
            }
        }

        Iterator<Map.Entry<BlockPos, GeneratorEntry>> iterator = state.trackedGenerators.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, GeneratorEntry> entry = iterator.next();
            if (playerPos.distanceSq(entry.getKey()) > scanRange * scanRange
                    || !visibleGenerators.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    public void performAutoplayCheck(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        String maxThreatLevel = ModConfig.getAutoplayMaxThreatLevel();
        List<String> enemyThreatPlayers = new ArrayList<String>();
        List<String> teammateThreatPlayers = new ArrayList<String>();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }

            String playerName = player.getName();

            if (state.partyMemberNames.contains(playerName)) {
                continue;
            }

            BedwarsStats stats = HypixelAPI.getCachedStats(playerName);

            if (stats != null && stats.isLoaded()) {
                BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
                boolean isThreat = false;

                if (maxThreatLevel.equals("HIGH")) {
                    isThreat = (threat == BedwarsStats.ThreatLevel.HIGH ||
                            threat == BedwarsStats.ThreatLevel.EXTREME);
                } else if (maxThreatLevel.equals("EXTREME")) {
                    isThreat = (threat == BedwarsStats.ThreatLevel.EXTREME);
                }

                if (isThreat) {
                    if (matchThreatService.isTeammate(mc, mc.thePlayer, player)) {
                        teammateThreatPlayers.add(playerName + " (" + threat.name() + ")");
                    } else {
                        enemyThreatPlayers.add(playerName + " (" + threat.name() + ")");
                    }
                }
            }
        }

        if (!teammateThreatPlayers.isEmpty()) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[Autoplay] " +
                            EnumChatFormatting.GREEN + "Teammate threats (ignored): " +
                            EnumChatFormatting.YELLOW + String.join(", ", teammateThreatPlayers)));
        }

        if (!enemyThreatPlayers.isEmpty()) {
            String threatMessage = EnumChatFormatting.RED + "Enemy threats detected: " +
                    EnumChatFormatting.YELLOW + String.join(", ", enemyThreatPlayers);
            if (ModConfig.isAutoplayRequeueEnabled() || state.gamePhase != GamePhase.IN_GAME) {
                requeueAutoplay(mc, threatMessage);
            } else {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[Autoplay] " + threatMessage));
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "[Autoplay] " +
                                EnumChatFormatting.GRAY + "Requeue is disabled. Staying in lobby."));
                state.autoplayEnabled = false;
            }
        } else {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[Autoplay] " +
                            EnumChatFormatting.GREEN + "No enemy threats detected! Stopping autoplay."));
            state.autoplayEnabled = false;
        }
    }

    private GeneratorResourceScan scanGeneratorResources(Minecraft mc, BlockPos generatorPos, boolean isDiamond) {
        int count = 0;
        boolean hasDesignatedIngotOnTop = false;
        double centerX = generatorPos.getX() + 0.5D;
        double centerY = generatorPos.getY() + 1.0D;
        double centerZ = generatorPos.getZ() + 0.5D;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem)) {
                continue;
            }

            EntityItem item = (EntityItem) entity;
            if (!isDesignatedGeneratorItem(item, isDiamond)) {
                continue;
            }

            double dx = Math.abs(item.posX - centerX);
            double dy = Math.abs(item.posY - centerY);
            double dz = Math.abs(item.posZ - centerZ);

            if (!hasDesignatedIngotOnTop && dx <= 0.75D && dz <= 0.75D) {
                double relativeY = item.posY - generatorPos.getY();
                if (relativeY >= 0.0D && relativeY <= 3.0D) {
                    hasDesignatedIngotOnTop = true;
                }
            }

            if (dx <= 3.0D && dy <= 3.0D && dz <= 3.0D) {
                count += item.getEntityItem().stackSize;
            }
        }

        return new GeneratorResourceScan(count, hasDesignatedIngotOnTop);
    }

    private boolean isDesignatedGeneratorItem(EntityItem item, boolean isDiamond) {
        if (item == null || item.getEntityItem() == null || item.getEntityItem().getItem() == null) {
            return false;
        }

        if (isDiamond) {
            return item.getEntityItem().getItem() == Items.diamond;
        }
        return item.getEntityItem().getItem() == Items.emerald;
    }

    static class GeneratorEntry {
        BlockPos position;
        boolean isDiamond;
        int resourceCount;
        boolean hasDesignatedIngotOnTop;
        long lastUpdate;

        GeneratorEntry(BlockPos pos, boolean diamond) {
            this.position = pos;
            this.isDiamond = diamond;
            this.resourceCount = 0;
            this.hasDesignatedIngotOnTop = false;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    private static class GeneratorResourceScan {
        int resourceCount;
        boolean hasDesignatedIngotOnTop;

        GeneratorResourceScan(int resourceCount, boolean hasDesignatedIngotOnTop) {
            this.resourceCount = resourceCount;
            this.hasDesignatedIngotOnTop = hasDesignatedIngotOnTop;
        }
    }
}
