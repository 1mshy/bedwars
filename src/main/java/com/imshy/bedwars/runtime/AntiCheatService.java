package com.imshy.bedwars.runtime;

import com.imshy.bedwars.ModConfig;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side hacker detector. Two modules:
 *   - AutoBlock: a swing-arm packet (S0BPacketAnimation type 0) arrives for an
 *     EntityOtherPlayerMP that is currently in the using-item state with a sword.
 *   - Scaffold:  per-tick state heuristics on EntityOtherPlayerMP — sprinting
 *     downward bridging, exact pitch lock, and per-tick pitch snap.
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
    private static final float PITCH_SNAP_THRESHOLD = 45.0F;
    private static final double SCAFFOLD_HORIZONTAL_SPEED_SQ = 0.18 * 0.18; // ~3.6 b/s
    private static final int LOCKED_PITCH_TICK_THRESHOLD = 8;
    private static final float PITCH_LOCK_EPSILON = 0.0001F;

    // AutoBlock confirmation: count blocking-while-swinging events before flagging
    private static final int AUTOBLOCK_MIN_HITS = 2;
    private static final long AUTOBLOCK_WINDOW_MS = 4000L;

    private final Map<UUID, Long> lastFlagAt = new HashMap<UUID, Long>();
    private final Map<UUID, ScaffoldState> scaffoldStates = new HashMap<UUID, ScaffoldState>();
    private final Map<UUID, AutoBlockState> autoBlockStates = new HashMap<UUID, AutoBlockState>();

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
    }

    public void shutdown() {
        uninstallHandler();
        scaffoldStates.clear();
        autoBlockStates.clear();
        lastFlagAt.clear();
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
            if (msg instanceof S0BPacketAnimation && ModConfig.isAntiCheatAutoBlockEnabled()) {
                try {
                    handleAnimation((S0BPacketAnimation) msg);
                } catch (Throwable t) {
                    // Never let a detection error break the network pipeline.
                    LOGGER.debug("AntiCheat animation handling error", t);
                }
            }
            super.channelRead(ctx, msg);
        }
    }

    // ---------------------------------------------------------------------
    // AutoBlock module (packet-driven)
    // ---------------------------------------------------------------------

    private void handleAnimation(S0BPacketAnimation packet) {
        // Animation type 0 = swing arm. Other values are hurt, eat, etc.
        if (packet.getAnimationType() != 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return;
        }
        Entity entity = mc.theWorld.getEntityByID(packet.getEntityID());
        if (!(entity instanceof EntityOtherPlayerMP)) {
            return;
        }
        EntityOtherPlayerMP player = (EntityOtherPlayerMP) entity;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) {
            return;
        }
        if (!player.isUsingItem()) {
            return;
        }

        // Confirm across multiple hits in a short window — avoids one-off network reordering.
        UUID id = player.getUniqueID();
        long now = System.currentTimeMillis();
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

    // ---------------------------------------------------------------------
    // Scaffold module (tick-driven)
    // ---------------------------------------------------------------------

    private void scanScaffold(Minecraft mc) {
        if (mc.theWorld == null) {
            return;
        }
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

            // (A) Sprint-bridge: sprinting + sharp downward pitch + actually moving.
            if (other.isSprinting() && pitch > DOWNWARD_PITCH_THRESHOLD && moving) {
                sendLocalFlag(other.getName(), id, "Scaffold (sprint-bridge)");
            }

            // (B) Pitch snap: large per-tick pitch jump while moving fast.
            if (deltaPitch > PITCH_SNAP_THRESHOLD && moving) {
                sendLocalFlag(other.getName(), id, "Scaffold (pitch-snap)");
            }

            // (C) Exact pitch lock at a sharp angle while horizontally moving.
            if (pitch > DOWNWARD_PITCH_THRESHOLD && deltaPitch < PITCH_LOCK_EPSILON && moving) {
                s.lockedPitchTicks++;
                if (s.lockedPitchTicks == LOCKED_PITCH_TICK_THRESHOLD) {
                    sendLocalFlag(other.getName(), id, "Scaffold (pitch-lock)");
                }
            } else {
                s.lockedPitchTicks = 0;
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
    }

    private static final class AutoBlockState {
        long firstHit;
        int hits;
    }
}
