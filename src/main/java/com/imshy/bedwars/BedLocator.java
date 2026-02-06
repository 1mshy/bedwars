package com.imshy.bedwars;

import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Locates the nearest bed to a reference position.
 */
public class BedLocator {

    public static class BedLocation {
        private final List<BlockPos> bedBlocks;

        BedLocation(List<BlockPos> bedBlocks) {
            this.bedBlocks = bedBlocks;
        }

        public List<BlockPos> getBedBlocks() {
            return Collections.unmodifiableList(bedBlocks);
        }
    }

    /**
     * Finds the nearest bed and returns one or two connected bed blocks.
     */
    public static BedLocation locateNearestBed(World world, BlockPos origin, int horizontalRange, int verticalRange) {
        if (world == null || origin == null) {
            return null;
        }

        BlockPos nearestBed = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    BlockPos checkPos = origin.add(x, y, z);
                    if (world.getBlockState(checkPos).getBlock() != Blocks.bed) {
                        continue;
                    }

                    double distanceSq = checkPos.distanceSq(origin);
                    if (distanceSq < nearestDistanceSq) {
                        nearestDistanceSq = distanceSq;
                        nearestBed = checkPos;
                    }
                }
            }
        }

        if (nearestBed == null) {
            return null;
        }

        List<BlockPos> bedBlocks = new ArrayList<BlockPos>();
        bedBlocks.add(nearestBed);

        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            BlockPos adjacent = nearestBed.offset(facing);
            if (world.getBlockState(adjacent).getBlock() == Blocks.bed && !bedBlocks.contains(adjacent)) {
                bedBlocks.add(adjacent);
                break;
            }
        }

        return new BedLocation(bedBlocks);
    }
}
