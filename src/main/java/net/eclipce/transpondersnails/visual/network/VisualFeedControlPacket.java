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
    /** A stream (a Visual Transmitter's broadcast): the client shows "no signal" if the camera's area is not loaded, instead of failing. */
    private final boolean stream;
    /**
     * A SPLIT screen (an intercepted call, shown on the projector wired to a Black Transponder Snail): the picture of {@code cameraPos} on the
     * left half and of {@code secondCamera} on the right. null = one picture on the whole screen.
     */
    @javax.annotation.Nullable
    private final BlockPos secondCamera;
    /** Which halves show static instead of a picture: the end of the call is protected by a White Transponder Snail. */
    private final boolean staticFirst;
    private final boolean staticSecond;
    /** The screen is a Black Transponder Snail's display: there is no call for it to answer or hand shake to finish. */
    private final boolean tap;

    public VisualFeedControlPacket(UUID callId, BlockPos screenPos, BlockPos cameraPos, boolean start, boolean stream) {
        this(callId, screenPos, cameraPos, start, stream, null, false, false, false);
    }

    public VisualFeedControlPacket(UUID callId, BlockPos screenPos, BlockPos cameraPos, boolean start, boolean stream,
                                   @javax.annotation.Nullable BlockPos secondCamera, boolean staticFirst, boolean staticSecond, boolean tap) {
        this.callId = callId;
        this.screenPos = screenPos;
        this.cameraPos = cameraPos;
        this.start = start;
        this.stream = stream;
        this.secondCamera = secondCamera;
        this.staticFirst = staticFirst;
        this.staticSecond = staticSecond;
        this.tap = tap;
    }

    public static void encode(VisualFeedControlPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
        buf.writeBlockPos(msg.screenPos);
        buf.writeBlockPos(msg.cameraPos);
        buf.writeBoolean(msg.start);
        buf.writeBoolean(msg.stream);
        buf.writeBoolean(msg.secondCamera != null);
        if (msg.secondCamera != null) buf.writeBlockPos(msg.secondCamera);
        buf.writeBoolean(msg.staticFirst);
        buf.writeBoolean(msg.staticSecond);
        buf.writeBoolean(msg.tap);
    }

    public static VisualFeedControlPacket decode(FriendlyByteBuf buf) {
        UUID callId = buf.readUUID();
        BlockPos screen = buf.readBlockPos();
        BlockPos camera = buf.readBlockPos();
        boolean start = buf.readBoolean();
        boolean stream = buf.readBoolean();
        BlockPos second = buf.readBoolean() ? buf.readBlockPos() : null;
        return new VisualFeedControlPacket(callId, screen, camera, start, stream, second, buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(VisualFeedControlPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VisualClientPackets.handleControl(msg.callId, msg.screenPos, msg.cameraPos, msg.start, msg.stream,
                                msg.secondCamera, msg.staticFirst, msg.staticSecond, msg.tap)));
        context.setPacketHandled(true);
    }
}
