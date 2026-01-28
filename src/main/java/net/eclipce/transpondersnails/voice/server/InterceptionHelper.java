package net.eclipce.transpondersnails.voice.server;

import net.eclipce.transpondersnails.config.ModConfig;
import net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem;
import net.eclipce.transpondersnails.item.BlackTransponderSnailItem;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for handling Black Transponder Snail interception events
 *
 * FIXED: Now recognizes all three Black Transponder Snail types:
 * - PortableBlackTransponderSnailItem (Curios-compatible portable baby)
 * - BlackTransponderSnailItem (Adult, non-Curios)
 * - BabyBlackTransponderSnailItem (Baby, non-Curios)
 */
public class InterceptionHelper {

    /**
     * Called when a player opens a Black Transponder Snail
     * Starts the interception process
     */
    public static void onSnailOpened(ServerPlayer player, TransponderCallManager callManager) {
        if (player == null || callManager == null) {
            return;
        }

        var interceptionManager = callManager.getInterceptionManager();
        if (interceptionManager == null) {
            System.err.println("InterceptionManager not initialized!");
            return;
        }

        // Check if player is already in a call
        if (isPlayerInActiveCall(player, callManager)) {
            player.displayClientMessage(
                    Component.literal("Cannot intercept while in a call")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        // Check if already searching or intercepting
        if (interceptionManager.isSearching(player.getUUID())) {
            System.out.println("Player already searching");
            return;
        }
        if (interceptionManager.isIntercepting(player.getUUID())) {
            System.out.println("Player already intercepting");
            return;
        }

        // Show "Searching..." message immediately
        player.displayClientMessage(
                Component.literal("Searching for call...")
                        .withStyle(ChatFormatting.YELLOW),
                true
        );

        // Find nearby active call
        UUID nearbyCallId = findNearbyActiveCall(player, callManager);

        if (nearbyCallId == null) {
            // No calls in range - schedule message after 1 second
            callManager.getScheduler().schedule(() -> {
                if (hasOpenBlackSnail(player)) {
                    player.displayClientMessage(
                            Component.literal("No calls in range")
                                    .withStyle(ChatFormatting.GRAY),
                            true
                    );
                }
            }, 1000, TimeUnit.MILLISECONDS);
            return;
        }

        // Found a call - start searching (5-second delay)
        boolean started = interceptionManager.startSearching(player, nearbyCallId);
        if (!started) {
            player.displayClientMessage(
                    Component.literal("Failed to start search")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    /**
     * Called when a player closes a Black Transponder Snail
     * Stops any active interception or searching
     */
    public static void onSnailClosed(ServerPlayer player, TransponderCallManager callManager) {
        if (player == null || callManager == null) {
            return;
        }

        var interceptionManager = callManager.getInterceptionManager();
        if (interceptionManager == null) {
            return;
        }

        // Stop searching if in progress
        if (interceptionManager.isSearching(player.getUUID())) {
            interceptionManager.stopSearching(player.getUUID());
            player.displayClientMessage(
                    Component.literal("Search cancelled")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
        }

        // Stop intercepting if active
        if (interceptionManager.isIntercepting(player.getUUID())) {
            interceptionManager.stopInterception(player.getUUID());
            // Message sent by stopInterception
        }
    }

    /**
     * Find a nearby active call within interception range
     */
    @Nullable
    private static UUID findNearbyActiveCall(ServerPlayer player, TransponderCallManager callManager) {
        double interceptionRange = getInterceptionRange(player);
        UUID nearestCallId = null;
        double nearestDistance = Double.MAX_VALUE;

        // Check all active calls via the activeCalls map
        for (CallSession call : callManager.getActiveCalls()) {
            if (call.getState() != CallSession.CallState.CONNECTED) {
                continue;
            }

            // Skip if player is a participant
            if (call.isParticipant(player.getUUID())) {
                continue;
            }

            // Find distance to nearest participant
            double distance = getDistanceToNearestParticipant(player, call, callManager);
            if (distance < interceptionRange && distance < nearestDistance) {
                nearestDistance = distance;
                nearestCallId = call.getCallId();
            }
        }

        if (nearestCallId != null) {
            System.out.println("Found nearby call: " + nearestCallId.toString().substring(0, 8) +
                    " at distance " + nearestDistance + " blocks");
        }

        return nearestCallId;
    }

    /**
     * Check if player is in an active call (robust version)
     */
    private static boolean isPlayerInActiveCall(ServerPlayer player, TransponderCallManager callManager) {
        // Check via playerToCallId map
        UUID callId = callManager.getPlayerCallId(player.getUUID());
        if (callId == null) {
            return false;
        }

        // Verify the call actually exists and is connected
        CallSession call = callManager.getCallSessionById(callId);
        if (call == null) {
            return false;
        }

        if (call.getState() != CallSession.CallState.CONNECTED) {
            return false;
        }

        // Verify player is actually a participant
        return call.isParticipant(player.getUUID());
    }

    /**
     * Get interception range based on the type of Black Snail
     *
     * FIXED: Now checks all three black snail types with appropriate ranges:
     * - BlackTransponderSnailItem (adult) → getAdultBlackSnailDefaultRange()
     * - PortableBlackTransponderSnailItem (baby Curios) → getBabyBlackSnailRange()
     * - BabyBlackTransponderSnailItem (baby non-Curios) → getBabyBlackSnailRange()
     */
    private static double getInterceptionRange(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // Check for Adult Black Transponder Snail (larger range)
        if (mainHand.getItem() instanceof BlackTransponderSnailItem ||
                offHand.getItem() instanceof BlackTransponderSnailItem) {
            System.out.println("[InterceptionHelper] Using ADULT range: " + ModConfig.getAdultBlackSnailDefaultRange());
            return ModConfig.getAdultBlackSnailDefaultRange();
        }

        // Check for Portable Black Transponder Snail (baby, Curios)
        if (mainHand.getItem() instanceof PortableBlackTransponderSnailItem ||
                offHand.getItem() instanceof PortableBlackTransponderSnailItem) {
            System.out.println("[InterceptionHelper] Using PORTABLE BABY range: " + ModConfig.getBabyBlackSnailRange());
            return ModConfig.getBabyBlackSnailRange();
        }

        // Check for Baby Black Transponder Snail (non-Curios)
        if (mainHand.getItem() instanceof BabyBlackTransponderSnailItem ||
                offHand.getItem() instanceof BabyBlackTransponderSnailItem) {
            System.out.println("[InterceptionHelper] Using BABY range: " + ModConfig.getBabyBlackSnailRange());
            return ModConfig.getBabyBlackSnailRange();
        }

        // Default fallback (shouldn't reach here if called correctly)
        System.out.println("[InterceptionHelper] WARNING: No black snail found, using default baby range");
        return ModConfig.getBabyBlackSnailRange();
    }

    /**
     * Get distance from player to nearest call participant
     */
    private static double getDistanceToNearestParticipant(ServerPlayer player, CallSession call,
                                                          TransponderCallManager callManager) {
        double minDistance = Double.MAX_VALUE;

        for (CallSession.CallParticipant participant : call.getAllParticipants()) {
            double distance;

            if (participant.isHandheld() && participant.hasActivePlayer()) {
                // Distance to handheld participant
                ServerPlayer participantPlayer = callManager.getPlayerById(participant.getPlayerId());
                if (participantPlayer != null) {
                    distance = player.position().distanceTo(participantPlayer.position());
                    minDistance = Math.min(minDistance, distance);
                }
            } else if (participant.isBlock() && participant.getBlockPosition() != null) {
                // Distance to block participant
                distance = player.position().distanceTo(
                        participant.getBlockPosition().getCenter()
                );
                minDistance = Math.min(minDistance, distance);
            }
        }

        return minDistance;
    }

    /**
     * Check if player has an open Black Transponder Snail
     *
     * FIXED: Now checks all three black snail types:
     * - PortableBlackTransponderSnailItem
     * - BlackTransponderSnailItem
     * - BabyBlackTransponderSnailItem
     */
    private static boolean hasOpenBlackSnail(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // Check Portable Black Transponder Snail (Curios baby)
        if (mainHand.getItem() instanceof PortableBlackTransponderSnailItem &&
                PortableBlackTransponderSnailItem.isOpen(mainHand)) {
            return true;
        }
        if (offHand.getItem() instanceof PortableBlackTransponderSnailItem &&
                PortableBlackTransponderSnailItem.isOpen(offHand)) {
            return true;
        }

        // Check Black Transponder Snail (Adult)
        if (mainHand.getItem() instanceof BlackTransponderSnailItem &&
                BlackTransponderSnailItem.isOpen(mainHand)) {
            return true;
        }
        if (offHand.getItem() instanceof BlackTransponderSnailItem &&
                BlackTransponderSnailItem.isOpen(offHand)) {
            return true;
        }

        // Check Baby Black Transponder Snail (non-Curios)
        if (mainHand.getItem() instanceof BabyBlackTransponderSnailItem &&
                BabyBlackTransponderSnailItem.isOpen(mainHand)) {
            return true;
        }
        if (offHand.getItem() instanceof BabyBlackTransponderSnailItem &&
                BabyBlackTransponderSnailItem.isOpen(offHand)) {
            return true;
        }

        return false;
    }
}