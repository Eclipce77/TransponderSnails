package net.eclipce.transpondersnails.voice.client;

import net.eclipce.transpondersnails.voice.client.PortableBlackSnailCallStateManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLIENT-SIDE manager for Black Transponder Snail (adult) call states
 * Tracks state synced from server via packets
 *
 * IMPORTANT: Predicate values MUST match the model JSON file thresholds:
 * - black_transponder_snail.json uses: 0.0 (idle), 0.1 (sound), 0.2 (call), 0.3 (active)
 */
public class BlackSnailCallStateManager {

    private static final BlackSnailCallStateManager INSTANCE = new BlackSnailCallStateManager();

    // Track call state per player (synced from server)
    private final Map<UUID, CallState> playerStates = new ConcurrentHashMap<>();

    // Track last update time for each player
    private final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private static final long STATE_TIMEOUT_MS = 2000; // Clear state after 2s of no updates

    /**
     * Call states with predicate values matching black_transponder_snail.json model thresholds
     * These values are used by Minecraft's item model predicate system
     */
    public enum CallState {
        IDLE(0.0f),      // Default state - no interception
        SOUND(0.1f),     // Searching/scanning for calls
        CALL(0.2f),      // Intercepting, no audio yet
        ACTIVE(0.3f);    // Intercepting + receiving audio

        private final float predicateValue;

        CallState(float predicateValue) {
            this.predicateValue = predicateValue;
        }

        public float getPredicateValue() {
            return predicateValue;
        }

        /**
         * Convert from PortableBlackSnailCallStateManager.CallState to this CallState
         * This allows the unified sync system to work with all snail types
         */
        public static CallState fromPortableState(PortableBlackSnailCallStateManager.CallState portableState) {
            if (portableState == null) {
                return IDLE;
            }
            switch (portableState) {
                case SOUND:
                    return SOUND;
                case CALL:
                    return CALL;
                case ACTIVE:
                    return ACTIVE;
                case IDLE:
                default:
                    return IDLE;
            }
        }
    }

    private BlackSnailCallStateManager() {}

    public static BlackSnailCallStateManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set call state for a player (called by packet handler)
     */
    public void setState(UUID playerId, CallState state) {
        System.out.println("[BLACK-STATE-MGR] setState: player=" + playerId.toString().substring(0, 8) +
                ", state=" + state + " (value=" + state.getPredicateValue() + ")");

        if (state == CallState.IDLE) {
            playerStates.remove(playerId);
            lastUpdateTime.remove(playerId);
        } else {
            playerStates.put(playerId, state);
            lastUpdateTime.put(playerId, System.currentTimeMillis());
        }
    }

    /**
     * Set state from a PortableBlackSnailCallStateManager.CallState
     * This allows the unified packet system to update this manager
     */
    public void setStateFromPortable(UUID playerId, PortableBlackSnailCallStateManager.CallState portableState) {
        CallState localState = CallState.fromPortableState(portableState);
        setState(playerId, localState);
    }

    /**
     * Get current call state for a player
     * Returns IDLE if state is too old or not set
     */
    public CallState getState(UUID playerId) {
        Long lastUpdate = lastUpdateTime.get(playerId);
        if (lastUpdate != null) {
            long timeSinceUpdate = System.currentTimeMillis() - lastUpdate;
            if (timeSinceUpdate > STATE_TIMEOUT_MS) {
                // State is stale, clear it
                playerStates.remove(playerId);
                lastUpdateTime.remove(playerId);
                return CallState.IDLE;
            }
        }

        return playerStates.getOrDefault(playerId, CallState.IDLE);
    }

    /**
     * Get predicate value for a player
     * This is called by the item model predicate system
     */
    public float getPredicateValue(UUID playerId) {
        CallState state = getState(playerId);
        return state.getPredicateValue();
    }

    /**
     * Clear state for a player
     */
    public void clearState(UUID playerId) {
        playerStates.remove(playerId);
        lastUpdateTime.remove(playerId);
    }

    /**
     * Clear all states
     */
    public void clearAll() {
        playerStates.clear();
        lastUpdateTime.clear();
    }
}