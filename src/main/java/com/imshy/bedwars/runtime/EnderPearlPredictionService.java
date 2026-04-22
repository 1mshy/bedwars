package com.imshy.bedwars.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

/**
 * Predicts where an ender pearl thrown by the local player <i>right now</i>
 * would land, using the same 1.8.9 thrown-entity physics as
 * {@link ProjectileTrackingService}. The prediction assumes the "ideal" throw
 * (no gaussian inaccuracy); the real pearl may land up to ~0.5 blocks off.
 *
 * <p>Seeded from:
 * <ul>
 *   <li>Eye position minus the vanilla throwable spawn offsets
 *       ({@code cos(yaw)*0.16}, {@code 0.10}, {@code sin(yaw)*0.16})</li>
 *   <li>Look vector scaled by the pearl's initial velocity ({@code 1.5})</li>
 *   <li>Player motion (Y always; X/Z only when airborne, mirroring vanilla)</li>
 * </ul>
 */
public class EnderPearlPredictionService {

    private static final double INITIAL_VELOCITY = 1.5D;
    private static final double SPAWN_HORIZONTAL_OFFSET = 0.16D;
    private static final double SPAWN_VERTICAL_OFFSET = 0.10D;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private final TrackedProjectile cached = new TrackedProjectile(-1, 0L);

    /**
     * Compute the predicted arc for the local player's held ender pearl.
     * Returns {@code null} if the world or player isn't available.
     */
    public TrackedProjectile predict(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }

        EntityPlayerSP player = mc.thePlayer;
        float yawRad = player.rotationYaw * DEG_TO_RAD;
        float pitchRad = player.rotationPitch * DEG_TO_RAD;

        double sinYaw = MathHelper.sin(yawRad);
        double cosYaw = MathHelper.cos(yawRad);
        double sinPitch = MathHelper.sin(pitchRad);
        double cosPitch = MathHelper.cos(pitchRad);

        double startX = player.posX - cosYaw * SPAWN_HORIZONTAL_OFFSET;
        double startY = player.posY + player.getEyeHeight() - SPAWN_VERTICAL_OFFSET;
        double startZ = player.posZ - sinYaw * SPAWN_HORIZONTAL_OFFSET;

        // Vanilla setHeadingFromThrower: direction vector * velocity, no RNG.
        double dx = -sinYaw * cosPitch;
        double dy = -sinPitch;
        double dz = cosYaw * cosPitch;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) {
            return null;
        }
        double scale = INITIAL_VELOCITY / len;
        double mx = dx * scale;
        double my = dy * scale;
        double mz = dz * scale;

        // Vanilla adds thrower momentum: Y always, X/Z only when airborne.
        my += player.motionY;
        if (!player.onGround) {
            mx += player.motionX;
            mz += player.motionZ;
        }

        cached.posX = startX;
        cached.posY = startY;
        cached.posZ = startZ;
        cached.motionX = mx;
        cached.motionY = my;
        cached.motionZ = mz;
        cached.threatening = false;
        cached.landingPlayerDistance = 0.0;
        cached.landingBedDistance = -1.0;

        ProjectileTrackingService.integrateArcFrom(mc.theWorld, cached,
                startX, startY, startZ, mx, my, mz);

        return cached;
    }
}
