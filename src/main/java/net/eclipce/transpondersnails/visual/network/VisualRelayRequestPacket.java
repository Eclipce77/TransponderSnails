package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.server.VisualRelayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "my client cannot draw the view of the camera at {@code camera} (its surroundings are not loaded here) - send me the
 * picture, as big as {@code wantedResolution}", or (wants = false) "I do not need it any more". Sent again every second while it is needed;
 * the server forgets a request that is not renewed. {@code resync}: "I lost track of the picture (I fell behind) - start me again from a key
 * frame". The server only answers a player who is really connected to that camera.
 */
public final class VisualRelayRequestPacket {

    private final BlockPos camera;
    private final boolean wants;
    private final int wantedResolution;
    private final boolean resync;

    public VisualRelayRequestPacket(BlockPos camera, boolean wants, int wantedResolution, boolean resync) {
        this.camera = camera;
        this.wants = wants;
        this.wantedResolution = wantedResolution;
        this.resync = resync;
    }

    public static void encode(VisualRelayRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.camera);
        buf.writeBoolean(msg.wants);
        buf.writeVarInt(msg.wantedResolution);
        buf.writeBoolean(msg.resync);
    }

    public static VisualRelayRequestPacket decode(FriendlyByteBuf buf) {
        return new VisualRelayRequestPacket(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(VisualRelayRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) VisualRelayManager.onRelayRequest(player, msg.camera, msg.wants, msg.wantedResolution, msg.resync);
        });
        context.setPacketHandled(true);
    }
}
