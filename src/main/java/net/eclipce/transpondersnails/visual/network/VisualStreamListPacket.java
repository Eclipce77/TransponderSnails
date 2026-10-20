package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.client.VisualClientPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: what the stream tuner of the projector at {@code pos} shows: the streams that are active in its dimension (anywhere
 * in it), the stream it is tuned to, and its three saved streams. How many are watching a stream is NOT in it: that is shown only at the
 * transmitter itself (VisualTransmitterStatusPacket).
 */
public final class VisualStreamListPacket {

    /** An active stream. */
    public record Stream(UUID id, String name, String owner, boolean isPrivate) { }

    /** One of the three preset boxes; {@code present} false = empty. {@code active}: the stream it holds is on air right now. */
    public record Preset(boolean present, UUID id, String name, boolean active, boolean isPrivate) {
        public static Preset empty() { return new Preset(false, new UUID(0L, 0L), "", false, false); }
    }

    private final BlockPos pos;
    @Nullable
    private final UUID tuned;
    private final List<Stream> streams;
    private final List<Preset> presets;

    public VisualStreamListPacket(BlockPos pos, @Nullable UUID tuned, List<Stream> streams, List<Preset> presets) {
        this.pos = pos;
        this.tuned = tuned;
        this.streams = streams;
        this.presets = presets;
    }

    public static void encode(VisualStreamListPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.tuned != null);
        if (msg.tuned != null) buf.writeUUID(msg.tuned);
        buf.writeVarInt(msg.streams.size());
        for (Stream s : msg.streams) {
            buf.writeUUID(s.id());
            buf.writeUtf(s.name(), 64);
            buf.writeUtf(s.owner(), 64);
            buf.writeBoolean(s.isPrivate());
        }
        buf.writeVarInt(msg.presets.size());
        for (Preset p : msg.presets) {
            buf.writeBoolean(p.present());
            if (p.present()) {
                buf.writeUUID(p.id());
                buf.writeUtf(p.name(), 64);
                buf.writeBoolean(p.active());
                buf.writeBoolean(p.isPrivate());
            }
        }
    }

    public static VisualStreamListPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID tuned = buf.readBoolean() ? buf.readUUID() : null;
        int n = buf.readVarInt();
        List<Stream> streams = new ArrayList<>(n);
        for (int i = 0; i < n; i++) streams.add(new Stream(buf.readUUID(), buf.readUtf(64), buf.readUtf(64), buf.readBoolean()));
        int m = buf.readVarInt();
        List<Preset> presets = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            presets.add(buf.readBoolean() ? new Preset(true, buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean()) : Preset.empty());
        }
        return new VisualStreamListPacket(pos, tuned, streams, presets);
    }

    public static void handle(VisualStreamListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        VisualClientPackets.handleStreamList(msg.pos, msg.tuned, msg.streams, msg.presets)));
        context.setPacketHandled(true);
    }
}
