package net.eclipce.transpondersnails.voice.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLIENT-SIDE manager for Portable Black Transponder Snail call states
 * Tracks state synced from server via packets
 */
public class PortableBlackSnailCallStateManager {

    private static final PortableBlackSnailCallStateManager INSTANCE = new PortableBlackSnailCallStateManager();

    // Track call state per player (synced from server)
    private final Map<UUID, CallState> playerStates = new ConcurrentHashMap<>();

    // Track last update time for each player
    private final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private static final long STATE_TIMEOUT_MS = 2000; // Clear state after 2s of no updates

    public enum CallState {
        IDLE(0.0f),
        SOUND(0.25f),    // Searching/scanning
        CALL(0.5f),      // Intercepting, no audio
        ACTIVE(0.75f);   // Intercepting + audio

        private final float predicateValue;

        CallState(float predicateValue) {
            this.predicateValue = predicateValue;
        }

        public float getPredicateValue() {
            return predicateValue;
        }
    }

    private PortableBlackSnailCallStateManager() {}

    public static PortableBlackSnailCallStateManager getInstance() {
        return INSTANCE;
    }

    /**
     * Set call state for a player (called by packet handler)
     */
    public void setState(UUID playerId, CallState state) {
        // ✅ VERBOSE LOGGING
//        System.out.println("[STATE-MGR] setState called on CLIENT");
//        System.out.println("[STATE-MGR]    Player: " + playerId.toString().substring(0, 8));
//        System.out.println("[STATE-MGR]    New state: " + state + " (value=" + state.getPredicateValue() + ")");

        if (state == CallState.IDLE) {
            playerStates.remove(playerId);
            lastUpdateTime.remove(playerId);
//            System.out.println("[STATE-MGR]    Action: Removed from map (IDLE state)");
        } else {
            playerStates.put(playerId, state);
            lastUpdateTime.put(playerId, System.currentTimeMillis());
//            System.out.println("[STATE-MGR]    Action: Added to map");
//            System.out.println("[STATE-MGR]    Map now contains " + playerStates.size() + " entries");
        }
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
//                System.out.println("[STATE-MGR] ⚠️ State timeout for player " + playerId.toString().substring(0, 8) +
//                        " (age: " + timeSinceUpdate + "ms)");
                return CallState.IDLE;
            }
        }

        CallState state = playerStates.getOrDefault(playerId, CallState.IDLE);
//        System.out.println("[STATE-MGR] getState for " + playerId.toString().substring(0, 8) +
//                " -> " + state + " (value=" + state.getPredicateValue() + ")");
        return state;
    }

    /**
     * Get predicate value for a player
     */
    public float getPredicateValue(UUID playerId) {
        CallState state = getState(playerId);
        float value = state.getPredicateValue();
//        System.out.println("[STATE-MGR] getPredicateValue for " + playerId.toString().substring(0, 8) +
//                " -> " + value);
        return value;
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