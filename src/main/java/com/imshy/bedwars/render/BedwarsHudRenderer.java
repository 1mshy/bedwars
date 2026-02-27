package com.imshy.bedwars.render;

import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.runtime.TeamDangerAnalyzer;
import com.imshy.bedwars.runtime.TeamDangerAnalyzer.TeamDangerEntry;
import com.imshy.bedwars.runtime.WorldScanService;
import com.imshy.bedwars.runtime.WorldScanService.GeneratorSummary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class BedwarsHudRenderer {

    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int SECTION_GAP = 4;

    public void render(ScaledResolution resolution, Minecraft mc,
                       TeamDangerAnalyzer teamDangerAnalyzer, WorldScanService worldScanService) {
        if (!ModConfig.isHudEnabled()) {
            return;
        }

        FontRenderer fr = mc.fontRendererObj;
        double scale = ModConfig.getHudScale();

        List<HudLine> lines = new ArrayList<HudLine>();
        buildHudContent(lines, mc, fr, teamDangerAnalyzer, worldScanService);

        if (lines.isEmpty()) {
            return;
        }

        int panelWidth = 0;
        for (HudLine line : lines) {
            int w = fr.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(line.text));
            if (w > panelWidth) {
                panelWidth = w;
            }
        }
        panelWidth += PADDING * 2;

        int panelHeight = PADDING * 2;
        for (HudLine line : lines) {
            panelHeight += line.isGap ? SECTION_GAP : LINE_HEIGHT;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0);

        int scaledWidth = (int) (resolution.getScaledWidth() / scale);
        int scaledHeight = (int) (resolution.getScaledHeight() / scale);

        int[] origin = computeOrigin(scaledWidth, scaledHeight, panelWidth, panelHeight);
        int originX = origin[0];
        int originY = origin[1];

        int bgAlpha = (int) (ModConfig.getHudBackgroundOpacity() * 255) & 0xFF;
        int bgColor = (bgAlpha << 24);
        Gui.drawRect(originX, originY, originX + panelWidth, originY + panelHeight, bgColor);

        int drawX = originX + PADDING;
        int drawY = originY + PADDING;
        for (HudLine line : lines) {
            if (line.isGap) {
                drawY += SECTION_GAP;
            } else {
                fr.drawStringWithShadow(line.text, drawX, drawY, 0xFFFFFFFF);
                drawY += LINE_HEIGHT;
            }
        }

        GlStateManager.popMatrix();
    }

    private void buildHudContent(List<HudLine> lines, Minecraft mc, FontRenderer fr,
                                  TeamDangerAnalyzer teamDangerAnalyzer, WorldScanService worldScanService) {
        boolean addedSection = false;

        if (ModConfig.isHudHighestThreatEnabled()) {
            addedSection = addHighestThreatSection(lines, mc, teamDangerAnalyzer);
        }

        if (ModConfig.isHudGeneratorCountsEnabled()) {
            if (addedSection) {
                lines.add(HudLine.gap());
            }
            boolean added = addGeneratorSection(lines, worldScanService);
            addedSection = addedSection || added;
        }

        if (ModConfig.isHudTeamSummaryEnabled()) {
            if (addedSection) {
                lines.add(HudLine.gap());
            }
            addTeamSummarySection(lines, mc, teamDangerAnalyzer);
        }
    }

    private boolean addHighestThreatSection(List<HudLine> lines, Minecraft mc,
                                             TeamDangerAnalyzer teamDangerAnalyzer) {
        List<TeamDangerEntry> summaries = teamDangerAnalyzer.buildTeamDangerSummary(mc);
        if (summaries.isEmpty()) {
            return false;
        }

        TeamDangerEntry highest = null;
        for (TeamDangerEntry entry : summaries) {
            if (entry.isOwnTeam || entry.playersWithKnownThreat == 0) {
                continue;
            }
            if (highest == null || entry.getAverageThreatScore() > highest.getAverageThreatScore()) {
                highest = entry;
            }
        }

        if (highest == null) {
            return false;
        }

        String label = TeamDangerAnalyzer.averageThreatLabel(highest.getAverageThreatScore());
        String color = TeamDangerAnalyzer.dangerLabelColor(label);

        lines.add(HudLine.text(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "HIGHEST THREAT"));
        lines.add(HudLine.text(highest.teamColor + highest.teamName
                + EnumChatFormatting.GRAY + " - "
                + color + label
                + EnumChatFormatting.GRAY + " ("
                + highest.playersWithKnownThreat + "/" + highest.totalPlayers + " known)"));
        return true;
    }

    private boolean addGeneratorSection(List<HudLine> lines, WorldScanService worldScanService) {
        GeneratorSummary summary = worldScanService.getGeneratorSummary();
        if (summary.diamondGenerators == 0 && summary.emeraldGenerators == 0) {
            return false;
        }

        lines.add(HudLine.text(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "GENERATORS"));

        StringBuilder sb = new StringBuilder();
        if (summary.diamondGenerators > 0) {
            sb.append(EnumChatFormatting.AQUA).append("Diamonds: ")
              .append(EnumChatFormatting.WHITE).append(summary.totalDiamonds);
        }
        if (summary.emeraldGenerators > 0) {
            if (sb.length() > 0) {
                sb.append(EnumChatFormatting.GRAY).append("  ");
            }
            sb.append(EnumChatFormatting.GREEN).append("Emeralds: ")
              .append(EnumChatFormatting.WHITE).append(summary.totalEmeralds);
        }
        lines.add(HudLine.text(sb.toString()));
        return true;
    }

    private boolean addTeamSummarySection(List<HudLine> lines, Minecraft mc,
                                           TeamDangerAnalyzer teamDangerAnalyzer) {
        List<TeamDangerEntry> summaries = teamDangerAnalyzer.buildTeamDangerSummary(mc);
        if (summaries.isEmpty()) {
            return false;
        }

        lines.add(HudLine.text(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "TEAM SUMMARY"));

        for (TeamDangerEntry entry : summaries) {
            String dangerLabel = entry.playersWithKnownThreat > 0
                    ? TeamDangerAnalyzer.averageThreatLabel(entry.getAverageThreatScore())
                    : "UNKNOWN";
            String dangerColor = TeamDangerAnalyzer.dangerLabelColor(dangerLabel);

            String ownSuffix = entry.isOwnTeam
                    ? EnumChatFormatting.AQUA + " (You)"
                    : "";

            lines.add(HudLine.text(entry.teamColor + entry.teamName
                    + ownSuffix
                    + EnumChatFormatting.GRAY + " - "
                    + dangerColor + dangerLabel
                    + EnumChatFormatting.GRAY + " ("
                    + entry.playersWithKnownThreat + "/" + entry.totalPlayers + ")"));
        }
        return true;
    }

    private int[] computeOrigin(int screenWidth, int screenHeight, int panelWidth, int panelHeight) {
        String position = ModConfig.getHudPosition();
        int margin = 4;
        int x, y;

        if ("TOP_RIGHT".equals(position)) {
            x = screenWidth - panelWidth - margin;
            y = margin;
        } else if ("BOTTOM_LEFT".equals(position)) {
            x = margin;
            y = screenHeight - panelHeight - margin;
        } else if ("BOTTOM_RIGHT".equals(position)) {
            x = screenWidth - panelWidth - margin;
            y = screenHeight - panelHeight - margin;
        } else {
            x = margin;
            y = margin;
        }

        return new int[]{x, y};
    }

    private static class HudLine {
        final String text;
        final boolean isGap;

        private HudLine(String text, boolean isGap) {
            this.text = text;
            this.isGap = isGap;
        }

        static HudLine text(String text) {
            return new HudLine(text, false);
        }

        static HudLine gap() {
            return new HudLine("", true);
        }
    }
}
