package com.imshy.bedwars.render;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.runtime.MatchSummary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the post-match summary card in the centre of the screen for a few
 * seconds after a Bedwars game ends. The card shows the outcome, duration,
 * map name, threat counts, top threat list (with monthly/weekly FKDR), and
 * suggested blacklist entries when the user lost to EXTREME-threat enemies.
 */
public class MatchSummaryRenderer {

    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int FADE_OUT_MS = 1500;

    public void render(ScaledResolution resolution, Minecraft mc, MatchSummary summary) {
        if (!ModConfig.isMatchSummaryCardEnabled()) {
            return;
        }
        if (summary == null) {
            return;
        }
        long ttlMs = ModConfig.getMatchSummaryCardDurationSeconds() * 1000L;
        if (summary.isExpired(ttlMs)) {
            return;
        }

        FontRenderer fr = mc.fontRendererObj;
        List<String> lines = buildLines(summary);

        int width = 0;
        for (String l : lines) {
            int w = fr.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(l));
            if (w > width) width = w;
        }
        width += PADDING * 2;
        int height = PADDING * 2 + lines.size() * LINE_HEIGHT;

        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        int originX = (screenWidth - width) / 2;
        int originY = Math.max(20, (screenHeight - height) / 3);

        // Fade the panel toward the end of its TTL so it doesn't snap away.
        long age = summary.getAgeMs();
        float fadeAlpha = 1.0F;
        long timeLeft = ttlMs - age;
        if (timeLeft < FADE_OUT_MS) {
            fadeAlpha = Math.max(0.0F, (float) timeLeft / (float) FADE_OUT_MS);
        }

        int bgAlpha = (int) (200 * fadeAlpha) & 0xFF;
        int bgColor = (bgAlpha << 24);
        Gui.drawRect(originX, originY, originX + width, originY + height, bgColor);

        int borderAlpha = (int) (255 * fadeAlpha) & 0xFF;
        int borderColor = (borderAlpha << 24) | borderColorForOutcome(summary.outcome);
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

    private static int borderColorForOutcome(MatchSummary.Outcome outcome) {
        if (outcome == MatchSummary.Outcome.WIN) return 0x55FF55;
        if (outcome == MatchSummary.Outcome.LOSS) return 0xFF5555;
        return 0xAAAAAA;
    }

    private static String outcomeBanner(MatchSummary.Outcome outcome) {
        switch (outcome) {
            case WIN:  return EnumChatFormatting.GREEN.toString() + EnumChatFormatting.BOLD + "VICTORY";
            case LOSS: return EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD + "DEFEAT";
            default:   return EnumChatFormatting.GRAY.toString() + EnumChatFormatting.BOLD + "GAME OVER";
        }
    }

    private List<String> buildLines(MatchSummary summary) {
        List<String> lines = new ArrayList<String>();
        lines.add(outcomeBanner(summary.outcome)
                + EnumChatFormatting.GRAY + " - " + EnumChatFormatting.WHITE + summary.getDurationLabel()
                + EnumChatFormatting.GRAY + " on "
                + EnumChatFormatting.AQUA + (summary.mapName == null ? "?" : summary.mapName));

        StringBuilder counts = new StringBuilder();
        counts.append(EnumChatFormatting.GRAY).append("Tracked: ")
              .append(EnumChatFormatting.WHITE).append(summary.playersTracked)
              .append(EnumChatFormatting.GRAY).append("  ")
              .append(EnumChatFormatting.DARK_RED).append("EXT ").append(summary.extremeThreatCount)
              .append(EnumChatFormatting.GRAY).append("  ")
              .append(EnumChatFormatting.RED).append("HIGH ").append(summary.highThreatCount);
        if (summary.blacklistedFaced > 0) {
            counts.append(EnumChatFormatting.GRAY).append("  ")
                  .append(EnumChatFormatting.GOLD).append("BL ").append(summary.blacklistedFaced);
        }
        lines.add(counts.toString());

        if (!summary.topThreats.isEmpty()) {
            lines.add("");
            lines.add(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "TOP THREATS");
            for (MatchSummary.TopThreat t : summary.topThreats) {
                String threatColor = t.threat == BedwarsStats.ThreatLevel.EXTREME
                        ? EnumChatFormatting.DARK_RED.toString()
                        : EnumChatFormatting.RED.toString();
                StringBuilder sb = new StringBuilder();
                sb.append(threatColor).append(t.name)
                  .append(EnumChatFormatting.GRAY).append(" ")
                  .append(EnumChatFormatting.WHITE).append(t.stars).append("\u2B50 ")
                  .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatioShort(t.careerFkdr))
                  .append(EnumChatFormatting.GRAY).append(" career");
                if (t.recentWindow != BedwarsStats.RecentWindow.NONE) {
                    String tag = t.recentWindow == BedwarsStats.RecentWindow.MONTHLY ? "MO" : "WK";
                    sb.append(EnumChatFormatting.GRAY).append("  ")
                      .append(EnumChatFormatting.YELLOW).append(BedwarsStats.formatRatioShort(t.recentFkdr))
                      .append(EnumChatFormatting.GRAY).append(" ").append(tag);
                }
                lines.add(sb.toString());
            }
        }

        if (!summary.suggestedBlacklist.isEmpty()) {
            lines.add("");
            lines.add(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.GOLD + "SUGGESTED BLACKLIST");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < summary.suggestedBlacklist.size(); i++) {
                if (i > 0) sb.append(EnumChatFormatting.GRAY).append(", ");
                sb.append(EnumChatFormatting.YELLOW).append(summary.suggestedBlacklist.get(i));
            }
            lines.add(sb.toString());
            lines.add(EnumChatFormatting.GRAY + "Use " + EnumChatFormatting.WHITE
                    + "/bw blacklist add <name>" + EnumChatFormatting.GRAY + " to add.");
        }
        return lines;
    }

    private static void drawBorder(int x, int y, int w, int h, int color) {
        int t = 1;
        Gui.drawRect(x, y, x + w, y + t, color);
        Gui.drawRect(x, y + h - t, x + w, y + h, color);
        Gui.drawRect(x, y, x + t, y + h, color);
        Gui.drawRect(x + w - t, y, x + w, y + h, color);
    }
}
