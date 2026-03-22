package net.eclipce.transpondersnails.entity.custom;

import net.eclipce.transpondersnails.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;

/**
 * Spawn conditions for Black Transponder Snails (both adult and baby)
 * These snails ONLY spawn underwater in medium to deep water
 *
 * FIXED: isEnclosed() and getWaterDepthAbove() now safely handle WorldGenRegion
 * boundary access during chunk generation. Previously, checking neighboring blocks
 * at chunk edges could access positions outside the WorldGenRegion's available area,
 * causing "We are asking a region for a chunk out of bound" RuntimeExceptions that
 * crashed the server during world generation.
 */
public class BlackTransponderSnailSpawnConditions {

    // Water depth requirements
    private static final int MIN_WATER_DEPTH = 5;   // Minimum 5 blocks of water above
    private static final int MAX_WATER_DEPTH = 30;  // Maximum 30 blocks of water above

    /**
     * Main spawn check for Black Transponder Snails
     * Adult and baby use the same conditions
     */
    public static boolean checkBlackTransponderSnailSpawnRules(EntityType<? extends PathfinderMob> entityType,
                                                               ServerLevelAccessor level,
                                                               MobSpawnType spawnType,
                                                               BlockPos pos,
                                                               RandomSource random) {

        System.out.println("=== BLACK TRANSPONDER SNAIL SPAWN ATTEMPT at " + pos + " ===");

        // 1. Check if this snail type is enabled in config
        String snailId = entityType == net.eclipce.transpondersnails.entity.ModEntities.BLACK_TRANSPONDER_SNAIL.get()
                ? "black_transponder_snail"
                : "baby_black_transponder_snail";

        if (!ModConfig.isSnailSpawnEnabled(snailId)) {
            System.out.println("REJECTED: " + snailId + " spawning is disabled in config");
            return false;
        }

        // 2. Check if spawn rate allows spawning
        double spawnRate = snailId.equals("black_transponder_snail")
                ? ModConfig.getBlackTransponderSnailSpawnRate()
                : ModConfig.getBabyBlackTransponderSnailSpawnRate();

        if (spawnRate <= 0) {
            System.out.println("REJECTED: Spawn rate is 0%");
            return false;
        }

        // Apply spawn rate as a random chance
        if (spawnRate < 100.0) {
            double roll = random.nextDouble() * 100.0;
            if (roll > spawnRate) {
                System.out.println("REJECTED: Failed spawn rate check (" + String.format("%.1f", roll) +
                        " > " + spawnRate + "%)");
                return false;
            }
            System.out.println("Passed spawn rate check (" + String.format("%.1f", roll) +
                    " <= " + spawnRate + "%)");
        }

        // 3. Must be underwater
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            System.out.println("REJECTED: Not underwater");
            return false;
        }
        System.out.println("Underwater - OK");

        // 4. Check water depth (must be medium to deep)
        int waterDepth = getWaterDepthAbove(level, pos);
        if (waterDepth < MIN_WATER_DEPTH) {
            System.out.println("REJECTED: Water too shallow (" + waterDepth + " blocks, need " + MIN_WATER_DEPTH + "+)");
            return false;
        }
        if (waterDepth > MAX_WATER_DEPTH) {
            System.out.println("REJECTED: Water too deep (" + waterDepth + " blocks, max " + MAX_WATER_DEPTH + ")");
            return false;
        }
        System.out.println("Water depth OK (" + waterDepth + " blocks)");

        // 5. Must have solid block below (can't swim, need floor)
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isSolid()) {
            System.out.println("REJECTED: No solid block below");
            return false;
        }
        System.out.println("Solid block below - OK");

        // 6. Block below should not be bedrock or barriers (reasonable spawning surface)
        if (level.getBlockState(below).is(Blocks.BEDROCK) ||
                level.getBlockState(below).is(Blocks.BARRIER)) {
            System.out.println("REJECTED: Invalid spawn surface (bedrock/barrier)");
            return false;
        }
        System.out.println("Valid spawn surface - OK");

        // 7. Check that spawn pos and above are water (not just the pos)
        if (!level.getFluidState(pos.above()).is(FluidTags.WATER)) {
            System.out.println("REJECTED: No water above spawn position");
            return false;
        }
        System.out.println("Water above spawn position - OK");

        // 8. Not in a tight space (need some room)
        if (isEnclosed(level, pos)) {
            System.out.println("REJECTED: Too enclosed/cramped");
            return false;
        }
        System.out.println("Open enough space - OK");

        System.out.println("=== UNDERWATER SPAWN APPROVED ===");
        return true;
    }

    /**
     * Get the depth of water above a position
     * Returns the number of water blocks above
     *
     * FIXED: Wrapped in try-catch to handle WorldGenRegion boundary access.
     * During chunk generation, vertical scans that cross into unloaded chunk
     * columns could theoretically throw. If that happens, we return the depth
     * counted so far rather than crashing.
     */
    private static int getWaterDepthAbove(LevelAccessor level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos mutablePos = pos.mutable();

        // Count water blocks above (up to MAX_WATER_DEPTH + 1 to detect "too deep")
        for (int i = 0; i < MAX_WATER_DEPTH + 10; i++) {
            mutablePos.move(0, 1, 0);
            try {
                if (level.getFluidState(mutablePos).is(FluidTags.WATER)) {
                    depth++;
                } else {
                    break; // Hit surface or non-water block
                }
            } catch (RuntimeException e) {
                // WorldGenRegion can throw RuntimeException when accessing blocks
                // outside the available chunk area during world generation.
                // Return the depth counted so far rather than crashing.
                System.out.println("BlackTransponderSnailSpawnConditions: Hit WorldGenRegion boundary during depth check at " + mutablePos + ", returning depth=" + depth);
                break;
            }
        }

        return depth;
    }

    /**
     * Check if the spawn position is too enclosed/cramped
     * Black Transponder Snails need some open water space
     *
     * FIXED: Wrapped each neighbor block check in try-catch to handle
     * WorldGenRegion boundary access. During chunk generation, the spawn
     * predicate can be called at positions on the edge of the generation
     * region. Checking neighboring blocks with offset(x, 0, z) can read
     * into chunks that are outside the WorldGenRegion's accessible area,
     * causing "We are asking a region for a chunk out of bound" crashes.
     *
     * If a neighbor check fails, we simply skip it (treat it as non-solid /
     * open water), which is a safe assumption for ocean biomes.
     */
    private static boolean isEnclosed(LevelAccessor level, BlockPos pos) {
        int solidBlocks = 0;
        int totalChecked = 0;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip center (spawn pos)

                BlockPos checkPos = pos.offset(x, 0, z);
                totalChecked++;

                try {
                    if (level.getBlockState(checkPos).isSolid()) {
                        solidBlocks++;
                    }
                } catch (RuntimeException e) {
                    // WorldGenRegion throws RuntimeException when accessing blocks
                    // outside the available chunk area during world generation.
                    // Treat inaccessible blocks as non-solid (open water assumption
                    // is safe since this spawns in ocean biomes).
                }
            }
        }

        // If more than half the surrounding blocks are solid, it's too enclosed
        return solidBlocks > (totalChecked / 2);
    }

    /**
     * Alternative spawn check for baby Black Transponder Snails
     * Uses same logic but could be customized differently
     */
    public static boolean checkBabyBlackTransponderSnailSpawnRules(EntityType<? extends PathfinderMob> entityType,
                                                                   ServerLevelAccessor level,
                                                                   MobSpawnType spawnType,
                                                                   BlockPos pos,
                                                                   RandomSource random) {
        // For now, use the same rules as adult
        // Could customize later (e.g., allow shallower water for babies)
        return checkBlackTransponderSnailSpawnRules(entityType, level, spawnType, pos, random);
    }
}