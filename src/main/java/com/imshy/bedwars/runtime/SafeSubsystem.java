package com.imshy.bedwars.runtime;

import com.imshy.bedwars.ClientThread;

import net.minecraft.client.renderer.GlStateManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crash shield for the mod's event handlers: a throwing subsystem loses ONE
 * HUD element instead of hard-crashing the client mid-game.
 *
 * <p>Each guarded body runs under a Throwable catch. A failure is logged with
 * its full stack once per unique (subsystem, exception class, top frame)
 * signature — repeats only count. After {@link #QUARANTINE_THRESHOLD} failures
 * a subsystem is quarantined (skipped entirely) and the player gets one local
 * chat notice; {@code /bw health} lists and re-arms quarantined subsystems.
 *
 * <p>Render guards additionally restore the GL flags the mod's renderers
 * toggle, so a mid-render throw doesn't corrupt the rest of the frame. (A
 * leaked matrix push cannot be rebalanced generically — the quarantine is
 * what stops that from recurring every frame.)
 */
public final class SafeSubsystem {

    private static final Logger LOGGER = LogManager.getLogger(SafeSubsystem.class);
    static final int QUARANTINE_THRESHOLD = 3;
    /**
     * Quarantine is a circuit breaker, not a permanent kill: after this
     * cooldown the subsystem auto-re-arms. Transient bad state (a weird tab
     * list, a despawning entity) recovers on its own; a genuinely broken
     * subsystem re-trips within a few ticks and stays effectively dark.
     */
    static final long QUARANTINE_COOLDOWN_MS = 60_000L;

    private static final class State {
        volatile int failures;
        volatile boolean quarantined;
        volatile long quarantinedUntil;
        volatile boolean noticeSent;
        final Set<String> seenSignatures = ConcurrentHashMap.newKeySet();
    }

    private static final ConcurrentHashMap<String, State> STATES =
            new ConcurrentHashMap<String, State>();

    private SafeSubsystem() {
    }

    /** Guard a non-render body. Skipped entirely while quarantined. */
    public static void run(String subsystem, Runnable body) {
        State state = stateFor(subsystem);
        if (isCurrentlyQuarantined(subsystem, state)) {
            return;
        }
        try {
            body.run();
        } catch (Throwable t) {
            record(subsystem, state, t);
        }
    }

    /**
     * Guard a render body; on a throw, rebalances the modelview matrix stack
     * back to its pre-call depth and restores the GL flags the mod's
     * renderers toggle.
     */
    public static void runRender(String subsystem, Runnable body) {
        State state = stateFor(subsystem);
        if (isCurrentlyQuarantined(subsystem, state)) {
            return;
        }
        int matrixDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        boolean lightingWasOn = GL11.glIsEnabled(GL11.GL_LIGHTING);
        try {
            body.run();
        } catch (Throwable t) {
            try {
                while (GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) > matrixDepth) {
                    GlStateManager.popMatrix();
                }
                GlStateManager.enableTexture2D();
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                GlStateManager.disableBlend();
                if (lightingWasOn) {
                    GlStateManager.enableLighting();
                } else {
                    GlStateManager.disableLighting();
                }
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glLineWidth(1.0F);
            } catch (Throwable ignored) {
                // GL context itself is broken; nothing more we can do here.
            }
            record(subsystem, state, t);
        }
    }

    /** Checks quarantine, auto-re-arming once the cooldown has elapsed. */
    private static boolean isCurrentlyQuarantined(String subsystem, State state) {
        if (!state.quarantined) {
            return false;
        }
        if (System.currentTimeMillis() >= state.quarantinedUntil) {
            state.quarantined = false;
            state.failures = 0;
            LOGGER.info("Subsystem '{}' auto-re-armed after quarantine cooldown", subsystem);
            return false;
        }
        return true;
    }

    /** Health rows for {@code /bw health}: "name: OK" / "name: QUARANTINED (n errors)". */
    public static List<String> healthReport() {
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, State> e : STATES.entrySet()) {
            State s = e.getValue();
            if (s.quarantined) {
                rows.add(e.getKey() + ": QUARANTINED (" + s.failures + " errors)");
            } else if (s.failures > 0) {
                rows.add(e.getKey() + ": OK (" + s.failures + " recovered errors)");
            } else {
                rows.add(e.getKey() + ": OK");
            }
        }
        java.util.Collections.sort(rows);
        return rows;
    }

    /** Re-arms every quarantined subsystem and resets failure counters. */
    public static int rearmAll() {
        int rearmed = 0;
        for (State s : STATES.values()) {
            if (s.quarantined) {
                rearmed++;
            }
            s.quarantined = false;
            s.failures = 0;
            s.seenSignatures.clear();
        }
        return rearmed;
    }

    static boolean isQuarantined(String subsystem) {
        State s = STATES.get(subsystem);
        return s != null && s.quarantined;
    }

    /** Test hook. */
    static void resetForTests() {
        STATES.clear();
    }

    private static State stateFor(String subsystem) {
        State state = STATES.get(subsystem);
        if (state == null) {
            State fresh = new State();
            State existing = STATES.putIfAbsent(subsystem, fresh);
            state = existing != null ? existing : fresh;
        }
        return state;
    }

    private static void record(String subsystem, State state, Throwable t) {
        state.failures++;
        String top = "";
        StackTraceElement[] stack = t.getStackTrace();
        if (stack != null && stack.length > 0) {
            top = stack[0].toString();
        }
        String signature = subsystem + "|" + t.getClass().getName() + "|" + top;
        if (state.seenSignatures.add(signature)) {
            LOGGER.error("Subsystem '" + subsystem + "' threw (failure "
                    + state.failures + "/" + QUARANTINE_THRESHOLD + ")", t);
        } else {
            LOGGER.warn("Subsystem '{}' threw again ({}): {}", subsystem,
                    state.failures, t.toString());
        }
        if (!state.quarantined && state.failures >= QUARANTINE_THRESHOLD) {
            state.quarantined = true;
            state.quarantinedUntil = System.currentTimeMillis() + QUARANTINE_COOLDOWN_MS;
            // One chat notice per subsystem per session — the auto-re-arm/
            // re-trip cycle must not re-spam chat every cooldown.
            if (!state.noticeSent) {
                state.noticeSent = true;
                ClientThread.modChat("§cSubsystem '" + subsystem
                        + "' paused after repeated errors (auto-retries in "
                        + (QUARANTINE_COOLDOWN_MS / 1000) + "s) — §e/bw health§c to inspect/re-arm.");
            }
        }
    }
}
