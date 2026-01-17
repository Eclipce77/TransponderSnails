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

        public SearchingSession(UUID interceptorId, UUID targetCallId, InterceptionSession.InterceptorType type) {
            this.interceptorId = interceptorId;
            this.targetCallId = targetCallId;
            this.type = type;
            this.startTime = System.currentTimeMillis();
        }

        public UUID getInterceptorId() { return interceptorId; }
        public UUID getTargetCallId() { return targetCallId; }
        public InterceptionSession.InterceptorType getType() { return type; }
        public long getTimeSearching() { return System.currentTimeMillis() - startTime; }
        public boolean isReadyToConnect() { return getTimeSearching() >= SEARCHING_DELAY_MS; }
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

        public enum InterceptorType {
            PORTABLE_BABY,      // Portable Black Transponder Snail (Baby)
            ADULT_HANDHELD,     // Adult Black Transponder Snail (Handheld)
            ADULT_PLACED        // Adult Black Transponder Snail (Placed with lightning rods)
        }

        public InterceptionSession(UUID interceptorId, UUID targetCallId, InterceptorType type) {
            this.interceptorId = interceptorId;
            this.targetCallId = targetCallId;
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.lastValidationTime = startTime;
            this.isValid = true;
        }

        // Getters
        public UUID getInterceptorId() { return interceptorId; }
        public UUID getTargetCallId() { return targetCallId; }
        public InterceptorType getType() { return type; }
        public long getStartTime() { return startTime; }
        public long getDuration() { return System.currentTimeMillis() - startTime; }
        public boolean isValid() { return isValid; }

        public void markValid() {
            this.isValid = true;
            this.lastValidationTime = System.currentTimeMillis();
        }

        public void markInvalid() {
            this.isValid = false;
        }

        public double getMaxRange() {
            switch (type) {
                case PORTABLE_BABY:
                    return net.eclipce.transpondersnails.config.ModConfig.getBabyBlackSnailRange();
                case ADULT_HANDHELD:
                    return net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
                case ADULT_PLACED:
                    // Will be boosted by lightning rods in future phases
                    return net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
                default:
                    return net.eclipce.transpondersnails.config.ModConfig.getBabyBlackSnailRange();
            }
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
     * Start searching for a call (initiates 5-second delay before connection)
     * Called when player opens Black Transponder Snail
     */
    public boolean startSearching(ServerPlayer interceptor, UUID targetCallId) {
        // Validation
        if (interceptor == null || targetCallId == null) {
            return false;
        }

        // Check if already intercepting or searching
        if (isIntercepting(interceptor.getUUID()) || isSearching(interceptor.getUUID())) {
            System.out.println("Player " + interceptor.getName().getString() + " is already intercepting or searching");
            return false;
        }

        // Verify the target call exists and is active
        CallSession targetCall = callManager.getCallSessionById(targetCallId);
        if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
            System.out.println("Target call not found or not connected: " + targetCallId);
            return false;
        }

        // Check if the interceptor is a participant in the target call
        if (targetCall.isParticipant(interceptor.getUUID())) {
            System.out.println("Cannot intercept own call");
            return false;
        }

        // Determine interceptor type based on held item
        InterceptionSession.InterceptorType type = determineInterceptorType(interceptor);
        if (type == null) {
            System.out.println("Player not holding a valid Black Transponder Snail");
            return false;
        }

        // Create searching session (5-second delay before connection)
        SearchingSession searchingSession = new SearchingSession(
                interceptor.getUUID(),
                targetCallId,
                type
        );

        searchingSessions.put(interceptor.getUUID(), searchingSession);

        System.out.println("Started searching for call: Player " + interceptor.getName().getString() +
                " searching for call " + targetCallId.toString().substring(0, 8));

        // ✨ SYNC: Tell client we're searching (SOUND state)
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
            if (session.isReadyToConnect()) {
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
                    net.minecraft.network.chat.Component.literal("Call ended before connection")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    true
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
                    net.minecraft.network.chat.Component.literal("Failed to connect to call")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true
            );
            return;
        }

        // Register the interception
        activeInterceptions.put(interceptorId, session);
        callInterceptors.computeIfAbsent(searchingSession.getTargetCallId(), k -> ConcurrentHashMap.newKeySet())
                .add(interceptorId);

        // Play connection sound at low volume
        playConnectionSound(interceptor);

        // Notify player
        interceptor.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Connected to call")
                        .withStyle(net.minecraft.ChatFormatting.GREEN),
                true
        );

        // ✨ SYNC: Tell client we're intercepting (CALL state - no audio yet)
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

            // ✨ SYNC: Tell client search cancelled (back to IDLE)
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
                    Component.literal("Disconnected from call")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );

            // ✨ SYNC: Tell client we're idle now
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
                        net.minecraft.network.chat.Component.literal("No other calls in range")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        true
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
                    net.minecraft.network.chat.Component.literal("Switching to another call...")
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
            // Out of range
            if (session.isValid()) {
                // First time going out of range - notify player
                interceptor.displayClientMessage(
                        Component.literal("Out of range...")
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
            }
            session.markInvalid();
            // Don't stop immediately - allow brief excursions
            // Will be stopped by cleanup if stays out of range
        } else {
            // In range
            if (!session.isValid()) {
                // Coming back in range - notify player
                interceptor.displayClientMessage(
                        Component.literal("Back in range")
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
            }
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

            // Notify player before disconnecting
            ServerPlayer player = callManager.getPlayerById(interceptorId);
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("Connection lost - too far")
                                .withStyle(ChatFormatting.RED),
                        true
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
        if (mainHand.getItem() instanceof PortableBlackTransponderSnailItem) {
            if (PortableBlackTransponderSnailItem.isOpen(mainHand)) {
                return InterceptionSession.InterceptorType.PORTABLE_BABY;
            }
        }

        // Check off hand
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PortableBlackTransponderSnailItem) {
            if (PortableBlackTransponderSnailItem.isOpen(offHand)) {
                return InterceptionSession.InterceptorType.PORTABLE_BABY;
            }
        }

        // TODO: Check Curios slots when integrated
        // TODO: Add Adult Black Transponder Snail checks

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