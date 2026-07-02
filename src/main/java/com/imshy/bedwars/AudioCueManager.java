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
        FIREBALL_INCOMING,
        FIREBALL_BED_INCOMING,
        ENDER_PEARL_INCOMING,
        GENERATOR_TIER_SOON,
        BRIDGE_INCOMING,
        BED_TAMPER
    }

    private static final Map<CueType, Long> lastCueTime = new HashMap<CueType, Long>();

    public static void clearCooldowns() {
        lastCueTime.clear();
    }

    /**
     * Play a configured cue if enabled and not on cooldown.
     */
    public static void playCue(final Minecraft mc, final CueType cueType) {
        if (mc == null || cueType == null) {
            return;
        }

        if (!ModConfig.isModEnabled()) {
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
            case FIREBALL_INCOMING:
                return ModConfig.isFireballAudioCueEnabled();
            case FIREBALL_BED_INCOMING:
                return ModConfig.isFireballAudioCueEnabled();
            case ENDER_PEARL_INCOMING:
                return ModConfig.isEnderPearlTrackingEnabled();
            case GENERATOR_TIER_SOON:
                return ModConfig.isGeneratorTierCueEnabled();
            case BRIDGE_INCOMING:
                return ModConfig.isBridgeRadarEnabled();
            case BED_TAMPER:
                return ModConfig.isBedTamperAlarmEnabled();
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
            case FIREBALL_INCOMING:
                return new SoundProfile("fireworks.blast", 2.0F);
            case FIREBALL_BED_INCOMING:
                return new SoundProfile("mob.blaze.hit", 0.6F);
            case ENDER_PEARL_INCOMING:
                return new SoundProfile("mob.endermen.portal", 1.3F);
            case GENERATOR_TIER_SOON:
                return new SoundProfile("note.harp", 1.6F);
            case BRIDGE_INCOMING:
                return new SoundProfile("note.bass", 0.7F);
            case BED_TAMPER:
                return new SoundProfile("random.anvil_land", 1.2F);
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
