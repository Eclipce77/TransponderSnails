package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.client.VisualClientPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: the status of the Visual Transmitter at {@code pos}: whether it is streaming, how many are watching, the name of
 * its stream. {@code open} opens the status menu (the player crouch + right clicked the transmitter); otherwise it only updates a menu
 * that is already open.
 */
public final class VisualTransmitterStatusPacket {

    private final BlockPos pos;
    private final boolean open;
    private final boolean active;
    private final int viewers;
    private final String customName;
    private final String defaultName;
    private final boolean canRename;

    public VisualTransmitterStatusPacket(BlockPos pos, boolean open, boolean active, int viewers, String customName, String defaultName, boolean canRename) {
        this.pos = pos;
        this.open = open;
        this.active = active;
        this.viewers = viewers;
        this.customName = customName;
        this.defaultName = defaultName;
        this.canRename = canRename;
    }

    public static void encode(VisualTransmitterStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.open);
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.viewers);
        buf.writeUtf(msg.customName, 64);
        buf.writeUtf(msg.defaultName, 64);
        buf.writeBoolean(msg.canRename);
    }

    public static VisualTransmitterStatusPacket decode(FriendlyByteBuf buf) {
        return new VisualTransmitterStatusPacket(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                buf.readUtf(64), buf.readUtf(64), buf.readBoolean());
    }

    public static void handle(VisualTransmitterStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VisualClientPackets.handleTransmitterStatus(msg.pos, msg.open, msg.active, msg.viewers, msg.customName, msg.defaultName, msg.canRename)));
        context.setPacketHandled(true);
    }
}
