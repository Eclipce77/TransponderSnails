package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.client.VisualClientPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: start or stop rendering the view of {@code cameraPos} onto the screen of the snail at
 * {@code screenPos}. Sent to players near the screen snail when a call reaches CONNECTING and whenever a player walks
 * into range of a running call; STOP is sent when the call ends.
 */
public final class VisualFeedControlPacket {

    private final UUID callId;
    private final BlockPos screenPos;
    private final BlockPos cameraPos;
    private final boolean start;

    public VisualFeedControlPacket(UUID callId, BlockPos screenPos, BlockPos cameraPos, boolean start) {
        this.callId = callId;
        this.screenPos = screenPos;
        this.cameraPos = cameraPos;
        this.start = start;
    }

    public static void encode(VisualFeedControlPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
        buf.writeBlockPos(msg.screenPos);
        buf.writeBlockPos(msg.cameraPos);
        buf.writeBoolean(msg.start);
    }

    public static VisualFeedControlPacket decode(FriendlyByteBuf buf) {
        return new VisualFeedControlPacket(buf.readUUID(), buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(VisualFeedControlPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VisualClientPackets.handleControl(msg.callId, msg.screenPos, msg.cameraPos, msg.start)));
        context.setPacketHandled(true);
    }
}
