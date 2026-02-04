package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.BlackTransponderSnailBlockEntity;
import net.eclipce.transpondersnails.sound.ModSounds;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.eclipce.transpondersnails.item.BlackTransponderSnailItem;
import net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages call interception for Black Transponder Snails.
 *
 * Features:
 * - Movement detection for handheld snails (disconnects when moving)
 * - Block-based interception with lightning rod range extension
 * - Audio routing to interceptors
 * - Range validation
 * - Visual state sync
 */
public class CallInterceptionManager {

    private final VoicechatServerApi voiceChatApi;
    private final TransponderCallManager callManager;

    // Active interceptions: interceptor UUID -> InterceptionSession
    private final Map<UUID, InterceptionSession> activeInterceptions = new ConcurrentHashMap<>();

    // Block-based interceptions: BlockPos -> BlockInterceptionSession
    private final Map<BlockPos, BlockInterceptionSession> blockInterceptions = new ConcurrentHashMap<>();

    // Call-based lookup: callId -> Set of interceptor UUIDs
    private final Map<UUID, Set<UUID>> callInterceptors = new ConcurrentHashMap<>();

    // Interceptor audio channels: interceptor UUID -> AudioChannel
    private final Map<UUID, AudioChannel> interceptorChannels = new ConcurrentHashMap<>();

    // Block audio channels: BlockPos -> AudioChannel
    private final Map<BlockPos, AudioChannel> blockAudioChannels = new ConcurrentHashMap<>();

    // Searching interceptions: interceptor UUID -> SearchingSession
    private final Map<UUID, SearchingSession> searchingSessions = new ConcurrentHashMap<>();

    // Track which calls each interceptor has already tapped (for call switching)
    private final Map<UUID, Set<UUID>> tappedCallsHistory = new ConcurrentHashMap<>();

    // Audio activity tracking for visual feedback
    private final Map<UUID, Long> lastAudioActivity = new ConcurrentHashMap<>();
    private static final long AUDIO_ACTIVITY_WINDOW_MS = 500; // 500ms window

    // Movement detection tracking
    private final Map<UUID, Vec3> lastPlayerPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMovementCheckTime = new ConcurrentHashMap<>();
    private static final double MOVEMENT_THRESHOLD = 0.1; // Blocks - very sensitive
    private static final long MOVEMENT_CHECK_INTERVAL_MS = 100; // Check every 100ms

    // Searching delay (5 seconds to "find" the call)
    private static final long SEARCHING_DELAY_MS = 5000;

    public CallInterceptionManager(VoicechatServerApi voiceChatApi, TransponderCallManager callManager) {
        this.voiceChatApi = voiceChatApi;
        this.callManager = callManager;
        System.out.println("CallInterceptionManager initialized with movement detection + block support");
    }

    // =================== INTERCEPTION TYPES ===================

    /**
     * Represents a searching/connecting session before interception begins
     */
    public static class SearchingSession {
        private final UUID interceptorId;
        private final UUID targetCallId;
        private final InterceptionSession.InterceptorType type;
        private final long startTime;
        @Nullable
        private final BlockPos blockPos; // For block-based interceptions
        private final int customRange; // For block-based range override

        public SearchingSession(UUID interceptorId, UUID targetCallId, InterceptionSession.InterceptorType type) {
            this(interceptorId, targetCallId, type, null, -1);
        }

        public SearchingSession(UUID interceptorId, UUID targetCallId, InterceptionSession.InterceptorType type,
                                @Nullable BlockPos blockPos, int customRange) {
            this.interceptorId = interceptorId;
            this.targetCallId = targetCallId;
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.blockPos = blockPos;
            this.customRange = customRange;
        }

        public UUID getInterceptorId() { return interceptorId; }
        public UUID getTargetCallId() { return targetCallId; }
        public InterceptionSession.InterceptorType getType() { return type; }
        public long getTimeSearching() { return System.currentTimeMillis() - startTime; }
        public boolean isReadyToConnect() { return getTimeSearching() >= SEARCHING_DELAY_MS; }
        @Nullable public BlockPos getBlockPos() { return blockPos; }
        public int getCustomRange() { return customRange; }
        public boolean isBlockBased() { return blockPos != null; }
    }

    /**
     * Represents an active interception session (handheld)
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
            BABY_HANDHELD,      // Baby Black Transponder Snail Item
            ADULT_HANDHELD,     // Adult Black Transponder Snail (Handheld)
            ADULT_PLACED        // Adult Black Transponder Snail (Placed block)
        }

        public InterceptionSession(UUID interceptorId, UUID targetCallId, InterceptorType type) {
            this.interceptorId = interceptorId;
            this.targetCallId = targetCallId;
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.lastValidationTime = startTime;
            this.isValid = true;
        }

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
                case BABY_HANDHELD:
                    return net.eclipce.transpondersnails.config.ModConfig.getBabyBlackSnailRange();
                case ADULT_HANDHELD:
                    return net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
                case ADULT_PLACED:
                    // Range is calculated by the block entity based on lightning rods
                    return net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailMaxRange();
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

    /**
     * Represents a block-based interception session
     */
    public static class BlockInterceptionSession {
        private final BlockPos blockPos;
        private final UUID targetCallId;
        private final UUID lastInteractorId;
        private final int range;
        private final long startTime;
        private long lastValidationTime;
        private boolean isValid;

        public BlockInterceptionSession(BlockPos blockPos, UUID targetCallId, UUID lastInteractorId, int range) {
            this.blockPos = blockPos;
            this.targetCallId = targetCallId;
            this.lastInteractorId = lastInteractorId;
            this.range = range;
            this.startTime = System.currentTimeMillis();
            this.lastValidationTime = startTime;
            this.isValid = true;
        }

        public BlockPos getBlockPos() { return blockPos; }
        public UUID getTargetCallId() { return targetCallId; }
        public UUID getLastInteractorId() { return lastInteractorId; }
        public int getRange() { return range; }
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
    }

    // =================== MOVEMENT DETECTION ===================

    /**
     * Check if a player has moved since last check
     * Called by validation loop
     */
    public boolean hasPlayerMoved(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Vec3 currentPos = player.position();
        long currentTime = System.currentTimeMillis();

        Vec3 lastPos = lastPlayerPositions.get(playerId);
        Long lastCheckTime = lastMovementCheckTime.get(playerId);

        // Update tracking
        lastPlayerPositions.put(playerId, currentPos);
        lastMovementCheckTime.put(playerId, currentTime);

        if (lastPos == null || lastCheckTime == null) {
            return false; // First check - don't count as movement
        }

        // Check if enough time has passed
        if (currentTime - lastCheckTime < MOVEMENT_CHECK_INTERVAL_MS) {
            return false; // Not enough time has passed
        }

        // Calculate distance moved
        double distance = currentPos.distanceTo(lastPos);

        // Debug logging for movement detection
        if (distance > MOVEMENT_THRESHOLD) {
            System.out.println("[MOVEMENT] Player " + player.getName().getString() +
                    " moved " + String.format("%.3f", distance) + " blocks (threshold: " + MOVEMENT_THRESHOLD + ")");
        }

        return distance > MOVEMENT_THRESHOLD;
    }

    /**
     * Start tracking player position for movement detection
     */
    private void startTrackingMovement(ServerPlayer player) {
        lastPlayerPositions.put(player.getUUID(), player.position());
        lastMovementCheckTime.put(player.getUUID(), System.currentTimeMillis());
    }

    /**
     * Stop tracking player movement
     */
    private void stopTrackingMovement(UUID playerId) {
        lastPlayerPositions.remove(playerId);
        lastMovementCheckTime.remove(playerId);
    }

    // =================== HANDHELD INTERCEPTION LIFECYCLE ===================

    /**
     * Start searching for a call (initiates 5-second delay before connection)
     * Called when player opens Black Transponder Snail (handheld)
     */
    public boolean startSearching(ServerPlayer interceptor, UUID targetCallId) {
        if (interceptor == null || targetCallId == null) {
            return false;
        }

        if (isIntercepting(interceptor.getUUID()) || isSearching(interceptor.getUUID())) {
            System.out.println("Player " + interceptor.getName().getString() + " is already intercepting or searching");
            return false;
        }

        CallSession targetCall = callManager.getCallSessionById(targetCallId);
        if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
            System.out.println("Target call not found or not connected: " + targetCallId);
            return false;
        }

        if (targetCall.isParticipant(interceptor.getUUID())) {
            System.out.println("Cannot intercept own call");
            return false;
        }

        InterceptionSession.InterceptorType type = determineInterceptorType(interceptor);
        if (type == null) {
            System.out.println("Player not holding a valid Black Transponder Snail");
            return false;
        }

        // Start tracking movement
        startTrackingMovement(interceptor);

        SearchingSession searchingSession = new SearchingSession(
                interceptor.getUUID(),
                targetCallId,
                type
        );

        searchingSessions.put(interceptor.getUUID(), searchingSession);

        System.out.println("Started searching for call: Player " + interceptor.getName().getString() +
                " searching for call " + targetCallId.toString().substring(0, 8));

        BlackSnailStateSyncHelper.syncSearching(interceptor);

        return true;
    }

    /**
     * Alias for startSearching - for backwards compatibility
     */
    public boolean startInterception(ServerPlayer interceptor, UUID targetCallId) {
        return startSearching(interceptor, targetCallId);
    }

    // =================== BLOCK-BASED INTERCEPTION ===================

    /**
     * Start searching for a call from a placed block
     * Called when player opens a placed Black Transponder Snail block
     */
    public boolean startSearchingForBlock(ServerPlayer player, UUID targetCallId, BlockPos blockPos, int range) {
        if (player == null || targetCallId == null || blockPos == null) {
            return false;
        }

        // Check if this block is already intercepting
        if (blockInterceptions.containsKey(blockPos)) {
            System.out.println("Block at " + blockPos + " is already intercepting");
            return false;
        }

        CallSession targetCall = callManager.getCallSessionById(targetCallId);
        if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
            System.out.println("Target call not found or not connected: " + targetCallId);
            return false;
        }

        // Create searching session for block
        SearchingSession searchingSession = new SearchingSession(
                player.getUUID(),
                targetCallId,
                InterceptionSession.InterceptorType.ADULT_PLACED,
                blockPos,
                range
        );

        searchingSessions.put(player.getUUID(), searchingSession);

        System.out.println("Started block-based searching: Block at " + blockPos +
                " searching for call " + targetCallId.toString().substring(0, 8) +
                " with range " + range);

        // Sync visual state
        BlackSnailStateSyncHelper.syncSearching(player);

        return true;
    }

    /**
     * Stop a block-based interception
     */
    public void stopBlockInterception(BlockPos blockPos) {
        BlockInterceptionSession session = blockInterceptions.remove(blockPos);
        if (session == null) {
            return;
        }

        // Remove from call interceptors
        Set<UUID> interceptors = callInterceptors.get(session.getTargetCallId());
        if (interceptors != null) {
            // Block interceptions use a synthetic UUID based on position
            UUID blockUUID = getBlockUUID(blockPos);
            interceptors.remove(blockUUID);
            if (interceptors.isEmpty()) {
                callInterceptors.remove(session.getTargetCallId());
            }
        }

        // Clean up audio channel
        AudioChannel channel = blockAudioChannels.remove(blockPos);
        if (channel != null) {
            System.out.println("Removed block interceptor audio channel at " + blockPos);
        }

        System.out.println("Stopped block interception at " + blockPos);
    }

    /**
     * Generate a synthetic UUID for a block position (for tracking purposes)
     */
    private UUID getBlockUUID(BlockPos pos) {
        return UUID.nameUUIDFromBytes(("block:" + pos.asLong()).getBytes());
    }

    // =================== PROCESS SEARCHING SESSIONS ===================

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

        CallSession targetCall = callManager.getCallSessionById(searchingSession.getTargetCallId());
        if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
            interceptor.displayClientMessage(
                    Component.literal("Call ended before connection").withStyle(ChatFormatting.GRAY),
                    true
            );
            stopTrackingMovement(interceptorId);
            return;
        }

        // Handle block-based vs handheld interception
        if (searchingSession.isBlockBased()) {
            connectBlockInterception(searchingSession, interceptor, targetCall);
        } else {
            connectHandheldInterception(searchingSession, interceptor, targetCall);
        }
    }

    /**
     * Connect a handheld interception session
     */
    private void connectHandheldInterception(SearchingSession searchingSession, ServerPlayer interceptor, CallSession targetCall) {
        InterceptionSession session = new InterceptionSession(
                searchingSession.getInterceptorId(),
                searchingSession.getTargetCallId(),
                searchingSession.getType()
        );

        if (!createInterceptorAudioChannel(interceptor, session)) {
            System.out.println("Failed to create interceptor audio channel");
            interceptor.displayClientMessage(
                    Component.literal("Failed to connect to call").withStyle(ChatFormatting.RED),
                    true
            );
            stopTrackingMovement(interceptor.getUUID());
            return;
        }

        activeInterceptions.put(interceptor.getUUID(), session);
        callInterceptors.computeIfAbsent(searchingSession.getTargetCallId(), k -> ConcurrentHashMap.newKeySet())
                .add(interceptor.getUUID());

        playConnectionSound(interceptor);

        interceptor.displayClientMessage(
                Component.literal("Connected to call").withStyle(ChatFormatting.GREEN),
                true
        );

        BlackSnailStateSyncHelper.syncIntercepting(interceptor);

        System.out.println("Connected handheld interception: " + session);
    }

    /**
     * Connect a block-based interception session
     */
    private void connectBlockInterception(SearchingSession searchingSession, ServerPlayer interceptor, CallSession targetCall) {
        BlockPos blockPos = searchingSession.getBlockPos();
        int range = searchingSession.getCustomRange();

        // Create block interception session
        BlockInterceptionSession session = new BlockInterceptionSession(
                blockPos,
                searchingSession.getTargetCallId(),
                interceptor.getUUID(),
                range
        );

        // Create audio channel at block location
        if (!createBlockAudioChannel(blockPos, interceptor.serverLevel(), range)) {
            System.out.println("Failed to create block audio channel at " + blockPos);
            interceptor.displayClientMessage(
                    Component.literal("Failed to connect to call").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        blockInterceptions.put(blockPos, session);

        UUID blockUUID = getBlockUUID(blockPos);
        callInterceptors.computeIfAbsent(searchingSession.getTargetCallId(), k -> ConcurrentHashMap.newKeySet())
                .add(blockUUID);

        // Update block entity
        BlockEntity be = interceptor.level().getBlockEntity(blockPos);
        if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
            snailBE.setInterceptingCallId(searchingSession.getTargetCallId());
        }

        playConnectionSound(interceptor);

        interceptor.displayClientMessage(
                Component.literal("Connected to call - Range: " + range + " blocks").withStyle(ChatFormatting.GREEN),
                true
        );

        System.out.println("Connected block interception at " + blockPos + ": call=" +
                searchingSession.getTargetCallId().toString().substring(0, 8) + ", range=" + range);
    }

    /**
     * Create audio channel for a block interceptor
     */
    private boolean createBlockAudioChannel(BlockPos blockPos, net.minecraft.server.level.ServerLevel level, int range) {
        try {
            LocationalAudioChannel channel = voiceChatApi.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    voiceChatApi.fromServerLevel(level),
                    voiceChatApi.createPosition(
                            blockPos.getX() + 0.5,
                            blockPos.getY() + 0.5,
                            blockPos.getZ() + 0.5
                    )
            );

            if (channel != null) {
                channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
                channel.setDistance((float) range);

                blockAudioChannels.put(blockPos, channel);

                System.out.println("Created block interceptor audio channel at " + blockPos +
                        " (range: " + range + " blocks)");
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to create block interceptor audio channel: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Play the transponder snail connection sound at low volume
     */
    private void playConnectionSound(ServerPlayer player) {
        player.playSound(
                ModSounds.SNAIL_CONNECTED.get(),
                0.3f,
                1.0f
        );
    }

    // =================== STOP INTERCEPTION ===================

    /**
     * Stop a searching session
     */
    public void stopSearching(UUID interceptorId) {
        SearchingSession searchingSession = searchingSessions.remove(interceptorId);
        if (searchingSession != null) {
            System.out.println("Cancelled searching session for " + interceptorId.toString().substring(0, 8));
            stopTrackingMovement(interceptorId);

            ServerPlayer player = callManager.getPlayerById(interceptorId);
            if (player != null) {
                BlackSnailStateSyncHelper.syncIdle(player);
            }
        }
    }

    /**
     * Stop an active interception or searching session (handheld)
     */
    public void stopInterception(UUID interceptorId) {
        // Stop searching session if exists
        SearchingSession searchingSession = searchingSessions.remove(interceptorId);
        if (searchingSession != null) {
            System.out.println("Cancelled searching session for " + interceptorId.toString().substring(0, 8));
            stopTrackingMovement(interceptorId);
            return;
        }

        // Stop active interception
        InterceptionSession session = activeInterceptions.remove(interceptorId);
        if (session == null) {
            return;
        }

        // Stop tracking movement
        stopTrackingMovement(interceptorId);

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
                    Component.literal("Disconnected from call").withStyle(ChatFormatting.GRAY),
                    true
            );

            BlackSnailStateSyncHelper.syncIdle(player);
        }

        System.out.println("Stopped interception: " + session);
    }

    /**
     * Switch to the next available call (crouch + right-click functionality)
     */
    public boolean switchToNextCall(ServerPlayer interceptor) {
        if (interceptor == null) {
            return false;
        }

        UUID playerId = interceptor.getUUID();

        InterceptionSession currentSession = activeInterceptions.get(playerId);
        SearchingSession currentSearch = searchingSessions.get(playerId);

        UUID currentCallId = null;
        if (currentSession != null) {
            currentCallId = currentSession.getTargetCallId();
        } else if (currentSearch != null) {
            currentCallId = currentSearch.getTargetCallId();
        }

        Set<UUID> tappedCalls = tappedCallsHistory.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        if (currentCallId != null) {
            tappedCalls.add(currentCallId);
        }

        UUID nextCallId = findNextCallToTap(interceptor, tappedCalls, currentCallId);

        if (nextCallId == null) {
            if (!tappedCalls.isEmpty()) {
                tappedCalls.clear();
                nextCallId = findNextCallToTap(interceptor, tappedCalls, currentCallId);
            }

            if (nextCallId == null) {
                interceptor.displayClientMessage(
                        Component.literal("No other calls in range").withStyle(ChatFormatting.GRAY),
                        true
                );
                return false;
            }
        }

        stopInterception(playerId);

        boolean success = startInterception(interceptor, nextCallId);

        if (success) {
            interceptor.displayClientMessage(
                    Component.literal("Switching to another call...").withStyle(ChatFormatting.YELLOW),
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

        Collection<CallSession> activeCalls = callManager.getActiveCalls();

        UUID closestUntapped = null;
        double closestUntappedDistance = maxRange;

        UUID closestTapped = null;
        double closestTappedDistance = maxRange;

        for (CallSession call : activeCalls) {
            if (call.getState() != CallSession.CallState.CONNECTED) {
                continue;
            }

            UUID callId = call.getCallId();

            if (callId.equals(currentCallId)) {
                continue;
            }

            if (call.isParticipant(interceptor.getUUID())) {
                continue;
            }

            double distance = getDistanceToNearestParticipant(interceptor, call);

            if (distance >= maxRange) {
                continue;
            }

            boolean isTapped = tappedCalls.contains(callId);

            if (!isTapped) {
                if (distance < closestUntappedDistance) {
                    closestUntappedDistance = distance;
                    closestUntapped = callId;
                }
            } else {
                if (distance < closestTappedDistance) {
                    closestTappedDistance = distance;
                    closestTapped = callId;
                }
            }
        }

        return closestUntapped != null ? closestUntapped : closestTapped;
    }

    /**
     * Stop all interceptions for a specific call (e.g., when call ends)
     */
    public void stopAllInterceptionsForCall(UUID callId) {
        // Stop handheld interceptions
        Set<UUID> interceptors = callInterceptors.remove(callId);
        if (interceptors != null) {
            for (UUID interceptorId : interceptors) {
                // Check if it's a block UUID
                if (isBlockUUID(interceptorId)) {
                    // Find and remove block interception
                    BlockPos blockPos = findBlockPosForUUID(interceptorId);
                    if (blockPos != null) {
                        stopBlockInterception(blockPos);
                    }
                } else {
                    stopInterception(interceptorId);
                }
            }
            System.out.println("Stopped all interceptions for call " + callId.toString().substring(0, 8));
        }
    }

    /**
     * Check if a UUID is a synthetic block UUID
     */
    private boolean isBlockUUID(UUID uuid) {
        // Block UUIDs are generated from "block:" prefix
        return blockInterceptions.values().stream()
                .anyMatch(session -> getBlockUUID(session.getBlockPos()).equals(uuid));
    }

    /**
     * Find block position for a synthetic block UUID
     */
    @Nullable
    private BlockPos findBlockPosForUUID(UUID uuid) {
        for (BlockInterceptionSession session : blockInterceptions.values()) {
            if (getBlockUUID(session.getBlockPos()).equals(uuid)) {
                return session.getBlockPos();
            }
        }
        return null;
    }

    // =================== AUDIO ROUTING ===================

    @Nullable
    public AudioChannel getInterceptorChannel(UUID interceptorId) {
        return interceptorChannels.get(interceptorId);
    }

    public Set<UUID> getInterceptorsForCall(UUID callId) {
        Set<UUID> interceptors = callInterceptors.get(callId);
        return interceptors != null ? new HashSet<>(interceptors) : Collections.emptySet();
    }

    /**
     * Get all block positions that are intercepting a specific call
     */
    public Set<BlockPos> getBlockInterceptionsForCall(UUID callId) {
        Set<BlockPos> positions = new HashSet<>();
        for (Map.Entry<BlockPos, BlockInterceptionSession> entry : blockInterceptions.entrySet()) {
            if (entry.getValue().getTargetCallId().equals(callId)) {
                positions.add(entry.getKey());
            }
        }
        return positions;
    }

    /**
     * Check if a UUID represents a block interceptor (synthetic block UUID)
     */
    public boolean isBlockInterceptorUUID(UUID uuid) {
        return isBlockUUID(uuid);
    }

    /**
     * Get block position for a synthetic block UUID (public accessor)
     */
    @Nullable
    public BlockPos getBlockPosForUUID(UUID uuid) {
        return findBlockPosForUUID(uuid);
    }

    /**
     * Get all active interceptor channels for a call (both handheld and block)
     */
    public List<AudioChannel> getInterceptorChannelsForCall(UUID callId) {
        List<AudioChannel> channels = new ArrayList<>();
        Set<UUID> interceptors = getInterceptorsForCall(callId);

        for (UUID interceptorId : interceptors) {
            // Check handheld channels
            AudioChannel channel = interceptorChannels.get(interceptorId);
            if (channel != null) {
                channels.add(channel);
            }

            // Check if it's a block UUID
            if (isBlockUUID(interceptorId)) {
                BlockPos blockPos = findBlockPosForUUID(interceptorId);
                if (blockPos != null) {
                    AudioChannel blockChannel = blockAudioChannels.get(blockPos);
                    if (blockChannel != null) {
                        channels.add(blockChannel);
                    }
                }
            }
        }

        return channels;
    }

    // =================== VALIDATION ===================

    /**
     * Validate all active interceptions - called periodically
     * Now includes movement detection for handheld snails
     */
    public void validateInterceptions(ServerPlayer interceptor) {
        if (interceptor == null) return;

        InterceptionSession session = activeInterceptions.get(interceptor.getUUID());
        if (session == null) return;

        // MOVEMENT DETECTION: Check if player moved (only for handheld types)
        if (session.getType() != InterceptionSession.InterceptorType.ADULT_PLACED) {
            if (hasPlayerMoved(interceptor)) {
                System.out.println("[MOVEMENT-DISCONNECT] Player " + interceptor.getName().getString() +
                        " moved while intercepting - disconnecting");

                interceptor.displayClientMessage(
                        Component.literal("Movement detected - Connection lost")
                                .withStyle(ChatFormatting.RED),
                        true
                );

                stopInterception(interceptor.getUUID());
                return;
            }
        }

        CallSession targetCall = callManager.getCallSessionById(session.getTargetCallId());
        if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
            stopInterception(interceptor.getUUID());
            return;
        }

        if (!hasOpenBlackSnail(interceptor)) {
            stopInterception(interceptor.getUUID());
            return;
        }

        double nearestDistance = getDistanceToNearestParticipant(interceptor, targetCall);
        double maxRange = session.getMaxRange();

        if (nearestDistance > maxRange) {
            if (session.isValid()) {
                interceptor.displayClientMessage(
                        Component.literal("Out of range...").withStyle(ChatFormatting.YELLOW),
                        true
                );
            }
            session.markInvalid();
        } else {
            if (!session.isValid()) {
                interceptor.displayClientMessage(
                        Component.literal("Back in range").withStyle(ChatFormatting.GREEN),
                        true
                );
            }
            session.markValid();
            updateInterceptorChannelPosition(interceptor);
        }
    }

    /**
     * Validate block-based interceptions
     */
    public void validateBlockInterceptions() {
        List<BlockPos> toRemove = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockInterceptionSession> entry : blockInterceptions.entrySet()) {
            BlockPos blockPos = entry.getKey();
            BlockInterceptionSession session = entry.getValue();

            // Check if call still exists
            CallSession targetCall = callManager.getCallSessionById(session.getTargetCallId());
            if (targetCall == null || targetCall.getState() != CallSession.CallState.CONNECTED) {
                toRemove.add(blockPos);
                continue;
            }

            // Check if block still exists and is open
            if (callManager.getPlayerById(session.getLastInteractorId()) != null) {
                net.minecraft.world.level.Level level = callManager.getPlayerById(session.getLastInteractorId()).level();
                BlockEntity be = level.getBlockEntity(blockPos);
                if (!(be instanceof BlackTransponderSnailBlockEntity snailBE) || !snailBE.isOpen()) {
                    toRemove.add(blockPos);
                    continue;
                }
            }

            // Check range to nearest participant
            double nearestDistance = getDistanceToNearestParticipantFromBlock(blockPos, targetCall);
            if (nearestDistance > session.getRange()) {
                if (session.isValid()) {
                    session.markInvalid();
                }
            } else {
                session.markValid();
            }
        }

        for (BlockPos pos : toRemove) {
            stopBlockInterception(pos);
        }
    }

    /**
     * Get distance from block position to nearest call participant
     */
    private double getDistanceToNearestParticipantFromBlock(BlockPos blockPos, CallSession targetCall) {
        double minDistance = Double.MAX_VALUE;

        for (CallSession.CallParticipant participant : targetCall.getAllParticipants()) {
            double distance;

            if (participant.isHandheld() && participant.hasActivePlayer()) {
                ServerPlayer participantPlayer = callManager.getPlayerById(participant.getPlayerId());
                if (participantPlayer != null) {
                    distance = Math.sqrt(blockPos.distSqr(participantPlayer.blockPosition()));
                    minDistance = Math.min(minDistance, distance);
                }
            } else if (participant.isBlock() && participant.getBlockPosition() != null) {
                distance = Math.sqrt(blockPos.distSqr(participant.getBlockPosition()));
                minDistance = Math.min(minDistance, distance);
            }
        }

        return minDistance;
    }

    /**
     * Clean up invalid interceptions - called periodically
     */
    public void cleanupInvalidInterceptions() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, InterceptionSession> entry : activeInterceptions.entrySet()) {
            InterceptionSession session = entry.getValue();

            if (!session.isValid() &&
                    (System.currentTimeMillis() - session.lastValidationTime) > 5000) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID interceptorId : toRemove) {
            System.out.println("Cleaning up invalid interception (out of range) for " +
                    interceptorId.toString().substring(0, 8));

            ServerPlayer player = callManager.getPlayerById(interceptorId);
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("Connection lost - too far").withStyle(ChatFormatting.RED),
                        true
                );
            }

            stopInterception(interceptorId);
        }

        // Also validate block interceptions
        validateBlockInterceptions();
    }

    // =================== HELPER METHODS ===================

    @Nullable
    private InterceptionSession.InterceptorType determineInterceptorType(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof PortableBlackTransponderSnailItem) {
            if (PortableBlackTransponderSnailItem.isOpen(mainHand)) {
                return InterceptionSession.InterceptorType.PORTABLE_BABY;
            }
        }
        if (mainHand.getItem() instanceof BlackTransponderSnailItem) {
            if (BlackTransponderSnailItem.isOpen(mainHand)) {
                return InterceptionSession.InterceptorType.ADULT_HANDHELD;
            }
        }
        if (mainHand.getItem() instanceof BabyBlackTransponderSnailItem) {
            if (BabyBlackTransponderSnailItem.isOpen(mainHand)) {
                return InterceptionSession.InterceptorType.BABY_HANDHELD;
            }
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PortableBlackTransponderSnailItem) {
            if (PortableBlackTransponderSnailItem.isOpen(offHand)) {
                return InterceptionSession.InterceptorType.PORTABLE_BABY;
            }
        }
        if (offHand.getItem() instanceof BlackTransponderSnailItem) {
            if (BlackTransponderSnailItem.isOpen(offHand)) {
                return InterceptionSession.InterceptorType.ADULT_HANDHELD;
            }
        }
        if (offHand.getItem() instanceof BabyBlackTransponderSnailItem) {
            if (BabyBlackTransponderSnailItem.isOpen(offHand)) {
                return InterceptionSession.InterceptorType.BABY_HANDHELD;
            }
        }

        return null;
    }

    private boolean hasOpenBlackSnail(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof PortableBlackTransponderSnailItem &&
                PortableBlackTransponderSnailItem.isOpen(mainHand)) {
            return true;
        }
        if (mainHand.getItem() instanceof BlackTransponderSnailItem &&
                BlackTransponderSnailItem.isOpen(mainHand)) {
            return true;
        }
        if (mainHand.getItem() instanceof BabyBlackTransponderSnailItem &&
                BabyBlackTransponderSnailItem.isOpen(mainHand)) {
            return true;
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof PortableBlackTransponderSnailItem &&
                PortableBlackTransponderSnailItem.isOpen(offHand)) {
            return true;
        }
        if (offHand.getItem() instanceof BlackTransponderSnailItem &&
                BlackTransponderSnailItem.isOpen(offHand)) {
            return true;
        }
        if (offHand.getItem() instanceof BabyBlackTransponderSnailItem &&
                BabyBlackTransponderSnailItem.isOpen(offHand)) {
            return true;
        }

        return false;
    }

    private double getDistanceToNearestParticipant(ServerPlayer interceptor, CallSession targetCall) {
        double minDistance = Double.MAX_VALUE;

        for (CallSession.CallParticipant participant : targetCall.getAllParticipants()) {
            double distance;

            if (participant.isHandheld() && participant.hasActivePlayer()) {
                ServerPlayer participantPlayer = callManager.getPlayerById(participant.getPlayerId());
                if (participantPlayer != null) {
                    distance = interceptor.position().distanceTo(participantPlayer.position());
                    minDistance = Math.min(minDistance, distance);
                }
            } else if (participant.isBlock() && participant.getBlockPosition() != null) {
                distance = interceptor.position().distanceTo(
                        participant.getBlockPosition().getCenter()
                );
                minDistance = Math.min(minDistance, distance);
            }
        }

        return minDistance;
    }

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

    public boolean isIntercepting(UUID playerId) {
        return activeInterceptions.containsKey(playerId);
    }

    public boolean isSearching(UUID playerId) {
        return searchingSessions.containsKey(playerId);
    }

    public boolean isInterceptingOrSearching(UUID playerId) {
        return isIntercepting(playerId) || isSearching(playerId);
    }

    @Nullable
    public SearchingSession getSearchingSession(UUID playerId) {
        return searchingSessions.get(playerId);
    }

    @Nullable
    public InterceptionSession getInterceptionSession(UUID playerId) {
        return activeInterceptions.get(playerId);
    }

    public boolean isCallBeingIntercepted(UUID callId) {
        Set<UUID> interceptors = callInterceptors.get(callId);
        return interceptors != null && !interceptors.isEmpty();
    }

    public int getActiveInterceptionCount() {
        return activeInterceptions.size() + blockInterceptions.size();
    }

    public String getStats() {
        return String.format("CallInterceptionManager{handheld=%d, blocks=%d, calls=%d}",
                activeInterceptions.size(),
                blockInterceptions.size(),
                callInterceptors.size());
    }

    // =================== AUDIO ACTIVITY TRACKING ===================

    public void markAudioActivity(UUID interceptorId) {
        lastAudioActivity.put(interceptorId, System.currentTimeMillis());
    }

    /**
     * Mark audio activity for a block interceptor
     */
    public void markBlockAudioActivity(BlockPos blockPos) {
        UUID blockUUID = getBlockUUID(blockPos);
        lastAudioActivity.put(blockUUID, System.currentTimeMillis());

        // Also update the block entity
        BlockInterceptionSession session = blockInterceptions.get(blockPos);
        if (session != null) {
            ServerPlayer player = callManager.getPlayerById(session.getLastInteractorId());
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(blockPos);
                if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                    snailBE.markAudioActivity();
                }
            }
        }
    }

    @Nullable
    public Long getLastAudioActivity(UUID interceptorId) {
        return lastAudioActivity.get(interceptorId);
    }

    public boolean hasRecentAudioActivity(UUID interceptorId) {
        Long lastActivity = lastAudioActivity.get(interceptorId);
        if (lastActivity == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastActivity) < AUDIO_ACTIVITY_WINDOW_MS;
    }

    /**
     * Update call states for all interceptors
     */
    public void updateCallStates() {
        for (UUID interceptorId : activeInterceptions.keySet()) {
            ServerPlayer player = callManager.getPlayerById(interceptorId);
            if (player != null) {
                if (!hasRecentAudioActivity(interceptorId)) {
                    BlackSnailStateSyncHelper.syncIntercepting(player);
                }
            }
        }
    }

    /**
     * Cleanup - called when shutting down
     */
    public void cleanup() {
        new ArrayList<>(activeInterceptions.keySet()).forEach(this::stopInterception);
        new ArrayList<>(blockInterceptions.keySet()).forEach(this::stopBlockInterception);

        searchingSessions.clear();
        tappedCallsHistory.clear();
        lastAudioActivity.clear();
        lastPlayerPositions.clear();
        lastMovementCheckTime.clear();

        activeInterceptions.clear();
        blockInterceptions.clear();
        callInterceptors.clear();
        interceptorChannels.clear();
        blockAudioChannels.clear();

        System.out.println("CallInterceptionManager cleaned up");
    }
}