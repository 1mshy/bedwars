package com.imshy.bedwars.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;

import com.imshy.bedwars.runtime.BridgeRadarService;
import com.imshy.bedwars.runtime.GeneratorTierSchedule;
import com.imshy.bedwars.runtime.TrackedEnemy;
import com.imshy.bedwars.runtime.TrackedFireball;
import com.imshy.bedwars.runtime.TrackedProjectile;

import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.List;

public class BedwarsOverlayRenderer {
    public void renderThreatLabel(EntityPlayer player, String text, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRendererObj;
        RenderManager renderManager = mc.getRenderManager();

        double distance = player.getDistanceSqToEntity(mc.thePlayer);
        if (distance > 4096.0D) {
            return;
        }

        float heightOffset = NameTagManager.getInstance().computeHeightOffset(player, NameTagManager.NameTagLayer.THREAT_LEVEL);

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + heightOffset, (float) z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        float scale = 0.016666668F * 1.5F;
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;

        GlStateManager.disableTexture2D();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(-halfWidth - 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(-halfWidth - 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(halfWidth + 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(halfWidth + 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        fontRenderer.drawString(text, -halfWidth, 0, 0xFFFFFFFF);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public void renderGeneratorLabel(BlockPos position, boolean isDiamond, int resourceCount,
            float partialTicks, int matchElapsedSeconds) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRendererObj;

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        double x = position.getX() + 0.5 - playerX;
        double y = position.getY() + 1.5 - playerY;
        double z = position.getZ() + 0.5 - playerZ;

        double distSq = x * x + y * y + z * z;
        int renderDist = com.imshy.bedwars.ModConfig.getGeneratorLabelRenderDistance();
        if (distSq > renderDist * renderDist) {
            return;
        }

        int untilSpawn = GeneratorTierSchedule.secondsUntilNextSpawn(matchElapsedSeconds);
        String color = isDiamond ? EnumChatFormatting.AQUA.toString() : EnumChatFormatting.GREEN.toString();
        String text = color + resourceCount
                + EnumChatFormatting.GRAY + " \u00b7 "
                + EnumChatFormatting.WHITE + untilSpawn + "s";

        float distance = (float) Math.sqrt(distSq);
        float baseScale = 0.016666668F * 2.5F;
        float distanceScale = Math.max(1.0F, distance / 20.0F);
        float scale = baseScale * distanceScale;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;

        GlStateManager.disableTexture2D();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        float bgAlpha = 0.4F;
        worldRenderer.pos(-halfWidth - 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(-halfWidth - 2, 10, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 2, 10, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        fontRenderer.drawString(text, -halfWidth, 0, 0xFFFFFFFF);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public void renderInvisiblePlayerIndicator(EntityPlayer player, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRendererObj;

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        double targetX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double targetY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double targetZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        double x = targetX - playerX;
        double y = targetY - playerY + NameTagManager.getInstance().computeHeightOffset(player, NameTagManager.NameTagLayer.INVISIBLE);
        double z = targetZ - playerZ;

        double distSq = x * x + y * y + z * z;
        int detectionRange = com.imshy.bedwars.ModConfig.getInvisibleDetectionRange();
        if (distSq > detectionRange * detectionRange * 4) {
            return;
        }

        String text = EnumChatFormatting.LIGHT_PURPLE + "👁 INVISIBLE";
        float scale = 0.016666668F * 2.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;

        GlStateManager.disableTexture2D();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        float bgAlpha = 0.5F;
        worldRenderer.pos(-halfWidth - 2, -2, 0.0D).color(0.5F, 0.0F, 0.5F, bgAlpha).endVertex();
        worldRenderer.pos(-halfWidth - 2, 10, 0.0D).color(0.5F, 0.0F, 0.5F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 2, 10, 0.0D).color(0.5F, 0.0F, 0.5F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 2, -2, 0.0D).color(0.5F, 0.0F, 0.5F, bgAlpha).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        fontRenderer.drawString(text, -halfWidth, 0, 0xFFFFFFFF);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public void renderEnemyLoadoutLabel(EntityPlayer player, TrackedEnemy data, double x, double y, double z) {
        if (data == null) {
            return;
        }
        String loadout = data.formatLoadoutCompact();
        if (loadout.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRendererObj;
        RenderManager renderManager = mc.getRenderManager();

        double distance = player.getDistanceSqToEntity(mc.thePlayer);
        if (distance > 4096.0D) {
            return;
        }

        String text = EnumChatFormatting.GRAY + "KIT " + EnumChatFormatting.WHITE + loadout;

        float heightOffset = NameTagManager.getInstance().computeHeightOffset(player, NameTagManager.NameTagLayer.LOADOUT);

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + heightOffset, (float) z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        float scale = 0.016666668F * 1.25F;
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;

        GlStateManager.disableTexture2D();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(-halfWidth - 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        worldRenderer.pos(-halfWidth - 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        worldRenderer.pos(halfWidth + 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        worldRenderer.pos(halfWidth + 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        fontRenderer.drawString(text, -halfWidth, 0, 0xFFFFFFFF);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public void renderEnemyTrackingLabel(EntityPlayer player, TrackedEnemy data, double x, double y, double z) {
        if (data == null || !data.hasAnyData()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRendererObj;
        RenderManager renderManager = mc.getRenderManager();

        double distance = player.getDistanceSqToEntity(mc.thePlayer);
        if (distance > 4096.0D) {
            return;
        }

        // Build the label text
        StringBuilder sb = new StringBuilder();

        // Resource counts
        if (data.diamondCount > 0) {
            sb.append(EnumChatFormatting.AQUA).append(data.diamondCount).append("x").append("\u25C6 ");
        }
        if (data.emeraldCount > 0) {
            sb.append(EnumChatFormatting.GREEN).append(data.emeraldCount).append("x").append("\u25C6 ");
        }

        // Armor protection
        if (data.armorProtectionLevel > 0) {
            sb.append(EnumChatFormatting.YELLOW).append("Prot ").append(toRoman(data.armorProtectionLevel)).append(" ");
        }

        String text = sb.toString().trim();
        boolean hasItems = !data.observedHotbarItems.isEmpty();
        if (text.isEmpty() && !hasItems) {
            return;
        }

        // Position above the threat label
        float heightOffset = NameTagManager.getInstance().computeHeightOffset(player, NameTagManager.NameTagLayer.HOTBAR_INTEL);

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + heightOffset, (float) z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        float scale = 0.016666668F * 1.5F;
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int baseTextWidth = fontRenderer.getStringWidth(text);
        int totalWidth = baseTextWidth;
        int itemDisplayCount = Math.min(data.observedHotbarItems.size(), 5);
        if (hasItems) {
            if (!text.isEmpty()) {
                totalWidth += 4; // gap between text and items
            }
            totalWidth += itemDisplayCount * 10;
        }

        int halfWidth = totalWidth / 2;

        GlStateManager.disableTexture2D();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(-halfWidth - 2, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        worldRenderer.pos(-halfWidth - 2, 9, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        worldRenderer.pos(halfWidth + 2, 9, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        worldRenderer.pos(halfWidth + 2, -1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.3F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        int currentX = -halfWidth;
        if (!text.isEmpty()) {
            fontRenderer.drawString(text, currentX, 0, 0xFFFFFFFF);
            currentX += baseTextWidth + 4;
        }

        if (hasItems) {
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();

            int shown = 0;
            for (ItemStack stack : data.observedHotbarItems) {
                if (shown >= 5) break;

                GlStateManager.pushMatrix();
                GlStateManager.translate(currentX, 0, 0.01f);
                float itemScale = 9.0f / 16.0f;
                GlStateManager.scale(itemScale, itemScale, 1.0f);

                mc.getRenderItem().renderItemIntoGUI(stack, 0, 0);

                GlStateManager.popMatrix();

                currentX += 10;
                shown++;
            }

            GlStateManager.disableRescaleNormal();
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public void renderFireballTrajectories(Collection<TrackedFireball> fireballs, float partialTicks) {
        if (fireballs == null || fireballs.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glLineWidth(3.0F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        for (TrackedFireball fb : fireballs) {
            float r;
            float g;
            float b;
            if (fb.bedThreatening) {
                // Aimed at the bed defense — magenta, distinct from the red
                // "aimed at you" case so the player knows to rotate, not jump.
                r = 1.0F;
                g = 0.1F;
                b = 0.7F;
            } else if (fb.threatening) {
                r = 1.0F;
                g = 0.1F;
                b = 0.1F;
            } else {
                r = 1.0F;
                g = 0.85F;
                b = 0.1F;
            }

            double fx = fb.posX - playerX;
            double fy = fb.posY - playerY;
            double fz = fb.posZ - playerZ;
            double ix = fb.impactX - playerX;
            double iy = fb.impactY - playerY;
            double iz = fb.impactZ - playerZ;

            // Trajectory line from fireball to projected impact.
            worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(fx, fy, fz).color(r, g, b, 1.0F).endVertex();
            worldRenderer.pos(ix, iy, iz).color(r, g, b, 1.0F).endVertex();
            tessellator.draw();

            // Wireframe impact marker (~0.5 block cube centered on impact point).
            double half = 0.25;
            double x1 = ix - half;
            double y1 = iy - half;
            double z1 = iz - half;
            double x2 = ix + half;
            double y2 = iy + half;
            double z2 = iz + half;
            drawWireframeBox(worldRenderer, tessellator, x1, y1, z1, x2, y2, z2, r, g, b, 1.0F);
        }

        GL11.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();

        // Impact countdowns as billboarded labels, drawn after the line pass
        // (text rendering needs its own texture/GL block).
        for (TrackedFireball fb : fireballs) {
            if (!fb.impactValid || fb.secondsToImpact < 0) {
                continue;
            }
            String text;
            if (fb.bedThreatening) {
                text = EnumChatFormatting.LIGHT_PURPLE + "BED "
                        + EnumChatFormatting.WHITE + formatImpactSeconds(fb.secondsToImpact);
            } else if (fb.threatening) {
                text = EnumChatFormatting.RED + "FIREBALL "
                        + EnumChatFormatting.WHITE + formatImpactSeconds(fb.secondsToImpact);
            } else {
                text = EnumChatFormatting.GRAY + formatImpactSeconds(fb.secondsToImpact);
            }
            renderFloatingAlertLabel(text, fb.impactX, fb.impactY + 0.8, fb.impactZ, partialTicks);
        }
    }

    /** Sub-10s countdown with a decimal ("0.8s"), whole seconds above that. */
    private static String formatImpactSeconds(double seconds) {
        if (seconds < 10.0) {
            return String.format(java.util.Locale.ROOT, "%.1fs", seconds);
        }
        return ((int) seconds) + "s";
    }

    /**
     * Billboarded, depth-ignoring label at a world position — the same recipe
     * as {@link #renderGeneratorLabel} but position/text generic so projectile
     * alerts can reuse it.
     */
    private void renderFloatingAlertLabel(String text, double worldX, double worldY, double worldZ,
            float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        FontRenderer fontRenderer = mc.fontRendererObj;

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        double x = worldX - playerX;
        double y = worldY - playerY;
        double z = worldZ - playerZ;

        double distSq = x * x + y * y + z * z;
        float distance = (float) Math.sqrt(distSq);
        float baseScale = 0.016666668F * 2.5F;
        float distanceScale = Math.max(1.0F, distance / 20.0F);
        float scale = baseScale * distanceScale;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;

        GlStateManager.disableTexture2D();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        float bgAlpha = 0.4F;
        worldRenderer.pos(-halfWidth - 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(-halfWidth - 2, 10, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 2, 10, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        fontRenderer.drawString(text, -halfWidth, 0, 0xFFFFFFFF);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /**
     * Orange polyline along each detected bridge run plus a floating banner at
     * its head — makes the radar's "bridge from NE" alert visually anchorable.
     */
    public void renderBridgeTrails(java.util.List<BridgeRadarService.BridgeAlert> alerts,
            float partialTicks) {
        if (alerts == null || alerts.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glLineWidth(3.0F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        for (BridgeRadarService.BridgeAlert alert : alerts) {
            worldRenderer.begin(3, DefaultVertexFormats.POSITION_COLOR); // GL_LINE_STRIP
            for (BlockPos pos : alert.points) {
                worldRenderer.pos(pos.getX() + 0.5 - playerX,
                                pos.getY() + 1.2 - playerY,
                                pos.getZ() + 0.5 - playerZ)
                        .color(1.0F, 0.55F, 0.1F, 0.9F).endVertex();
            }
            tessellator.draw();
        }

        GL11.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();

        for (BridgeRadarService.BridgeAlert alert : alerts) {
            BlockPos head = alert.points.get(alert.points.size() - 1);
            String text = EnumChatFormatting.GOLD + "BRIDGE"
                    + (alert.etaSeconds > 0
                            ? " " + EnumChatFormatting.WHITE + alert.etaSeconds + "s"
                            : "");
            renderFloatingAlertLabel(text, head.getX() + 0.5, head.getY() + 2.0,
                    head.getZ() + 0.5, partialTicks);
        }
    }

    public void renderProjectileTrajectories(Collection<TrackedProjectile> projectiles, float partialTicks) {
        if (projectiles == null || projectiles.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glLineWidth(2.5F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        for (TrackedProjectile tp : projectiles) {
            float r;
            float g;
            float b;
            if (tp.threatening) {
                r = 1.0F; g = 0.3F; b = 0.3F;
            } else {
                r = 0.6F; g = 0.2F; b = 1.0F;
            }

            // Draw poly-line arc through the integrated path.
            if (tp.arcPoints.size() >= 2) {
                worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                for (TrackedProjectile.Point p : tp.arcPoints) {
                    worldRenderer.pos(p.x - playerX, p.y - playerY, p.z - playerZ)
                                  .color(r, g, b, 1.0F).endVertex();
                }
                tessellator.draw();
            }

            // Wireframe landing marker (~0.6 block cube).
            double lx = tp.landingX - playerX;
            double ly = tp.landingY - playerY;
            double lz = tp.landingZ - playerZ;
            double half = 0.3;
            drawWireframeBox(worldRenderer, tessellator,
                    lx - half, ly - half, lz - half,
                    lx + half, ly + half, lz + half,
                    r, g, b, 1.0F);
        }

        GL11.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /**
     * Render the local player's predicted ender-pearl arc in cyan so it can't
     * be confused with incoming enemy pearls (which render in red/purple).
     */
    public void renderPreThrowArc(TrackedProjectile preview, float partialTicks) {
        if (preview == null || preview.arcPoints.size() < 2) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glLineWidth(1.8F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        float r = 0.3F;
        float g = 0.9F;
        float b = 1.0F;

        worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (TrackedProjectile.Point p : preview.arcPoints) {
            worldRenderer.pos(p.x - playerX, p.y - playerY, p.z - playerZ)
                          .color(r, g, b, 1.0F).endVertex();
        }
        tessellator.draw();

        double lx = preview.landingX - playerX;
        double ly = preview.landingY - playerY;
        double lz = preview.landingZ - playerZ;
        double half = 0.3;
        drawWireframeBox(worldRenderer, tessellator,
                lx - half, ly - half, lz - half,
                lx + half, ly + half, lz + half,
                r, g, b, 1.0F);

        GL11.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static void drawWireframeBox(WorldRenderer worldRenderer, Tessellator tessellator,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2,
                                         float r, float g, float b, float a) {
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        // Bottom square
        worldRenderer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        // Top square
        worldRenderer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        // Vertical edges
        worldRenderer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        worldRenderer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        tessellator.draw();
    }

    private static String toRoman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return String.valueOf(level);
        }
    }

    /**
     * Highlight the exposed faces of every bed block in {@code bedBlocks}. A
     * face is "exposed" when the adjacent block is air, allowing an enemy to
     * break the bed in one shot from that side. Highlighted faces pulse so
     * they're visible against bright Bedwars maps.
     */
    public void renderBedDefenseAssist(java.util.List<BlockPos> bedBlocks, float partialTicks) {
        if (bedBlocks == null || bedBlocks.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;

        // Pulse alpha so the highlight is hard to miss but doesn't get in the way.
        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 220.0) + 1.0) / 2.0);
        float alpha = 0.35F + 0.30F * pulse;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glLineWidth(2.0F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        for (BlockPos bedPos : bedBlocks) {
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos adjacent = bedPos.offset(facing);
                if (mc.theWorld.getBlockState(adjacent).getBlock() != Blocks.air) {
                    continue;
                }

                double[] face = computeFaceCorners(bedPos, facing);
                double x1 = face[0] - playerX;
                double y1 = face[1] - playerY;
                double z1 = face[2] - playerZ;
                double x2 = face[3] - playerX;
                double y2 = face[4] - playerY;
                double z2 = face[5] - playerZ;

                drawFaceQuad(worldRenderer, tessellator, facing, x1, y1, z1, x2, y2, z2, alpha);
            }
        }

        GL11.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /**
     * Returns {@code [x1,y1,z1, x2,y2,z2]} for a slightly inset, slightly
     * outset face of the bed block so it draws cleanly above the bed mesh.
     */
    private static double[] computeFaceCorners(BlockPos pos, EnumFacing facing) {
        double inset = 0.005;
        double bedHeight = 0.5625; // 9/16 — bed model height in 1.8.9
        double minX = pos.getX() + inset;
        double minY = pos.getY() + inset;
        double minZ = pos.getZ() + inset;
        double maxX = pos.getX() + 1 - inset;
        double maxY = pos.getY() + bedHeight - inset;
        double maxZ = pos.getZ() + 1 - inset;

        switch (facing) {
            case UP:
                return new double[]{minX, maxY + 0.002, minZ, maxX, maxY + 0.002, maxZ};
            case DOWN:
                return new double[]{minX, minY - 0.002, minZ, maxX, minY - 0.002, maxZ};
            case NORTH:
                return new double[]{minX, minY, minZ - 0.002, maxX, maxY, minZ - 0.002};
            case SOUTH:
                return new double[]{minX, minY, maxZ + 0.002, maxX, maxY, maxZ + 0.002};
            case WEST:
                return new double[]{minX - 0.002, minY, minZ, minX - 0.002, maxY, maxZ};
            case EAST:
            default:
                return new double[]{maxX + 0.002, minY, minZ, maxX + 0.002, maxY, maxZ};
        }
    }

    private static void drawFaceQuad(WorldRenderer worldRenderer, Tessellator tessellator,
                                      EnumFacing facing,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      float alpha) {
        // Solid danger fill (red).
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        switch (facing) {
            case UP:
            case DOWN:
                worldRenderer.pos(x1, y1, z1).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x1, y1, z2).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x2, y2, z2).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x2, y2, z1).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                break;
            case NORTH:
            case SOUTH:
                worldRenderer.pos(x1, y1, z1).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x2, y1, z2).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x2, y2, z2).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x1, y2, z1).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                break;
            case WEST:
            case EAST:
            default:
                worldRenderer.pos(x1, y1, z1).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x2, y1, z2).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x2, y2, z2).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                worldRenderer.pos(x1, y2, z1).color(1.0F, 0.15F, 0.15F, alpha).endVertex();
                break;
        }
        tessellator.draw();

        // Bright outline so the exposed face stands out.
        worldRenderer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        switch (facing) {
            case UP:
            case DOWN:
                worldRenderer.pos(x1, y1, z1).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x1, y1, z2).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x2, y2, z2).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x2, y2, z1).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                break;
            case NORTH:
            case SOUTH:
                worldRenderer.pos(x1, y1, z1).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x2, y1, z2).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x2, y2, z2).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x1, y2, z1).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                break;
            case WEST:
            case EAST:
            default:
                worldRenderer.pos(x1, y1, z1).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x2, y1, z2).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x2, y2, z2).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                worldRenderer.pos(x1, y2, z1).color(1.0F, 1.0F, 0.4F, 1.0F).endVertex();
                break;
        }
        tessellator.draw();
    }
}
