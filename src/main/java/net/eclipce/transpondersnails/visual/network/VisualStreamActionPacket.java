package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.visual.server.VisualStreamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: something a player does in the stream tuner (at a projector) or in the status menu (at a transmitter). One packet for
 * all of it; the server checks every one of them (the snail is where it says, the player is near it) before it does anything.
 */
public final class VisualStreamActionPacket {

    public enum Action {
        /** Tuner: send me the streams and my presets (at the projector at pos). */
        LIST,
        /** Tuner: tune the projector at pos in to the stream {@code id}. */
        TUNE,
        /** Tuner: save the stream {@code id} in preset box {@code slot} of the projector at pos. */
        PRESET,
        /** Tuner: empty preset box {@code slot} of the projector at pos. */
        CLEAR_PRESET,
        /** Tuner: leave the stream the projector at pos is tuned to. */
        DISCONNECT,
        /** Status menu: send me the status of the transmitter at pos. */
        STATUS,
        /** Status menu: name the stream of the transmitter at pos {@code text}. */
        RENAME,
        /** Status menu: make the stream of the transmitter at pos private ({@code slot} 1) or public (0). */
        SET_PRIVATE,
        /** Status menu: invite the snail number {@code slot} to the stream of the transmitter at pos. */
        INVITE,
        /** Status menu: take the snail number {@code slot} off the invite list of the stream of the transmitter at pos. */
        UNINVITE
    }

    private final Action action;
    private final BlockPos pos;
    @Nullable
    private final UUID id;
    private final int slot;
    private final String text;

    public VisualStreamActionPacket(Action action, BlockPos pos, @Nullable UUID id, int slot, String text) {
        this.action = action;
        this.pos = pos;
        this.id = id;
        this.slot = slot;
        this.text = text == null ? "" : text;
    }

    public static VisualStreamActionPacket list(BlockPos pos) { return new VisualStreamActionPacket(Action.LIST, pos, null, 0, ""); }
    public static VisualStreamActionPacket tune(BlockPos pos, UUID id) { return new VisualStreamActionPacket(Action.TUNE, pos, id, 0, ""); }
    public static VisualStreamActionPacket preset(BlockPos pos, int slot, UUID id) { return new VisualStreamActionPacket(Action.PRESET, pos, id, slot, ""); }
    public static VisualStreamActionPacket clearPreset(BlockPos pos, int slot) { return new VisualStreamActionPacket(Action.CLEAR_PRESET, pos, null, slot, ""); }
    public static VisualStreamActionPacket disconnect(BlockPos pos) { return new VisualStreamActionPacket(Action.DISCONNECT, pos, null, 0, ""); }
    public static VisualStreamActionPacket status(BlockPos pos) { return new VisualStreamActionPacket(Action.STATUS, pos, null, 0, ""); }
    public static VisualStreamActionPacket setPrivate(BlockPos pos, boolean on) { return new VisualStreamActionPacket(Action.SET_PRIVATE, pos, null, on ? 1 : 0, ""); }
    public static VisualStreamActionPacket invite(BlockPos pos, int number) { return new VisualStreamActionPacket(Action.INVITE, pos, null, number, ""); }
    public static VisualStreamActionPacket uninvite(BlockPos pos, int number) { return new VisualStreamActionPacket(Action.UNINVITE, pos, null, number, ""); }
    public static VisualStreamActionPacket rename(BlockPos pos, String name) { return new VisualStreamActionPacket(Action.RENAME, pos, null, 0, name); }

    public static void encode(VisualStreamActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.id != null);
        if (msg.id != null) buf.writeUUID(msg.id);
        buf.writeVarInt(msg.slot);
        buf.writeUtf(msg.text, 64);
    }

    public static VisualStreamActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        BlockPos pos = buf.readBlockPos();
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        int slot = buf.readVarInt();
        String text = buf.readUtf(64);
        return new VisualStreamActionPacket(action, pos, id, slot, text);
    }

    public static void handle(VisualStreamActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) VisualStreamManager.handleAction(player, msg.action, msg.pos, msg.id, msg.slot, msg.text);
        });
        context.setPacketHandled(true);
    }
}
