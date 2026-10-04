package net.eclipce.transpondersnails.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Finds the surface a projector snail projects onto: a ray from the projector lens straight along the direction the screen is on,
 * i.e. in line with the snail, up to PROJECTOR_MAX_THROW blocks. The first thing with a collision shape that the ray actually hits
 * counts - full blocks, slabs, stairs, glass, fences - so the screen lies exactly on the visible face, not on a block boundary.
 *
 * Used on both sides (the client draws on it, the server works out who is close enough to watch). It never loads chunks: a ray that
 * runs into an unloaded chunk simply ends without finding a surface.
 */
public final class ProjectorSurface {

    private ProjectorSurface() {}

    /**
     * @param dir the horizontal direction the screen is on (away from the snail's back, see VisualCallConstants.SCREEN_BEHIND_SNAIL)
     * @return blocks from the snail block's face on that side to the surface, or NaN if there is none within reach
     */
    public static double find(Level level, BlockPos snail, Direction dir) {
        double maxThrow = VisualCallConstants.PROJECTOR_MAX_THROW;
        int stepX = dir.getStepX();
        int stepZ = dir.getStepZ();

        // the lens: middle of the snail's column, at the projector height
        Vec3 from = new Vec3(snail.getX() + 0.5, snail.getY() + VisualCallConstants.PROJECTOR_LENS_HEIGHT, snail.getZ() + 0.5);
        double length = maxThrow + 1.0;
        Vec3 to = from.add(stepX * length, 0.0, stepZ * length);

        int blocks = (int) Math.ceil(maxThrow) + 1;
        for (int i = 1; i <= blocks; i++) {
            BlockPos p = snail.relative(dir, i);
            if (!level.isLoaded(p)) return Double.NaN; // never force chunks to load

            BlockState state = level.getBlockState(p);
            VoxelShape shape = state.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;

            BlockHitResult hit = shape.clip(from, to, p); // null if the ray passes through a gap of this block
            if (hit == null) continue;

            double distance = from.distanceTo(hit.getLocation()) - 0.5; // from the block's face, not from its middle
            return distance > maxThrow ? Double.NaN : Math.max(0.0, distance);
        }
        return Double.NaN;
    }
}
