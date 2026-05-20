package com.imshy.bedwars.runtime;

import com.imshy.bedwars.ModConfig;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Client-side hacker detector. Two modules:
 *   - AutoBlock: a swing-arm packet (S0BPacketAnimation type 0) arrives for an
 *     EntityOtherPlayerMP that is currently in the using-item state with a sword.
 *   - Scaffold:  per-tick state heuristics on EntityOtherPlayerMP. Gated by two
 *     hard preconditions before any heuristic can fire: the player must be
 *     holding a block item, and a non-air block update must have arrived near
 *     them within PLACEMENT_RECENCY_MS. Heuristics themselves require sustained
 *     suspicion (consecutive ticks or repeated hits within a window) — a single
 *     tick of looking down while sprinting is not enough.
 *
 * Flag chat messages are local-only (mc.thePlayer.addChatMessage) and rate-limited
 * per player+cheat to avoid spam.
 */
public class AntiCheatService {

    private static final Logger LOGGER = LogManager.getLogger(AntiCheatService.class);
    private static final String HANDLER_NAME = "bedwars_anticheat";
    private static final String PREFIX = EnumChatFormatting.RED + "[AntiCheat] " + EnumChatFormatting.RESET;

    // Scaffold heuristics
    private static final float DOWNWARD_PITCH_THRESHOLD = 75.0F;
    private static final float PITCH_SNAP_THRESHOLD = 55.0F;            // raised — fewer combat false positives
    private static final double SCAFFOLD_HORIZONTAL_SPEED_SQ = 0.18 * 0.18; // ~3.6 b/s
    private static final int LOCKED_PITCH_TICK_THRESHOLD = 20;          // ~1s of perfect lock
    private static final float PITCH_LOCK_EPSILON = 0.0001F;

    // Suspicion accumulation: a single tick is never enough to flag.
    private static final int SPRINT_BRIDGE_SUSPICION_TICKS = 8;         // ~0.4s sustained pattern
    private static final int PITCH_SNAP_SUSPICION_HITS = 3;             // 3 snaps within window
    private static final long PITCH_SNAP_WINDOW_MS = 4000L;

    // Placement gate: must have placed blocks near themselves recently.
    private static final long PLACEMENT_RECENCY_MS = 2500L;
    private static final double PLACEMENT_PROXIMITY_SQ = 5.0 * 5.0;     // 5-block radius
    private static final int PLACEMENT_BUFFER_LIMIT = 256;              // safety cap on the netty queue

    // AutoBlock confirmation: count blocking-while-swinging events before flagging
    private static final int AUTOBLOCK_MIN_HITS = 2;
    private static final long AUTOBLOCK_WINDOW_MS = 4000L;

    // CPS tracker: rolling window of swing-arm packets per player.
    private static final long CPS_WINDOW_MS = 1000L;                    // 1-second sliding window
    private static final int CPS_MAP_PRUNE_THRESHOLD = 256;             // size cap before pruning stale entries

    private final Map<UUID, Long> lastFlagAt = new HashMap<UUID, Long>();
    private final Map<UUID, ScaffoldState> scaffoldStates = new HashMap<UUID, ScaffoldState>();
    private final Map<UUID, AutoBlockState> autoBlockStates = new HashMap<UUID, AutoBlockState>();

    // Swing timestamps per player; written on the client thread (drainSwings), read on
    // the render thread.
    private final ConcurrentHashMap<UUID, Deque<Long>> swingTimes =
            new ConcurrentHashMap<UUID, Deque<Long>>();

    // Swing-arm packets arrive on the netty thread; onClientTick drains them on the
    // client thread, where the world/entity access and flag bookkeeping are safe.
    private static final int SWING_BUFFER_LIMIT = 256;
    private final ConcurrentLinkedQueue<SwingEvent> pendingSwings =
            new ConcurrentLinkedQueue<SwingEvent>();

    // Block updates arrive on the netty thread; main-thread scanScaffold drains.
    private final ConcurrentLinkedQueue<PlacementEvent> pendingPlacements =
            new ConcurrentLinkedQueue<PlacementEvent>();
    private final Deque<PlacementEvent> recentPlacements = new ArrayDeque<PlacementEvent>();

    private NetworkManager attachedManager;
    private boolean handlerInstalled;

    public void onClientTick(Minecraft mc) {
        if (!ModConfig.isAntiCheatEnabled()) {
            return;
        }
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            uninstallHandler();
            return;
        }
        ensureHandlerInstalled(mc);
        if (ModConfig.isAntiCheatScaffoldEnabled()) {
            scanScaffold(mc);
        }
        // Process swing-arm packets (CPS + AutoBlock) on the client thread.
        drainSwings(mc);
        // Bound swing-timestamp memory: scanScaffold's prune only runs when scaffold is on.
        if (swingTimes.size() > CPS_MAP_PRUNE_THRESHOLD) {
            pruneStateMaps(mc);
        }
    }

    public void shutdown() {
        uninstallHandler();
        scaffoldStates.clear();
        autoBlockStates.clear();
        lastFlagAt.clear();
        pendingPlacements.clear();
        recentPlacements.clear();
        pendingSwings.clear();
        swingTimes.clear();
    }

    // ---------------------------------------------------------------------
    // Packet handler installation
    // ---------------------------------------------------------------------

    private void ensureHandlerInstalled(Minecraft mc) {
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null) {
            uninstallHandler();
            return;
        }
        NetworkManager manager = netHandler.getNetworkManager();
        if (manager == null || manager.channel() == null || !manager.channel().isOpen()) {
            uninstallHandler();
            return;
        }
        if (handlerInstalled && manager == attachedManager) {
            return;
        }
        // Connection swapped (returning to lobby, joining a new game) — reinstall.
        uninstallHandler();
        try {
            ChannelPipeline pipeline = manager.channel().pipeline();
            if (pipeline.get(HANDLER_NAME) == null) {
                pipeline.addBefore("packet_handler", HANDLER_NAME, new InboundHandler());
            }
            attachedManager = manager;
            handlerInstalled = true;
        } catch (Throwable t) {
            LOGGER.warn("AntiCheat handler installation failed: {}", t.toString());
        }
    }

    private void uninstallHandler() {
        if (attachedManager != null && attachedManager.channel() != null) {
            try {
                ChannelPipeline pipeline = attachedManager.channel().pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
            } catch (Throwable ignored) {
                // Channel may already be closed.
            }
        }
        attachedManager = null;
        handlerInstalled = false;
    }

    private final class InboundHandler extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof S0BPacketAnimation) {
                // Capture the swing here (netty thread) but do all world/entity access and
                // flag bookkeeping on the client thread via drainSwings. The arrival
                // timestamp is taken now so CPS stays accurate despite deferred processing.
                try {
                    S0BPacketAnimation anim = (S0BPacketAnimation) msg;
                    boolean wanted = ModConfig.isAntiCheatCpsEnabled()
                            || ModConfig.isAntiCheatAutoBlockEnabled();
                    if (wanted && anim.getAnimationType() == 0
                            && pendingSwings.size() < SWING_BUFFER_LIMIT) {
                        pendingSwings.add(new SwingEvent(
                                anim.getEntityID(), System.currentTimeMillis()));
                    }
                } catch (Throwable t) {
                    // Never let a detection error break the network pipeline.
                    LOGGER.debug("AntiCheat animation handling error", t);
                }
            } else if (msg instanceof S23PacketBlockChange && ModConfig.isAntiCheatScaffoldEnabled()) {
                try {
                    enqueueBlockChange((S23PacketBlockChange) msg);
                } catch (Throwable t) {
                    LOGGER.debug("AntiCheat block-change handling error", t);
                }
            } else if (msg instanceof S22PacketMultiBlockChange && ModConfig.isAntiCheatScaffoldEnabled()) {
                try {
                    enqueueMultiBlockChange((S22PacketMultiBlockChange) msg);
                } catch (Throwable t) {
                    LOGGER.debug("AntiCheat multi-block-change handling error", t);
                }
            }
            super.channelRead(ctx, msg);
        }
    }

    // ---------------------------------------------------------------------
    // Swing processing (client thread) — CPS + AutoBlock
    // ---------------------------------------------------------------------

    /**
     * Drains swing-arm packets captured on the netty thread and processes them here on
     * the client thread, where world/entity access, AutoBlock state, and flag output are
     * all safe. Resolves each entity once and feeds both the CPS tracker and AutoBlock.
     */
    private void drainSwings(Minecraft mc) {
        if (mc.theWorld == null) {
            return;
        }
        boolean cps = ModConfig.isAntiCheatCpsEnabled();
        boolean autoBlock = ModConfig.isAntiCheatAutoBlockEnabled();
        SwingEvent ev;
        while ((ev = pendingSwings.poll()) != null) {
            if (!cps && !autoBlock) {
                continue; // flags toggled off after enqueue; just drain the queue
            }
            Entity entity = mc.theWorld.getEntityByID(ev.entityId);
            if (!(entity instanceof EntityOtherPlayerMP)) {
                continue;
            }
            EntityOtherPlayerMP player = (EntityOtherPlayerMP) entity;
            if (cps) {
                recordSwing(player.getUniqueID(), ev.when);
            }
            if (autoBlock) {
                handleAutoBlock(player, ev.when);
            }
        }
    }

    /**
     * AutoBlock heuristic for one swing. {@code isUsingItem()}/{@code getHeldItem()} are
     * read up to one tick after the swing arrived — intentional, since AutoBlock confirms
     * across {@link #AUTOBLOCK_MIN_HITS} hits within {@link #AUTOBLOCK_WINDOW_MS}, which
     * tolerates the deferral. Do NOT inline this back into channelRead: it touches the
     * live world entity and must stay on the client thread.
     */
    private void handleAutoBlock(EntityOtherPlayerMP player, long now) {
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) {
            return;
        }
        if (!player.isUsingItem()) {
            return;
        }

        // Confirm across multiple hits in a short window — avoids one-off network reordering.
        UUID id = player.getUniqueID();
        AutoBlockState st = autoBlockStates.get(id);
        if (st == null || now - st.firstHit > AUTOBLOCK_WINDOW_MS) {
            st = new AutoBlockState();
            st.firstHit = now;
            autoBlockStates.put(id, st);
        }
        st.hits++;
        if (st.hits >= AUTOBLOCK_MIN_HITS) {
            sendLocalFlag(player.getName(), id, "AutoBlock");
            st.hits = 0;
            st.firstHit = now;
        }
    }

    /**
     * Records a swing-arm timestamp for the source player. Unlike AutoBlock this counts
     * every swing regardless of held item, so the rolling 1-second window reflects raw
     * click rate. Called from drainSwings on the client thread; read by getCps on the
     * render thread, hence the concurrent map plus per-deque lock.
     */
    private void recordSwing(UUID id, long now) {
        Deque<Long> deque = swingTimes.get(id);
        if (deque == null) {
            deque = new ArrayDeque<Long>();
            Deque<Long> existing = swingTimes.putIfAbsent(id, deque);
            if (existing != null) {
                deque = existing;
            }
        }
        synchronized (deque) {
            deque.addLast(now);
            pruneSwings(deque, now);
        }
    }

    /**
     * Returns the swing count for the given player over the last {@link #CPS_WINDOW_MS} millis,
     * i.e. their current clicks-per-second. Read on the render thread.
     */
    public int getCps(UUID id) {
        if (id == null) {
            return 0;
        }
        Deque<Long> deque = swingTimes.get(id);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            pruneSwings(deque, System.currentTimeMillis());
            return deque.size();
        }
    }

    /** Drops timestamps older than the rolling window. Caller must hold the deque's monitor. */
    private void pruneSwings(Deque<Long> deque, long now) {
        Long head;
        while ((head = deque.peekFirst()) != null && now - head > CPS_WINDOW_MS) {
            deque.pollFirst();
        }
    }

    // ---------------------------------------------------------------------
    // Scaffold module (tick-driven)
    // ---------------------------------------------------------------------

    private void scanScaffold(Minecraft mc) {
        if (mc.theWorld == null) {
            return;
        }
        long now = System.currentTimeMillis();
        drainAndPrunePlacements(now);

        UUID localId = mc.thePlayer != null ? mc.thePlayer.getUniqueID() : null;
        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityOtherPlayerMP)) {
                continue;
            }
            EntityOtherPlayerMP other = (EntityOtherPlayerMP) obj;
            UUID id = other.getUniqueID();
            if (localId != null && localId.equals(id)) {
                continue;
            }

            ScaffoldState s = scaffoldStates.get(id);
            if (s == null) {
                s = new ScaffoldState();
                s.lastPitch = other.rotationPitch;
                s.lastX = other.posX;
                s.lastZ = other.posZ;
                scaffoldStates.put(id, s);
                continue;
            }

            float pitch = other.rotationPitch;
            float deltaPitch = Math.abs(pitch - s.lastPitch);
            double dx = other.posX - s.lastX;
            double dz = other.posZ - s.lastZ;
            double horizSpeedSq = dx * dx + dz * dz;
            boolean moving = horizSpeedSq > SCAFFOLD_HORIZONTAL_SPEED_SQ;

            // Hard gates: must hold a block AND have placed a block nearby recently.
            ItemStack held = other.getHeldItem();
            boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
            boolean placedNearby = holdingBlock && hasRecentPlacementNear(other, now);

            if (!placedNearby) {
                // Decay tick-based suspicion when the placement gate isn't satisfied.
                s.sprintBridgeTicks = 0;
                s.lockedPitchTicks = 0;
                // pitchSnapHits is window-bounded; let it expire naturally.
            } else {
                // (A) Sprint-bridge: needs sustained pattern, not a single tick.
                if (other.isSprinting() && pitch > DOWNWARD_PITCH_THRESHOLD && moving) {
                    s.sprintBridgeTicks++;
                    if (s.sprintBridgeTicks == SPRINT_BRIDGE_SUSPICION_TICKS) {
                        sendLocalFlag(other.getName(), id, "Scaffold (sprint-bridge)");
                    }
                } else {
                    s.sprintBridgeTicks = 0;
                }

                // (B) Pitch snap: needs N snaps within a window, not a single one.
                if (deltaPitch > PITCH_SNAP_THRESHOLD && moving) {
                    if (now - s.lastPitchSnapWindowStart > PITCH_SNAP_WINDOW_MS) {
                        s.lastPitchSnapWindowStart = now;
                        s.pitchSnapHits = 0;
                    }
                    s.pitchSnapHits++;
                    if (s.pitchSnapHits >= PITCH_SNAP_SUSPICION_HITS) {
                        sendLocalFlag(other.getName(), id, "Scaffold (pitch-snap)");
                        s.pitchSnapHits = 0;
                        s.lastPitchSnapWindowStart = now;
                    }
                }

                // (C) Pitch lock: ~1s of perfectly locked pitch while bridging.
                if (pitch > DOWNWARD_PITCH_THRESHOLD && deltaPitch < PITCH_LOCK_EPSILON && moving) {
                    s.lockedPitchTicks++;
                    if (s.lockedPitchTicks == LOCKED_PITCH_TICK_THRESHOLD) {
                        sendLocalFlag(other.getName(), id, "Scaffold (pitch-lock)");
                    }
                } else {
                    s.lockedPitchTicks = 0;
                }
            }

            s.lastPitch = pitch;
            s.lastX = other.posX;
            s.lastZ = other.posZ;
        }

        // Forget players that left the world to keep maps small.
        if (scaffoldStates.size() > 256) {
            pruneStateMaps(mc);
        }
    }

    private void enqueueBlockChange(S23PacketBlockChange p) {
        IBlockState state = p.getBlockState();
        if (state == null || state.getBlock() == Blocks.air) return;
        if (pendingPlacements.size() >= PLACEMENT_BUFFER_LIMIT) return;
        pendingPlacements.add(new PlacementEvent(p.getBlockPosition(), System.currentTimeMillis()));
    }

    private void enqueueMultiBlockChange(S22PacketMultiBlockChange p) {
        long now = System.currentTimeMillis();
        S22PacketMultiBlockChange.BlockUpdateData[] updates = p.getChangedBlocks();
        if (updates == null) return;
        for (S22PacketMultiBlockChange.BlockUpdateData u : updates) {
            IBlockState state = u.getBlockState();
            if (state == null || state.getBlock() == Blocks.air) continue;
            if (pendingPlacements.size() >= PLACEMENT_BUFFER_LIMIT) break;
            pendingPlacements.add(new PlacementEvent(u.getPos(), now));
        }
    }

    private void drainAndPrunePlacements(long now) {
        PlacementEvent ev;
        while ((ev = pendingPlacements.poll()) != null) {
            recentPlacements.addLast(ev);
        }
        long cutoff = now - PLACEMENT_RECENCY_MS;
        Iterator<PlacementEvent> it = recentPlacements.iterator();
        while (it.hasNext()) {
            if (it.next().when < cutoff) {
                it.remove();
            } else {
                break; // entries are append-ordered ≈ time-ordered
            }
        }
    }

    private boolean hasRecentPlacementNear(EntityOtherPlayerMP other, long now) {
        if (recentPlacements.isEmpty()) return false;
        double px = other.posX, py = other.posY, pz = other.posZ;
        long cutoff = now - PLACEMENT_RECENCY_MS;
        for (PlacementEvent ev : recentPlacements) {
            if (ev.when < cutoff) continue;
            double ex = ev.pos.getX() + 0.5;
            double ey = ev.pos.getY() + 0.5;
            double ez = ev.pos.getZ() + 0.5;
            double dx = ex - px, dy = ey - py, dz = ez - pz;
            if (dx * dx + dy * dy + dz * dz <= PLACEMENT_PROXIMITY_SQ) return true;
        }
        return false;
    }

    private void pruneStateMaps(Minecraft mc) {
        java.util.Set<UUID> live = new java.util.HashSet<UUID>();
        for (Object obj : mc.theWorld.playerEntities) {
            if (obj instanceof EntityPlayer) {
                live.add(((EntityPlayer) obj).getUniqueID());
            }
        }
        scaffoldStates.keySet().retainAll(live);
        autoBlockStates.keySet().retainAll(live);
        lastFlagAt.keySet().retainAll(live);
        swingTimes.keySet().retainAll(live);
    }

    // ---------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------

    private void sendLocalFlag(String playerName, UUID id, String cheatName) {
        if (playerName == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastFlagAt.get(id);
        long cooldown = ModConfig.getAntiCheatFlagCooldownMs();
        if (previous != null && now - previous < cooldown) {
            return;
        }
        lastFlagAt.put(id, now);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        String message = PREFIX
                + EnumChatFormatting.YELLOW + playerName
                + EnumChatFormatting.GRAY + " flagged for "
                + EnumChatFormatting.RED + cheatName;
        mc.thePlayer.addChatMessage(new ChatComponentText(message));
    }

    private static final class ScaffoldState {
        float lastPitch;
        double lastX;
        double lastZ;
        int lockedPitchTicks;
        int sprintBridgeTicks;
        long lastPitchSnapWindowStart;
        int pitchSnapHits;
    }

    private static final class AutoBlockState {
        long firstHit;
        int hits;
    }

    private static final class PlacementEvent {
        final BlockPos pos;
        final long when;
        PlacementEvent(BlockPos pos, long when) {
            this.pos = pos;
            this.when = when;
        }
    }

    /** A swing-arm packet captured on the netty thread, drained on the client thread. */
    private static final class SwingEvent {
        final int entityId;
        final long when;
        SwingEvent(int entityId, long when) {
            this.entityId = entityId;
            this.when = when;
        }
    }
}
