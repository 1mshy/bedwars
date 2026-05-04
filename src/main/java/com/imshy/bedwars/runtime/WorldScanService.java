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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class WorldScanService {
    private static final double GENERATOR_RESET_DISTANCE_SQ = 25.0D; // 5 blocks
    private static final long MIN_VALID_INTERVAL_MS = 250L;
    private static final long MAX_VALID_INTERVAL_MS = 120000L;
    private static final int MIN_PREDICTION_BASELINE = 2;

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
        int elapsedSeconds = GeneratorTierSchedule.elapsedSecondsFromMatchStart(state.matchStartTime);
        for (GeneratorEntry generator : state.trackedGenerators.values()) {
            if (!generator.hasEverHadResource) {
                continue;
            }
            BlockPos labelPos = generator.labelPosition != null ? generator.labelPosition : generator.position;
            renderer.renderGeneratorLabel(
                    labelPos,
                    generator.isDiamond,
                    generator.resourceCount,
                    partialTicks,
                    elapsedSeconds);
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
        long now = System.currentTimeMillis();
        Set<BlockPos> observedGenerators = new HashSet<BlockPos>();

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
                        // Many maps decorate the spawner pad with adjacent diamond/emerald blocks; collapse
                        // each cluster to a single canonical block (the lex-greatest x,z) so we track one entry.
                        if (isCollapsedClusterMember(mc, checkPos, isDiamond)) {
                            continue;
                        }

                        observedGenerators.add(checkPos);

                        GeneratorEntry existing = state.trackedGenerators.get(checkPos);
                        if (existing == null) {
                            existing = new GeneratorEntry(checkPos, isDiamond);
                            state.trackedGenerators.put(checkPos, existing);
                        }

                        GeneratorResourceScan scan = scanGeneratorResources(mc, checkPos, isDiamond);
                        updateGeneratorFromObservedScan(existing, scan.resourceCount, scan.hasDesignatedIngotOnTop, now);
                        existing.labelPosition = pickGeneratorLabelPosition(mc, checkPos, isDiamond);
                    }
                }
            }
        }

        Iterator<Map.Entry<BlockPos, GeneratorEntry>> iterator = state.trackedGenerators.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, GeneratorEntry> entry = iterator.next();
            GeneratorEntry generator = entry.getValue();
            BlockPos generatorPos = entry.getKey();

            if (playerPos.distanceSq(generatorPos) > scanRange * scanRange) {
                iterator.remove();
                continue;
            }

            // Drop entries that are no longer the canonical cluster anchor (cluster topology
            // changed, e.g. a neighbouring same-type block was placed/destroyed).
            if (!observedGenerators.contains(generatorPos)
                    && isCollapsedClusterMember(mc, generatorPos, generator.isDiamond)) {
                iterator.remove();
                continue;
            }

            if (isAnyPlayerNearGenerator(mc, generatorPos)) {
                resetGeneratorCount(generator, now);
                continue;
            }

            if (!observedGenerators.contains(generatorPos)) {
                predictGeneratorCountWhenUnobserved(generator, now);
            }
        }
    }

    private void updateGeneratorFromObservedScan(GeneratorEntry generator, int observedCount,
            boolean hasDesignatedIngotOnTop, long now) {
        int previousObservedCount = generator.lastObservedCount;
        long previousIncrementTimestamp = generator.lastIncrementTimestampMs;

        if (observedCount > previousObservedCount) {
            if (previousIncrementTimestamp > 0L) {
                long observedInterval = now - previousIncrementTimestamp;
                if (observedInterval >= MIN_VALID_INTERVAL_MS && observedInterval <= MAX_VALID_INTERVAL_MS) {
                    if (generator.estimatedGenerationIntervalMs <= 0L) {
                        generator.estimatedGenerationIntervalMs = observedInterval;
                    } else {
                        generator.estimatedGenerationIntervalMs = (generator.estimatedGenerationIntervalMs * 2L + observedInterval) / 3L;
                    }
                }
            }
            generator.lastIncrementTimestampMs = now;
        } else if (observedCount < previousObservedCount) {
            generator.lastIncrementTimestampMs = 0L;
        }

        generator.resourceCount = observedCount;
        generator.hasDesignatedIngotOnTop = hasDesignatedIngotOnTop;
        generator.lastObservedCount = observedCount;
        generator.lastObservedTimestampMs = now;
        generator.lastPredictionTimestampMs = now;
        generator.lastUpdate = now;
        if (observedCount > 0) {
            generator.hasEverHadResource = true;
        }
        if (observedCount >= MIN_PREDICTION_BASELINE) {
            generator.hasPredictionBaseline = true;
        }
    }

    private void predictGeneratorCountWhenUnobserved(GeneratorEntry generator, long now) {
        if (!generator.hasPredictionBaseline || generator.estimatedGenerationIntervalMs <= 0L) {
            return;
        }

        if (generator.lastPredictionTimestampMs <= 0L) {
            generator.lastPredictionTimestampMs = now;
            return;
        }

        if (generator.resourceCount < MIN_PREDICTION_BASELINE) {
            generator.resourceCount = MIN_PREDICTION_BASELINE;
        }

        long elapsed = now - generator.lastPredictionTimestampMs;
        if (elapsed < generator.estimatedGenerationIntervalMs) {
            return;
        }

        int generated = (int) (elapsed / generator.estimatedGenerationIntervalMs);
        if (generated <= 0) {
            return;
        }

        generator.resourceCount += generated;
        generator.lastPredictionTimestampMs += generated * generator.estimatedGenerationIntervalMs;
        generator.lastUpdate = now;
    }

    private void resetGeneratorCount(GeneratorEntry generator, long now) {
        generator.resourceCount = 0;
        generator.hasDesignatedIngotOnTop = false;
        generator.lastObservedCount = 0;
        generator.lastPredictionTimestampMs = now;
        generator.hasPredictionBaseline = false;
        generator.lastUpdate = now;
    }

    private boolean isAnyPlayerNearGenerator(Minecraft mc, BlockPos generatorPos) {
        double centerX = generatorPos.getX() + 0.5D;
        double centerY = generatorPos.getY() + 1.0D;
        double centerZ = generatorPos.getZ() + 0.5D;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player.isDead) {
                continue;
            }
            double dx = player.posX - centerX;
            double dy = player.posY - centerY;
            double dz = player.posZ - centerZ;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq <= GENERATOR_RESET_DISTANCE_SQ) {
                return true;
            }
        }
        return false;
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

    /**
     * True when this block is part of a multi-block generator pad whose canonical anchor
     * (lex-greatest x,z,y) is a different block. Returns false for the canonical block itself
     * and for isolated single-block pads. Cluster discovery traverses through same-type blocks
     * regardless of whether they have air above, so a pad split by decoration (glowstone,
     * beacon) still collapses to one anchor.
     */
    private boolean isCollapsedClusterMember(Minecraft mc, BlockPos pos, boolean isDiamond) {
        Set<BlockPos> cluster = findGeneratorCluster(mc, pos, isDiamond);
        if (cluster.size() <= 1) {
            return false;
        }
        BlockPos canonical = pos;
        for (BlockPos p : cluster) {
            if (lexGreater(p, canonical)) {
                canonical = p;
            }
        }
        return !pos.equals(canonical);
    }

    private static boolean lexGreater(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) return a.getX() > b.getX();
        if (a.getZ() != b.getZ()) return a.getZ() > b.getZ();
        return a.getY() > b.getY();
    }

    private Set<BlockPos> findGeneratorCluster(Minecraft mc, BlockPos start, boolean isDiamond) {
        Block target = isDiamond ? Blocks.diamond_block : Blocks.emerald_block;
        Set<BlockPos> result = new HashSet<BlockPos>();
        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        queue.add(start);
        while (!queue.isEmpty() && result.size() < 32) {
            BlockPos cur = queue.removeFirst();
            if (!result.add(cur)) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos n = new BlockPos(cur.getX() + dx, cur.getY(), cur.getZ() + dz);
                    if (result.contains(n)) continue;
                    if (!mc.theWorld.isBlockLoaded(n)) continue;
                    if (mc.theWorld.getBlockState(n).getBlock() != target) continue;
                    queue.add(n);
                }
            }
        }
        return result;
    }

    /**
     * Pick the cluster member that should host the generator label: the candidate (block with
     * air above) closest to dropped diamonds/emeralds. Falls back to the canonical anchor when
     * no items are present.
     */
    private BlockPos pickGeneratorLabelPosition(Minecraft mc, BlockPos anchor, boolean isDiamond) {
        Set<BlockPos> cluster = findGeneratorCluster(mc, anchor, isDiamond);
        List<BlockPos> candidates = new ArrayList<BlockPos>();
        for (BlockPos p : cluster) {
            if (mc.theWorld.isAirBlock(p.up())) candidates.add(p);
        }
        if (candidates.size() <= 1) {
            return anchor;
        }

        double centerX = 0.0D;
        double centerZ = 0.0D;
        for (BlockPos p : candidates) {
            centerX += p.getX() + 0.5D;
            centerZ += p.getZ() + 0.5D;
        }
        centerX /= candidates.size();
        centerZ /= candidates.size();
        int padTopY = anchor.getY() + 1;

        double itemSumX = 0.0D;
        double itemSumZ = 0.0D;
        int itemCount = 0;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem)) continue;
            EntityItem item = (EntityItem) entity;
            if (!isDesignatedGeneratorItem(item, isDiamond)) continue;
            double dx = item.posX - centerX;
            double dz = item.posZ - centerZ;
            if (Math.abs(item.posY - padTopY) > 4.0D) continue;
            if (dx * dx + dz * dz > 9.0D) continue;
            itemSumX += item.posX;
            itemSumZ += item.posZ;
            itemCount++;
        }

        if (itemCount == 0) {
            return anchor;
        }

        double targetX = itemSumX / itemCount;
        double targetZ = itemSumZ / itemCount;

        BlockPos best = anchor;
        double bestDsq = Double.MAX_VALUE;
        for (BlockPos p : candidates) {
            double dx = (p.getX() + 0.5D) - targetX;
            double dz = (p.getZ() + 0.5D) - targetZ;
            double dsq = dx * dx + dz * dz;
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = p;
            }
        }
        return best;
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
        BlockPos labelPosition;
        boolean isDiamond;
        int resourceCount;
        boolean hasDesignatedIngotOnTop;
        long lastUpdate;
        int lastObservedCount;
        long lastObservedTimestampMs;
        long lastIncrementTimestampMs;
        long estimatedGenerationIntervalMs;
        long lastPredictionTimestampMs;
        boolean hasPredictionBaseline;
        boolean hasEverHadResource;

        GeneratorEntry(BlockPos pos, boolean diamond) {
            this.position = pos;
            this.labelPosition = pos;
            this.isDiamond = diamond;
            this.resourceCount = 0;
            this.hasDesignatedIngotOnTop = false;
            this.lastUpdate = System.currentTimeMillis();
            this.lastObservedCount = 0;
            this.lastObservedTimestampMs = 0L;
            this.lastIncrementTimestampMs = 0L;
            this.estimatedGenerationIntervalMs = 0L;
            this.lastPredictionTimestampMs = 0L;
            this.hasPredictionBaseline = false;
            this.hasEverHadResource = false;
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
