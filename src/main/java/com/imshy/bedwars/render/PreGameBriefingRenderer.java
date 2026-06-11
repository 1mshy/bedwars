package com.imshy.bedwars.render;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.runtime.PreGameBriefing;
import com.imshy.bedwars.runtime.TeamDangerAnalyzer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the "pre-game scouting report" card that appears briefly at the
 * start of a Bedwars match, summarising each team's threat profile and the
 * recommended focus target for the local player.
 */
public class PreGameBriefingRenderer {

    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int FADE_OUT_MS = 1200;

    public void render(ScaledResolution resolution, Minecraft mc, PreGameBriefing briefing) {
        if (!ModConfig.isPreGameBriefingEnabled() || briefing == null) {
            return;
        }

        long ttlMs = ModConfig.getPreGameBriefingDurationSeconds() * 1000L;
        if (briefing.isExpired(ttlMs)) {
            return;
        }

        FontRenderer fr = mc.fontRendererObj;
        List<String> lines = buildLines(briefing);

        int width = 0;
        for (String l : lines) {
            int w = fr.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(l));
            if (w > width) width = w;
        }
        width += PADDING * 2;
        int height = PADDING * 2 + lines.size() * LINE_HEIGHT;

        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int originX;
        int originY;
        if (ModConfig.isPreGameBriefingCustomPosition()) {
            // HUD-editor anchor + offset override (raw ScaledResolution space).
            originX = HudAnchorMath.computeX(ModConfig.getPreGameBriefingAnchorX(),
                    screenWidth, width, ModConfig.getPreGameBriefingAnchorOffsetX());
            originY = HudAnchorMath.computeY(ModConfig.getPreGameBriefingAnchorY(),
                    screenHeight, height, ModConfig.getPreGameBriefingAnchorOffsetY());
        } else {
            originX = (screenWidth - width) / 2;
            originY = Math.max(20, (screenHeight - height) / 4);
        }

        long age = briefing.getAgeMs();
        float fadeAlpha = 1.0F;
        long timeLeft = ttlMs - age;
        if (timeLeft < FADE_OUT_MS) {
            fadeAlpha = Math.max(0.0F, (float) timeLeft / (float) FADE_OUT_MS);
        }

        int bgAlpha = (int) (200 * fadeAlpha) & 0xFF;
        Gui.drawRect(originX, originY, originX + width, originY + height, bgAlpha << 24);

        int borderAlpha = (int) (255 * fadeAlpha) & 0xFF;
        int borderColor = (borderAlpha << 24) | 0xFFAA00;
        drawBorder(originX, originY, width, height, borderColor);

        GlStateManager.pushMatrix();
        if (fadeAlpha < 1.0F) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, fadeAlpha);
        }

        int drawX = originX + PADDING;
        int drawY = originY + PADDING;
        for (String line : lines) {
            fr.drawStringWithShadow(line, drawX, drawY, applyAlpha(0xFFFFFF, fadeAlpha));
            drawY += LINE_HEIGHT;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static int applyAlpha(int rgb, float alpha) {
        int a = (int) (255 * Math.max(0.0F, Math.min(1.0F, alpha))) & 0xFF;
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private List<String> buildLines(PreGameBriefing briefing) {
        List<String> lines = new ArrayList<String>();
        String mapLabel = briefing.mapName == null || briefing.mapName.isEmpty()
                ? "?"
                : briefing.mapName;
        lines.add(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.GOLD + "SCOUTING REPORT "
                + EnumChatFormatting.GRAY + "- " + EnumChatFormatting.AQUA + mapLabel);

        for (PreGameBriefing.TeamBriefing team : briefing.teams) {
            String dangerLabel = team.knownPlayers > 0
                    ? TeamDangerAnalyzer.averageThreatLabel(team.averageThreatScore)
                    : "UNKNOWN";
            String dangerColor = TeamDangerAnalyzer.dangerLabelColor(dangerLabel);

            StringBuilder sb = new StringBuilder();
            sb.append(team.teamColor).append(team.teamName);
            if (team.isOwnTeam) {
                sb.append(EnumChatFormatting.AQUA).append(" (You)");
            }
            sb.append(EnumChatFormatting.GRAY).append(" - ")
              .append(dangerColor).append(dangerLabel)
              .append(EnumChatFormatting.GRAY).append(" (")
              .append(team.knownPlayers).append("/").append(team.totalPlayers).append(")")
              .append(EnumChatFormatting.GRAY).append("  \u2B50 ")
              .append(EnumChatFormatting.WHITE).append(team.combinedStars);
            if (team.nickedPlayers > 0) {
                sb.append(EnumChatFormatting.LIGHT_PURPLE).append(" [")
                  .append(team.nickedPlayers).append(" NICK]");
            }
            if (team.blacklistedPlayers > 0) {
                sb.append(EnumChatFormatting.GOLD).append(" [")
                  .append(team.blacklistedPlayers).append(" BL]");
            }
            lines.add(sb.toString());

            if (team.topThreatName != null && !team.isOwnTeam) {
                String threatColor = threatColorFor(team.topThreatLevel);
                StringBuilder top = new StringBuilder();
                top.append(EnumChatFormatting.GRAY).append("   top: ")
                   .append(threatColor).append(team.topThreatName)
                   .append(EnumChatFormatting.GRAY).append(" ")
                   .append(EnumChatFormatting.WHITE).append(team.topThreatStars).append("\u2B50 ")
                   .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatioShort(team.topThreatFkdr))
                   .append(EnumChatFormatting.GRAY).append(" FKDR");
                lines.add(top.toString());
            }
        }

        if (briefing.focusTargetName != null) {
            lines.add("");
            String threatColor = threatColorFor(briefing.focusTargetThreat);
            lines.add(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "FOCUS: "
                    + threatColor + briefing.focusTargetName
                    + EnumChatFormatting.GRAY + " ("
                    + threatColor + briefing.focusTargetThreat.name()
                    + EnumChatFormatting.GRAY + ")");
        }

        return lines;
    }

    private static String threatColorFor(BedwarsStats.ThreatLevel level) {
        if (level == null) return EnumChatFormatting.GRAY.toString();
        switch (level) {
            case EXTREME: return EnumChatFormatting.DARK_RED.toString();
            case HIGH:    return EnumChatFormatting.RED.toString();
            case MEDIUM:  return EnumChatFormatting.YELLOW.toString();
            case LOW:     return EnumChatFormatting.GREEN.toString();
            case NICKED:  return EnumChatFormatting.LIGHT_PURPLE.toString();
            default:      return EnumChatFormatting.GRAY.toString();
        }
    }

    private static void drawBorder(int x, int y, int w, int h, int color) {
        int t = 1;
        Gui.drawRect(x, y, x + w, y + t, color);
        Gui.drawRect(x, y + h - t, x + w, y + h, color);
        Gui.drawRect(x, y, x + t, y + h, color);
        Gui.drawRect(x + w - t, y, x + w, y + h, color);
    }
}
