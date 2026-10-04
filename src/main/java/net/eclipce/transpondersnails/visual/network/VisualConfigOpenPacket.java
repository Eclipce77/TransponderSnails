package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.client.VisualClientPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: open the projector settings menu of the snail at {@code pos}, showing its current settings.
 * Sent when a player crouch + right clicks the snail.
 */
public final class VisualConfigOpenPacket {

    private final BlockPos pos;
    private final int size;
    private final float side;
    private final float up;
    private final float back;

    public VisualConfigOpenPacket(BlockPos pos, int size, float side, float up, float back) {
        this.pos = pos;
        this.size = size;
        this.side = side;
        this.up = up;
        this.back = back;
    }

    public static void encode(VisualConfigOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.size);
        buf.writeFloat(msg.side);
        buf.writeFloat(msg.up);
        buf.writeFloat(msg.back);
    }

    public static VisualConfigOpenPacket decode(FriendlyByteBuf buf) {
        return new VisualConfigOpenPacket(buf.readBlockPos(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(VisualConfigOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VisualClientPackets.openConfig(msg.pos, msg.size, msg.side, msg.up, msg.back)));
        context.setPacketHandled(true);
    }
}
