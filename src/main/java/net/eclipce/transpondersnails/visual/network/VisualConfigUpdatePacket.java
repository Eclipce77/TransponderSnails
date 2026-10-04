package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.server.VisualProjectorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the player changed the projector settings of the snail at {@code pos}. The server checks the player is
 * close enough and that the block is a projector snail, and clamps / snaps every value before storing it - nothing in this
 * packet is trusted.
 */
public final class VisualConfigUpdatePacket {

    private final BlockPos pos;
    private final int size;
    private final float side;
    private final float up;
    private final float back;

    public VisualConfigUpdatePacket(BlockPos pos, int size, float side, float up, float back) {
        this.pos = pos;
        this.size = size;
        this.side = side;
        this.up = up;
        this.back = back;
    }

    public static void encode(VisualConfigUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.size);
        buf.writeFloat(msg.side);
        buf.writeFloat(msg.up);
        buf.writeFloat(msg.back);
    }

    public static VisualConfigUpdatePacket decode(FriendlyByteBuf buf) {
        return new VisualConfigUpdatePacket(buf.readBlockPos(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(VisualConfigUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                VisualProjectorConfig.handleUpdate(sender, msg.pos, msg.size, msg.side, msg.up, msg.back);
            }
        });
        context.setPacketHandled(true);
    }
}
