package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Spawn conditions for the Horned Den Den Mushi.
 *
 * Mirrors {@link DenDenMushiSpawnConditions} exactly — surface spawning,
 * config-driven spawn rate, no cave spawning (unless structure spawn),
 * valid land or shallow-water space required.
 */
public class HornedDenDenMushiSpawnConditions {

    public static boolean checkHornedDenDenMushiSpawnRules(EntityType<? extends PathfinderMob> entityType,
                                                           ServerLevelAccessor level,
                                                           MobSpawnType spawnType,
                                                           BlockPos pos,
                                                           RandomSource random) {


        // 1. Config-gated enable/disable
        if (!ModConfig.isSnailSpawnEnabled("horned_den_den_mushi")) {
            return false;
        }

        // 2. Spawn rate check
        double spawnRate = ModConfig.getDenDenMushiSpawnRate(); // reuse same rate as regular DDM
        if (spawnRate <= 0) {
            return false;
        }

        if (spawnRate < 100.0) {
            double roll = random.nextDouble() * 100.0;
            if (roll > spawnRate) {
                return false;
            }
        }

        // 3. No cave spawning
        if (spawnType != MobSpawnType.STRUCTURE && isInCave(level, pos)) {
            return false;
        }

        // 4. Valid spawn space
        if (!hasValidSpawnSpace(level, pos)) {
            return false;
        }

        return true;
    }

    private static boolean isInCave(ServerLevelAccessor level, BlockPos pos) {
        if (level.canSeeSky(pos)) return false;
        int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        return (surfaceHeight - pos.getY()) > 10;
    }

    private static boolean hasValidSpawnSpace(ServerLevelAccessor level, BlockPos pos) {
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER);

        if (inWater) {
            BlockPos below = pos.below();
            return level.getBlockState(below).isSolid()
                    && level.getFluidState(pos.above()).is(FluidTags.WATER);
        } else {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isValidSpawn(level, below, EntityType.PIG)) {
                return false;
            }
            return level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir();
        }
    }
}