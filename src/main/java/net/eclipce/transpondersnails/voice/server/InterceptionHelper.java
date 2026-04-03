package net.eclipce.transpondersnails.voice.server;

import net.eclipce.transpondersnails.config.ModConfig;
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
 */
public class InterceptionHelper {

    /**
     * Called when a player opens a Black Transponder Snail (items)
     * Starts the interception process
     */
    public static void onSnailOpened(ServerPlayer player, TransponderCallManager callManager) {
        onSnailOpenedInternal(player, callManager, -1, null);
    }

    /**
     * Called when a player opens a Black Transponder Snail Block
     * Starts the interception process with range indicator
     *
     * @param player The player opening the snail
     * @param callManager The call manager
     * @param lightningRodCount Number of lightning rods connected (-1 for none)
     * @param blockPos Position of the block (for validation)
     */
    public static void onSnailBlockOpened(ServerPlayer player, TransponderCallManager callManager,
                                          int lightningRodCount, net.minecraft.core.BlockPos blockPos) {
        onSnailOpenedInternal(player, callManager, lightningRodCount, blockPos);
    }

    /**
     * Internal method that handles both item and block opening
     */
    private static void onSnailOpenedInternal(ServerPlayer player, TransponderCallManager callManager,
                                              int lightningRodCount, net.minecraft.core.BlockPos blockPos) {
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
                    Component.literal("✗ Cannot intercept while in a call")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        // Check if already searching or intercepting
        if (interceptionManager.isSearching(player.getUUID())) {
            return;
        }
        if (interceptionManager.isIntercepting(player.getUUID())) {
            return;
        }

        // Show "Searching..." message immediately with indicator (action bar - will be refreshed)
        String rangeIndicator = getRangeIndicator(lightningRodCount);
        String searchMessage = rangeIndicator.isEmpty()
                ? "⟳ Searching for call..."
                : "⟳ Searching for call... [" + rangeIndicator + "]";

        player.displayClientMessage(
                Component.literal(searchMessage)
                        .withStyle(ChatFormatting.YELLOW),
                true
        );

        // Find nearby active call
        double searchRange = getSearchRange(lightningRodCount);
        UUID nearbyCallId = findNearbyActiveCall(player, callManager, searchRange);

        // ✅ Call correct overload based on block vs item
        boolean started;
        if (blockPos != null) {
            // BLOCK variant - pass lightning rod count and position
            started = interceptionManager.startSearching(player, nearbyCallId, lightningRodCount, blockPos);
        } else {
            // ITEM variant - use simple 2-parameter method
            started = interceptionManager.startSearching(player, nearbyCallId);
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
                    Component.literal("○ Search cancelled")
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
     * Check if player has an open Black Transponder Snail (any variant) or is near an open block
     */
    private static boolean hasOpenBlackSnail(ServerPlayer player, net.minecraft.core.BlockPos blockPos) {
        // Check if it's a block entity (blockPos provided)
        if (blockPos != null) {
            net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(blockPos);
            if (be instanceof net.eclipce.transpondersnails.block.entity.BlackTransponderSnailBlockEntity blackSnailBE) {
                return blackSnailBE.isOpen();
            }
        }

        // Otherwise check handheld
        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (isOpenBlackSnail(mainHand)) {
            return true;
        }

        // Check off hand
        ItemStack offHand = player.getOffhandItem();
        if (isOpenBlackSnail(offHand)) {
            return true;
        }

        // TODO: Check Curios slots when integrated

        return false;
    }

    /**
     * Check if an ItemStack is an open Black Transponder Snail (any variant)
     */
    private static boolean isOpenBlackSnail(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Check Portable Black Transponder Snail (Baby)
        if (stack.getItem() instanceof PortableBlackTransponderSnailItem) {
            return PortableBlackTransponderSnailItem.isOpen(stack);
        }

        // Check Baby Black Transponder Snail
        if (stack.getItem() instanceof net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem) {
            return net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem.isOpen(stack);
        }

        // Check Adult Black Transponder Snail (unified item)
        if (stack.getItem() instanceof net.eclipce.transpondersnails.item.BlackTransponderSnailItem) {
            return net.eclipce.transpondersnails.item.BlackTransponderSnailItem.isOpen(stack);
        }

        return false;
    }

    /**
     * Get range indicator text based on lightning rod count
     * Returns: "Normal", "Longer", "Far", "Extended", "Max", or "" (for items)
     */
    private static String getRangeIndicator(int lightningRodCount) {
        if (lightningRodCount < 0) {
            // Not a block (item snail) - no indicator
            return "";
        }

        if (lightningRodCount == 0) {
            // Block with no lightning rods - show default range
            return "Default";
        }

        // Calculate range and determine indicator
        double minRange = ModConfig.getAdultBlackSnailMinRange();
        double maxRange = ModConfig.getAdultBlackSnailMaxRange();
        double currentRange = calculateBlockRange(lightningRodCount);

        // Calculate thresholds (equidistant between min and max)
        double rangeSpan = maxRange - minRange;
        double threshold1 = minRange + (rangeSpan * 0.25); // Longer
        double threshold2 = minRange + (rangeSpan * 0.50); // Far
        double threshold3 = minRange + (rangeSpan * 0.75); // Extended

        if (currentRange >= maxRange) {
            return "Max";
        } else if (currentRange >= threshold3) {
            return "Extended";
        } else if (currentRange >= threshold2) {
            return "Far";
        } else if (currentRange >= threshold1) {
            return "Longer";
        } else {
            return "Normal";
        }
    }

    /**
     * Get search range based on lightning rod count
     */
    private static double getSearchRange(int lightningRodCount) {
        if (lightningRodCount < 0) {
            // Item snail - use baby black snail range
            return ModConfig.getBabyBlackSnailRange();
        }

        return calculateBlockRange(lightningRodCount);
    }

    /**
     * Calculate block interception range based on lightning rod count
     */
    private static double calculateBlockRange(int lightningRodCount) {
        double baseRange = ModConfig.getAdultBlackSnailDefaultRange();
        double minRange = ModConfig.getAdultBlackSnailMinRange();
        double maxRange = ModConfig.getAdultBlackSnailMaxRange();

        if (lightningRodCount == 0) {
            // No lightning rods - use default range
            return baseRange;
        }

        // Each lightning rod adds 5 blocks, starting from minRange
        double extraRange = lightningRodCount * 5.0;
        return Math.min(minRange + extraRange, maxRange);
    }

    /**
     * Find nearby active call within custom range
     */
    @Nullable
    private static UUID findNearbyActiveCall(ServerPlayer player, TransponderCallManager callManager,
                                             double searchRange) {
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
            if (distance < searchRange && distance < nearestDistance) {
                nearestDistance = distance;
                nearestCallId = call.getCallId();
            }
        }

        if (nearestCallId != null) {
        }

        return nearestCallId;
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
                // Distance to handheld participant player
                ServerPlayer participantPlayer = callManager.getPlayerById(participant.getPlayerId());
                if (participantPlayer != null) {
                    distance = player.position().distanceTo(participantPlayer.position());
                    minDistance = Math.min(minDistance, distance);
                }
            } else if (participant.isBlock() && participant.getBlockPosition() != null) {
                // Distance to block participant
                distance = player.position().distanceTo(participant.getBlockPosition().getCenter());
                minDistance = Math.min(minDistance, distance);
            }
        }

        return minDistance;
    }
}