package com.imshy.bedwars.runtime;

import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TabListScanner {

    public static List<TabListPlayer> scanAllPlayers(Minecraft mc) {
        List<TabListPlayer> result = new ArrayList<TabListPlayer>();
        if (mc == null || mc.getNetHandler() == null || mc.thePlayer == null) {
            return result;
        }

        String localUuid = mc.thePlayer.getUniqueID().toString();
        Collection<NetworkPlayerInfo> playerInfoMap = mc.getNetHandler().getPlayerInfoMap();

        for (NetworkPlayerInfo info : playerInfoMap) {
            if (info.getGameProfile() == null) {
                continue;
            }

            String name = info.getGameProfile().getName();
            if (name == null || name.isEmpty()) {
                continue;
            }

            if (MatchThreatService.isWatchdogBotName(name)) {
                continue;
            }

            String uuid = info.getGameProfile().getId() != null
                    ? info.getGameProfile().getId().toString()
                    : null;

            boolean isLocal = localUuid.equals(uuid);

            String formattedName = null;
            if (info.getDisplayName() != null) {
                // Tab stats injection appends " §8| ..." (and "[NICK]" for nicked
                // players) to display names; team parsing must see the server's
                // original text or the suffix's bracket/color leaks into results.
                formattedName = TabStatsInjector.stripInjectedSuffix(
                        info.getDisplayName().getFormattedText());
            }

            if (formattedName == null || formattedName.isEmpty()) {
                continue;
            }

            Character colorCode = parseFirstColorCode(formattedName);
            if (colorCode == null) {
                continue;
            }

            String teamName = parseTeamName(formattedName);
            String teamColorPrefix = "\u00a7" + colorCode;

            result.add(new TabListPlayer(name, uuid, colorCode, teamName, teamColorPrefix, isLocal));
        }

        return result;
    }

    public static Character getLocalPlayerColorCode(Minecraft mc) {
        if (mc == null || mc.getNetHandler() == null || mc.thePlayer == null) {
            return null;
        }

        NetworkPlayerInfo localInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (localInfo == null) {
            return null;
        }

        String formattedName = null;
        if (localInfo.getDisplayName() != null) {
            // The local player is never injected, so the strip is a no-op here —
            // kept for symmetry with scanAllPlayers.
            formattedName = TabStatsInjector.stripInjectedSuffix(
                    localInfo.getDisplayName().getFormattedText());
        }

        if (formattedName == null || formattedName.isEmpty()) {
            return null;
        }

        return parseFirstColorCode(formattedName);
    }

    private static Character parseFirstColorCode(String formattedText) {
        for (int i = 0; i < formattedText.length() - 1; i++) {
            if (formattedText.charAt(i) == '\u00a7') {
                char code = Character.toLowerCase(formattedText.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    return Character.valueOf(code);
                }
            }
        }
        return null;
    }

    private static String parseTeamName(String formattedText) {
        String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(formattedText);
        if (stripped == null) {
            return "?";
        }

        int open = stripped.indexOf('[');
        int close = stripped.indexOf(']', open + 1);
        if (open >= 0 && close > open) {
            String name = stripped.substring(open + 1, close).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }

        return "?";
    }

    /**
     * Parse a star count from a tab display-name bracket token. In pre-game
     * lobbies Hypixel's tab format is "[123✫] Name", so parseTeamName captures
     * "123✫"; in-game the same slot holds the team tag (e.g. "R"), which has no
     * leading digits. The digits must be followed by a non-alphanumeric glyph
     * (✫, ✪, ⚝, ...) so plain numeric tokens are not mistaken for star counts.
     *
     * @return the star count, or -1 when the token is not a star prefix
     */
    public static int parseStarCount(String bracketToken) {
        if (bracketToken == null) {
            return -1;
        }
        String token = bracketToken.trim();
        int digits = 0;
        while (digits < token.length() && Character.isDigit(token.charAt(digits))) {
            digits++;
        }
        if (digits == 0 || digits == token.length()) {
            return -1;
        }
        if (Character.isLetterOrDigit(token.charAt(digits))) {
            return -1;
        }
        try {
            return Integer.parseInt(token.substring(0, digits));
        } catch (NumberFormatException e) {
            return -1; // absurd digit runs overflow int
        }
    }

    /**
     * Pick the fetch-queue priority for a tab-list player from what is already
     * known: unknown identity (no cached stats, no known UUID) outranks every
     * tab-driven hint; a parsed high-star prefix outranks the default. Shared
     * by the tab-scan fetch paths (LobbyTrackerService, TeamDangerAnalyzer).
     */
    static HypixelAPI.FetchPriority resolveFetchPriority(TabListPlayer tp) {
        if (HypixelAPI.getCachedStats(tp.name) == null && !HypixelAPI.hasKnownUuid(tp.name)) {
            return HypixelAPI.FetchPriority.UNKNOWN_PLAYER;
        }
        if (parseStarCount(tp.teamName) >= ModConfig.getHighStarThreshold()) {
            return HypixelAPI.FetchPriority.TAB_HIGH_STAR;
        }
        return HypixelAPI.FetchPriority.NORMAL;
    }

    public static class TabListPlayer {
        public final String name;
        public final String uuid;
        public final Character teamColorCode;
        public final String teamName;
        public final String teamColorPrefix;
        public final boolean isLocalPlayer;

        TabListPlayer(String name, String uuid, Character teamColorCode, String teamName,
                      String teamColorPrefix, boolean isLocalPlayer) {
            this.name = name;
            this.uuid = uuid;
            this.teamColorCode = teamColorCode;
            this.teamName = teamName;
            this.teamColorPrefix = teamColorPrefix;
            this.isLocalPlayer = isLocalPlayer;
        }
    }
}
