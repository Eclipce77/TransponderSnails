package net.eclipce.transpondersnails.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Spawn conditions for Den Den Mushi entities
 * Simplified to spawn like vanilla passive mobs
 */
public class DenDenMushiSpawnConditions {

    /**
     * Main spawn check - simplified version
     */
    public static boolean checkDenDenMushiSpawnRules(EntityType<? extends PathfinderMob> entityType,
                                                     ServerLevelAccessor level,
                                                     MobSpawnType spawnType,
                                                     BlockPos pos,
                                                     RandomSource random) {

        System.out.println("=== DEN DEN MUSHI SPAWN ATTEMPT at " + pos + " ===");

        // Dimension check removed - biome modifier handles this!

        // 1. Don't spawn in caves (unless structure spawn)
        if (spawnType != MobSpawnType.STRUCTURE && isInCave(level, pos)) {
            System.out.println("REJECTED: In cave");
            return false;
        }
        System.out.println("✓ Not in cave");

        // 2. Check if position is valid
        if (!hasValidSpawnSpace(level, pos)) {
            System.out.println("REJECTED: Invalid spawn space");
            return false;
        }
        System.out.println("✓ Valid spawn space");

        System.out.println("✓✓✓ SPAWN APPROVED ✓✓✓");
        return true;
    }

    /**
     * Check if in Overworld dimension
     */
    private static boolean isOverworld(LevelAccessor level) {
        // For spawn checks, level is ServerLevelAccessor which we need to handle differently
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.dimension() == Level.OVERWORLD;
        }

        // Fallback: check dimension key string
        try {
            // This should work for ServerLevelAccessor
            if (level.getLevelData() != null) {
                // If we can't get dimension directly, assume overworld for now
                // The biome modifier already restricts to overworld biomes
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to check dimension: " + e.getMessage());
        }

        return false;
    }

    /**
     * Check if the spawn location is in a cave
     */
    private static boolean isInCave(ServerLevelAccessor level, BlockPos pos) {
        // If can see sky, not in cave
        if (level.canSeeSky(pos)) {
            return false;
        }

        // Check depth below surface
        int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int depthBelowSurface = surfaceHeight - pos.getY();

        // If more than 10 blocks below surface and can't see sky = cave
        return depthBelowSurface > 10;
    }

    /**
     * Check if spawn position has valid space
     */
    private static boolean hasValidSpawnSpace(ServerLevelAccessor level, BlockPos pos) {
        // Check if in water (allowed)
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER);

        if (inWater) {
            // Underwater spawning
            // Need solid block below and water above
            BlockPos below = pos.below();
            return level.getBlockState(below).isSolid() &&
                    level.getFluidState(pos.above()).is(FluidTags.WATER);
        } else {
            // Land spawning
            BlockPos below = pos.below();

            // Need solid block below
            if (!level.getBlockState(below).isValidSpawn(level, below, EntityType.PIG)) {
                return false;
            }

            // Need air space (2 blocks tall)
            return level.getBlockState(pos).isAir() &&
                    level.getBlockState(pos.above()).isAir();
        }
    }
}