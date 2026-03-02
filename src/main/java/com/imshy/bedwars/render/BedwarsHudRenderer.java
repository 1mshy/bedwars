package com.imshy.bedwars.render;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.runtime.ChatDetectedPlayer;
import com.imshy.bedwars.runtime.EnemyTrackingService;
import com.imshy.bedwars.runtime.TeamDangerAnalyzer;
import com.imshy.bedwars.runtime.TeamDangerAnalyzer.TeamDangerEntry;
import com.imshy.bedwars.runtime.TrackedEnemy;
import com.imshy.bedwars.runtime.WorldScanService;
import com.imshy.bedwars.runtime.WorldScanService.GeneratorSummary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BedwarsHudRenderer {

    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int SECTION_GAP = 4;
    private static final int FACE_SIZE = 8;
    private static final int FACE_TEXT_GAP = 2;
    private static final int FACE_OFFSET = FACE_SIZE + FACE_TEXT_GAP;
    private static final long TEAM_SUMMARY_DURATION_MS = 10000;

    public void render(ScaledResolution resolution, Minecraft mc,
                       TeamDangerAnalyzer teamDangerAnalyzer, WorldScanService worldScanService,
                       EnemyTrackingService enemyTrackingService,
                       long matchStartTime, List<ChatDetectedPlayer> chatDetectedPlayers) {
        if (!ModConfig.isModEnabled()) {
            return;
        }

        if (!ModConfig.isHudEnabled()) {
            return;
        }

        FontRenderer fr = mc.fontRendererObj;
        double scale = ModConfig.getHudScale();

        List<HudLine> lines = new ArrayList<HudLine>();
        buildHudContent(lines, mc, fr, teamDangerAnalyzer, worldScanService, enemyTrackingService,
                matchStartTime, chatDetectedPlayers);

        if (lines.isEmpty()) {
            return;
        }

        int panelWidth = 0;
        for (HudLine line : lines) {
            int w = fr.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(line.text));
            if (line.skinLocation != null) {
                w += FACE_OFFSET;
            }
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
                int textX = drawX;
                if (line.skinLocation != null) {
                    GlStateManager.enableBlend();
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    mc.getTextureManager().bindTexture(line.skinLocation);
                    Gui.drawScaledCustomSizeModalRect(drawX, drawY, 8.0F, 8.0F, 8, 8,
                            FACE_SIZE, FACE_SIZE, 64.0F, 64.0F);
                    Gui.drawScaledCustomSizeModalRect(drawX, drawY, 40.0F, 8.0F, 8, 8,
                            FACE_SIZE, FACE_SIZE, 64.0F, 64.0F);
                    textX += FACE_OFFSET;
                }
                fr.drawStringWithShadow(line.text, textX, drawY, 0xFFFFFFFF);
                drawY += LINE_HEIGHT;
            }
        }

        GlStateManager.popMatrix();
    }

    private void buildHudContent(List<HudLine> lines, Minecraft mc, FontRenderer fr,
                                  TeamDangerAnalyzer teamDangerAnalyzer, WorldScanService worldScanService,
                                  EnemyTrackingService enemyTrackingService,
                                  long matchStartTime, List<ChatDetectedPlayer> chatDetectedPlayers) {
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
            boolean added = addTeamSummarySection(lines, mc, teamDangerAnalyzer, matchStartTime);
            addedSection = addedSection || added;
        }

        if (ModConfig.isHudChatDetectedEnabled()) {
            if (addedSection) {
                lines.add(HudLine.gap());
            }
            boolean added = addChatDetectedSection(lines, mc, chatDetectedPlayers);
            addedSection = addedSection || added;
        }

        if (ModConfig.isEnemyTrackingEnabled() && ModConfig.isEnemyTrackingHudEnabled()) {
            if (addedSection) {
                lines.add(HudLine.gap());
            }
            addEnemyTrackingSection(lines, mc, enemyTrackingService);
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
                                           TeamDangerAnalyzer teamDangerAnalyzer, long matchStartTime) {
        boolean earlyPhase = matchStartTime <= 0
                || (System.currentTimeMillis() - matchStartTime) < TEAM_SUMMARY_DURATION_MS;

        if (earlyPhase) {
            return addTeamLevelSummary(lines, mc, teamDangerAnalyzer);
        }
        return addHighThreatPlayerList(lines, mc);
    }

    private boolean addTeamLevelSummary(List<HudLine> lines, Minecraft mc,
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

    private boolean addHighThreatPlayerList(List<HudLine> lines, Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }

        List<ThreatPlayerEntry> threats = new ArrayList<ThreatPlayerEntry>();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }
            if (player.isOnSameTeam(mc.thePlayer)) {
                continue;
            }

            BedwarsStats stats = HypixelAPI.getCachedStats(player.getName());
            if (stats == null || !stats.isLoaded()) {
                continue;
            }

            BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
            if (threat != BedwarsStats.ThreatLevel.HIGH && threat != BedwarsStats.ThreatLevel.EXTREME) {
                continue;
            }

            ResourceLocation skin = null;
            if (player instanceof AbstractClientPlayer) {
                skin = ((AbstractClientPlayer) player).getLocationSkin();
            }

            int distance = (int) mc.thePlayer.getDistanceToEntity(player);
            threats.add(new ThreatPlayerEntry(player.getName(), threat, distance, skin, getPlayerTeamColor(player)));
        }

        if (threats.isEmpty()) {
            return false;
        }

        Collections.sort(threats, new Comparator<ThreatPlayerEntry>() {
            @Override
            public int compare(ThreatPlayerEntry a, ThreatPlayerEntry b) {
                return Integer.compare(a.distance, b.distance);
            }
        });

        lines.add(HudLine.text(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "THREATS"));

        for (ThreatPlayerEntry entry : threats) {
            String threatColor = entry.threat == BedwarsStats.ThreatLevel.EXTREME
                    ? EnumChatFormatting.DARK_RED.toString()
                    : EnumChatFormatting.RED.toString();

            String line = entry.teamColor + entry.name
                    + EnumChatFormatting.GRAY + " - "
                    + threatColor + entry.threat.name()
                    + EnumChatFormatting.GRAY + "  " + entry.distance + "m";

            lines.add(HudLine.playerLine(line, entry.skin));
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

    private boolean addChatDetectedSection(List<HudLine> lines, Minecraft mc,
                                            List<ChatDetectedPlayer> chatDetectedPlayers) {
        if (chatDetectedPlayers == null || chatDetectedPlayers.isEmpty()) {
            return false;
        }

        lines.add(HudLine.text(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "DETECTED PLAYERS"));

        synchronized (chatDetectedPlayers) {
            for (ChatDetectedPlayer cdp : chatDetectedPlayers) {
                BedwarsStats stats = cdp.stats;
                BedwarsStats.ThreatLevel threat = stats.getThreatLevel();
                String threatColor = stats.getThreatColor();

                ResourceLocation skin = null;
                String teamColor = EnumChatFormatting.WHITE.toString();
                if (mc.theWorld != null) {
                    for (EntityPlayer player : mc.theWorld.playerEntities) {
                        if (player.getName().equals(cdp.name) && player instanceof AbstractClientPlayer) {
                            skin = ((AbstractClientPlayer) player).getLocationSkin();
                            teamColor = getPlayerTeamColor(player);
                            break;
                        }
                    }
                }

                String line = threatColor + "[" + threat.name() + "] " +
                        teamColor + cdp.name + " " +
                        EnumChatFormatting.GRAY + stats.getStars() + "\u2B50 " +
                        EnumChatFormatting.YELLOW + String.format("%.1f", stats.getFkdr()) + " FKDR";

                lines.add(HudLine.playerLine(line, skin));
            }
        }
        return true;
    }

    private static class ThreatPlayerEntry {
        final String name;
        final BedwarsStats.ThreatLevel threat;
        final int distance;
        final ResourceLocation skin;
        final String teamColor;

        ThreatPlayerEntry(String name, BedwarsStats.ThreatLevel threat, int distance, ResourceLocation skin, String teamColor) {
            this.name = name;
            this.threat = threat;
            this.distance = distance;
            this.skin = skin;
            this.teamColor = teamColor;
        }
    }

    private static class HudLine {
        final String text;
        final boolean isGap;
        final ResourceLocation skinLocation;

        private HudLine(String text, boolean isGap, ResourceLocation skinLocation) {
            this.text = text;
            this.isGap = isGap;
            this.skinLocation = skinLocation;
        }

        static HudLine text(String text) {
            return new HudLine(text, false, null);
        }

        static HudLine playerLine(String text, ResourceLocation skin) {
            return new HudLine(text, false, skin);
        }

        static HudLine gap() {
            return new HudLine("", true, null);
        }
    }

    private boolean addEnemyTrackingSection(List<HudLine> lines, Minecraft mc,
                                             EnemyTrackingService enemyTrackingService) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }

        // Find the nearest enemy with tracking data
        EntityPlayer nearest = null;
        TrackedEnemy nearestData = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }
            if (player.isOnSameTeam(mc.thePlayer)) {
                continue;
            }

            TrackedEnemy data = enemyTrackingService.getTrackedEnemy(player.getName());
            if (data == null || !data.hasAnyData()) {
                continue;
            }

            double distSq = mc.thePlayer.getDistanceSqToEntity(player);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = player;
                nearestData = data;
            }
        }

        if (nearest == null || nearestData == null) {
            return false;
        }

        ResourceLocation skin = null;
        if (nearest instanceof AbstractClientPlayer) {
            skin = ((AbstractClientPlayer) nearest).getLocationSkin();
        }

        lines.add(HudLine.text(EnumChatFormatting.BOLD.toString() + EnumChatFormatting.WHITE + "ENEMY INTEL"));
        String nameColor = getPlayerTeamColor(nearest);
        lines.add(HudLine.playerLine(nameColor + nearest.getName()
                + EnumChatFormatting.GRAY + " (" + (int) Math.sqrt(nearestDistSq) + "m)", skin));

        // Resources
        if (nearestData.diamondCount > 0 || nearestData.emeraldCount > 0) {
            StringBuilder res = new StringBuilder();
            if (nearestData.diamondCount > 0) {
                res.append(EnumChatFormatting.AQUA).append("\u25C6 ").append(nearestData.diamondCount);
            }
            if (nearestData.emeraldCount > 0) {
                if (res.length() > 0) res.append("  ");
                res.append(EnumChatFormatting.GREEN).append("\u25C6 ").append(nearestData.emeraldCount);
            }
            lines.add(HudLine.text(res.toString()));
        }

        // Armor
        if (nearestData.armorProtectionLevel > 0) {
            lines.add(HudLine.text(EnumChatFormatting.YELLOW + "Armor: Prot " + toRoman(nearestData.armorProtectionLevel)));
        }

        // Hotbar items
        if (!nearestData.observedHotbarItems.isEmpty()) {
            StringBuilder hotbar = new StringBuilder();
            hotbar.append(EnumChatFormatting.GRAY).append("Hotbar: ").append(EnumChatFormatting.WHITE);
            int shown = 0;
            for (ItemStack stack : nearestData.observedHotbarItems) {
                if (shown >= 6) break;
                if (shown > 0) hotbar.append(EnumChatFormatting.GRAY).append(", ").append(EnumChatFormatting.WHITE);
                hotbar.append(getShortItemName(stack));
                shown++;
            }
            lines.add(HudLine.text(hotbar.toString()));
        }

        return true;
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

    private static String getPlayerTeamColor(EntityPlayer player) {
        Team team = player.getTeam();
        if (!(team instanceof ScorePlayerTeam)) {
            return EnumChatFormatting.WHITE.toString();
        }
        String prefix = ((ScorePlayerTeam) team).getColorPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return EnumChatFormatting.WHITE.toString();
        }
        // Extract just the first color code from the prefix (e.g. "§c[RED] " -> "§c")
        for (int i = 0; i < prefix.length() - 1; i++) {
            if (prefix.charAt(i) == '\u00a7') {
                char code = prefix.charAt(i + 1);
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    return "\u00a7" + code;
                }
            }
        }
        return EnumChatFormatting.WHITE.toString();
    }

    private static String getShortItemName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "?";
        String name = stack.getDisplayName();
        if (name.contains("Sword")) return "Sword";
        if (name.contains("Bow")) return "Bow";
        if (name.contains("Pickaxe")) return "Pick";
        if (name.contains("Axe")) return "Axe";
        if (name.contains("Pearl")) return "Pearl";
        if (name.contains("Fireball")) return "Fireball";
        if (name.contains("TNT")) return "TNT";
        if (name.contains("Wool") || name.contains("Terracotta") || name.contains("Clay")
                || name.contains("Sandstone") || name.contains("End Stone")
                || name.contains("Obsidian") || name.contains("Planks")) return "Blocks";
        if (name.contains("Potion") || name.contains("Water")) return "Potion";
        if (name.contains("Golden Apple")) return "Gapple";
        if (name.contains("Shears")) return "Shears";
        if (name.contains("Crossbow")) return "Crossbow";
        if (name.length() > 10) return name.substring(0, 10);
        return name;
    }
}
