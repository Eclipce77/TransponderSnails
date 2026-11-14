package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.server.VisualRelayManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: a shader pack is on ({@code shaders}) or off. A client with a shader pack on cannot draw a camera's view (see ShaderCompat),
 * so the server does not ask it to render for the viewers of far cameras.
 */
public final class VisualShaderStatePacket {

    private final boolean shaders;

    public VisualShaderStatePacket(boolean shaders) {
        this.shaders = shaders;
    }

    public static void encode(VisualShaderStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.shaders);
    }

    public static VisualShaderStatePacket decode(FriendlyByteBuf buf) {
        return new VisualShaderStatePacket(buf.readBoolean());
    }

    public static void handle(VisualShaderStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) VisualRelayManager.setShaderClient(sender.getUUID(), msg.shaders);
        });
        context.setPacketHandled(true);
    }
}
