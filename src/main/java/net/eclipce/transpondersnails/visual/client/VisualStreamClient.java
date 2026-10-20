package net.eclipce.transpondersnails.visual.client;

import net.eclipce.transpondersnails.visual.network.VisualStreamListPacket;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The latest answers of the server to the stream tuner (at a projector) and the status menu (at a transmitter). The screens ask now and then
 * and compare the {@code version} to see whether there is something new. Render thread only.
 */
@OnlyIn(Dist.CLIENT)
public final class VisualStreamClient {

    private VisualStreamClient() {}

    /** What the tuner of the projector at {@code pos} shows. */
    public record ListData(BlockPos pos, @Nullable UUID tuned, List<VisualStreamListPacket.Stream> streams,
                           List<VisualStreamListPacket.Preset> presets, long version) { }

    /** The status of the transmitter at {@code pos}. */
    public record Status(BlockPos pos, boolean active, int viewers, String customName, String defaultName, boolean canRename,
                         boolean isPrivate, List<Integer> invites, long version) { }

    private static ListData list;
    private static Status status;
    private static long counter = 0L;

    static void receiveList(BlockPos pos, @Nullable UUID tuned, List<VisualStreamListPacket.Stream> streams, List<VisualStreamListPacket.Preset> presets) {
        list = new ListData(pos.immutable(), tuned, List.copyOf(streams), List.copyOf(presets), ++counter);
    }

    static void receiveStatus(BlockPos pos, boolean active, int viewers, String customName, String defaultName, boolean canRename,
                              boolean isPrivate, List<Integer> invites) {
        status = new Status(pos.immutable(), active, viewers, customName, defaultName, canRename, isPrivate, List.copyOf(invites), ++counter);
    }

    /** The latest list for the projector at pos, or null if there is none yet. */
    @Nullable
    public static ListData list(BlockPos pos) {
        return list != null && list.pos().equals(pos) ? list : null;
    }

    /** The latest status of the transmitter at pos, or null if there is none yet. */
    @Nullable
    public static Status status(BlockPos pos) {
        return status != null && status.pos().equals(pos) ? status : null;
    }
}
