package net.eclipce.transpondersnails.voice.server;

import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.network.packets.BlackSnailCallStateSyncPacket;
import net.eclipce.transpondersnails.voice.client.PortableBlackSnailCallStateManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * PERFORMANCE: All System.out.println removed from syncState().
 * This method is called from hot paths (updateCallStates every 200ms,
 * plus every state transition). Any logging here multiplies directly
 * with player count × call frequency.
 */
public class BlackSnailStateSyncHelper {

    /**
     * Sync IDLE state to client
     */
    public static void syncIdle(ServerPlayer player) {
        syncState(player, PortableBlackSnailCallStateManager.CallState.IDLE);
    }

    /**
     * Sync SOUND state (searching) to client
     */
    public static void syncSearching(ServerPlayer player) {
        syncState(player, PortableBlackSnailCallStateManager.CallState.SOUND);
    }

    /**
     * Sync CALL state (intercepting, no audio) to client
     */
    public static void syncIntercepting(ServerPlayer player) {
        syncState(player, PortableBlackSnailCallStateManager.CallState.CALL);
    }

    /**
     * Sync ACTIVE state (intercepting + audio) to client
     */
    public static void syncActive(ServerPlayer player) {
        syncState(player, PortableBlackSnailCallStateManager.CallState.ACTIVE);
    }

    /**
     * Send state packet to client
     */
    private static void syncState(ServerPlayer player, PortableBlackSnailCallStateManager.CallState state) {
        // ✅ VERBOSE LOGGING

        BlackSnailCallStateSyncPacket packet = new BlackSnailCallStateSyncPacket(player.getUUID(), state);
        ModPackets.sendToPlayer(packet, player);

    }
}