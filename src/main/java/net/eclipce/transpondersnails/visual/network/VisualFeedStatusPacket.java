package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.server.VisualCallManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: the video feed for the screen at {@code screenPos} is up ({@code ok}) or has failed.
 * A failure ends the call (see VisualCallConstants.FAIL_CALL_ON_ANY_VIEWER_ERROR).
 */
public final class VisualFeedStatusPacket {

    private static final int MAX_REASON = 128;

    private final UUID callId;
    private final BlockPos screenPos;
    private final boolean ok;
    private final String reason;

    public VisualFeedStatusPacket(UUID callId, BlockPos screenPos, boolean ok, String reason) {
        this.callId = callId;
        this.screenPos = screenPos;
        this.ok = ok;
        this.reason = reason == null ? "" : (reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
    }

    public static void encode(VisualFeedStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
        buf.writeBlockPos(msg.screenPos);
        buf.writeBoolean(msg.ok);
        buf.writeUtf(msg.reason, MAX_REASON);
    }

    public static VisualFeedStatusPacket decode(FriendlyByteBuf buf) {
        return new VisualFeedStatusPacket(buf.readUUID(), buf.readBlockPos(), buf.readBoolean(), buf.readUtf(MAX_REASON));
    }

    public static void handle(VisualFeedStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            VisualCallManager manager = VisualCallManager.get();
            if (sender != null && manager != null) {
                manager.onVideoStatus(sender, msg.callId, msg.screenPos, msg.ok, msg.reason);
            }
        });
        context.setPacketHandled(true);
    }
}
