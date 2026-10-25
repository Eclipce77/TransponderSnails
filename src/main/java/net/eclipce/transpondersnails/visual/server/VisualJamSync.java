package net.eclipce.transpondersnails.visual.server;

import net.eclipce.transpondersnails.jam.JamMath;
import net.eclipce.transpondersnails.visual.network.VisualJamPacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.voice.server.HornedDDMJammerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells the screens of video calls and streams how jammed they are, so the picture dissolves into static by the same amount as the sound. Sent when
 * the level has moved enough to see, when it reaches or leaves zero, and (while there is any) about once a second, so a screen that has just
 * started showing the call learns it too. The screens of a Black Transponder Snail's tap are told by VisualInterceptionManager.
 */
public final class VisualJamSync {

    private VisualJamSync() {}

    private static final int RESEND_TICKS = 20;

    /** Per call or stream connection: the last level sent, and the ticks since. */
    private static final Map<UUID, double[]> LAST = new HashMap<>();

    public static void tick(MinecraftServer server) {
        VisualCallManager calls = VisualCallManager.peek();
        if (server == null || calls == null) {
            LAST.clear();
            return;
        }
        HornedDDMJammerManager jam = HornedDDMJammerManager.getInstance();
        Set<UUID> seen = new HashSet<>();
        for (VisualCallSession s : calls.callsSnapshot()) {
            if (s.isEnded()) continue;
            double level = s.isStream() ? jam.levelForStream(s.callerGp())
                    : s.getState() == VisualCallSession.State.CONNECTED ? jam.levelForCall(s.callId()) : 0.0;
            seen.add(s.callId());
            double[] last = LAST.computeIfAbsent(s.callId(), k -> new double[]{0.0, RESEND_TICKS});
            last[1]++;
            if (JamMath.shouldSend(last[0], level, (int) last[1], RESEND_TICKS)) {
                last[0] = level;
                last[1] = 0;
                VisualJamPacket packet = new VisualJamPacket(s.callId(), JamMath.quantize(level));
                for (VisualCallSession.ViewerKey key : s.viewerKeys()) {
                    ServerPlayer viewer = server.getPlayerList().getPlayer(key.player());
                    if (viewer != null) VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
                }
            }
        }
        LAST.keySet().retainAll(seen);
    }

    public static void clear() {
        LAST.clear();
    }
}
