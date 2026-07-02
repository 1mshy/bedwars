package com.imshy.bedwars;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Client-thread marshaling helpers.
 *
 * <p>Forge 1.8.9's {@link Minecraft#addScheduledTask} runs a {@code Runnable}
 * INLINE when called from the client thread and queues it to the next tick
 * otherwise. Routing every async ({@link HypixelAPI} executor pool) or netty
 * callback side effect through here makes "only touch client state on the
 * client thread" the default, so a raw off-thread {@code addChatMessage} or
 * world-list iteration becomes the visible exception rather than the norm.
 *
 * <p>This is the single home for the dominant historical bug class in this mod
 * (async callbacks mutating the chat GUI / world list / plain collections off
 * the main thread).
 */
public final class ClientThread {

    /** Standard mod chat prefix (gold {@code [BW] }). */
    public static final String PREFIX = EnumChatFormatting.GOLD + "[BW] ";

    private ClientThread() {
    }

    /**
     * Run {@code r} on the client thread: inline if already on it, otherwise
     * queued to the next client tick. Falls back to running inline when no
     * Minecraft instance exists (unit-test / headless contexts).
     */
    public static void run(Runnable r) {
        if (r == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            r.run();
            return;
        }
        mc.addScheduledTask(r);
    }

    /** Send a chat component to the local player on the client thread (no-op if absent). */
    public static void chat(final IChatComponent component) {
        if (component == null) {
            return;
        }
        run(new Runnable() {
            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(component);
                }
            }
        });
    }

    /** Send a plain string as chat to the local player on the client thread. */
    public static void chat(String message) {
        if (message != null) {
            chat(new ChatComponentText(message));
        }
    }

    /** Send a {@code [BW] }-prefixed message to the local player on the client thread. */
    public static void modChat(String message) {
        if (message != null) {
            chat(new ChatComponentText(PREFIX + message));
        }
    }

    /**
     * Wrap a {@link HypixelAPI.StatsCallback} so BOTH callbacks fire on the
     * client thread. Use this at any async stat consumer that touches client
     * state (chat GUI, world list, unsynchronized collections) — the delegate
     * body can then be written as if it were plain main-thread code.
     */
    public static HypixelAPI.StatsCallback marshal(final HypixelAPI.StatsCallback delegate) {
        return new HypixelAPI.StatsCallback() {
            @Override
            public void onStatsLoaded(final BedwarsStats stats) {
                run(new Runnable() {
                    @Override
                    public void run() {
                        delegate.onStatsLoaded(stats);
                    }
                });
            }

            @Override
            public void onError(final String error) {
                run(new Runnable() {
                    @Override
                    public void run() {
                        delegate.onError(error);
                    }
                });
            }
        };
    }
}
