package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.HeadMotion;
import net.eclipce.transpondersnails.visual.ProjectorHeadAim;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where the head of each projector snail is now, and how to draw something turned like it. The rules for where it points are in {@link ProjectorHeadAim} (pure, unit tested); the
 * glide between two poses is {@link HeadMotion}. This keeps the one thing the game needs on top: the pose each snail's head is at right now, so that everything drawn for the
 * head - the head itself (VisualProjectorHeadRenderer) and the glow over it (VisualScreenRenderer) - turns together.
 */
@OnlyIn(Dist.CLIENT)
final class VisualProjectorHead {

    private VisualProjectorHead() {}

    /** One snail's head: the angles it is at, and when they were last moved on. */
    private static final class State {
        double yaw;
        double pitch;
        long lastNanos;
    }

    /** Weak: a snail that is gone (broken, unloaded) takes its state with it. */
    private static final Map<BlockEntity, State> STATES = new WeakHashMap<>();

    /**
     * The pose the head is heading for: the screen's left/right and up/down offsets of this snail. While the settings menu is open for THIS snail its live values count, so the
     * head follows the arrows at once; otherwise the saved ones, which the server keeps every client up to date with. Nothing else goes in - not the zoom.
     */
    static ProjectorHeadAim.Pose target(TransponderSnailBlockEntity snail) {
        float side = snail.getScreenOffsetSide();
        float up = snail.getScreenOffsetUp();
        VisualSettingsPanel editing = VisualSettingsPanel.active();
        if (editing != null && editing.pos().equals(snail.getBlockPos())) {
            side = editing.side();
            up = editing.up();
        }
        return ProjectorHeadAim.poseFor(side, up);
    }

    /**
     * Moves this snail's head on to now (a glide towards its target) and returns where it is. The renderer calls this once a frame for every projector it draws. A snail seen for
     * the first time just has its head where it should be: it does not swing round when you walk up to it.
     */
    static ProjectorHeadAim.Pose advance(TransponderSnailBlockEntity snail) {
        ProjectorHeadAim.Pose target = target(snail);
        long now = System.nanoTime();
        State state = STATES.get(snail);
        if (state == null) {
            state = new State();
            state.yaw = target.yaw();
            state.pitch = target.pitch();
            state.lastNanos = now;
            STATES.put(snail, state);
            return target;
        }
        double seconds = (now - state.lastNanos) / 1.0e9;
        state.lastNanos = now;
        ProjectorHeadAim.Pose next = HeadMotion.step(ProjectorHeadAim.pose(state.yaw, state.pitch), target, seconds, VisualCallConstants.PROJECTOR_HEAD_TURN_SPEED);
        state.yaw = next.yaw();
        state.pitch = next.pitch();
        return next;
    }

    /** Where the head is right now, without moving it on - for what is drawn after the head in the same frame (the glow). A head not drawn yet is at its target. */
    static ProjectorHeadAim.Pose current(TransponderSnailBlockEntity snail) {
        State state = STATES.get(snail);
        return state == null ? target(snail) : ProjectorHeadAim.pose(state.yaw, state.pitch);
    }

    /**
     * Turns the pose stack the way the head is turned: about the pivot, lifted by the little that keeps it out of the neck. Call it with the stack at the snail's model space (the
     * block's corner, already turned for the snail's facing) and draw the head or its glow model after it. The same turn, in the same order, as ProjectorHeadAim.apply.
     */
    static void turn(PoseStack stack, ProjectorHeadAim.Pose pose) {
        double pixel = 1.0 / 16.0;
        stack.translate(VisualCallConstants.PROJECTOR_HEAD_PIVOT_X * pixel, (VisualCallConstants.PROJECTOR_HEAD_PIVOT_Y + pose.lift()) * pixel,
                VisualCallConstants.PROJECTOR_HEAD_PIVOT_Z * pixel);
        stack.mulPose(Axis.YP.rotationDegrees((float) pose.yaw()));
        stack.mulPose(Axis.XP.rotationDegrees((float) pose.pitch()));
        stack.translate(-VisualCallConstants.PROJECTOR_HEAD_PIVOT_X * pixel, -VisualCallConstants.PROJECTOR_HEAD_PIVOT_Y * pixel, -VisualCallConstants.PROJECTOR_HEAD_PIVOT_Z * pixel);
    }

    /** The blockstate turns the model by 0 / 90 / 180 / 270 degrees (clockwise from above) for north / east / south / west. */
    static int yRotation(Direction facing) {
        switch (facing) {
            case EAST: return 90;
            case SOUTH: return 180;
            case WEST: return 270;
            default: return 0;
        }
    }

    /** A direction of the model (drawn for a snail facing north) as it is for a snail facing {@code facing}: the side a face looks to, so it is shaded like the rest of the snail. */
    static Direction rotated(Direction direction, Direction facing) {
        if (direction.getAxis() == Direction.Axis.Y) return direction;
        Direction turned = direction;
        for (int i = yRotation(facing) / 90; i > 0; i--) {
            turned = turned.getClockWise();
        }
        return turned;
    }
}
