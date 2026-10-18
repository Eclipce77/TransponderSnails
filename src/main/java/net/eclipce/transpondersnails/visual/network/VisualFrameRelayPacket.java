package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.RelayFrames;
import net.eclipce.transpondersnails.visual.client.VisualClientPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: one payload of the picture of the camera at {@code camera}, exactly as the server got it. They must be applied in the order they arrive. */
public final class VisualFrameRelayPacket {

    private final BlockPos camera;
    private final int flags;
    private final byte[] data;

    public VisualFrameRelayPacket(BlockPos camera, int flags, byte[] data) {
        this.camera = camera;
        this.flags = flags;
        this.data = data;
    }

    public static void encode(VisualFrameRelayPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.camera);
        buf.writeByte(msg.flags);
        buf.writeByteArray(msg.data);
    }

    public static VisualFrameRelayPacket decode(FriendlyByteBuf buf) {
        return new VisualFrameRelayPacket(buf.readBlockPos(), buf.readUnsignedByte(), buf.readByteArray(RelayFrames.MAX_FRAME_BYTES));
    }

    public static void handle(VisualFrameRelayPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VisualClientPackets.handleFrame(msg.camera, msg.flags, msg.data)));
        context.setPacketHandled(true);
    }
}
