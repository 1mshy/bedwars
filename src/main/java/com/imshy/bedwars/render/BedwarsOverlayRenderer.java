package com.imshy.bedwars.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;

import com.imshy.bedwars.runtime.TrackedEnemy;

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

        float heightOffset = player.height + 0.5F + 0.3F;

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
            boolean hasDesignatedIngotOnTop, float partialTicks) {
        if (resourceCount <= 0) {
            return;
        }
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

        String icon = isDiamond ? "💎" : "💚";
        String color = isDiamond ? EnumChatFormatting.AQUA.toString() : EnumChatFormatting.GREEN.toString();
        String text = color + icon;
        if (hasDesignatedIngotOnTop) {
            text = text + " " + resourceCount;
        }

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
        double y = targetY - playerY + player.height + 0.5;
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

        // Hotbar items (abbreviated, up to 5)
        if (!data.observedHotbarItems.isEmpty()) {
            sb.append(EnumChatFormatting.WHITE);
            int shown = 0;
            for (ItemStack item : data.observedHotbarItems) {
                if (shown >= 5) break;
                if (shown > 0) sb.append(" ");
                sb.append(getItemAbbreviation(item));
                shown++;
            }
        }

        String text = sb.toString().trim();
        if (text.isEmpty()) {
            return;
        }

        // Position below the threat label
        float heightOffset = player.height + 0.5F;

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

    private static String toRoman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return String.valueOf(level);
        }
    }

    private static String getItemAbbreviation(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "?";
        String name = stack.getDisplayName();
        // Common bedwars items
        if (name.contains("Sword")) return "\u2694";
        if (name.contains("Bow")) return "\u2639";
        if (name.contains("Pickaxe")) return "\u26CF";
        if (name.contains("Axe")) return "\u2692";
        if (name.contains("Pearl")) return "\u2726";
        if (name.contains("Fireball")) return "\u2739";
        if (name.contains("TNT")) return "TNT";
        if (name.contains("Wool") || name.contains("Terracotta") || name.contains("Clay") || name.contains("Sandstone") || name.contains("End Stone") || name.contains("Obsidian") || name.contains("Planks")) return "\u25A0";
        if (name.contains("Potion") || name.contains("Water")) return "\u2617";
        if (name.contains("Golden Apple")) return "\u2764";
        if (name.contains("Shears")) return "\u2702";
        // Fallback: first 3 chars
        if (name.length() > 3) return name.substring(0, 3);
        return name;
    }
}
