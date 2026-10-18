package net.eclipce.transpondersnails.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import net.eclipce.transpondersnails.visual.network.VisualStreamListPacket;

import java.util.List;
import java.util.UUID;

/** Client-only entry points for packets (kept separate so the dedicated server never loads client classes). */
@OnlyIn(Dist.CLIENT)
public final class VisualClientPackets {

    private VisualClientPackets() {}

    public static void handleControl(UUID callId, BlockPos screenPos, BlockPos cameraPos, boolean start, boolean stream) {
        VisualFeedManager.onControl(callId, screenPos, cameraPos, start, stream);
    }

    /** The tuner of a projector: the streams on air, its presets, what it is tuned to. */
    public static void handleStreamList(BlockPos pos, @javax.annotation.Nullable UUID tuned, List<VisualStreamListPacket.Stream> streams,
                                        List<VisualStreamListPacket.Preset> presets) {
        VisualStreamClient.receiveList(pos, tuned, streams, presets);
    }

    /** The server asks this client to render a far camera's view and send it up (or stops asking). */
    public static void handleFeedRequest(BlockPos camera, int fps, boolean active, int resolution, int kbps, boolean forceKey) {
        VisualRelayClient.onFeedRequest(camera, fps, active, resolution, kbps, forceKey);
    }

    /** A payload of a far camera's picture, relayed by the server (applied in the order they arrive). */
    public static void handleFrame(BlockPos camera, int flags, byte[] data) {
        VisualRelayClient.onFrame(camera, flags, data);
    }

    /** The status of a transmitter; opens its menu if the server says so. */
    public static void handleTransmitterStatus(BlockPos pos, boolean open, boolean active, int viewers, String customName, String defaultName, boolean canRename) {
        VisualStreamClient.receiveStatus(pos, active, viewers, customName, defaultName, canRename);
        Minecraft mc = Minecraft.getInstance();
        if (open && !(mc.screen instanceof VisualTransmitterScreen current && current.pos().equals(pos))) {
            mc.setScreen(new VisualTransmitterScreen(pos));
        }
    }

    /** Opens the projector settings menu (sent by the server when a player crouch + right clicks a projector snail). */
    public static void openConfig(BlockPos pos, int size, float side, float up, float back) {
        Minecraft.getInstance().setScreen(new VisualConfigScreen(pos, size, side, up, back));
    }
}
