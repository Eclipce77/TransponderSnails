package net.eclipce.transpondersnails.visual.server;

import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.StreamAccess;
import net.eclipce.transpondersnails.visual.StreamPresets;
import net.eclipce.transpondersnails.visual.VisualRoles;
import net.eclipce.transpondersnails.visual.VisualSnailRole;
import net.eclipce.transpondersnails.visual.VisualStreamNames;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.visual.network.VisualStreamActionPacket;
import net.eclipce.transpondersnails.visual.network.VisualStreamListPacket;
import net.eclipce.transpondersnails.visual.network.VisualTransmitterStatusPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Streams: a Visual Transmitter (camera) that is switched on broadcasts its picture and sound; any Visual Transponder Snail (projector) in
 * the same dimension can tune in, wherever it is. The connections themselves are VisualCallManager's (one stream session per tuned
 * projector); this class is what the players do: switch a stream on and off, name it, list the streams, tune in, save presets.
 *
 * Nothing the client sends is trusted: every action names a snail by position, and the snail must be there, of the right kind, and near
 * the player.
 */
public final class VisualStreamManager {

    private VisualStreamManager() {}

    /** How far a player may be from a snail whose menu they are using (blocks, squared). */
    private static final double REACH_SQ = 100.0;
    /** A click on a camera that is within this many ticks of the last one that switched it is ignored. */
    private static final long TOGGLE_COOLDOWN_TICKS = 10L;

    /** A stream that is on air. */
    public record Info(UUID id, String name, String owner, GlobalPos pos, boolean isPrivate) { }

    private static void msg(ServerPlayer player, String text, ChatFormatting color) {
        player.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    // =====================================================================================================
    // Looking up
    // =====================================================================================================

    /** The streams that are on air in a dimension, by name. */
    public static List<Info> activeStreams(ServerLevel level) {
        return collect(level, be -> true);
    }

    /**
     * The streams the snail with this number may see: every public one, and the private ones that invited it. This is what a tuner is shown
     * (and all it may tune to). {@link #activeStreams} is every stream - a Black Transponder Snail's tap is not stopped by a guest list.
     */
    public static List<Info> streamsVisibleTo(ServerLevel level, int snailNumber) {
        return collect(level, be -> be.canSeeStream(snailNumber));
    }

    private static List<Info> collect(ServerLevel level, java.util.function.Predicate<TransponderSnailBlockEntity> filter) {
        List<Info> out = new ArrayList<>();
        for (TransponderSnailBlockEntity be : VisualSnailRegistry.all()) {
            if (be.isRemoved() || be.getLevel() != level || !be.isStreaming() || VisualRoles.ofEntity(be) != VisualSnailRole.CAMERA) continue;
            if (!filter.test(be)) continue;
            out.add(new Info(be.getOrCreateStreamId(), be.getStreamDisplayName(), be.getOwnerName(), GlobalPos.of(level.dimension(), be.getBlockPos()),
                    be.isStreamPrivate()));
        }
        out.sort((a, b) -> {
            int c = VisualStreamNames.compare(a.name(), b.name());
            return c != 0 ? c : a.id().compareTo(b.id());
        });
        return out;
    }

    /** The camera whose stream is on air with this id, in ANY dimension, or null. */
    @Nullable
    private static TransponderSnailBlockEntity findStreamAnywhere(UUID id) {
        for (TransponderSnailBlockEntity be : VisualSnailRegistry.all()) {
            if (!be.isRemoved() && be.getLevel() != null && be.isStreaming() && VisualRoles.ofEntity(be) == VisualSnailRole.CAMERA
                    && id.equals(be.getOrCreateStreamId())) {
                return be;
            }
        }
        return null;
    }

    /** The snail of a kind at a position, if it is loaded and the player is near enough to be using its menu. */
    @Nullable
    private static TransponderSnailBlockEntity snailAt(ServerPlayer player, BlockPos pos, VisualSnailRole role) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return null;
        if (!(level.getBlockEntity(pos) instanceof TransponderSnailBlockEntity be) || be.isRemoved()) return null;
        if (VisualRoles.ofEntity(be) != role) return null;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_SQ) return null;
        // a projector wired to a Black Transponder Snail only shows what that snail intercepts: it cannot tune to a stream
        if (role == VisualSnailRole.DUPLEX && VisualTapManager.isTapped(level, pos)) {
            msg(player, "This snail is connected to a Black Transponder Snail", ChatFormatting.RED);
            return null;
        }
        return be;
    }

    // =====================================================================================================
    // The transmitter: switch on and off, status, name
    // =====================================================================================================

    /** Right click on a transmitter: on air -> off the air, otherwise on the air. */
    public static void toggle(ServerPlayer player, TransponderSnailBlockEntity camera) {
        if (!(camera.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now - camera.lastStreamToggleTick < TOGGLE_COOLDOWN_TICKS) return;
        camera.lastStreamToggleTick = now;

        if (camera.isStreaming()) {
            VisualCallManager calls = VisualCallManager.peek();
            if (calls != null) calls.endStreamsOf(GlobalPos.of(level.dimension(), camera.getBlockPos()), "The stream ended.");
            camera.setStreaming(false);
            msg(player, "Stopped Streaming: "+ camera.getStreamDisplayName(), ChatFormatting.GRAY);
        } else {
            camera.setOwnerIfAbsent(player);   // a transmitter placed before owners existed belongs to whoever first uses it
            camera.getOrCreateStreamId();
            camera.setStreaming(true);
            msg(player, "Started Streaming: " + camera.getStreamDisplayName(), ChatFormatting.GREEN);
        }
    }

    /** Crouch + right click on a transmitter: opens its status menu. */
    public static void openStatus(ServerPlayer player, TransponderSnailBlockEntity camera) {
        sendStatus(player, camera, true);
    }

    private static boolean canRename(ServerPlayer player, TransponderSnailBlockEntity camera) {
        UUID owner = camera.getOwnerId();
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    private static void sendStatus(ServerPlayer player, TransponderSnailBlockEntity camera, boolean open) {
        VisualCallManager calls = VisualCallManager.peek();
        int viewers = 0;
        if (calls != null && camera.getLevel() != null) {
            viewers = calls.viewerCount(GlobalPos.of(camera.getLevel().dimension(), camera.getBlockPos()));
        }
        VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new VisualTransmitterStatusPacket(camera.getBlockPos(), open,
                camera.isStreaming(), viewers, camera.getStreamName(), VisualStreamNames.displayName("", camera.getOwnerName()), canRename(player, camera),
                camera.isStreamPrivate(), camera.getStreamInvites()));
    }

    // ---- private streams: the owner (or an op) makes a stream invite only and keeps its guest list ----

    /** Disconnects the projectors that are tuned to this stream but may not see it (it became private, or their number was taken off the list). */
    private static void enforceAccess(TransponderSnailBlockEntity camera) {
        VisualCallManager calls = VisualCallManager.peek();
        if (calls == null || !(camera.getLevel() instanceof ServerLevel level)) return;
        calls.endStreamViewersWhere(GlobalPos.of(level.dimension(), camera.getBlockPos()),
                projector -> !camera.canSeeStream(projector.getSnailNumber()), "The stream is now private.");
    }

    private static boolean mayManage(ServerPlayer player, TransponderSnailBlockEntity camera) {
        if (canRename(player, camera)) return true;
        msg(player, "Only the owner can change this stream.", ChatFormatting.RED);
        return false;
    }

    private static void setPrivate(ServerPlayer player, TransponderSnailBlockEntity camera, boolean on) {
        if (mayManage(player, camera)) {
            camera.setStreamPrivate(on);
            if (on) enforceAccess(camera);
            msg(player, on ? "Stream is now private: invite only." : "Stream is now public.", on ? ChatFormatting.GOLD : ChatFormatting.GREEN);
        }
        sendStatus(player, camera, false);
    }

    private static void invite(ServerPlayer player, TransponderSnailBlockEntity camera, int number) {
        if (mayManage(player, camera)) {
            if (!StreamAccess.validNumber(number)) {
                msg(player, "Enter a four-digit snail number.", ChatFormatting.RED);
            } else if (net.eclipce.transpondersnails.data.SnailNumberRegistry.getInstance().getKindOfNumber(number) != net.eclipce.transpondersnails.data.SnailKind.VISUAL) {
                msg(player, "No Visual Transponder Snail has the number #" + number + ".", ChatFormatting.RED);   // only a visual snail can watch a stream
            } else {
                switch (camera.inviteNumber(number)) {
                    case ADDED -> msg(player, "Invited #" + number + ".", ChatFormatting.GREEN);
                    case ALREADY_INVITED -> msg(player, "#" + number + " is already invited.", ChatFormatting.YELLOW);
                    case FULL -> msg(player, "The invite list is full (" + StreamAccess.MAX_INVITES + ").", ChatFormatting.RED);
                    case INVALID_NUMBER -> msg(player, "Enter a four-digit snail number.", ChatFormatting.RED);
                }
            }
        }
        sendStatus(player, camera, false);
    }

    private static void uninvite(ServerPlayer player, TransponderSnailBlockEntity camera, int number) {
        if (mayManage(player, camera) && camera.uninviteNumber(number)) {
            enforceAccess(camera);   // a snail that is watching and is no longer invited is disconnected at once
            msg(player, "Removed #" + number + ".", ChatFormatting.GRAY);
        }
        sendStatus(player, camera, false);
    }

    private static void rename(ServerPlayer player, TransponderSnailBlockEntity camera, String text) {
        if (!canRename(player, camera)) {
            msg(player, "Only the owner can rename this stream.", ChatFormatting.RED);
        } else {
            camera.setStreamName(text);
        }
        sendStatus(player, camera, false);
    }

    // =====================================================================================================
    // The tuner at a projector: the list, tuning in, presets
    // =====================================================================================================

    /** Sends the streams that are on air, the projector's presets and what it is tuned to. */
    public static void sendList(ServerPlayer player, TransponderSnailBlockEntity projector) {
        ServerLevel level = player.serverLevel();
        List<Info> active = streamsVisibleTo(level, projector.getSnailNumber());   // a private stream is not even listed for a snail that is not invited
        Map<UUID, Info> byId = new HashMap<>();
        List<VisualStreamListPacket.Stream> streams = new ArrayList<>();
        for (Info info : active) {
            byId.put(info.id(), info);
            streams.add(new VisualStreamListPacket.Stream(info.id(), info.name(), info.owner(), info.isPrivate())); // no viewer count: only the transmitter shows that
        }

        StreamPresets saved = projector.getPresets();
        boolean renamed = false;
        List<VisualStreamListPacket.Preset> presets = new ArrayList<>();
        for (int slot = 0; slot < StreamPresets.SLOTS; slot++) {
            StreamPresets.Preset preset = saved.get(slot);
            if (preset == null) {
                presets.add(VisualStreamListPacket.Preset.empty());
                continue;
            }
            Info info = byId.get(preset.id());
            if (info != null && saved.rename(preset.id(), info.name())) renamed = true; // a stream that was renamed is renamed in the preset
            presets.add(new VisualStreamListPacket.Preset(true, preset.id(), info != null ? info.name() : preset.name(), info != null, info != null && info.isPrivate()));
        }
        if (renamed) projector.setChanged();

        UUID tuned = null;
        VisualCallManager calls = VisualCallManager.peek();
        if (calls != null) {
            VisualCallSession session = calls.streamSessionOf(GlobalPos.of(level.dimension(), projector.getBlockPos()));
            TransponderSnailBlockEntity camera = session == null ? null : VisualSnailRegistry.get(session.callerGp());
            if (camera != null) tuned = camera.getOrCreateStreamId();
        }
        UUID tunedId = tuned;
        VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new VisualStreamListPacket(projector.getBlockPos(), tunedId, streams, presets));
    }

    private static void tune(ServerPlayer player, TransponderSnailBlockEntity projector, UUID id) {
        VisualCallManager calls = VisualCallManager.get();
        if (calls == null) {
            msg(player, "Voice chat system not available!", ChatFormatting.RED);
            return;
        }
        TransponderSnailBlockEntity camera = findStreamAnywhere(id);
        if (camera != null && !camera.canSeeStream(projector.getSnailNumber())) camera = null;   // private, and this snail is not invited
        if (camera == null) {
            StreamPresets.Preset saved = projector.getPresets().slotOf(id) >= 0 ? projector.getPresets().get(projector.getPresets().slotOf(id)) : null;
            msg(player, (saved == null || saved.name().isEmpty() ? "That stream" : saved.name()) + " is not active.", ChatFormatting.RED);
        } else if (camera.getLevel() != player.level()) {
            msg(player, camera.getStreamDisplayName() + " is in another dimension.", ChatFormatting.RED);   // the one limit on the range
        } else {
            calls.tune(player, projector, camera, camera.getStreamDisplayName());
        }
        sendList(player, projector);
    }

    private static void setPreset(ServerPlayer player, TransponderSnailBlockEntity projector, int slot, UUID id) {
        if (!StreamPresets.validSlot(slot)) return;
        Info found = null;
        for (Info info : streamsVisibleTo(player.serverLevel(), projector.getSnailNumber())) {
            if (info.id().equals(id)) found = info;
        }
        if (found == null) {
            msg(player, "That stream is not active.", ChatFormatting.RED);
        } else {
            projector.getPresets().set(slot, id, found.name());
            projector.setChanged();
            msg(player, "Saved " + found.name() + " in box " + (slot + 1) + ".", ChatFormatting.GREEN);
        }
        sendList(player, projector);
    }

    private static void clearPreset(ServerPlayer player, TransponderSnailBlockEntity projector, int slot) {
        if (StreamPresets.validSlot(slot) && !projector.getPresets().isEmpty(slot)) {
            projector.getPresets().clear(slot);
            projector.setChanged();
            msg(player, "Box " + (slot + 1) + " cleared.", ChatFormatting.GRAY);
        }
        sendList(player, projector);
    }

    private static void disconnect(ServerPlayer player, TransponderSnailBlockEntity projector) {
        VisualCallManager calls = VisualCallManager.peek();
        if (calls != null) calls.disconnectStream(GlobalPos.of(player.serverLevel().dimension(), projector.getBlockPos()), player);
        sendList(player, projector);
    }

    // =====================================================================================================
    // What the client asks for
    // =====================================================================================================

    public static void handleAction(ServerPlayer player, VisualStreamActionPacket.Action action, BlockPos pos, @Nullable UUID id, int slot, String text) {
        switch (action) {
            case LIST -> {
                TransponderSnailBlockEntity projector = snailAt(player, pos, VisualSnailRole.DUPLEX);
                if (projector != null) sendList(player, projector);
            }
            case TUNE -> {
                TransponderSnailBlockEntity projector = snailAt(player, pos, VisualSnailRole.DUPLEX);
                if (projector != null && id != null) tune(player, projector, id);
            }
            case PRESET -> {
                TransponderSnailBlockEntity projector = snailAt(player, pos, VisualSnailRole.DUPLEX);
                if (projector != null && id != null) setPreset(player, projector, slot, id);
            }
            case CLEAR_PRESET -> {
                TransponderSnailBlockEntity projector = snailAt(player, pos, VisualSnailRole.DUPLEX);
                if (projector != null) clearPreset(player, projector, slot);
            }
            case DISCONNECT -> {
                TransponderSnailBlockEntity projector = snailAt(player, pos, VisualSnailRole.DUPLEX);
                if (projector != null) disconnect(player, projector);
            }
            case STATUS -> {
                TransponderSnailBlockEntity camera = snailAt(player, pos, VisualSnailRole.CAMERA);
                if (camera != null) sendStatus(player, camera, false);
            }
            case RENAME -> {
                TransponderSnailBlockEntity camera = snailAt(player, pos, VisualSnailRole.CAMERA);
                if (camera != null) rename(player, camera, text);
            }
            case SET_PRIVATE -> {
                TransponderSnailBlockEntity camera = snailAt(player, pos, VisualSnailRole.CAMERA);
                if (camera != null) setPrivate(player, camera, slot != 0);
            }
            case INVITE -> {
                TransponderSnailBlockEntity camera = snailAt(player, pos, VisualSnailRole.CAMERA);
                if (camera != null) invite(player, camera, slot);
            }
            case UNINVITE -> {
                TransponderSnailBlockEntity camera = snailAt(player, pos, VisualSnailRole.CAMERA);
                if (camera != null) uninvite(player, camera, slot);
            }
        }
    }
}
