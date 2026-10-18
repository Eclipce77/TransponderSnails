package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.RelayFrames;
import net.eclipce.transpondersnails.visual.server.VisualRelayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: one payload of the picture of the camera at {@code camera} (see TileCodec): the tiles that changed, or a key frame's part. */
public final class VisualFrameUploadPacket {

    private final BlockPos camera;
    private final int flags;
    private final byte[] data;

    public VisualFrameUploadPacket(BlockPos camera, int flags, byte[] data) {
        this.camera = camera;
        this.flags = flags;
        this.data = data;
    }

    public static void encode(VisualFrameUploadPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.camera);
        buf.writeByte(msg.flags);
        buf.writeByteArray(msg.data);
    }

    public static VisualFrameUploadPacket decode(FriendlyByteBuf buf) {
        return new VisualFrameUploadPacket(buf.readBlockPos(), buf.readUnsignedByte(), buf.readByteArray(RelayFrames.MAX_FRAME_BYTES));
    }

    public static void handle(VisualFrameUploadPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) VisualRelayManager.onFrame(player, msg.camera, msg.flags, msg.data);
        });
        context.setPacketHandled(true);
    }
}
