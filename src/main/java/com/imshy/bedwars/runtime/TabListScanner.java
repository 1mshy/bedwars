package com.imshy.bedwars.runtime;

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
                formattedName = info.getDisplayName().getFormattedText();
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
            formattedName = localInfo.getDisplayName().getFormattedText();
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
