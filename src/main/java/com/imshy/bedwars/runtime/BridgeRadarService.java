package com.imshy.bedwars.runtime;

import com.imshy.bedwars.AudioCueManager;
import com.imshy.bedwars.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-thread consumer of {@link BlockChangeFeed}: clusters block
 * placements into advancing bridge runs (the radar) and watches for block
 * changes near the local team's bed (the tamper alarm).
 *
 * <p>Scope, honestly: block updates only arrive for loaded chunks (Hypixel's
 * ~6-8 chunk view distance), packets carry no placer identity, and builder
 * attribution degrades to direction-only beyond entity tracking range. Even
 * so, a bridge is detected 30-60 blocks out — 10-15s before an enemy entity
 * crosses the 15-block bed-proximity alert — and a defense break is heard the
 * instant the wool pops, even behind terrain.
 *
 * <p>Everything here is passive analysis of already-received packets plus
 * local rendering/audio; nothing outbound is produced.
 */
public class BridgeRadarService {

    // Clustering
    private static final double ATTACH_DISTANCE_SQ = 3.5 * 3.5;
    private static final int ATTACH_MAX_DY = 2;
    private static final long CLUSTER_IDLE_DROP_MS = 8_000L;
    private static final int MIN_RUN_POINTS = 6;
    private static final double MIN_HORIZONTAL_EXTENT = 6.0;
    private static final double MAX_LINE_DEVIATION = 2.5;
    // A long bridge keeps only its recent geometry — enough for direction,
    // head, and ETA — so per-cluster memory and per-tick analysis stay bounded.
    private static final int MAX_CLUSTER_POINTS = 96;
    // Full alert rebuild (geometry + entity scan) runs on this tick cadence
    // when no new placements arrived.
    private static final int REBUILD_INTERVAL_TICKS = 10;

    // Threat projection
    private static final double APPROACH_RADIUS = 15.0;
    private static final double MAX_PROJECT_DISTANCE = 90.0;

    // Tamper alarm
    private static final double TEAMMATE_NEAR_SQ = 6.0 * 6.0;
    private static final long TAMPER_BANNER_MS = 4_000L;
    private static final long TAMPER_RECUE_MS = 5_000L;

    /** One suspected bridge run advancing toward the bed or player. */
    public static final class BridgeAlert {
        public final List<BlockPos> points;
        /** Direction octant from the defended point toward the bridge head (e.g. "NE"). */
        public final String direction;
        public final double distanceBlocks;
        public final int etaSeconds;
        /** Nearest enemy to the bridge head, or null beyond tracking range. */
        public final String builderName;

        BridgeAlert(List<BlockPos> points, String direction, double distanceBlocks,
                int etaSeconds, String builderName) {
            this.points = points;
            this.direction = direction;
            this.distanceBlocks = distanceBlocks;
            this.etaSeconds = etaSeconds;
            this.builderName = builderName;
        }
    }

    static final class Cluster {
        final List<BlockChangeFeed.BlockChangeEvent> points =
                new ArrayList<BlockChangeFeed.BlockChangeEvent>();
        long lastAt;
        boolean cueFired;
    }

    private final List<Cluster> clusters = new ArrayList<Cluster>();
    private final List<BridgeAlert> activeAlerts = new ArrayList<BridgeAlert>();
    private int ticksSinceRebuild;

    private long tamperBannerUntil;
    private long lastTamperCueAt;
    private String tamperDirection = "";

    /**
     * Feeds one tick's drained block changes through both detectors.
     * Client thread only.
     */
    public void onClientTick(Minecraft mc, List<BlockChangeFeed.BlockChangeEvent> events,
            MatchThreatService matchThreatService, List<BlockPos> bedBlocks,
            boolean ownBedAlive) {
        long now = System.currentTimeMillis();

        if (ModConfig.isBedTamperAlarmEnabled() && ownBedAlive) {
            detectTamper(mc, events, matchThreatService, bedBlocks, now);
        }
        if (ModConfig.isBridgeRadarEnabled()) {
            boolean newPlacements = false;
            for (BlockChangeFeed.BlockChangeEvent ev : events) {
                if (!ev.isAir) {
                    attach(ev);
                    newPlacements = true;
                }
            }
            // Rebuild only when the geometry changed or on the idle cadence
            // (alerts still need to expire as clusters go idle).
            ticksSinceRebuild++;
            if (newPlacements || ticksSinceRebuild >= REBUILD_INTERVAL_TICKS) {
                ticksSinceRebuild = 0;
                pruneClusters(now);
                rebuildAlerts(mc, matchThreatService, bedBlocks, now);
            }
        } else if (!clusters.isEmpty() || !activeAlerts.isEmpty()) {
            clusters.clear();
            activeAlerts.clear();
        }
    }

    /** Active bridge alerts for the HUD/overlay. Client thread only. */
    public List<BridgeAlert> getActiveAlerts() {
        return activeAlerts;
    }

    /** Red banner lines for the HUD ALERTS section (empty when calm). */
    public List<String> getHudAlertLines(long now) {
        List<String> lines = new ArrayList<String>();
        if (now < tamperBannerUntil) {
            lines.add("§c⚠ DEFENSE BEING BROKEN"
                    + (tamperDirection.isEmpty() ? "" : " (" + tamperDirection + ")"));
        }
        for (BridgeAlert alert : activeAlerts) {
            StringBuilder sb = new StringBuilder("§6⚠ Bridge from ")
                    .append(alert.direction).append(", ~")
                    .append((int) alert.distanceBlocks).append("m");
            if (alert.etaSeconds > 0) {
                sb.append(" ETA ").append(alert.etaSeconds).append("s");
            }
            if (alert.builderName != null) {
                sb.append(" §e(").append(alert.builderName).append(")");
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    public void clear() {
        clusters.clear();
        activeAlerts.clear();
        tamperBannerUntil = 0;
        lastTamperCueAt = 0;
        tamperDirection = "";
    }

    // -------------------------------------------------------------------
    // Bed-tamper alarm
    // -------------------------------------------------------------------

    private void detectTamper(Minecraft mc, List<BlockChangeFeed.BlockChangeEvent> events,
            MatchThreatService matchThreatService, List<BlockPos> bedBlocks, long now) {
        if (bedBlocks == null || bedBlocks.isEmpty() || mc.thePlayer == null) {
            return;
        }
        double radius = ModConfig.getBedTamperRadius();
        double radiusSq = radius * radius;
        for (BlockChangeFeed.BlockChangeEvent ev : events) {
            double bestSq = Double.MAX_VALUE;
            BlockPos nearestBed = null;
            for (BlockPos bed : bedBlocks) {
                double dx = ev.pos.getX() - bed.getX();
                double dy = ev.pos.getY() - bed.getY();
                double dz = ev.pos.getZ() - bed.getZ();
                double dSq = dx * dx + dy * dy + dz * dz;
                if (dSq < bestSq) {
                    bestSq = dSq;
                    nearestBed = bed;
                }
            }
            if (nearestBed == null || bestSq > radiusSq) {
                continue;
            }
            // A teammate (or the player) next to the changed block means our
            // own team is building/mining — not an attack. Teammates beyond
            // tracking range can still false-positive; accepted trade-off.
            if (isFriendlyNear(mc, matchThreatService, ev.pos)) {
                continue;
            }
            tamperDirection = octantFrom(mc.thePlayer.posX, mc.thePlayer.posZ,
                    ev.pos.getX() + 0.5, ev.pos.getZ() + 0.5);
            tamperBannerUntil = now + TAMPER_BANNER_MS;
            if (now - lastTamperCueAt >= TAMPER_RECUE_MS) {
                lastTamperCueAt = now;
                AudioCueManager.playCue(mc, AudioCueManager.CueType.BED_TAMPER);
            }
        }
    }

    private boolean isFriendlyNear(Minecraft mc, MatchThreatService matchThreatService,
            BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) obj;
            double dx = p.posX - cx;
            double dy = p.posY - cy;
            double dz = p.posZ - cz;
            if (dx * dx + dy * dy + dz * dz > TEAMMATE_NEAR_SQ) {
                continue;
            }
            if (p == mc.thePlayer || matchThreatService.isTeammate(mc, mc.thePlayer, p)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------
    // Bridge clustering
    // -------------------------------------------------------------------

    private void attach(BlockChangeFeed.BlockChangeEvent ev) {
        for (Cluster c : clusters) {
            BlockChangeFeed.BlockChangeEvent tail = c.points.get(c.points.size() - 1);
            double dx = ev.pos.getX() - tail.pos.getX();
            double dy = ev.pos.getY() - tail.pos.getY();
            double dz = ev.pos.getZ() - tail.pos.getZ();
            if (Math.abs(dy) <= ATTACH_MAX_DY && dx * dx + dz * dz <= ATTACH_DISTANCE_SQ) {
                c.points.add(ev);
                if (c.points.size() > MAX_CLUSTER_POINTS) {
                    c.points.remove(0);
                }
                c.lastAt = ev.when;
                return;
            }
        }
        Cluster c = new Cluster();
        c.points.add(ev);
        c.lastAt = ev.when;
        clusters.add(c);
    }

    private void pruneClusters(long now) {
        Iterator<Cluster> it = clusters.iterator();
        while (it.hasNext()) {
            if (now - it.next().lastAt > CLUSTER_IDLE_DROP_MS) {
                it.remove();
            }
        }
        // Runaway safety: a chaotic mid-fight map can spawn many one-point
        // clusters; keep only the most recent ones.
        while (clusters.size() > 64) {
            clusters.remove(0);
        }
    }

    private void rebuildAlerts(Minecraft mc, MatchThreatService matchThreatService,
            List<BlockPos> bedBlocks, long now) {
        activeAlerts.clear();
        if (mc.thePlayer == null) {
            return;
        }
        // Defend the bed when it stands; defend the player otherwise.
        double targetX;
        double targetZ;
        if (bedBlocks != null && !bedBlocks.isEmpty()) {
            BlockPos bed = bedBlocks.get(0);
            targetX = bed.getX() + 0.5;
            targetZ = bed.getZ() + 0.5;
        } else {
            targetX = mc.thePlayer.posX;
            targetZ = mc.thePlayer.posZ;
        }

        for (Cluster c : clusters) {
            BridgeAlert alert = analyzeCluster(c, targetX, targetZ);
            if (alert == null) {
                continue;
            }
            // Friendly bridges (teammate at the head) are not alerts.
            BlockPos head = alert.points.get(alert.points.size() - 1);
            String builder = null;
            boolean friendly = false;
            double bestSq = 6.0 * 6.0;
            for (Object obj : mc.theWorld.playerEntities) {
                if (!(obj instanceof EntityPlayer)) {
                    continue;
                }
                EntityPlayer p = (EntityPlayer) obj;
                double dx = p.posX - (head.getX() + 0.5);
                double dy = p.posY - (head.getY() + 1.0);
                double dz = p.posZ - (head.getZ() + 0.5);
                double dSq = dx * dx + dy * dy + dz * dz;
                if (dSq < bestSq) {
                    bestSq = dSq;
                    if (p == mc.thePlayer || matchThreatService.isTeammate(mc, mc.thePlayer, p)) {
                        friendly = true;
                    } else {
                        friendly = false;
                        builder = p.getName();
                    }
                }
            }
            if (friendly) {
                continue;
            }
            activeAlerts.add(new BridgeAlert(alert.points, alert.direction,
                    alert.distanceBlocks, alert.etaSeconds, builder));
            if (!c.cueFired) {
                c.cueFired = true;
                AudioCueManager.playCue(mc, AudioCueManager.CueType.BRIDGE_INCOMING);
            }
        }
    }

    /**
     * Pure geometry: is this cluster a long, roughly straight, advancing run
     * whose extension passes near the defended point? Package-private for
     * unit tests (no Minecraft state touched).
     */
    static BridgeAlert analyzeCluster(Cluster c, double targetX, double targetZ) {
        if (c.points.size() < MIN_RUN_POINTS) {
            return null;
        }
        BlockChangeFeed.BlockChangeEvent first = c.points.get(0);
        BlockChangeFeed.BlockChangeEvent last = c.points.get(c.points.size() - 1);

        double dirX = last.pos.getX() - first.pos.getX();
        double dirZ = last.pos.getZ() - first.pos.getZ();
        double extent = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (extent < MIN_HORIZONTAL_EXTENT) {
            return null;
        }
        dirX /= extent;
        dirZ /= extent;

        // Straightness: mean perpendicular deviation from the first->last line.
        double deviation = 0;
        for (BlockChangeFeed.BlockChangeEvent ev : c.points) {
            double relX = ev.pos.getX() - first.pos.getX();
            double relZ = ev.pos.getZ() - first.pos.getZ();
            deviation += Math.abs(relX * dirZ - relZ * dirX);
        }
        if (deviation / c.points.size() > MAX_LINE_DEVIATION) {
            return null;
        }

        // Does the extended run approach the defended point, ahead of the head?
        double headX = last.pos.getX() + 0.5;
        double headZ = last.pos.getZ() + 0.5;
        double toTargetX = targetX - headX;
        double toTargetZ = targetZ - headZ;
        double along = toTargetX * dirX + toTargetZ * dirZ;
        if (along <= 0 || along > MAX_PROJECT_DISTANCE) {
            return null;
        }
        double perp = Math.abs(toTargetX * dirZ - toTargetZ * dirX);
        if (perp > APPROACH_RADIUS) {
            return null;
        }

        double distance = Math.sqrt(toTargetX * toTargetX + toTargetZ * toTargetZ);
        long buildMs = last.when - first.when;
        int eta = 0;
        if (buildMs > 500) {
            double blocksPerSecond = extent / (buildMs / 1000.0);
            if (blocksPerSecond > 0.3) {
                eta = (int) Math.round(distance / blocksPerSecond);
            }
        }
        String direction = octantFrom(targetX, targetZ, headX, headZ);

        List<BlockPos> pts = new ArrayList<BlockPos>(c.points.size());
        for (BlockChangeFeed.BlockChangeEvent ev : c.points) {
            pts.add(ev.pos);
        }
        return new BridgeAlert(pts, direction, distance, eta, null);
    }

    /** Compass octant of (x,z) as seen from (fromX,fromZ): N is -Z, E is +X. */
    static String octantFrom(double fromX, double fromZ, double x, double z) {
        double dx = x - fromX;
        double dz = z - fromZ;
        if (dx == 0 && dz == 0) {
            return "";
        }
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = N, 90 = E
        if (angle < 0) {
            angle += 360;
        }
        String[] octants = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return octants[(int) Math.round(angle / 45.0) % 8];
    }
}
