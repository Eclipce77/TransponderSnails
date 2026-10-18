package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.client.VisualClientPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: "render the view of the camera at {@code camera} at {@code resolution} pixels and send it up, about {@code fps} frames a
 * second and no more than {@code kbps} KB a second on average" - because somebody far away is watching it and only a client that has its
 * surroundings loaded can draw it. {@code forceKey}: send every tile again now (a new key frame). Renewed every second; the client stops when
 * it runs out, or when {@code active} is false.
 */
public final class VisualFeedRequestPacket {

    private final BlockPos camera;
    private final int fps;
    private final boolean active;
    private final int resolution;
    private final int kbps;
    private final boolean forceKey;

    public VisualFeedRequestPacket(BlockPos camera, int fps, boolean active, int resolution, int kbps, boolean forceKey) {
        this.camera = camera;
        this.fps = fps;
        this.active = active;
        this.resolution = resolution;
        this.kbps = kbps;
        this.forceKey = forceKey;
    }

    public static void encode(VisualFeedRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.camera);
        buf.writeVarInt(msg.fps);
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.resolution);
        buf.writeVarInt(msg.kbps);
        buf.writeBoolean(msg.forceKey);
    }

    public static VisualFeedRequestPacket decode(FriendlyByteBuf buf) {
        return new VisualFeedRequestPacket(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(VisualFeedRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VisualClientPackets.handleFeedRequest(msg.camera, msg.fps, msg.active, msg.resolution, msg.kbps, msg.forceKey)));
        context.setPacketHandled(true);
    }
}
