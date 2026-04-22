package com.imshy.bedwars.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracked state for a gravity-affected projectile (currently ender pearls).
 *
 * <p>Unlike {@link TrackedFireball}, the predicted path isn't a straight line —
 * the service integrates motion with gravity and drag step-by-step and stores
 * the resulting poly-line in {@link #arcPoints} plus a terminal {@link #landingX} /
 * {@link #landingY} / {@link #landingZ} landing point.
 */
public class TrackedProjectile {

    public static class Point {
        public final double x;
        public final double y;
        public final double z;
        Point(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public final int entityId;
    public double posX;
    public double posY;
    public double posZ;
    public double motionX;
    public double motionY;
    public double motionZ;

    public final List<Point> arcPoints = new ArrayList<Point>();
    public double landingX;
    public double landingY;
    public double landingZ;
    public boolean landingValid;

    /** Distance from the predicted landing spot to the local player (blocks). */
    public double landingPlayerDistance;
    /** Distance from the predicted landing spot to the nearest own-bed block (blocks), or -1 if unknown. */
    public double landingBedDistance = -1.0;

    public boolean threatening;
    public long firstSeenMs;
    public long lastSeenMs;

    public TrackedProjectile(int entityId, long nowMs) {
        this.entityId = entityId;
        this.firstSeenMs = nowMs;
        this.lastSeenMs = nowMs;
    }
}
