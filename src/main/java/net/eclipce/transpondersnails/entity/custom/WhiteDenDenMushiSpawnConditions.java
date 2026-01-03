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

public class WhiteDenDenMushiSpawnConditions {

    public static boolean checkWhiteDenDenMushiSpawnRules(EntityType<? extends PathfinderMob> entityType,
                                                          ServerLevelAccessor level,
                                                          MobSpawnType spawnType,
                                                          BlockPos pos,
                                                          RandomSource random) {

        if (!ModConfig.isSnailSpawnEnabled("white_den_den_mushi")) {
            return false;
        }

        double spawnRate = ModConfig.getWhiteDenDenMushiSpawnRate();
        if (spawnRate <= 0) {
            return false;
        }

        if (spawnRate < 100.0) {
            double roll = random.nextDouble() * 100.0;
            if (roll > spawnRate) {
                return false;
            }
        }

        if (spawnType != MobSpawnType.STRUCTURE && isInCave(level, pos)) {
            return false;
        }

        if (!hasValidSpawnSpace(level, pos)) {
            return false;
        }

        return true;
    }

    private static boolean isInCave(ServerLevelAccessor level, BlockPos pos) {
        if (level.canSeeSky(pos)) {
            return false;
        }

        int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int depthBelowSurface = surfaceHeight - pos.getY();

        return depthBelowSurface > 10;
    }

    private static boolean hasValidSpawnSpace(ServerLevelAccessor level, BlockPos pos) {
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER);

        if (inWater) {
            BlockPos below = pos.below();
            return level.getBlockState(below).isSolid() &&
                    level.getFluidState(pos.above()).is(FluidTags.WATER);
        } else {
            BlockPos below = pos.below();

            if (!level.getBlockState(below).isValidSpawn(level, below, EntityType.PIG)) {
                return false;
            }

            return level.getBlockState(pos).isAir() &&
                    level.getBlockState(pos.above()).isAir();
        }
    }
}