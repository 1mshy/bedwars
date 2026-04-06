package com.imshy.bedwars.runtime;

public class TrackedFireball {

    public final int entityId;
    public double posX;
    public double posY;
    public double posZ;
    public double motionX;
    public double motionY;
    public double motionZ;

    // Projected impact point (where ray-trace hit a block). If no hit within max trace distance,
    // impactValid is false and impactX/Y/Z hold the ray's far endpoint instead.
    public double impactX;
    public double impactY;
    public double impactZ;
    public boolean impactValid;

    // Closest-point-of-approach results relative to the local player at the most recent scan.
    // closestDistance is in blocks. ticksToClosest is an approximate tick count (positive = future).
    public double closestDistance;
    public double ticksToClosest;

    public boolean threatening;
    public long firstSeenMs;
    public long lastSeenMs;

    public TrackedFireball(int entityId, long nowMs) {
        this.entityId = entityId;
        this.firstSeenMs = nowMs;
        this.lastSeenMs = nowMs;
    }
}
