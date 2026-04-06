package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Tracks active Ghast-style fireballs ({@link EntityLargeFireball}) — the entity class Hypixel
 * Bedwars uses for its shop fireball. Computes projected impact points using the fireball's
 * current motion vector (Bedwars fireballs travel in a straight line and are not affected by
 * gravity) and determines whether the fireball is heading toward the local player.
 */
public class FireballTrackingService {

    private final Map<Integer, TrackedFireball> tracked = new HashMap<Integer, TrackedFireball>();
    private final Set<Integer> alertedIds = new HashSet<Integer>();

    public Collection<TrackedFireball> getTracked() {
        return Collections.unmodifiableCollection(tracked.values());
    }

    public void clearAll() {
        tracked.clear();
        alertedIds.clear();
    }

    public void scanFireballs(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<Integer> seenThisTick = new HashSet<Integer>();
        double alertRadius = ModConfig.getFireballAlertRadius();
        int maxTrace = ModConfig.getFireballMaxTraceDistance();

        double playerX = mc.thePlayer.posX;
        double playerY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double playerZ = mc.thePlayer.posZ;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLargeFireball)) {
                continue;
            }

            EntityLargeFireball fireball = (EntityLargeFireball) entity;
            int id = fireball.getEntityId();
            seenThisTick.add(id);

            TrackedFireball tf = tracked.get(id);
            if (tf == null) {
                tf = new TrackedFireball(id, now);
                tracked.put(id, tf);
            }

            tf.posX = fireball.posX;
            tf.posY = fireball.posY;
            tf.posZ = fireball.posZ;
            tf.motionX = fireball.motionX;
            tf.motionY = fireball.motionY;
            tf.motionZ = fireball.motionZ;
            tf.lastSeenMs = now;

            double motionLenSq = tf.motionX * tf.motionX
                    + tf.motionY * tf.motionY
                    + tf.motionZ * tf.motionZ;

            if (motionLenSq < 1.0E-6) {
                tf.impactValid = false;
                tf.impactX = tf.posX;
                tf.impactY = tf.posY;
                tf.impactZ = tf.posZ;
                tf.closestDistance = distanceTo(tf.posX, tf.posY, tf.posZ, playerX, playerY, playerZ);
                tf.ticksToClosest = 0.0;
                tf.threatening = false;
                continue;
            }

            double motionLen = Math.sqrt(motionLenSq);
            // Extend the motion vector out to maxTrace blocks for the ray-trace endpoint.
            double scale = maxTrace / motionLen;
            double endX = tf.posX + tf.motionX * scale;
            double endY = tf.posY + tf.motionY * scale;
            double endZ = tf.posZ + tf.motionZ * scale;

            Vec3 start = new Vec3(tf.posX, tf.posY, tf.posZ);
            Vec3 end = new Vec3(endX, endY, endZ);
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, end, false, true, false);

            if (hit != null && hit.hitVec != null) {
                tf.impactValid = true;
                tf.impactX = hit.hitVec.xCoord;
                tf.impactY = hit.hitVec.yCoord;
                tf.impactZ = hit.hitVec.zCoord;
            } else {
                tf.impactValid = false;
                tf.impactX = endX;
                tf.impactY = endY;
                tf.impactZ = endZ;
            }

            // Closest-point-of-approach between the ray P(t) = F + M*t and the player position Q.
            // t* = dot(Q - F, M) / dot(M, M), clamped to t >= 0 (future only).
            double dx = playerX - tf.posX;
            double dy = playerY - tf.posY;
            double dz = playerZ - tf.posZ;
            double tStar = (dx * tf.motionX + dy * tf.motionY + dz * tf.motionZ) / motionLenSq;
            if (tStar < 0.0) {
                tStar = 0.0;
            }

            // Clamp to the segment between the fireball and its projected impact so we don't
            // consider closest-approach points on the far side of a wall.
            double segmentLen;
            if (tf.impactValid) {
                double ix = tf.impactX - tf.posX;
                double iy = tf.impactY - tf.posY;
                double iz = tf.impactZ - tf.posZ;
                segmentLen = Math.sqrt(ix * ix + iy * iy + iz * iz) / motionLen;
            } else {
                segmentLen = scale;
            }
            if (tStar > segmentLen) {
                tStar = segmentLen;
            }

            double closestX = tf.posX + tf.motionX * tStar;
            double closestY = tf.posY + tf.motionY * tStar;
            double closestZ = tf.posZ + tf.motionZ * tStar;
            tf.closestDistance = distanceTo(closestX, closestY, closestZ, playerX, playerY, playerZ);
            tf.ticksToClosest = tStar;
            tf.threatening = tf.closestDistance < alertRadius;

            if (tf.threatening && !alertedIds.contains(id)) {
                alertedIds.add(id);
                if (ModConfig.isFireballAudioCueEnabled()) {
                    AudioCueManager.playCue(mc, AudioCueManager.CueType.FIREBALL_INCOMING);
                }
            }
        }

        // Prune entries for fireballs that no longer exist (exploded / despawned).
        Iterator<Map.Entry<Integer, TrackedFireball>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrackedFireball> entry = it.next();
            if (!seenThisTick.contains(entry.getKey())) {
                it.remove();
                alertedIds.remove(entry.getKey());
            }
        }
    }

    private static double distanceTo(double x1, double y1, double z1,
                                     double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
