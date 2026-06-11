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

        List<String> lines = new ArrayList<String>(entries.size());
        int maxWidth = 0;
        for (KillFeedTracker.Entry entry : entries) {
            String line = buildLine(entry);
            lines.add(line);
            int w = fr.getStringWidth(line);
            if (w > maxWidth) {
                maxWidth = w;
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
        for (String line : lines) {
            // Right-anchored feeds keep lines flush against the screen edge.
            int drawX = rightAnchored ? origin[0] + maxWidth - fr.getStringWidth(line) : origin[0];
            fr.drawStringWithShadow(line, drawX, drawY, 0xFFFFFFFF);
            drawY += LINE_HEIGHT;
        }

        GlStateManager.popMatrix();
    }

    /**
     * "<threatColor>killer §7killed <teamColor>victim" or
     * "§7victim §8died" for killer-less entries.
     */
    private static String buildLine(KillFeedTracker.Entry entry) {
        if (entry.killerName == null) {
            return EnumChatFormatting.GRAY + entry.victimName + " "
                    + EnumChatFormatting.DARK_GRAY + "died";
        }
        String threatColor = EnumChatFormatting.GRAY.toString();
        BedwarsStats stats = HypixelAPI.getCachedStats(entry.killerName);
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

        int screenWidth = (int) (rawScreenWidth / scale);
        int screenHeight = (int) (rawScreenHeight / scale);
        int offX = ModConfig.getKillfeedOffsetX();
        int offY = ModConfig.getKillfeedOffsetY();
        int x, y;

        if ("TOP_RIGHT".equals(anchor)) {
            x = screenWidth - width - offX;
            y = offY;
        } else if ("BOTTOM_LEFT".equals(anchor)) {
            x = offX;
            y = screenHeight - height - offY;
        } else if ("BOTTOM_RIGHT".equals(anchor)) {
            x = screenWidth - width - offX;
            y = screenHeight - height - offY;
        } else {
            x = offX;
            y = offY;
        }

        return new int[]{x, y};
    }
}
