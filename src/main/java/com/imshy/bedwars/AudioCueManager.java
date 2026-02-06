package com.imshy.bedwars;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized audio cue handling with per-cue cooldowns.
 */
public class AudioCueManager {

    public enum CueType {
        INVISIBLE_NEARBY,
        BED_DANGER,
        EXTREME_PLAYER_JOIN
    }

    private static final Map<CueType, Long> lastCueTime = new HashMap<CueType, Long>();

    /**
     * Play a configured cue if enabled and not on cooldown.
     */
    public static void playCue(final Minecraft mc, final CueType cueType) {
        if (mc == null || cueType == null) {
            return;
        }

        if (!mc.isCallingFromMinecraftThread()) {
            mc.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    playCueInternal(mc, cueType);
                }
            });
            return;
        }

        playCueInternal(mc, cueType);
    }

    private static void playCueInternal(Minecraft mc, CueType cueType) {
        if (mc.thePlayer == null || !ModConfig.isAudioAlertsEnabled() || !isCueEnabled(cueType)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastPlayed = lastCueTime.get(cueType);
        if (lastPlayed != null && now - lastPlayed < ModConfig.getAudioCueCooldownMs()) {
            return;
        }

        SoundProfile profile = getSoundProfile(cueType);
        mc.thePlayer.playSound(profile.soundName, (float) ModConfig.getAudioCueVolume(), profile.pitch);
        lastCueTime.put(cueType, now);
    }

    private static boolean isCueEnabled(CueType cueType) {
        switch (cueType) {
            case INVISIBLE_NEARBY:
                return ModConfig.isInvisibleAudioCueEnabled();
            case BED_DANGER:
                return ModConfig.isBedDangerAudioCueEnabled();
            case EXTREME_PLAYER_JOIN:
                return ModConfig.isExtremeJoinAudioCueEnabled();
            default:
                return false;
        }
    }

    private static SoundProfile getSoundProfile(CueType cueType) {
        switch (cueType) {
            case INVISIBLE_NEARBY:
                return new SoundProfile("note.pling", 1.8F);
            case BED_DANGER:
                return new SoundProfile("note.bass", 0.8F);
            case EXTREME_PLAYER_JOIN:
                return new SoundProfile("random.orb", 1.0F);
            default:
                return new SoundProfile("random.click", 1.0F);
        }
    }

    private static class SoundProfile {
        final String soundName;
        final float pitch;

        SoundProfile(String soundName, float pitch) {
            this.soundName = soundName;
            this.pitch = pitch;
        }
    }
}
