package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.eclipce.transpondersnails.sound.ModSounds;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages call interception for Black Transponder Snails.
 *
 * Phase 1: Basic interception with range limits
 * - Tracks active interceptors
 * - Routes audio to interceptors
 * - Enforces range limits
 *
 * Future Phases:
 * - Audio quality degradation based on distance
 * - Auditory tapping indicators
 * - Lightning rod range boosting for adult variants
 */
public class CallInterceptionManager {

    private final VoicechatServerApi voiceChatApi;
    private final TransponderCallManager callManager;

    // Active interceptions: interceptor UUID -> InterceptionSession
    private final Map<UUID, InterceptionSession> activeInterceptions = new ConcurrentHashMap<>();

    // Call-based lookup: callId -> Set of interceptor UUIDs
    private final Map<UUID, Set<UUID>> callInterceptors = new ConcurrentHashMap<>();

    // Interceptor audio channels: interceptor UUID -> AudioChannel
    private final Map<UUID, AudioChannel> interceptorChannels = new ConcurrentHashMap<>();

    // Searching interceptions: interceptor UUID -> SearchingSession
    private final Map<UUID, SearchingSession> searchingSessions = new ConcurrentHashMap<>();

    // Track which calls each interceptor has already tapped (for call switching)
    private final Map<UUID, Set<UUID>> tappedCallsHistory = new ConcurrentHashMap<>();

    // Audio activity tracking for visual feedback
    private final Map<UUID, Long> lastAudioActivity = new ConcurrentHashMap<>();
    private static final long AUDIO_ACTIVITY_WINDOW_MS = 500; // 500ms window

    // Searching delay (5 seconds to "find" the call)
    private static final long SEARCHING_DELAY_MS = 5000;

    public CallInterceptionManager(VoicechatServerApi voiceChatApi, TransponderCallManager callManager) {
        this.voiceChatApi = voiceChatApi;
        this.callManager = callManager;
        System.out.println("CallInterceptionManager initialized");
    }

    /**
     * Represents a searching/connecting session before interception begins
     */
    public static class SearchingSession {
        private final UUID interceptorId;
        private final UUID targetCallId;
        private final InterceptionSession.InterceptorType type;
        private final long startTime;
        private final int lightningRodCount; // -1 for items, >= 0 for blocks
        private final net.minecraft.core.BlockPos blockPos; // null for items

        public SearchingSession(UUID interceptorId, UUID targetCallId, InterceptionSession.InterceptorType type) {
            this(interceptorId, targetCallId, type, -1, null);
        }

        public SearchingSession(UUID interceptorId, UUID targetCallId, InterceptionSession.InterceptorType type,
                                int lightningRodCount, net.minecraft.core.BlockPos blockPos) {
            this.interceptorId = interceptorId;
            this.targetCallId = targetCallId;
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.lightningRodCount = lightningRodCount;
            this.blockPos = blockPos;
        }

        public UUID getInterceptorId() { return interceptorId; }
        public UUID getTargetCallId() { return targetCallId; }
        public InterceptionSession.InterceptorType getType() { return type; }
        public long getTimeSearching() { return System.currentTimeMillis() - startTime; }
        public boolean isReadyToConnect() { return getTimeSearching() >= SEARCHING_DELAY_MS; }
        public int getLightningRodCount() { return lightningRodCount; }
        public net.minecraft.core.BlockPos getBlockPos() { return blockPos; }
        public boolean isBlock() { return blockPos != null; }
    }

    /**
     * Represents an active interception session
     */
    public static class InterceptionSession {
        private final UUID interceptorId;
        private final UUID targetCallId;
        private final InterceptorType type;
        private final long startTime;
        private long lastValidationTime;
        private boolean isValid;
        private final int lightningRodCount; // -1 for items, >= 0 for blocks
        private final net.minecraft.core.BlockPos blockPos; // null for items

        public enum InterceptorType {
            PORTABLE_BABY,      // Portable Black Transponder Snail (Baby)
            ADULT_HANDHELD,     // Adult Black Transponder Snail (Handheld)
            ADULT_PLACED        // Adult Black Transponder Snail (Placed with lightning rods)
        }

        public InterceptionSession(UUID interceptorId, UUID targetCallId, InterceptorType type) {
            this(interceptorId, targetCallId, type, -1, null);
        }

        public InterceptionSession(UUID interceptorId, UUID targetCallId, InterceptorType type,
                                   int lightningRodCount, net.minecraft.core.BlockPos blockPos) {
            this.interceptorId = interceptorId;
            this.targetCallId = targetCallId;
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.lastValidationTime = startTime;
            this.isValid = true;
            this.lightningRodCount = lightningRodCount;
            this.blockPos = blockPos;
        }

        // Getters
        public UUID getInterceptorId() { return interceptorId; }
        public UUID getTargetCallId() { return targetCallId; }
        public InterceptorType getType() { return type; }
        public long getStartTime() { return startTime; }
        public long getDuration() { return System.currentTimeMillis() - startTime; }
        public boolean isValid() { return isValid; }
        public int getLightningRodCount() { return lightningRodCount; }
        public net.minecraft.core.BlockPos getBlockPos() { return blockPos; }
        public boolean isBlock() { return blockPos != null; }

        public void markValid() {
            this.isValid = true;
            this.lastValidationTime = System.currentTimeMillis();
        }

        public void markInvalid() {
            this.isValid = false;
        }

        public double getMaxRange() {
            if (isBlock() && lightningRodCount >= 0) {
                // Calculate enhanced range for blocks with lightning rods
                return calculateBlockRange(lightningRodCount);
            }

            switch (type) {
                case PORTABLE_BABY:
                    return net.eclipce.transpondersnails.config.ModConfig.getBabyBlackSnailRange();
                case ADULT_HANDHELD:
                    return net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
                case ADULT_PLACED:
                    return net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
                default:
                    return net.eclipce.transpondersnails.config.ModConfig.getBabyBlackSnailRange();
            }
        }

        /**
         * Calculate block range based on lightning rod count
         */
        private double calculateBlockRange(int rodCount) {
            double baseRange = net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
            double minRange = net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailMinRange();
            double maxRange = net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailMaxRange();

            if (rodCount == 0) {
                return baseRange;
            }

            // Each rod adds 5 blocks, starting from minRange
            double extraRange = rodCount * 5.0;
            return Math.min(minRange + extraRange, maxRange);
        }

        @Override
        public String toString() {
            return String.format("Interception{interceptor=%s, call=%s, type=%s, duration=%ds, valid=%s}",
                    interceptorId.toString().substring(0, 8),
                    targetCallId.toString().substring(0, 8),
                    type, getDuration() / 1000, isValid);
        }
    }

    // =================== INTERCEPTION LIFECYCLE ===================

    /**
     * Start searching for a call
     * @param targetCallId Can be NULL if no calls found
     */
    public boolean startSearching(ServerPlayer interceptor, UUID targetCallId) {
        // Validation
        if (interceptor == null) {  // ✅ ONLY check interceptor
            return false;
        }

        // Check if already searching or intercepting
        if (isSearching(interceptor.getUUID())) {
            System.out.println("Player already searching");
            return false;
        }
        if (isIntercepting(interceptor.getUUID())) {
            System.out.println("Player already intercepting");
            return false;
        }

        // Check if player is in a call
        if (interceptor != null && callManager.isInCall(interceptor.getUUID())) {
            System.out.println("Cannot intercept - player is in a call");
            return false;
        }

        // Verify the target call exists and is active (if provided)
        if (targetCallId != null) {
            CallSession targetCall = callManager.getCallSessionById(targetCallId);
            if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
                System.out.println("Target call not found or not connected: " + targetCallId);
                targetCallId = null;  // ✅ Treat as "no call found"
            }

            // Check if the interceptor is a participant in the target call
            if (targetCallId != null && targetCall != null && targetCall.isParticipant(interceptor.getUUID())) {
                System.out.println("Cannot intercept own call");
                return false;
            }
        }

        // Determine interceptor type based on held item
        InterceptionSession.InterceptorType type = determineInterceptorType(interceptor);
        if (type == null) {
            System.out.println("Player not holding a valid Black Transponder Snail");
            return false;
        }

        // Create searching session - targetCallId CAN BE NULL! ✅
        SearchingSession searchingSession = new SearchingSession(
                interceptor.getUUID(),
                targetCallId,  // ✅ Can be null for "no calls found"
                type
        );

        searchingSessions.put(interceptor.getUUID(), searchingSession);

        String targetInfo = targetCallId != null
                ? targetCallId.toString().substring(0, 8)
                : "null (no calls found)";
        System.out.println("Started searching: Player " + interceptor.getName().getString() +
                " searching for call " + targetInfo);

        // ✨ SYNC: Tell client we're searching (SOUND state)
        BlackSnailStateSyncHelper.syncSearching(interceptor);

        return true;
    }

    /**
     * Start searching for a call (BLOCK VARIANT with range parameters)
     * Called when player opens a Black Transponder Snail BLOCK
     */
    public boolean startSearching(ServerPlayer interceptor, UUID targetCallId,
                                  int lightningRodCount, net.minecraft.core.BlockPos blockPos) {
        // Validation
        if (interceptor == null) {
            return false;
        }

        // Check if already searching or intercepting
        if (isSearching(interceptor.getUUID())) {
            System.out.println("Player already searching");
            return false;
        }
        if (isIntercepting(interceptor.getUUID())) {
            System.out.println("Player already intercepting");
            return false;
        }

        // Check if player is in a call
        if (callManager.isInCall(interceptor.getUUID())) {
            System.out.println("Cannot intercept - player is in a call");
            return false;
        }

        // Verify the target call exists (if provided - can be null!)
        if (targetCallId != null) {
            CallSession targetCall = callManager.getCallSessionById(targetCallId);
            if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
                System.out.println("Target call not found or not connected");
                targetCallId = null;  // ✅ Treat as "no call found"
            } else if (targetCall.isParticipant(interceptor.getUUID())) {
                System.out.println("Cannot intercept own call");
                return false;
            }
        }

        // For blocks, type is always ADULT_PLACED
        InterceptionSession.InterceptorType type = InterceptionSession.InterceptorType.ADULT_PLACED;

        // Create searching session with block parameters
        SearchingSession searchingSession = new SearchingSession(
                interceptor.getUUID(),
                targetCallId,  // ✅ Can be null for "no calls found"
                type,
                lightningRodCount,
                blockPos
        );

        searchingSessions.put(interceptor.getUUID(), searchingSession);

        String targetInfo = targetCallId != null
                ? targetCallId.toString().substring(0, 8)
                : "null (no calls)";
        System.out.println("Started searching (BLOCK): " + interceptor.getName().getString() +
                " → call " + targetInfo + ", rods=" + lightningRodCount);

        // Sync client state
        BlackSnailStateSyncHelper.syncSearching(interceptor);

        return true;
    }

    /**
     * Alias for startSearching - for backwards compatibility
     */
    public boolean startInterception(ServerPlayer interceptor, UUID targetCallId) {
        return startSearching(interceptor, targetCallId);
    }

    /**
     * Process all searching sessions and connect them when ready
     * Should be called periodically (e.g., every 250ms)
     */
    public void processSearchingSessions() {
        List<UUID> toConnect = new ArrayList<>();

        for (SearchingSession session : searchingSessions.values()) {
            // Only connect if we actually found a call (targetCallId not null)
            if (session.getTargetCallId() != null && session.isReadyToConnect()) {
                toConnect.add(session.getInterceptorId());
            }
        }

        for (UUID interceptorId : toConnect) {
            connectSearchingSession(interceptorId);
        }
    }

    /**
     * Convert a searching session into an active interception
     */
    private void connectSearchingSession(UUID interceptorId) {
        SearchingSession searchingSession = searchingSessions.remove(interceptorId);
        if (searchingSession == null) {
            return;
        }

        ServerPlayer interceptor = callManager.getPlayerById(interceptorId);
        if (interceptor == null) {
            System.out.println("Interceptor player not found: " + interceptorId);
            return;
        }

        // Verify call still exists
        CallSession targetCall = callManager.getCallSessionById(searchingSession.getTargetCallId());
        if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
            interceptor.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("✗ Call ended before connection")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    true  // true = action bar
            );
            return;
        }

        // Create interception session
        InterceptionSession session = new InterceptionSession(
                interceptorId,
                searchingSession.getTargetCallId(),
                searchingSession.getType()
        );

        // Create audio channel for interceptor
        if (!createInterceptorAudioChannel(interceptor, session)) {
            System.out.println("Failed to create interceptor audio channel");
            interceptor.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("✗ Failed to connect to call")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    false  // false = chat message (persistent for errors)
            );
            return;
        }

        // Register the interception
        activeInterceptions.put(interceptorId, session);
        callInterceptors.computeIfAbsent(searchingSession.getTargetCallId(), k -> ConcurrentHashMap.newKeySet())
                .add(interceptorId);

        // Play connection sound at low volume
        playConnectionSound(interceptor);

        // Notify player (action bar - will be refreshed to stay visible)
        interceptor.displayClientMessage(
                net.minecraft.network.chat.Component.literal("✓ Connected to call")
                        .withStyle(net.minecraft.ChatFormatting.GREEN),
                true  // true = action bar (will be refreshed)
        );

        // âœ¨ SYNC: Tell client we're intercepting (CALL state - no audio yet)
        BlackSnailStateSyncHelper.syncIntercepting(interceptor);

        System.out.println("Connected interception: " + session);
    }

    /**
     * Play the transponder snail connection sound at low volume
     */
    private void playConnectionSound(ServerPlayer player) {
        // Play at 30% volume (0.3f) to indicate stealth connection
        player.playSound(
                ModSounds.SNAIL_CONNECTED.get(),
                0.3f,  // Low volume
                1.0f   // Normal pitch
        );
    }

    /**
     * Stop a searching session
     */
    public void stopSearching(UUID interceptorId) {
        SearchingSession searchingSession = searchingSessions.remove(interceptorId);
        if (searchingSession != null) {
            System.out.println("Cancelled searching session for " + interceptorId.toString().substring(0, 8));

            // âœ¨ SYNC: Tell client search cancelled (back to IDLE)
            ServerPlayer player = callManager.getPlayerById(interceptorId);
            if (player != null) {
                BlackSnailStateSyncHelper.syncIdle(player);
            }
        }
    }

    /**
     * Stop an active interception or searching session
     */
    public void stopInterception(UUID interceptorId) {
        // Stop searching session if exists
        SearchingSession searchingSession = searchingSessions.remove(interceptorId);
        if (searchingSession != null) {
            System.out.println("Cancelled searching session for " + interceptorId.toString().substring(0, 8));
            // Don't notify player here - InterceptionHelper handles it
            return;
        }

        // Stop active interception
        InterceptionSession session = activeInterceptions.remove(interceptorId);
        if (session == null) {
            return;
        }

        // Remove from call interceptors
        Set<UUID> interceptors = callInterceptors.get(session.getTargetCallId());
        if (interceptors != null) {
            interceptors.remove(interceptorId);
            if (interceptors.isEmpty()) {
                callInterceptors.remove(session.getTargetCallId());
            }
        }

        // Clean up audio channel
        AudioChannel channel = interceptorChannels.remove(interceptorId);
        if (channel != null) {
            System.out.println("Removed interceptor audio channel for " + interceptorId.toString().substring(0, 8));
        }

        // Notify player
        ServerPlayer player = callManager.getPlayerById(interceptorId);
        if (player != null) {
            player.displayClientMessage(
                    Component.literal("○ Disconnected from call")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );

            // âœ¨ SYNC: Tell client we're idle now
            BlackSnailStateSyncHelper.syncIdle(player);
        }

        System.out.println("Stopped interception: " + session);
    }

    /**
     * Switch to the next available call (crouch + right-click functionality)
     * Prioritizes calls that haven't been tapped yet, then cycles through all calls
     */
    public boolean switchToNextCall(ServerPlayer interceptor) {
        if (interceptor == null) {
            return false;
        }

        UUID playerId = interceptor.getUUID();

        // Get current interception or searching session
        InterceptionSession currentSession = activeInterceptions.get(playerId);
        SearchingSession currentSearch = searchingSessions.get(playerId);

        UUID currentCallId = null;
        if (currentSession != null) {
            currentCallId = currentSession.getTargetCallId();
        } else if (currentSearch != null) {
            currentCallId = currentSearch.getTargetCallId();
        }

        // Get tapped calls history
        Set<UUID> tappedCalls = tappedCallsHistory.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        // Mark current call as tapped
        if (currentCallId != null) {
            tappedCalls.add(currentCallId);
        }

        // Find next call
        UUID nextCallId = findNextCallToTap(interceptor, tappedCalls, currentCallId);

        if (nextCallId == null) {
            // No other calls available - if we had tapped calls, reset and try first one
            if (!tappedCalls.isEmpty()) {
                tappedCalls.clear();
                nextCallId = findNextCallToTap(interceptor, tappedCalls, currentCallId);
            }

            if (nextCallId == null) {
                // Still no calls - show message
                interceptor.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("✗ No other calls in range")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        true  // true = action bar
                );
                return false;
            }
        }

        // Stop current interception/search
        stopInterception(playerId);

        // Start new search for next call
        boolean success = startInterception(interceptor, nextCallId);

        if (success) {
            interceptor.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("⟳ Switching to another call...")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW),
                    true
            );
            System.out.println("CallInterceptionManager: Player " + interceptor.getName().getString() +
                    " switching to call " + nextCallId.toString().substring(0, 8));
        }

        return success;
    }

    /**
     * Find the next call to tap, prioritizing untapped calls
     */
    private UUID findNextCallToTap(ServerPlayer interceptor, Set<UUID> tappedCalls, UUID currentCallId) {
        double maxRange = net.eclipce.transpondersnails.config.ModConfig.getBabyBlackSnailRange();

        // Get all active calls
        Collection<CallSession> activeCalls = callManager.getActiveCalls();

        // First pass: Find closest untapped call
        UUID closestUntapped = null;
        double closestUntappedDistance = maxRange;

        // Second pass: Find closest tapped call (different from current)
        UUID closestTapped = null;
        double closestTappedDistance = maxRange;

        for (CallSession call : activeCalls) {
            // Skip if not connected
            if (call.getState() != CallSession.CallState.CONNECTED) {
                continue;
            }

            UUID callId = call.getCallId();

            // Skip if this is the current call
            if (callId.equals(currentCallId)) {
                continue;
            }

            // Skip if player is a participant
            if (call.isParticipant(interceptor.getUUID())) {
                continue;
            }

            // Calculate distance
            double distance = getDistanceToNearestParticipant(interceptor, call);

            if (distance >= maxRange) {
                continue; // Out of range
            }

            // Check if tapped
            boolean isTapped = tappedCalls.contains(callId);

            if (!isTapped) {
                // Untapped call - prioritize
                if (distance < closestUntappedDistance) {
                    closestUntappedDistance = distance;
                    closestUntapped = callId;
                }
            } else {
                // Tapped call - use as fallback
                if (distance < closestTappedDistance) {
                    closestTappedDistance = distance;
                    closestTapped = callId;
                }
            }
        }

        // Prioritize untapped, fall back to tapped
        return closestUntapped != null ? closestUntapped : closestTapped;
    }

    /**
     * Stop all interceptions for a specific call (e.g., when call ends)
     */
    public void stopAllInterceptionsForCall(UUID callId) {
        Set<UUID> interceptors = callInterceptors.remove(callId);
        if (interceptors != null) {
            for (UUID interceptorId : interceptors) {
                stopInterception(interceptorId);
            }
            System.out.println("Stopped all interceptions for call " + callId.toString().substring(0, 8));
        }
    }

    // =================== AUDIO ROUTING ===================

    /**
     * Get the audio channel for an interceptor to receive audio
     */
    @Nullable
    public AudioChannel getInterceptorChannel(UUID interceptorId) {
        return interceptorChannels.get(interceptorId);
    }

    /**
     * Get all interceptors for a specific call
     */
    public Set<UUID> getInterceptorsForCall(UUID callId) {
        Set<UUID> interceptors = callInterceptors.get(callId);
        return interceptors != null ? new HashSet<>(interceptors) : Collections.emptySet();
    }

    /**
     * Get all active interceptor channels for a call
     * Used by SnailAudioRelay to forward audio
     */
    public List<AudioChannel> getInterceptorChannelsForCall(UUID callId) {
        List<AudioChannel> channels = new ArrayList<>();
        Set<UUID> interceptors = getInterceptorsForCall(callId);

        for (UUID interceptorId : interceptors) {
            AudioChannel channel = interceptorChannels.get(interceptorId);
            if (channel != null) {
                channels.add(channel);
            }
        }

        return channels;
    }

    // =================== VALIDATION & UPDATES ===================

    /**
     * Validate all active interceptions - called periodically
     * Checks range limits and snail state
     */
    public void validateInterceptions(ServerPlayer interceptor) {
        if (interceptor == null) return;

        InterceptionSession session = activeInterceptions.get(interceptor.getUUID());
        if (session == null) return;

        // Get the target call
        CallSession targetCall = callManager.getCallSessionById(session.getTargetCallId());
        if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
            // Call ended or disconnected
            stopInterception(interceptor.getUUID());
            return;
        }

        // Check if interceptor still has the snail open
        if (!hasOpenBlackSnail(interceptor)) {
            stopInterception(interceptor.getUUID());
            return;
        }

        // Check range to nearest call participant
        double nearestDistance = getDistanceToNearestParticipant(interceptor, targetCall);
        double maxRange = session.getMaxRange();

        if (nearestDistance > maxRange) {
            // Out of range - mark invalid silently
            // Don't notify player to prevent location tracking exploits
            session.markInvalid();
            // Don't stop immediately - allow brief excursions
            // Will be stopped by cleanup if stays out of range
        } else {
            // In range - silently restore validity
            // Don't notify to prevent location tracking exploits
            session.markValid();
            // Update interceptor audio channel position
            updateInterceptorChannelPosition(interceptor);
        }
    }

    /**
     * Clean up invalid interceptions - called periodically
     */
    public void cleanupInvalidInterceptions() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, InterceptionSession> entry : activeInterceptions.entrySet()) {
            InterceptionSession session = entry.getValue();

            // Remove if invalid for more than 5 seconds
            if (!session.isValid() &&
                    (System.currentTimeMillis() - session.lastValidationTime) > 5000) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID interceptorId : toRemove) {
            System.out.println("Cleaning up invalid interception (out of range) for " +
                    interceptorId.toString().substring(0, 8));

            // Notify player before disconnecting (persistent error message)
            ServerPlayer player = callManager.getPlayerById(interceptorId);
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("✗ Connection lost - too far")
                                .withStyle(ChatFormatting.RED),
                        true  // true = action bar
                );
            }

            stopInterception(interceptorId);
        }
    }

    // =================== HELPER METHODS ===================

    /**
     * Determine the type of Black Transponder Snail the player is using
     */
    @Nullable
    private InterceptionSession.InterceptorType determineInterceptorType(ServerPlayer player) {
        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        InterceptionSession.InterceptorType mainType = checkItemType(mainHand);
        if (mainType != null) {
            return mainType;
        }

        // Check off hand
        ItemStack offHand = player.getOffhandItem();
        InterceptionSession.InterceptorType offType = checkItemType(offHand);
        if (offType != null) {
            return offType;
        }

        // TODO: Check Curios slots when integrated
        return null;
    }

    /**
     * Check if an ItemStack is an open Black Transponder Snail and return its type
     */
    @Nullable
    private InterceptionSession.InterceptorType checkItemType(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        // Check Portable Black Transponder Snail (Baby)
        if (stack.getItem() instanceof PortableBlackTransponderSnailItem) {
            if (PortableBlackTransponderSnailItem.isOpen(stack)) {
                return InterceptionSession.InterceptorType.PORTABLE_BABY;
            }
        }

        // ✅ Check Baby Black Transponder Snail
        if (stack.getItem() instanceof net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem) {
            if (net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem.isOpen(stack)) {
                return InterceptionSession.InterceptorType.ADULT_HANDHELD;
            }
        }

        // ✅ Check Adult Black Transponder Snail (unified item)
        if (stack.getItem() instanceof net.eclipce.transpondersnails.item.BlackTransponderSnailItem) {
            if (net.eclipce.transpondersnails.item.BlackTransponderSnailItem.isOpen(stack)) {
                return InterceptionSession.InterceptorType.ADULT_HANDHELD;
            }
        }

        return null;
    }

    /**
     * Check if player has an open Black Transponder Snail
     */
    private boolean hasOpenBlackSnail(ServerPlayer player) {
        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof PortableBlackTransponderSnailItem &&
                PortableBlackTransponderSnailItem.isOpen(mainHand)) {
            return true;
        }

        // Check off hand
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PortableBlackTransponderSnailItem &&
                PortableBlackTransponderSnailItem.isOpen(offHand)) {
            return true;
        }

        // TODO: Check Curios slots

        return false;
    }

    /**
     * Get distance from interceptor to nearest call participant
     */
    private double getDistanceToNearestParticipant(ServerPlayer interceptor, CallSession targetCall) {
        double minDistance = Double.MAX_VALUE;

        // Check all call participants
        for (CallSession.CallParticipant participant : targetCall.getAllParticipants()) {
            double distance;

            if (participant.isHandheld() && participant.hasActivePlayer()) {
                // Distance to handheld participant player
                ServerPlayer participantPlayer = callManager.getPlayerById(participant.getPlayerId());
                if (participantPlayer != null) {
                    distance = interceptor.position().distanceTo(participantPlayer.position());
                    minDistance = Math.min(minDistance, distance);
                }
            } else if (participant.isBlock() && participant.getBlockPosition() != null) {
                // Distance to block participant
                distance = interceptor.position().distanceTo(
                        participant.getBlockPosition().getCenter()
                );
                minDistance = Math.min(minDistance, distance);
            }
        }

        return minDistance;
    }

    /**
     * Create audio channel for an interceptor
     */
    private boolean createInterceptorAudioChannel(ServerPlayer interceptor, InterceptionSession session) {
        try {
            LocationalAudioChannel channel = voiceChatApi.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    voiceChatApi.fromServerLevel(interceptor.serverLevel()),
                    voiceChatApi.createPosition(
                            interceptor.getX(),
                            interceptor.getY() + 1.5,
                            interceptor.getZ()
                    )
            );

            if (channel != null) {
                channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
                // Interceptor hears the call at their location
                channel.setDistance((float) session.getMaxRange());

                interceptorChannels.put(interceptor.getUUID(), channel);

                System.out.println("Created interceptor audio channel for " +
                        interceptor.getName().getString() +
                        " (range: " + session.getMaxRange() + " blocks)");
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to create interceptor audio channel: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Update interceptor audio channel position as they move
     */
    private void updateInterceptorChannelPosition(ServerPlayer interceptor) {
        AudioChannel channel = interceptorChannels.get(interceptor.getUUID());
        if (channel instanceof LocationalAudioChannel locChannel) {
            locChannel.updateLocation(voiceChatApi.createPosition(
                    interceptor.getX(),
                    interceptor.getY() + 1.5,
                    interceptor.getZ()
            ));
        }
    }

    // =================== QUERY METHODS ===================

    /**
     * Check if a player is currently intercepting any call
     */
    public boolean isIntercepting(UUID playerId) {
        return activeInterceptions.containsKey(playerId);
    }

    /**
     * Check if a player is currently searching for a call
     */
    public boolean isSearching(UUID playerId) {
        return searchingSessions.containsKey(playerId);
    }

    /**
     * Check if a player is intercepting or searching
     */
    public boolean isInterceptingOrSearching(UUID playerId) {
        return isIntercepting(playerId) || isSearching(playerId);
    }

    /**
     * Get the searching session for a player
     */
    @Nullable
    public SearchingSession getSearchingSession(UUID playerId) {
        return searchingSessions.get(playerId);
    }

    /**
     * Get the interception session for a player
     */
    @Nullable
    public InterceptionSession getInterceptionSession(UUID playerId) {
        return activeInterceptions.get(playerId);
    }

    /**
     * Check if a call is being intercepted
     */
    public boolean isCallBeingIntercepted(UUID callId) {
        Set<UUID> interceptors = callInterceptors.get(callId);
        return interceptors != null && !interceptors.isEmpty();
    }

    /**
     * Get count of active interceptions
     */
    public int getActiveInterceptionCount() {
        return activeInterceptions.size();
    }

    /**
     * Get statistics for debugging
     */
    public String getStats() {
        return String.format("CallInterceptionManager{active=%d, calls=%d}",
                activeInterceptions.size(),
                callInterceptors.size());
    }

    // =================== AUDIO ACTIVITY TRACKING ===================

    /**
     * Mark that audio was forwarded to an interceptor
     * Called by SnailAudioRelay when audio packets are sent
     */
    public void markAudioActivity(UUID interceptorId) {
        lastAudioActivity.put(interceptorId, System.currentTimeMillis());
    }

    /**
     * Get the last time audio was forwarded to an interceptor
     * Used by client-side predicates for visual feedback
     */
    @Nullable
    public Long getLastAudioActivity(UUID interceptorId) {
        return lastAudioActivity.get(interceptorId);
    }

    /**
     * Check if interceptor has recent audio activity
     */
    public boolean hasRecentAudioActivity(UUID interceptorId) {
        Long lastActivity = lastAudioActivity.get(interceptorId);
        if (lastActivity == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastActivity) < AUDIO_ACTIVITY_WINDOW_MS;
    }

    /**
     * Update call states for all interceptors
     * Syncs CALL state when no recent audio, keeps ACTIVE state when audio is present
     * Should be called periodically (every 100-200ms)
     */
    public void updateCallStates() {
        for (UUID interceptorId : activeInterceptions.keySet()) {
            ServerPlayer player = callManager.getPlayerById(interceptorId);
            if (player != null) {
                // If no recent audio, sync CALL state (intercepting but silent)
                if (!hasRecentAudioActivity(interceptorId)) {
                    BlackSnailStateSyncHelper.syncIntercepting(player);
                }
                // If has recent audio, ACTIVE state is already synced by SnailAudioRelay
            }
        }
    }

    /**
     * Refresh status messages to keep them visible on action bar
     * Called every 2 seconds by scheduler
     */
    public void refreshStatusMessages() {
        try {
            // NEW CODE - Check for null targetCallId:
            for (Map.Entry<UUID, SearchingSession> entry : searchingSessions.entrySet()) {
                UUID interceptorId = entry.getKey();
                SearchingSession session = entry.getValue();
                ServerPlayer player = callManager.getPlayerById(interceptorId);

                if (player != null) {
                    String rangeIndicator = getRangeIndicatorForSession(session);

                    // ✅ Check if no calls found (targetCallId == null)
                    if (session.getTargetCallId() == null) {
                        // NO CALLS FOUND - show gray message
                        String noCallsMessage = rangeIndicator.isEmpty()
                                ? "✗ No calls in range"
                                : "✗ No calls in range [" + rangeIndicator + "]";

                        player.displayClientMessage(
                                Component.literal(noCallsMessage)
                                        .withStyle(ChatFormatting.GRAY),
                                true  // Action bar
                        );
                    } else {
                        // CALL FOUND - show searching message
                        String searchMessage = rangeIndicator.isEmpty()
                                ? "⟳ Searching for call..."
                                : "⟳ Searching for call... [" + rangeIndicator + "]";

                        player.displayClientMessage(
                                Component.literal(searchMessage)
                                        .withStyle(ChatFormatting.YELLOW),
                                true  // Action bar
                        );
                    }
                }
            }

            // Refresh "Connected to call" message for all active interceptions
            for (Map.Entry<UUID, InterceptionSession> entry : activeInterceptions.entrySet()) {
                UUID interceptorId = entry.getKey();
                InterceptionSession session = entry.getValue();
                ServerPlayer player = callManager.getPlayerById(interceptorId);

                if (player != null) {
                    String rangeIndicator = getRangeIndicatorForSession(session);
                    String connectedMessage = rangeIndicator.isEmpty()
                            ? "✓ Connected to call"
                            : "✓ Connected to call [" + rangeIndicator + "]";

                    player.displayClientMessage(
                            Component.literal(connectedMessage)
                                    .withStyle(ChatFormatting.GREEN),
                            true  // Action bar
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Error refreshing status messages: " + e.getMessage());
        }
    }

    /**
     * Get range indicator for a searching session
     */
    private String getRangeIndicatorForSession(SearchingSession session) {
        if (!session.isBlock()) {
            return ""; // Items don't show range indicators
        }
        return calculateRangeIndicator(session.getLightningRodCount());
    }

    /**
     * Get range indicator for an interception session
     */
    private String getRangeIndicatorForSession(InterceptionSession session) {
        if (!session.isBlock()) {
            return ""; // Items don't show range indicators
        }
        return calculateRangeIndicator(session.getLightningRodCount());
    }

    /**
     * Calculate range indicator text based on lightning rod count
     * Returns: "Normal", "Longer", "Far", "Extended", "Max" (5 types)
     * Uses equal 20% intervals for balanced distribution
     */
    private String calculateRangeIndicator(int lightningRodCount) {
        double baseRange = net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
        double minRange = net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailMinRange();
        double maxRange = net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailMaxRange();

        // Calculate current range
        double currentRange;
        if (lightningRodCount == 0) {
            currentRange = baseRange;
        } else {
            currentRange = Math.min(minRange + (lightningRodCount * 5.0), maxRange);
        }

        // For 0 rods (default range) or at/below minimum, show "Normal"
        if (lightningRodCount == 0 || currentRange <= minRange) {
            return "Normal";
        }

        // Calculate progress from min to max (0.0 to 1.0)
        double progress = (currentRange - minRange) / (maxRange - minRange);

        // 5 equal categories (20% each)
        if (progress >= 0.8) {
            return "Max";        // 80-100%
        } else if (progress >= 0.6) {
            return "Extended";   // 60-80%
        } else if (progress >= 0.4) {
            return "Far";        // 40-60% (middle)
        } else if (progress >= 0.2) {
            return "Longer";     // 20-40%
        } else {
            return "Normal";     // 0-20%
        }
    }

    /**
     * Cleanup - called when shutting down
     */
    public void cleanup() {
        // Stop all active interceptions
        new ArrayList<>(activeInterceptions.keySet()).forEach(this::stopInterception);

        // Clear all searching sessions
        searchingSessions.clear();

        // Clear tapped calls history
        tappedCallsHistory.clear();

        // Clear audio activity
        lastAudioActivity.clear();

        activeInterceptions.clear();
        callInterceptors.clear();
        interceptorChannels.clear();

        System.out.println("CallInterceptionManager cleaned up");
    }
}