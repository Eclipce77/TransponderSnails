package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.jam.JamMath;
import net.eclipce.transpondersnails.visual.client.VisualClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: how jammed the call or stream that a screen shows is (0 = not at all, 255 = all static). The screen dissolves its picture
 * into static by that much. {@code linkId} is the id the screen was told to show (a call's, a stream connection's, or a tapping display's).
 */
public final class VisualJamPacket {

    private final UUID linkId;
    private final int level;

    public VisualJamPacket(UUID linkId, int level) {
        this.linkId = linkId;
        this.level = level;
    }

    public static void encode(VisualJamPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.linkId);
        buf.writeByte(msg.level);
    }

    public static VisualJamPacket decode(FriendlyByteBuf buf) {
        return new VisualJamPacket(buf.readUUID(), buf.readUnsignedByte());
    }

    public static void handle(VisualJamPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                VisualClientPackets.handleJam(msg.linkId, (float) JamMath.dequantize(msg.level))));
        context.setPacketHandled(true);
    }
}
