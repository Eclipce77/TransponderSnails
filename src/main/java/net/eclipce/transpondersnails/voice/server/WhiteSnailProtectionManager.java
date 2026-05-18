package net.eclipce.transpondersnails.voice.server;

import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.custom.WireBlock;
import net.eclipce.transpondersnails.block.custom.WhiteTransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.WhiteTransponderSnailBlockEntity;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages White Transponder Snail protection for call interception.
 *
 * When a Transponder Snail with Transmitter is connected to a White Transponder Snail
 * via wire, all audio from that participant will be blocked from interception.
 * Interceptors will only hear static instead of the actual audio.
 *
 * This manager also tracks and updates the visual states of White Snails:
 * - IDLE: Not protecting any call
 * - CONNECTED: Protecting an active call
 * - BLOCKING: Actively blocking an interception attempt
 *
 * Protection is per-participant, not per-call. So if only one side of a call
 * is protected, interceptors can still hear the unprotected participant.
 * 
 * ✨ NEW IN V3: Wire network change detection
 * - Automatically re-evaluates protection when wires are placed/broken
 * - Updates White Snail states in real-time during active calls
 */
public class WhiteSnailProtectionManager {

    private static final WhiteSnailProtectionManager INSTANCE = new WhiteSnailProtectionManager();

    // Cache protection status to avoid repeated wire traces
    // Key: BlockPos of Transponder Snail with Transmitter
    // Value: ProtectionInfo with status and connected White Snail
    private final Map<BlockPos, ProtectionCacheEntry> protectionCache = new ConcurrentHashMap<>();

    // Track which White Snails are actively protecting calls
    // Key: BlockPos of White Snail
    // Value: Set of protected snail positions (can protect multiple)
    private final Map<BlockPos, Set<BlockPos>> activeProtections = new ConcurrentHashMap<>();

    // Track which White Snails are currently blocking interceptions
    // Key: BlockPos of White Snail
    // Value: Set of interceptor UUIDs being blocked
    private final Map<BlockPos, Set<UUID>> activeBlockings = new ConcurrentHashMap<>();

    // Track levels for updating blockstates
    private final Map<BlockPos, ServerLevel> whiteSnailLevels = new ConcurrentHashMap<>();

    // ✨ NEW: Track which Transponder Snails are in active calls (for wire change detection)
    // Key: BlockPos of Transponder Snail with Transmitter
    // Value: ServerLevel
    private final Map<BlockPos, ServerLevel> activeCallSnails = new ConcurrentHashMap<>();

    // ✨ NEW: Reference to call manager for re-evaluation
    private TransponderCallManager callManager;

    // Cache timeout (5 seconds - wire connections don't change often)
    private static final long CACHE_TIMEOUT_MS = 5000;

    // Maximum wire trace distance (same as WireBlock)
    private static final int MAX_WIRE_RANGE = 64;

    // Background scheduler for state updates
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "WhiteSnail-StateManager"));

    private WhiteSnailProtectionManager() {
        // Periodically check and reset blocking states that are stale
        scheduler.scheduleAtFixedRate(this::cleanupStaleBlockingStates, 1, 1, TimeUnit.SECONDS);
    }

    public static WhiteSnailProtectionManager getInstance() {
        return INSTANCE;
    }

    /**
     * ✨ NEW: Set the call manager reference (called during initialization)
     */
    public void setCallManager(TransponderCallManager callManager) {
        this.callManager = callManager;

    }

    /**
     * Cache entry for protection status
     */
    private static class ProtectionCacheEntry {
        final boolean isProtected;
        final long timestamp;
        final BlockPos whiteSnailPos; // Position of connected White Snail (null if not protected)

        ProtectionCacheEntry(boolean isProtected, @Nullable BlockPos whiteSnailPos) {
            this.isProtected = isProtected;
            this.timestamp = System.currentTimeMillis();
            this.whiteSnailPos = whiteSnailPos;
        }

        boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < CACHE_TIMEOUT_MS;
        }
    }

    // =================== PROTECTION QUERIES ===================

    /**
     * Check if a participant at the given snail position is protected from interception.
     *
     * @param level The server level
     * @param snailPos Position of the Transponder Snail (with Transmitter) block
     * @return true if the participant is protected by a connected White Transponder Snail
     */
    public boolean isParticipantProtected(Level level, BlockPos snailPos) {
        if (level == null || snailPos == null) {
            return false;
        }

        // Check if this is a Transponder Snail with Transmitter
        BlockState state = level.getBlockState(snailPos);
        if (!isTransponderSnailWithTransmitter(state)) {
            return false;
        }

        // Check cache first
        ProtectionCacheEntry cached = protectionCache.get(snailPos);
        if (cached != null && cached.isValid()) {
            return cached.isProtected;
        }

        // Cache miss or expired - trace wire network
        BlockPos whiteSnailPos = traceToWhiteTransponderSnail(level, snailPos);
        boolean isProtected = whiteSnailPos != null;

        // Update cache
        protectionCache.put(snailPos, new ProtectionCacheEntry(isProtected, whiteSnailPos));

        if (isProtected) {
        }

        return isProtected;
    }

    /**
     * Check if a participant (by snail number) is protected.
     */
    public boolean isParticipantProtected(TransponderCallManager callManager, int snailNumber) {
        if (callManager == null || snailNumber < 0) {
            return false;
        }

        TransponderSnailBlockEntity blockEntity = callManager.getRegisteredSnailBlock(snailNumber);
        if (blockEntity == null) {
            return false;
        }

        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();

        return isParticipantProtected(level, pos);
    }

    /**
     * Check if a call participant is protected.
     */
    public boolean isParticipantProtected(TransponderCallManager callManager,
                                          CallSession.CallParticipant participant) {
        if (participant == null) {
            return false;
        }

        // Handheld snails cannot be protected by White Snails
        if (participant.isHandheld()) {
            return false;
        }

        if (participant.isBlock() && participant.getBlockPosition() != null) {
            TransponderSnailBlockEntity blockEntity =
                    callManager.getRegisteredSnailBlock(participant.getSnailNumber());

            if (blockEntity != null && blockEntity.getLevel() != null) {
                return isParticipantProtected(blockEntity.getLevel(), participant.getBlockPosition());
            }
        }

        return false;
    }

    /**
     * Get the White Snail protecting a participant (if any)
     */
    @Nullable
    public BlockPos getProtectingWhiteSnail(Level level, BlockPos snailPos) {
        if (level == null || snailPos == null) {
            return null;
        }

        ProtectionCacheEntry cached = protectionCache.get(snailPos);
        if (cached != null && cached.isValid()) {
            return cached.whiteSnailPos;
        }

        return traceToWhiteTransponderSnail(level, snailPos);
    }

    /**
     * Get the cached level for a White Snail position.
     * Used when we need to access the level but only have the position.
     *
     * @param whiteSnailPos Position of the White Snail
     * @return The ServerLevel, or null if not cached
     */
    @Nullable
    public ServerLevel getWhiteSnailLevel(BlockPos whiteSnailPos) {
        return whiteSnailLevels.get(whiteSnailPos);
    }

    // =================== VISUAL STATE MANAGEMENT ===================

    /**
     * Called when a call becomes active and a participant is protected.
     * Updates the White Snail's visual state to CONNECTED.
     *
     * @param level The level containing the White Snail
     * @param protectedSnailPos Position of the protected Transponder Snail
     */
    public void onCallProtectionStarted(ServerLevel level, BlockPos protectedSnailPos) {
        BlockPos whiteSnailPos = getProtectingWhiteSnail(level, protectedSnailPos);
        if (whiteSnailPos == null) {
            return;
        }

        // Track this protection
        activeProtections.computeIfAbsent(whiteSnailPos, k -> ConcurrentHashMap.newKeySet())
                .add(protectedSnailPos);
        whiteSnailLevels.put(whiteSnailPos, level);

        // ✨ NEW: Track active call snails for wire change detection
        activeCallSnails.put(protectedSnailPos, level);

        // Update visual state to CONNECTED
        updateWhiteSnailVisualState(level, whiteSnailPos);

        // Update wire connection display
        WhiteTransponderSnailBlock.updateWireConnections(level, whiteSnailPos);

    }

    /**
     * Called when a call ends or participant disconnects.
     * Updates the White Snail's visual state.
     *
     * @param level The level containing the White Snail
     * @param protectedSnailPos Position of the formerly protected Transponder Snail
     */
    public void onCallProtectionEnded(ServerLevel level, BlockPos protectedSnailPos) {
        // ✨ NEW: Remove from active call tracking
        activeCallSnails.remove(protectedSnailPos);

        BlockPos whiteSnailPos = getProtectingWhiteSnail(level, protectedSnailPos);
        if (whiteSnailPos == null) {
            // White Snail may have been disconnected - check all active protections
            whiteSnailPos = findWhiteSnailProtecting(protectedSnailPos);
            if (whiteSnailPos == null) {
                return;
            }
        }

        // Remove this protection
        Set<BlockPos> protections = activeProtections.get(whiteSnailPos);
        if (protections != null) {
            protections.remove(protectedSnailPos);
            if (protections.isEmpty()) {
                activeProtections.remove(whiteSnailPos);
            }
        }

        // Update visual state
        updateWhiteSnailVisualState(level, whiteSnailPos);

    }

    /**
     * ✨ NEW: Find which White Snail was protecting a position (from our tracking)
     */
    @Nullable
    private BlockPos findWhiteSnailProtecting(BlockPos protectedSnailPos) {
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : activeProtections.entrySet()) {
            if (entry.getValue().contains(protectedSnailPos)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Called when a White Snail is actively blocking an interception attempt.
     * Updates the visual state to BLOCKING.
     *
     * @param level The level containing the White Snail
     * @param whiteSnailPos Position of the White Snail
     * @param interceptorId UUID of the interceptor being blocked
     */
    public void onInterceptionBlocked(ServerLevel level, BlockPos whiteSnailPos, UUID interceptorId) {
        if (whiteSnailPos == null || interceptorId == null) {
            return;
        }

        // Track this blocking
        activeBlockings.computeIfAbsent(whiteSnailPos, k -> ConcurrentHashMap.newKeySet())
                .add(interceptorId);
        whiteSnailLevels.put(whiteSnailPos, level);

        // Update visual state to BLOCKING
        updateWhiteSnailVisualState(level, whiteSnailPos);

    }

    /**
     * Called when an interception attempt ends.
     *
     * @param level The level containing the White Snail
     * @param whiteSnailPos Position of the White Snail
     * @param interceptorId UUID of the interceptor that stopped
     */
    public void onInterceptionEnded(ServerLevel level, BlockPos whiteSnailPos, UUID interceptorId) {
        if (whiteSnailPos == null || interceptorId == null) {
            return;
        }

        // Remove this blocking
        Set<UUID> blockings = activeBlockings.get(whiteSnailPos);
        if (blockings != null) {
            blockings.remove(interceptorId);
            if (blockings.isEmpty()) {
                activeBlockings.remove(whiteSnailPos);
            }
        }

        // Update visual state
        updateWhiteSnailVisualState(level, whiteSnailPos);

    }

    /**
     * Update a White Snail's visual state based on its current activity
     */
    private void updateWhiteSnailVisualState(Level level, BlockPos whiteSnailPos) {
        if (level == null || whiteSnailPos == null) {
            return;
        }

        int newState;

        // BLOCKING takes priority over CONNECTED
        Set<UUID> blockings = activeBlockings.get(whiteSnailPos);
        if (blockings != null && !blockings.isEmpty()) {
            newState = WhiteTransponderSnailBlock.STATE_BLOCKING;
        }
        // Check if protecting any active calls
        else if (activeProtections.containsKey(whiteSnailPos) &&
                !activeProtections.get(whiteSnailPos).isEmpty()) {
            newState = WhiteTransponderSnailBlock.STATE_CONNECTED;
        }
        // Default to IDLE
        else {
            newState = WhiteTransponderSnailBlock.STATE_IDLE;
        }

        // Update blockstate
        WhiteTransponderSnailBlock.setSnailState(level, whiteSnailPos, newState);
    }

    // =================== ✨ NEW: WIRE NETWORK CHANGE DETECTION ===================

    /**
     * ✨ NEW: Called when a wire is placed or broken.
     * Re-evaluates protection status for all active calls that might be affected.
     *
     * @param level The level where the change occurred
     * @param changedPos The position where wire was placed/broken
     */
    public void onWireNetworkChanged(Level level, BlockPos changedPos) {
        if (level == null || level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Invalidate cache around the changed position
        invalidateCacheInRange(changedPos, MAX_WIRE_RANGE);

        // Re-evaluate all active call snails
        reEvaluateActiveCallProtections(serverLevel);
    }

    /**
     * ✨ NEW: Called when a White Transponder Snail is placed or broken.
     * Re-evaluates protection status for all active calls.
     *
     * @param level The level where the change occurred
     * @param whiteSnailPos The position of the White Snail
     * @param placed True if placed, false if broken
     */
    public void onWhiteSnailChanged(Level level, BlockPos whiteSnailPos, boolean placed) {
        if (level == null || level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }


        if (!placed) {
            // White Snail was broken - immediately remove all protections it was providing
            Set<BlockPos> protectedSnails = activeProtections.remove(whiteSnailPos);
            if (protectedSnails != null && !protectedSnails.isEmpty()) {
                
                // Clear visual state (block is gone, but update any cached state)
                activeBlockings.remove(whiteSnailPos);
                whiteSnailLevels.remove(whiteSnailPos);
            }
        }

        // Invalidate cache
        invalidateCacheInRange(whiteSnailPos, MAX_WIRE_RANGE);

        // Re-evaluate all active call protections
        reEvaluateActiveCallProtections(serverLevel);
    }

    /**
     * ✨ NEW: Re-evaluate protection status for all active calls.
     * Called when wire network changes might affect protection.
     */
    private void reEvaluateActiveCallProtections(ServerLevel level) {
        if (callManager == null) {
            System.err.println("WhiteSnailProtectionManager: Cannot re-evaluate - callManager not set!");
            return;
        }

        // Get all active connected calls
        for (CallSession callSession : callManager.getActiveCalls()) {
            if (callSession.getState() != CallSession.CallState.CONNECTED) {
                continue;
            }

            // Check each block participant
            for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
                if (!participant.isBlock() || participant.getBlockPosition() == null) {
                    continue;
                }

                BlockPos snailPos = participant.getBlockPosition();
                
                // Get current protection status (fresh trace, not cached)
                BlockPos currentWhiteSnail = traceToWhiteTransponderSnail(level, snailPos);
                boolean currentlyProtected = currentWhiteSnail != null;

                // Find which White Snail was previously protecting this snail
                BlockPos previousWhiteSnail = findWhiteSnailProtecting(snailPos);
                boolean wasProtected = previousWhiteSnail != null;

                // Check if protection status changed
                if (currentlyProtected && !wasProtected) {
                    // Newly protected - wire was connected mid-call
                    
                    // Add to tracking
                    activeProtections.computeIfAbsent(currentWhiteSnail, k -> ConcurrentHashMap.newKeySet())
                            .add(snailPos);
                    whiteSnailLevels.put(currentWhiteSnail, level);
                    activeCallSnails.put(snailPos, level);
                    
                    // Update visual state
                    updateWhiteSnailVisualState(level, currentWhiteSnail);
                    WhiteTransponderSnailBlock.updateWireConnections(level, currentWhiteSnail);
                    
                } else if (!currentlyProtected && wasProtected) {
                    // No longer protected - wire was disconnected mid-call
                    
                    // Remove from tracking
                    Set<BlockPos> protections = activeProtections.get(previousWhiteSnail);
                    if (protections != null) {
                        protections.remove(snailPos);
                        if (protections.isEmpty()) {
                            activeProtections.remove(previousWhiteSnail);
                        }
                    }
                    
                    // Update visual state
                    updateWhiteSnailVisualState(level, previousWhiteSnail);
                    
                } else if (currentlyProtected && wasProtected && 
                           !currentWhiteSnail.equals(previousWhiteSnail)) {
                    // Protection changed to a different White Snail (rare but possible)

                    // Remove from old
                    Set<BlockPos> oldProtections = activeProtections.get(previousWhiteSnail);
                    if (oldProtections != null) {
                        oldProtections.remove(snailPos);
                        if (oldProtections.isEmpty()) {
                            activeProtections.remove(previousWhiteSnail);
                        }
                    }
                    updateWhiteSnailVisualState(level, previousWhiteSnail);
                    
                    // Add to new
                    activeProtections.computeIfAbsent(currentWhiteSnail, k -> ConcurrentHashMap.newKeySet())
                            .add(snailPos);
                    whiteSnailLevels.put(currentWhiteSnail, level);
                    updateWhiteSnailVisualState(level, currentWhiteSnail);
                    WhiteTransponderSnailBlock.updateWireConnections(level, currentWhiteSnail);
                }

                // Update cache
                protectionCache.put(snailPos, new ProtectionCacheEntry(currentlyProtected, currentWhiteSnail));
            }
        }
    }

    /**
     * ✨ NEW: Invalidate cache entries within a range of a position
     */
    private void invalidateCacheInRange(BlockPos center, int range) {
        // Remove all cache entries that might be affected
        // Since wire traces can be long, we need to be conservative
        List<BlockPos> toRemove = new ArrayList<>();
        
        for (BlockPos cachedPos : protectionCache.keySet()) {
            if (cachedPos.closerThan(center, range)) {
                toRemove.add(cachedPos);
            }
        }
        
        for (BlockPos pos : toRemove) {
            protectionCache.remove(pos);
        }
        
        if (!toRemove.isEmpty()) {
        }
    }

    /**
     * Clean up stale blocking states (if interceptor disconnected without cleanup)
     */
    private void cleanupStaleBlockingStates() {
        try {
            // Check each blocking state
            for (Map.Entry<BlockPos, Set<UUID>> entry : activeBlockings.entrySet()) {
                BlockPos whiteSnailPos = entry.getKey();
                ServerLevel level = whiteSnailLevels.get(whiteSnailPos);

                if (level == null) {
                    continue;
                }

                // TODO: Cross-reference with CallInterceptionManager to verify
                // interceptors are still active. For now, trust the normal cleanup flow.
            }
        } catch (Exception e) {
            System.err.println("WhiteSnailProtectionManager: Error during cleanup: " + e.getMessage());
        }
    }

    // =================== WIRE TRACING ===================

    /**
     * Check if a block is a Transponder Snail with Transmitter
     */
    private boolean isTransponderSnailWithTransmitter(BlockState state) {
        if (state == null) {
            return false;
        }

        Block block = state.getBlock();
        return block == ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get();
    }

    /**
     * Trace from a Transponder Snail position through wire network to find
     * a connected White Transponder Snail.
     *
     * @param level The world
     * @param startPos Starting position (Transponder Snail with Transmitter)
     * @return Position of connected White Transponder Snail, or null if none found
     */
    @Nullable
    public BlockPos traceToWhiteTransponderSnail(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && visited.size() < MAX_WIRE_RANGE * MAX_WIRE_RANGE) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            Block block = state.getBlock();

            // Check if this is a White Transponder Snail
            if (block instanceof WhiteTransponderSnailBlock) {
                return current;
            }

            // Only trace through wire blocks (except for starting position)
            if (!(block instanceof WireBlock) && !current.equals(startPos)) {
                continue;
            }

            // Check all horizontal neighbors with vertical transitions
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                checkAndAddNeighbor(level, current, dir, visited, queue);
            }
        }

        return null;
    }

    /**
     * Find ALL White Transponder Snails connected to a position.
     */
    public Set<BlockPos> traceToAllWhiteTransponderSnails(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> whiteSnails = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && visited.size() < MAX_WIRE_RANGE * MAX_WIRE_RANGE) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            Block block = state.getBlock();

            if (block instanceof WhiteTransponderSnailBlock) {
                whiteSnails.add(current);
            }

            if (!(block instanceof WireBlock) && !current.equals(startPos)) {
                continue;
            }

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                checkAndAddNeighbor(level, current, dir, visited, queue);
            }
        }

        return whiteSnails;
    }

    /**
     * Helper to check neighbor positions including up/down transitions
     */
    private void checkAndAddNeighbor(Level level, BlockPos current, Direction dir,
                                     Set<BlockPos> visited, Queue<BlockPos> queue) {
        BlockPos neighbor = current.relative(dir);
        addIfConnectable(level, neighbor, visited, queue);

        BlockPos aboveNeighbor = neighbor.above();
        addIfConnectable(level, aboveNeighbor, visited, queue);

        BlockPos belowNeighbor = neighbor.below();
        addIfConnectable(level, belowNeighbor, visited, queue);
    }

    private void addIfConnectable(Level level, BlockPos pos, Set<BlockPos> visited, Queue<BlockPos> queue) {
        if (!visited.contains(pos)) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();

            if (block instanceof WireBlock || block instanceof WhiteTransponderSnailBlock) {
                visited.add(pos);
                queue.add(pos);
            }
        }
    }

    // =================== CACHE MANAGEMENT ===================

    /**
     * Invalidate cache for a specific position.
     * Call this when wires or snails are placed/broken.
     */
    public void invalidateCache(BlockPos pos) {
        protectionCache.remove(pos);

        for (Direction dir : Direction.values()) {
            protectionCache.remove(pos.relative(dir));
        }
    }

    /**
     * Clear entire cache.
     */
    public void clearCache() {
        protectionCache.clear();
    }

    /**
     * Clear all state tracking (call on server shutdown)
     */
    public void clearAll() {
        protectionCache.clear();
        activeProtections.clear();
        activeBlockings.clear();
        whiteSnailLevels.clear();
        activeCallSnails.clear();
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        clearAll();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =================== DEBUG INFO ===================

    /**
     * Get debug info about protection status
     */
    public String getDebugInfo(Level level, BlockPos snailPos) {
        if (level == null || snailPos == null) {
            return "Invalid position";
        }

        BlockState state = level.getBlockState(snailPos);
        if (!isTransponderSnailWithTransmitter(state)) {
            return "Not a Transponder Snail with Transmitter";
        }

        ProtectionCacheEntry cached = protectionCache.get(snailPos);
        BlockPos whiteSnailPos = traceToWhiteTransponderSnail(level, snailPos);

        StringBuilder sb = new StringBuilder();
        sb.append("Protection Status for ").append(snailPos).append(":\n");
        sb.append("  Is Protected: ").append(whiteSnailPos != null).append("\n");
        if (whiteSnailPos != null) {
            sb.append("  White Snail at: ").append(whiteSnailPos).append("\n");

            int visualState = WhiteTransponderSnailBlock.getSnailState(level, whiteSnailPos);
            sb.append("  Visual State: ").append(WhiteTransponderSnailBlock.getStateName(visualState)).append("\n");

            Set<UUID> blockings = activeBlockings.get(whiteSnailPos);
            sb.append("  Active Blockings: ").append(blockings != null ? blockings.size() : 0);
        }
        sb.append("\n  Cache Status: ").append(cached != null ? (cached.isValid() ? "Valid" : "Expired") : "None");
        sb.append("\n  Active Call Snails Tracked: ").append(activeCallSnails.size());

        return sb.toString();
    }
}
