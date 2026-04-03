package net.eclipce.transpondersnails.voice.server;

import net.eclipce.transpondersnails.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all placed Horned Den Den Mushi jammer blocks.
 *
 * Static singleton — accessible from both the block entity (registration)
 * and TransponderCallManager (jamming checks) without any constructor injection.
 *
 * A jammer blocks ALL call initiation, ringing, and active calls for any
 * snail or player whose position falls within the configured jamming radius.
 *
 * Thread-safe: all maps use ConcurrentHashMap.
 */
public class HornedDDMJammerManager {

    // =================== SINGLETON ===================

    private static final HornedDDMJammerManager INSTANCE = new HornedDDMJammerManager();

    public static HornedDDMJammerManager getInstance() {
        return INSTANCE;
    }

    private HornedDDMJammerManager() {}

    // =================== DATA ===================

    /**
     * Immutable BlockPos → dimension key for every active jammer.
     * The dimension key is stored alongside the position so we only check
     * jammers that are in the same world as the snail / player being tested.
     */
    private final Map<BlockPos, ResourceKey<Level>> activeJammers = new ConcurrentHashMap<>();

    // =================== REGISTRATION ===================

    /**
     * Called by HornedDenDenMushiBlockEntity.onLoad().
     */
    public void registerJammer(BlockPos pos, ServerLevel level) {
        activeJammers.put(pos.immutable(), level.dimension());
    }

    /**
     * Called by HornedDenDenMushiBlockEntity.setRemoved() and when the block is broken.
     */
    public void unregisterJammer(BlockPos pos) {
        ResourceKey<Level> removed = activeJammers.remove(pos.immutable());
        if (removed != null) {
        }
    }

    // =================== JAMMED CHECKS ===================

    /**
     * Returns true if the given world position is within the jamming radius of
     * ANY active jammer in the same dimension.
     *
     * The radius is a SPHERE: uses squared-distance comparison.
     * Default radius = 20 blocks (configurable in server config).
     */
    public boolean isPositionJammed(BlockPos pos, ResourceKey<Level> dimension) {
        if (activeJammers.isEmpty()) return false;

        double radius = ModConfig.getHornedDDMJammingRadius();
        double radiusSq = radius * radius;

        for (Map.Entry<BlockPos, ResourceKey<Level>> entry : activeJammers.entrySet()) {
            // Skip jammers in a different dimension
            if (!entry.getValue().equals(dimension)) continue;

            BlockPos jammerPos = entry.getKey();
            double dx = pos.getX() - jammerPos.getX();
            double dy = pos.getY() - jammerPos.getY();
            double dz = pos.getZ() - jammerPos.getZ();

            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience overload for block positions in a specific level.
     */
    public boolean isBlockPosJammed(BlockPos pos, ServerLevel level) {
        return isPositionJammed(pos, level.dimension());
    }

    /**
     * Returns true if the player is standing within any active jammer's radius.
     * Uses the player's block position for the check.
     */
    public boolean isPlayerJammed(ServerPlayer player) {
        return isPositionJammed(player.blockPosition(), player.level().dimension());
    }

    // =================== UTILITY ===================

    public int getActiveJammerCount() {
        return activeJammers.size();
    }

    /**
     * Returns a snapshot of all active jammer positions (for debug / future use).
     */
    public Set<BlockPos> getActiveJammerPositions() {
        return Set.copyOf(activeJammers.keySet());
    }

    /**
     * Clears all registered jammers.
     * Called on server stop to avoid stale entries across restarts.
     */
    public void clear() {
        int count = activeJammers.size();
        activeJammers.clear();
        if (count > 0) {
        }
    }
}