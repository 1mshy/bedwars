package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks ender pearls thrown by non-teammates and predicts their landing point
 * using gravity-aware motion integration — the same 1.8.9 thrown-entity physics
 * the vanilla client runs per tick.
 *
 * <p>Integration constants:
 * <ul>
 *   <li>Gravity: {@code motionY -= 0.03} each tick after applying motion</li>
 *   <li>Drag: {@code motion{X,Y,Z} *= 0.99} each tick in air (pearls get 0.8 in water,
 *       but underwater pearling isn't relevant here)</li>
 * </ul>
 */
public class ProjectileTrackingService {

    static final double GRAVITY = 0.03D;
    static final double DRAG = 0.99D;
    static final int MAX_INTEGRATION_STEPS = 200; // ~10s worst case

    private final Map<Integer, TrackedProjectile> tracked = new HashMap<Integer, TrackedProjectile>();
    private final Set<Integer> alertedIds = new HashSet<Integer>();

    public Collection<TrackedProjectile> getTracked() {
        return Collections.unmodifiableCollection(tracked.values());
    }

    public void clearAll() {
        tracked.clear();
        alertedIds.clear();
    }

    public void scanProjectiles(Minecraft mc, MatchThreatService matchThreatService,
                                 List<BlockPos> ownBedBlocks) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<Integer> seenThisTick = new HashSet<Integer>();
        double alertRadius = ModConfig.getEnderPearlAlertRadius();

        double playerX = mc.thePlayer.posX;
        double playerY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double playerZ = mc.thePlayer.posZ;

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityEnderPearl)) {
                continue;
            }

            EntityThrowable pearl = (EntityThrowable) entity;

            // Skip pearls thrown by the local player or by confirmed teammates.
            EntityLivingBase thrower = pearl.getThrower();
            if (thrower == mc.thePlayer) {
                continue;
            }
            if (thrower instanceof net.minecraft.entity.player.EntityPlayer
                    && matchThreatService != null
                    && matchThreatService.isTeammate(mc, mc.thePlayer,
                            (net.minecraft.entity.player.EntityPlayer) thrower)) {
                continue;
            }

            int id = pearl.getEntityId();
            seenThisTick.add(id);

            TrackedProjectile tp = tracked.get(id);
            if (tp == null) {
                tp = new TrackedProjectile(id, now);
                tracked.put(id, tp);
            }
            tp.posX = pearl.posX;
            tp.posY = pearl.posY;
            tp.posZ = pearl.posZ;
            tp.motionX = pearl.motionX;
            tp.motionY = pearl.motionY;
            tp.motionZ = pearl.motionZ;
            tp.lastSeenMs = now;

            integrateArc(mc, tp);

            tp.landingPlayerDistance = distance(tp.landingX, tp.landingY, tp.landingZ,
                    playerX, playerY, playerZ);

            tp.landingBedDistance = -1.0;
            if (ownBedBlocks != null && !ownBedBlocks.isEmpty()) {
                double best = Double.MAX_VALUE;
                for (BlockPos bed : ownBedBlocks) {
                    double d = distance(tp.landingX, tp.landingY, tp.landingZ,
                            bed.getX() + 0.5, bed.getY() + 0.5, bed.getZ() + 0.5);
                    if (d < best) best = d;
                }
                tp.landingBedDistance = best;
            }

            boolean nearPlayer = tp.landingPlayerDistance < alertRadius;
            boolean nearBed = tp.landingBedDistance >= 0 && tp.landingBedDistance < alertRadius;
            tp.threatening = nearPlayer || nearBed;

            if (tp.threatening && !alertedIds.contains(id)) {
                alertedIds.add(id);
                AudioCueManager.playCue(mc, AudioCueManager.CueType.ENDER_PEARL_INCOMING);
            }
        }

        Iterator<Map.Entry<Integer, TrackedProjectile>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrackedProjectile> entry = it.next();
            if (!seenThisTick.contains(entry.getKey())) {
                it.remove();
                alertedIds.remove(entry.getKey());
            }
        }
    }

    private void integrateArc(Minecraft mc, TrackedProjectile tp) {
        integrateArcFrom(mc.theWorld, tp, tp.posX, tp.posY, tp.posZ,
                tp.motionX, tp.motionY, tp.motionZ);
    }

    /**
     * Step-integrate 1.8.9 thrown-entity physics from an arbitrary origin and
     * motion, populating {@code tp.arcPoints} plus the terminal landing
     * position. Shared between the enemy-pearl tracker and the local-player
     * pre-throw preview so both produce identical curves.
     */
    static void integrateArcFrom(World world, TrackedProjectile tp,
                                  double startX, double startY, double startZ,
                                  double motionX, double motionY, double motionZ) {
        tp.arcPoints.clear();
        double x = startX;
        double y = startY;
        double z = startZ;
        double mx = motionX;
        double my = motionY;
        double mz = motionZ;

        tp.arcPoints.add(new TrackedProjectile.Point(x, y, z));
        tp.landingValid = false;
        tp.landingX = x;
        tp.landingY = y;
        tp.landingZ = z;

        if (world == null) {
            return;
        }

        for (int step = 0; step < MAX_INTEGRATION_STEPS; step++) {
            double nx = x + mx;
            double ny = y + my;
            double nz = z + mz;

            Vec3 start = new Vec3(x, y, z);
            Vec3 end = new Vec3(nx, ny, nz);
            MovingObjectPosition hit = world.rayTraceBlocks(start, end, false, true, false);
            if (hit != null && hit.hitVec != null) {
                tp.landingValid = true;
                tp.landingX = hit.hitVec.xCoord;
                tp.landingY = hit.hitVec.yCoord;
                tp.landingZ = hit.hitVec.zCoord;
                tp.arcPoints.add(new TrackedProjectile.Point(tp.landingX, tp.landingY, tp.landingZ));
                return;
            }

            x = nx;
            y = ny;
            z = nz;

            mx *= DRAG;
            my *= DRAG;
            mz *= DRAG;
            my -= GRAVITY;

            if ((step & 1) == 0) {
                tp.arcPoints.add(new TrackedProjectile.Point(x, y, z));
            }
        }

        tp.landingX = x;
        tp.landingY = y;
        tp.landingZ = z;
        tp.arcPoints.add(new TrackedProjectile.Point(x, y, z));
    }

    private static double distance(double x1, double y1, double z1,
                                    double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
