package net.eclipce.transpondersnails.voice;

import net.eclipce.transpondersnails.network.PacketHandler;
import net.eclipce.transpondersnails.network.packet.IncomingCallPacket;
import net.eclipce.transpondersnails.network.packet.OutgoingCallPacket;
import net.eclipce.transpondersnails.network.packet.HangUpPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class CallManager {

    private record CallSession(ServerPlayer caller, ServerPlayer receiver, int number) {}

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final Map<UUID, ScheduledFuture<?>> RING_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, CallSession> SESSIONS = new ConcurrentHashMap<>();

    public static void startRinging(ServerPlayer caller, ServerPlayer receiver, UUID callId, int number) {
        // 1) Store the session
        SESSIONS.put(callId, new CallSession(caller, receiver, number));

        // 2) Cancel any previous task (shouldn't normally exist, but just in case)
        cancelRinging(callId);

        // 3) Schedule per-second ringing packets
        ScheduledFuture<?> task = SCHEDULER.scheduleAtFixedRate(() -> {
            PacketHandler.sendToPlayer(new OutgoingCallPacket(callId, number), caller);
            PacketHandler.sendToPlayer(new IncomingCallPacket(callId, number), receiver);
        }, 0, 1, TimeUnit.SECONDS);

        RING_TASKS.put(callId, task);

        // 4) Schedule the timeout bounce *after* 30 seconds
        SCHEDULER.schedule(() -> timeoutBounce(callId), 30, TimeUnit.SECONDS);
    }

    private static void cancelRinging(UUID callId) {
        ScheduledFuture<?> task = RING_TASKS.remove(callId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private static void timeoutBounce(UUID callId) {
        // 1) Always cancel further ringing first
        cancelRinging(callId);

        // 2) Remove session, so accept/decline handlers know it’s gone
        CallSession session = SESSIONS.remove(callId);
        if (session == null) {
            return; // already accepted/declined
        }

        // 3) Send HangUpPacket so clients stop their loops
        PacketHandler.sendToPlayer(new HangUpPacket(callId), session.caller);
        PacketHandler.sendToPlayer(new HangUpPacket(callId), session.receiver);

        // 4) Notify caller of the timeout
        session.caller.sendSystemMessage(
                Component.literal("Call to Snail #" +
                        String.format("%04d", session.number) +
                        " timed out."),
                false
        );
    }

    public static boolean acceptCall(UUID callId) {
        // identical pattern: cancel rings first...
        cancelRinging(callId);
        CallSession session = SESSIONS.remove(callId);
        if (session == null) return false;

        // then stop client loops
        PacketHandler.sendToPlayer(new HangUpPacket(callId), session.caller);
        PacketHandler.sendToPlayer(new HangUpPacket(callId), session.receiver);

        // now open live channel
        TransponderSnailAudioPlugin.openCallChannel(
                session.caller, session.receiver, callId
        );
        return true;
    }

    public static boolean declineCall(UUID callId) {
        cancelRinging(callId);
        CallSession session = SESSIONS.remove(callId);
        if (session == null) return false;

        PacketHandler.sendToPlayer(new HangUpPacket(callId), session.caller);
        PacketHandler.sendToPlayer(new HangUpPacket(callId), session.receiver);

        session.caller.sendSystemMessage(
                Component.literal("Snail #" +
                        String.format("%04d", session.number) +
                        " declined your call."),
                false
        );
        return true;
    }
}

