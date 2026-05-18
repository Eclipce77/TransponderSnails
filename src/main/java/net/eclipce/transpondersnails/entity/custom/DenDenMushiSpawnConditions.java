package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.config.ModConfig;
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
 * Simplified to spawn like vanilla passive mobs, with config support
 */
public class DenDenMushiSpawnConditions {

    /**
     * Main spawn check - now with config support
     */
    public static boolean checkDenDenMushiSpawnRules(EntityType<? extends PathfinderMob> entityType,
                                                     ServerLevelAccessor level,
                                                     MobSpawnType spawnType,
                                                     BlockPos pos,
                                                     RandomSource random) {

        // 1. Check if Den Den Mushi spawning is enabled in config
        if (!ModConfig.isSnailSpawnEnabled("den_den_mushi")) {
            return false;
        }

        // 2. Check spawn rate from config
        double spawnRate = ModConfig.getDenDenMushiSpawnRate();
        if (spawnRate <= 0) {
            return false;
        }

        // Apply spawn rate as a random chance
        if (spawnRate < 100.0) {
            double roll = random.nextDouble() * 100.0;
            if (roll > spawnRate) {
                return false;
            }
        }

        // 3. Don't spawn in caves (unless structure spawn)
        if (spawnType != MobSpawnType.STRUCTURE && isInCave(level, pos)) {
            return false;
        }

        // 4. Check if position is valid
        if (!hasValidSpawnSpace(level, pos)) {
            return false;
        }

        return true;
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