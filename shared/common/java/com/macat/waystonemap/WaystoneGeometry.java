package com.macat.waystonemap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Shared, loader-independent waystone lookup and safe arrival placement.
 *
 * Callers provide their registry's block ID lookup, preserving the original
 * Forge/NeoForge/Fabric block detection behavior without hard loader imports.
 * Never chunk-load when allowChunkLoad is false (used by command interception).
 */
public final class WaystoneGeometry {
    private final Function<Block, ResourceLocation> blockIdLookup;
    private final Map<Block, Boolean> waystoneBlockCache = new ConcurrentHashMap<>();

    public WaystoneGeometry(Function<Block, ResourceLocation> blockIdLookup) {
        this.blockIdLookup = blockIdLookup;
    }

    public Optional<BlockPos> findWaystoneBottom(Level level, BlockPos targetPos) {
        return findWaystoneBottom(level, targetPos, true);
    }

    public Optional<BlockPos> findWaystoneBottom(Level level, BlockPos targetPos, boolean allowChunkLoad) {
        int radiusXZ = 1;
        int baseY = targetPos.getY();
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        for (int dy = -2; dy <= 2; dy++) {
            int y = baseY + dy;
            for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    checkPos.set(targetPos.getX() + dx, y, targetPos.getZ() + dz);
                    if (isWaystoneBlock(level, checkPos, allowChunkLoad)) {
                        // Walk down to bottom of the 2-block waystone.
                        BlockPos bottom = checkPos.immutable();
                        while (isWaystoneBlock(level, bottom.below(), allowChunkLoad)) {
                            bottom = bottom.below();
                        }
                        return Optional.of(bottom);
                    }
                }
            }
        }

        // Also handle the common case where the waypoint is on the TOP block.
        BlockPos belowTarget = targetPos.below();
        if (isWaystoneBlock(level, belowTarget, allowChunkLoad)) {
            BlockPos bottom = belowTarget;
            while (isWaystoneBlock(level, bottom.below(), allowChunkLoad)) {
                bottom = bottom.below();
            }
            return Optional.of(bottom);
        }

        return Optional.empty();
    }

    public boolean isWaystoneBlock(Level level, BlockPos pos) {
        return isWaystoneBlock(level, pos, true);
    }

    public boolean isWaystoneBlock(Level level, BlockPos pos, boolean allowChunkLoad) {
        if (level.isOutsideBuildHeight(pos)) return false;

        // getBlockState() on an unloaded server chunk can synchronously load/wait for that chunk.
        // hasChunkAt() lets detection/interception paths avoid causing ServerChunkCache.waitForTasks().
        if (!allowChunkLoad && !level.hasChunkAt(pos)) return false;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        // Cache per Block instance so repeated scans do not repeatedly ask the registry.
        return waystoneBlockCache.computeIfAbsent(state.getBlock(), block -> {
            ResourceLocation rl = blockIdLookup.apply(block);
            return rl != null && "waystones".equals(rl.getNamespace()) && rl.getPath().contains("waystone");
        });
    }

    private static final Direction[] SIDE_ORDER = new Direction[]{
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /**
     * Preferred behavior:
     *  1) Try adjacent spots at waystone level (needs 2 blocks free + standable floor)
     *  2) Try standing on top of a 1-block-high neighbor (feet at adjacent+1)
     *  3) Try above the waystone (feet at bottom+2)
     *  4) If no floor exists at that level, try 1-2 blocks lower (still 2 blocks free + floor)
     *  5) Fallback: allow spots with 2 blocks free even without a floor (prevents "false no space")
     */
    public Optional<Vec3> findSafeTeleportPos(ServerLevel level, BlockPos waystoneBottom) {
        // 1) Same level with floor
        Optional<Vec3> same = findAdjacent(level, waystoneBottom, true, new int[]{0});
        if (same.isPresent()) return same;

        // 2) On top of a 1-block-high neighbor (lets players stand on a block next to the waystone)
        Optional<Vec3> atopNeighbor = findAdjacentTop(level, waystoneBottom);
        if (atopNeighbor.isPresent()) return atopNeighbor;

        // 3) Above waystone (two blocks above bottom, because waystone is 2 tall)
        // Prefer this BEFORE placing players next to floorless drop-offs.
        BlockPos aboveFeet = waystoneBottom.above(2);
        if (isTwoTallFree(level, aboveFeet, true)) {
            return Optional.of(centerFeet(aboveFeet));
        }

        // 4) Lower levels with floor (up to 2 blocks down)
        Optional<Vec3> lower = findAdjacent(level, waystoneBottom, true, new int[]{-1, -2});
        if (lower.isPresent()) return lower;

        // 5) Fallback without floor requirement (same -> lower -> above)
        Optional<Vec3> sameNoFloor = findAdjacent(level, waystoneBottom, false, new int[]{0, -1, -2});
        if (sameNoFloor.isPresent()) return sameNoFloor;

        if (isTwoTallFree(level, aboveFeet, false)) {
            return Optional.of(centerFeet(aboveFeet));
        }

        return Optional.empty();
    }

    /**
     * Allows teleporting onto a single block adjacent to the waystone, e.g. if there's a 1-block step.
     * Example: if the block next to the waystone is solid, we can place the player on top of it.
     */
    private Optional<Vec3> findAdjacentTop(ServerLevel level, BlockPos waystoneBottom) {
        for (Direction dir : SIDE_ORDER) {
            BlockPos neighbor = waystoneBottom.relative(dir);
            BlockPos feet = neighbor.above();
            // Require a floor (the neighbor block) and 2 blocks of headroom at the feet position.
            if (isTwoTallFree(level, feet, true)) {
                return Optional.of(centerFeet(feet));
            }
        }
        return Optional.empty();
    }

    private Optional<Vec3> findAdjacent(ServerLevel level, BlockPos waystoneBottom, boolean requireFloor, int[] yOffsets) {
        for (int yOff : yOffsets) {
            for (Direction dir : SIDE_ORDER) {
                BlockPos feet = waystoneBottom.relative(dir).offset(0, yOff, 0);
                if (isTwoTallFree(level, feet, requireFloor)) {
                    return Optional.of(centerFeet(feet));
                }
            }
        }
        return Optional.empty();
    }

    private boolean isTwoTallFree(ServerLevel level, BlockPos feet, boolean requireFloor) {
        if (!isNonColliding(level, feet)) return false;
        if (!isNonColliding(level, feet.above())) return false;

        if (requireFloor) {
            BlockPos floorPos = feet.below();
            BlockState floor = level.getBlockState(floorPos);
            // Waystone tops may not report as "sturdy" even though you can stand on them.
            // Allow standing on the waystone itself (used for the "above waystone" case).
            if (!floor.isFaceSturdy(level, floorPos, Direction.UP) && !isWaystoneBlock(level, floorPos)) return false;
        }

        return true;
    }

    private boolean isNonColliding(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private Vec3 centerFeet(BlockPos feet) {
        return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
    }

}
