package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.voice.server.CallInterceptionManager;
import net.eclipce.transpondersnails.voice.server.CallSession;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

/**
 * FORGE BUS EVENTS ONLY
 * These events are fired during gameplay (server lifecycle, player events, etc.)
 *
 * IMPORTANT: This uses Bus.FORGE, not Bus.MOD!
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {

    // =================== SERVER LIFECYCLE EVENTS ===================

    /**
     * Called when server is starting - clean up any stale call states
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {

        // The TransponderCallManager will be initialized by the voice chat plugin
        // when it starts, which happens after this event. The fresh initialization
        // will automatically start with clean state - no stale calls possible.
    }

    /**
     * Called when server is stopping - clean up all active calls
     * This ensures snails return to idle state and no stale calls persist
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager != null) {
            try {
                // End all active calls cleanly
                for (CallSession session : new ArrayList<>(callManager.getActiveCalls())) {
                    try {
                        callManager.endCall(session.getCallId());
                    } catch (Exception e) {
                        System.err.println("Error ending call during shutdown: " + e.getMessage());
                    }
                }

                // Clean up the manager itself
                callManager.shutdown();
            } catch (Exception e) {
                System.err.println("TransponderSnails: Error during shutdown cleanup: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // =================== PLAYER DEATH AND DISCONNECT EVENTS ===================

    /**
     * Called when a player dies - stop any interception
     * Ends the call if they were CARRYING a snail in one (see below); a player at a placed snail leaves it where it is
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager != null) {
                try {
                    // Stop any active interception
                    CallInterceptionManager interceptionManager = callManager.getInterceptionManager();
                    if (interceptionManager != null) {
                        if (interceptionManager.isSearching(player.getUUID())) {
                            interceptionManager.stopSearching(player.getUUID());
                        }
                        if (interceptionManager.isIntercepting(player.getUUID())) {
                            interceptionManager.stopInterception(player.getUUID());
                        }
                    }

                    // A player who dies CARRYING a snail that is in a call: the snail drops with the rest of what they had and nobody is left on that side, so that side of the call ends
                    // (see TransponderCallManager.onPlayerDeath). Dropping or breaking a snail does not end a call - only the player dying, or leaving the game, does.
                    callManager.onPlayerDeath(player.getUUID());

                } catch (Exception e) {
                    System.err.println("Error handling player death: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Called when a player logs out - handle cleanup
     * Stops interception immediately, handles call participant logout gracefully
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager != null) {
                try {
                    // Stop any active interception or searching
                    CallInterceptionManager interceptionManager = callManager.getInterceptionManager();
                    if (interceptionManager != null) {
                        if (interceptionManager.isSearching(player.getUUID())) {
                            interceptionManager.stopSearching(player.getUUID());
                        }
                        if (interceptionManager.isIntercepting(player.getUUID())) {
                            interceptionManager.stopInterception(player.getUUID());
                        }
                    }

                    // Handle call participant logout
                    // The manager will decide if the call should continue for other participants
                    callManager.onPlayerDisconnect(player.getUUID());

                } catch (Exception e) {
                    System.err.println("Error handling player logout: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}