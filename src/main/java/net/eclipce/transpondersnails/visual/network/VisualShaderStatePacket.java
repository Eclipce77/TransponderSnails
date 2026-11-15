package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.server.VisualRelayManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: whether a shader pack is on, and what this client can do about a camera's view then. A client with a shader pack on cannot draw
 * a camera the normal way; what it can draw (ShaderCompat, ShaderCapture) carries its own pack's look. So the server asks such a client to draw a
 * camera for far viewers only when no player without a pack can (a shaded picture is better than a frozen one), and never when it cannot draw at all.
 */
public final class VisualShaderStatePacket {

    /** No shader pack is on: this client draws a camera the normal way. */
    public static final int NO_PACK = 0;
    /** A pack is on and this client can draw a camera through it (a picture with the look of its pack). */
    public static final int PACK_CAN_DRAW = 1;
    /** A pack is on and this client cannot draw a camera at all (the pass through the pack is switched off or failed). */
    public static final int PACK_CANNOT_DRAW = 2;

    private final int mode;

    public VisualShaderStatePacket(int mode) {
        this.mode = mode;
    }

    public static void encode(VisualShaderStatePacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.mode);
    }

    public static VisualShaderStatePacket decode(FriendlyByteBuf buf) {
        int mode = buf.readByte();
        return new VisualShaderStatePacket(mode < NO_PACK || mode > PACK_CANNOT_DRAW ? PACK_CANNOT_DRAW : mode);
    }

    public static void handle(VisualShaderStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) VisualRelayManager.setShaderClient(sender.getUUID(), msg.mode);
        });
        context.setPacketHandled(true);
    }
}
