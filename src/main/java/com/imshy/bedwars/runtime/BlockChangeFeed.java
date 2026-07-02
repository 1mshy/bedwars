package com.imshy.bedwars.runtime;

import com.imshy.bedwars.ModConfig;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Blocks;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Passive feed of server block-change packets (S22/S23), INCLUDING the air
 * updates {@link AntiCheatService} discards — air updates are how block
 * BREAKS appear, which the bed-tamper alarm needs.
 *
 * <p>Mirrors the AntiCheatService netty contract exactly: {@code channelRead}
 * is enqueue-only (a timestamp and a BlockPos — no world or entity access) and
 * everything else runs on the client thread via {@link #drainNew}. Do NOT add
 * any processing to the netty path. The handler reads packets and always
 * forwards them unmodified — nothing outbound is touched.
 */
public class BlockChangeFeed {

    private static final Logger LOGGER = LogManager.getLogger(BlockChangeFeed.class);
    private static final String HANDLER_NAME = "bedwars_blockfeed";
    private static final int BUFFER_LIMIT = 1024;

    /** One block update: position, whether the new state is air (= a break). */
    public static final class BlockChangeEvent {
        public final BlockPos pos;
        public final boolean isAir;
        public final long when;

        public BlockChangeEvent(BlockPos pos, boolean isAir, long when) {
            this.pos = pos;
            this.isAir = isAir;
            this.when = when;
        }
    }

    private final ConcurrentLinkedQueue<BlockChangeEvent> pending =
            new ConcurrentLinkedQueue<BlockChangeEvent>();
    // ConcurrentLinkedQueue.size() is O(n) — the netty path uses this counter
    // for the buffer-limit check instead.
    private final java.util.concurrent.atomic.AtomicInteger pendingCount =
            new java.util.concurrent.atomic.AtomicInteger();

    private NetworkManager attachedManager;
    private boolean handlerInstalled;

    /** Keeps the pipeline handler attached to the live connection. Client thread only. */
    public void onClientTick(Minecraft mc) {
        if (!ModConfig.isBridgeRadarEnabled() && !ModConfig.isBedTamperAlarmEnabled()) {
            uninstall();
            return;
        }
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            uninstall();
            return;
        }
        ensureInstalled(mc);
    }

    /** Drains all captured events (client thread; single consumer). */
    public List<BlockChangeEvent> drainNew() {
        if (pending.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<BlockChangeEvent> out = new ArrayList<BlockChangeEvent>();
        BlockChangeEvent ev;
        while ((ev = pending.poll()) != null) {
            pendingCount.decrementAndGet();
            out.add(ev);
        }
        return out;
    }

    public void shutdown() {
        uninstall();
        pending.clear();
        pendingCount.set(0);
    }

    private void ensureInstalled(Minecraft mc) {
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null) {
            uninstall();
            return;
        }
        NetworkManager manager = netHandler.getNetworkManager();
        if (manager == null || manager.channel() == null || !manager.channel().isOpen()) {
            uninstall();
            return;
        }
        if (handlerInstalled && manager == attachedManager) {
            return;
        }
        uninstall();
        try {
            ChannelPipeline pipeline = manager.channel().pipeline();
            if (pipeline.get(HANDLER_NAME) == null) {
                pipeline.addBefore("packet_handler", HANDLER_NAME, new InboundHandler());
            }
            attachedManager = manager;
            handlerInstalled = true;
        } catch (Throwable t) {
            LOGGER.warn("BlockChangeFeed handler installation failed: {}", t.toString());
        }
    }

    private void uninstall() {
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
            // Enqueue-only — see the class javadoc. Never let a capture error
            // break the network pipeline.
            if (msg instanceof S23PacketBlockChange) {
                try {
                    S23PacketBlockChange p = (S23PacketBlockChange) msg;
                    IBlockState state = p.getBlockState();
                    if (state != null && pendingCount.get() < BUFFER_LIMIT) {
                        pending.add(new BlockChangeEvent(p.getBlockPosition(),
                                state.getBlock() == Blocks.air, System.currentTimeMillis()));
                        pendingCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    LOGGER.debug("BlockChangeFeed S23 handling error", t);
                }
            } else if (msg instanceof S22PacketMultiBlockChange) {
                try {
                    long now = System.currentTimeMillis();
                    S22PacketMultiBlockChange.BlockUpdateData[] updates =
                            ((S22PacketMultiBlockChange) msg).getChangedBlocks();
                    if (updates != null) {
                        for (S22PacketMultiBlockChange.BlockUpdateData u : updates) {
                            if (pendingCount.get() >= BUFFER_LIMIT) {
                                break;
                            }
                            IBlockState state = u.getBlockState();
                            if (state == null) {
                                continue;
                            }
                            pending.add(new BlockChangeEvent(u.getPos(),
                                    state.getBlock() == Blocks.air, now));
                            pendingCount.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.debug("BlockChangeFeed S22 handling error", t);
                }
            }
            super.channelRead(ctx, msg);
        }
    }
}
