package com.imshy.bedwars.render;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.runtime.KillFeedTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone killfeed HUD element: compact lines, newest first, anchored to a
 * configurable screen corner (default top-right). Killer names are colored by
 * cached threat level AT RENDER TIME (gray until stats land — this renderer
 * never fetches; the existing fetch throttles fill the cache).
 */
public class KillFeedRenderer {

    private static final int LINE_HEIGHT = 10;

    /** Line + measured width memoized per entry, invalidated when the cached
     *  stats instance changes (same identity trick as TabStatsInjector). */
    private static final class CachedLine {
        final BedwarsStats statsIdentity;
        final String line;
        final int width;

        CachedLine(BedwarsStats statsIdentity, String line, int width) {
            this.statsIdentity = statsIdentity;
            this.line = line;
            this.width = width;
        }
    }

    private final java.util.HashMap<KillFeedTracker.Entry, CachedLine> lineCache =
            new java.util.HashMap<KillFeedTracker.Entry, CachedLine>();

    public void render(ScaledResolution resolution, Minecraft mc, KillFeedTracker tracker) {
        if (!ModConfig.isModEnabled() || !ModConfig.isKillfeedEnabled()) {
            return;
        }
        if (tracker.isEmpty()) {
            return;
        }

        List<KillFeedTracker.Entry> entries = tracker.getActiveEntries(System.currentTimeMillis());
        if (entries.isEmpty()) {
            return;
        }

        FontRenderer fr = mc.fontRendererObj;
        double scale = ModConfig.getHudScale();

        // Expired entries leave stale keys behind; the tracker holds at most 6
        // active, so the cache only grows across match transitions.
        if (lineCache.size() > 24) {
            lineCache.clear();
        }

        List<String> lines = new ArrayList<String>(entries.size());
        int[] widths = new int[entries.size()];
        int maxWidth = 0;
        int index = 0;
        for (KillFeedTracker.Entry entry : entries) {
            BedwarsStats stats = entry.killerName == null
                    ? null
                    : HypixelAPI.getCachedStats(entry.killerName);
            CachedLine cached = lineCache.get(entry);
            if (cached == null || cached.statsIdentity != stats) {
                String line = buildLine(entry, stats);
                cached = new CachedLine(stats, line, fr.getStringWidth(line));
                lineCache.put(entry, cached);
            }
            lines.add(cached.line);
            widths[index++] = cached.width;
            if (cached.width > maxWidth) {
                maxWidth = cached.width;
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0);

        String anchor = ModConfig.getKillfeedAnchor();
        boolean rightAnchored = ModConfig.isKillfeedCustomPosition()
                ? HudAnchorMath.ANCHOR_RIGHT.equals(ModConfig.getKillfeedAnchorX())
                : anchor.endsWith("RIGHT");
        int[] origin = computeOrigin(anchor, resolution.getScaledWidth(), resolution.getScaledHeight(),
                scale, maxWidth, lines.size() * LINE_HEIGHT);

        int drawY = origin[1];
        for (int i = 0; i < lines.size(); i++) {
            // Right-anchored feeds keep lines flush against the screen edge.
            int drawX = rightAnchored ? origin[0] + maxWidth - widths[i] : origin[0];
            fr.drawStringWithShadow(lines.get(i), drawX, drawY, 0xFFFFFFFF);
            drawY += LINE_HEIGHT;
        }

        GlStateManager.popMatrix();
    }

    /**
     * "<threatColor>killer §7killed <teamColor>victim" or
     * "§7victim §8died" for killer-less entries.
     */
    private static String buildLine(KillFeedTracker.Entry entry, BedwarsStats stats) {
        if (entry.killerName == null) {
            return EnumChatFormatting.GRAY + entry.victimName + " "
                    + EnumChatFormatting.DARK_GRAY + "died";
        }
        String threatColor = EnumChatFormatting.GRAY.toString();
        if (stats != null && stats.isLoaded()) {
            threatColor = stats.getThreatColor();
        }
        String teamColor = entry.victimTeamColorCode == null || entry.victimTeamColorCode.isEmpty()
                ? EnumChatFormatting.GRAY.toString()
                : entry.victimTeamColorCode;
        return threatColor + entry.killerName + " "
                + EnumChatFormatting.GRAY + "killed "
                + teamColor + entry.victimName;
    }

    /**
     * Feed origin in the scale-divided space the feed draws in. The HUD-editor
     * anchor+offset override (unscaled ScaledResolution pixels) is resolved in
     * raw space first and converted; the legacy killfeedAnchor corner + margin
     * behavior is unchanged when no override is active.
     */
    private static int[] computeOrigin(String anchor, int rawScreenWidth, int rawScreenHeight,
                                       double scale, int width, int height) {
        if (ModConfig.isKillfeedCustomPosition()) {
            int rawX = HudAnchorMath.computeX(ModConfig.getKillfeedAnchorX(), rawScreenWidth,
                    (int) Math.round(width * scale), ModConfig.getKillfeedAnchorOffsetX());
            int rawY = HudAnchorMath.computeY(ModConfig.getKillfeedAnchorY(), rawScreenHeight,
                    (int) Math.round(height * scale), ModConfig.getKillfeedAnchorOffsetY());
            return new int[]{(int) Math.round(rawX / scale), (int) Math.round(rawY / scale)};
        }

        // Delegate to the unit-tested helper so the editor proxy and the live
        // feed can never drift apart.
        int screenWidth = (int) (rawScreenWidth / scale);
        int screenHeight = (int) (rawScreenHeight / scale);
        return HudAnchorMath.legacyCornerOrigin(anchor, screenWidth, screenHeight,
                width, height, ModConfig.getKillfeedOffsetX(), ModConfig.getKillfeedOffsetY());
    }
}
