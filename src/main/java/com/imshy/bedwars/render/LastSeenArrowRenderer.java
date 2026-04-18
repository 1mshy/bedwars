package com.imshy.bedwars.render;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.runtime.EnemyTrackingService;
import com.imshy.bedwars.runtime.TrackedEnemy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import java.util.Map;

/**
 * Renders compass-style arrows around the centre of the screen pointing at the
 * last-known position of recently sighted threat players. Useful when a HIGH
 * or EXTREME enemy disappears behind cover or jukes off-screen — the arrow
 * keeps you oriented without needing the full nametag overlay.
 */
public class LastSeenArrowRenderer {

    private static final int ARROW_HALF_LENGTH = 8;
    private static final int ARROW_HALF_WIDTH = 5;
    private static final int LABEL_OFFSET = 14;

    public void render(ScaledResolution resolution, Minecraft mc, EnemyTrackingService enemyTrackingService) {
        if (!ModConfig.isModEnabled() || !ModConfig.isHudEnabled()) {
            return;
        }
        if (!ModConfig.isLastSeenArrowEnabled()) {
            return;
        }
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (enemyTrackingService == null) {
            return;
        }

        Map<String, TrackedEnemy> tracked = enemyTrackingService.getAllTrackedEnemies();
        if (tracked == null || tracked.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long maxAgeMs = ModConfig.getLastSeenArrowFreshSeconds() * 1000L;
        boolean threatOnly = ModConfig.isLastSeenArrowOnlyThreats();

        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Place arrows around an inset ring inside the screen so they sit at the
        // edge but never get clipped off the GUI safe area.
        int margin = 32;
        double radiusX = Math.max(40, centerX - margin);
        double radiusY = Math.max(40, centerY - margin);

        // Camera yaw in radians where 0 means looking south (+Z) in Minecraft.
        double yawRad = Math.toRadians(mc.thePlayer.rotationYaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -forwardZ;
        double rightZ = forwardX;

        FontRenderer fr = mc.fontRendererObj;

        for (Map.Entry<String, TrackedEnemy> entry : tracked.entrySet()) {
            TrackedEnemy data = entry.getValue();
            if (data == null || data.lastSeenTime == 0) {
                continue;
            }
            if (now - data.lastSeenTime > maxAgeMs) {
                continue;
            }

            String name = entry.getKey();
            BedwarsStats.ThreatLevel threat = BedwarsStats.ThreatLevel.UNKNOWN;
            BedwarsStats stats = HypixelAPI.getCachedStats(name);
            if (stats != null && stats.isLoaded()) {
                threat = stats.getThreatLevel();
            }

            if (threatOnly) {
                if (threat != BedwarsStats.ThreatLevel.HIGH && threat != BedwarsStats.ThreatLevel.EXTREME) {
                    continue;
                }
            }

            // Direction in world coordinates from the player to the last-seen point.
            double dx = data.lastSeenX - mc.thePlayer.posX;
            double dz = data.lastSeenZ - mc.thePlayer.posZ;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist < 0.5) {
                // Standing on top of us — no useful direction to draw.
                continue;
            }

            double forwardComponent = dx * forwardX + dz * forwardZ;
            double rightComponent = dx * rightX + dz * rightZ;

            // Angle 0 = forward, +PI/2 = right of player, -PI/2 = left.
            double angle = Math.atan2(rightComponent, forwardComponent);

            double sin = Math.sin(angle);
            double cos = Math.cos(angle);

            // Place the arrow on an ellipse around the screen centre so it
            // hugs the edge regardless of aspect ratio.
            double arrowX = centerX + radiusX * sin;
            double arrowY = centerY - radiusY * cos;

            float[] color = colorForThreat(threat);
            float alpha = computeAlpha(now - data.lastSeenTime, maxAgeMs);

            drawArrow(arrowX, arrowY, angle, color[0], color[1], color[2], alpha);

            String label = name + EnumChatFormatting.GRAY + " " + (int) horizontalDist + "m";
            int labelWidth = fr.getStringWidth(label);

            int labelX = (int) (arrowX - labelWidth / 2.0);
            int labelY = (int) (arrowY + LABEL_OFFSET);
            // Keep label inside the screen bounds.
            if (labelX < 2) labelX = 2;
            if (labelX + labelWidth > screenWidth - 2) labelX = screenWidth - 2 - labelWidth;
            if (labelY > screenHeight - 12) labelY = (int) (arrowY - LABEL_OFFSET - 8);

            fr.drawStringWithShadow(label, labelX, labelY, 0xFFFFFFFF);
        }
    }

    private static float[] colorForThreat(BedwarsStats.ThreatLevel threat) {
        switch (threat) {
            case EXTREME: return new float[]{0.55F, 0.0F, 0.0F};
            case HIGH:    return new float[]{0.95F, 0.20F, 0.20F};
            case MEDIUM:  return new float[]{0.95F, 0.85F, 0.20F};
            case LOW:     return new float[]{0.40F, 0.95F, 0.40F};
            case NICKED:  return new float[]{0.85F, 0.40F, 0.95F};
            default:      return new float[]{0.80F, 0.80F, 0.80F};
        }
    }

    /** Fade out arrows as their last-seen sample ages. */
    private static float computeAlpha(long ageMs, long maxAgeMs) {
        if (maxAgeMs <= 0) {
            return 1.0F;
        }
        float remaining = 1.0F - ((float) ageMs / (float) maxAgeMs);
        if (remaining < 0.2F) remaining = 0.2F;
        if (remaining > 1.0F) remaining = 1.0F;
        return remaining;
    }

    private static void drawArrow(double x, double y, double angle,
                                   float r, float g, float b, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.rotate((float) Math.toDegrees(angle), 0.0F, 0.0F, 1.0F);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        // Outline (drawn first, slightly larger).
        worldRenderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(0.0, -ARROW_HALF_LENGTH - 1, 0.0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
        worldRenderer.pos(-ARROW_HALF_WIDTH - 1, ARROW_HALF_LENGTH * 0.6, 0.0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
        worldRenderer.pos(ARROW_HALF_WIDTH + 1, ARROW_HALF_LENGTH * 0.6, 0.0).color(0.0F, 0.0F, 0.0F, alpha).endVertex();
        tessellator.draw();

        // Filled chevron.
        worldRenderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(0.0, -ARROW_HALF_LENGTH, 0.0).color(r, g, b, alpha).endVertex();
        worldRenderer.pos(-ARROW_HALF_WIDTH, ARROW_HALF_LENGTH * 0.6, 0.0).color(r, g, b, alpha).endVertex();
        worldRenderer.pos(ARROW_HALF_WIDTH, ARROW_HALF_LENGTH * 0.6, 0.0).color(r, g, b, alpha).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
