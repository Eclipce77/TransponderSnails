package net.eclipce.transpondersnails.voice.server;

import net.eclipce.transpondersnails.sound.ModSounds;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages spatial sound effects for Transponder Snail calls
 * Enhanced with sound categorization for blockstate texture changes and faster timing
 */
public class CallSoundManager {

    // Snail connection sounds
    public static final ResourceLocation SNAIL_RINGING_SOUND = ModSounds.SNAIL_RINGING.getId();
    public static final ResourceLocation SNAIL_CALL_CONNECTION_SOUND = ModSounds.SNAIL_CONNECTED.getId();
    public static final ResourceLocation SNAIL_CALL_DISCONNECTED_SOUND = ModSounds.SNAIL_DISCONNECTED.getId();
    public static final ResourceLocation SNAIL_CALL_BUSY_SOUND = ModSounds.SNAIL_BUSY.getId();

    // Pick up and hang up sounds
    public static final ResourceLocation HANDSET_CALL_PICK_UP_SOUND = ModSounds.SNAIL_PICK_UP.getId();
    public static final ResourceLocation HANDSET_CALL_HANG_UP_SOUND = ModSounds.SNAIL_HANG_UP.getId();

    // Tracking active sounds for cleanup
    private final Map<UUID, SoundInstance> activeSounds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Track ambient snail sounds per position for blockstate updates
    private final Map<BlockPos, Set<SoundCategory>> activeAmbientSounds = new ConcurrentHashMap<>();

    // Sound categorization for blockstate logic
    public enum SoundCategory {
        AMBIENT_SNAIL_SOUNDS,    // Affects blockstate texture (ringing, connection status)
        INTERACTION_SOUNDS       // Does not affect blockstate (pick up, hang up)
    }

    // Inner class to track sound instances with categories
    private static class SoundInstance {
        final BlockPos snailPosition;
        final Level level;
        final ResourceLocation soundLocation;
        final SoundType type;
        final SoundCategory category;
        final long startTime;
        java.util.concurrent.ScheduledFuture<?> stopTask;

        SoundInstance(BlockPos snailPosition, Level level, ResourceLocation soundLocation, SoundType type, SoundCategory category) {
            this.snailPosition = snailPosition;
            this.level = level;
            this.soundLocation = soundLocation;
            this.type = type;
            this.category = category;
            this.startTime = System.currentTimeMillis();
        }
    }

    public enum SoundType {
        RING_TONE,      // Repeating ring sound
        ONE_SHOT,       // Single play sound
        AMBIENT         // Continuous ambient sound
    }

    // Callback interface for blockstate updates
    public interface BlockstateUpdateCallback {
        void onSoundStateChanged(BlockPos pos, boolean hasAmbientSound);
    }

    private final Set<BlockstateUpdateCallback> blockstateCallbacks = ConcurrentHashMap.newKeySet();

    // Register for blockstate updates
    public void registerBlockstateCallback(BlockstateUpdateCallback callback) {
        blockstateCallbacks.add(callback);
        System.out.println("CallSoundManager: Registered blockstate callback");
    }

    public void unregisterBlockstateCallback(BlockstateUpdateCallback callback) {
        boolean removed = blockstateCallbacks.remove(callback);
        if (removed) {
            System.out.println("CallSoundManager: Unregistered blockstate callback");
        }
    }

    // =================== SPATIAL AUDIO METHODS WITH ENHANCED TIMING ===================

    /**
     * Play ringtone at snail location with faster intervals - AMBIENT sound that affects blockstate
     */
    public void playLocationalRingTone(ServerPlayer player, BlockPos snailLocation) {
        UUID soundId = UUID.randomUUID();
        playSnailPositionRepeatingSound(player.level(), snailLocation, SNAIL_RINGING_SOUND,
                soundId, SoundType.RING_TONE, SoundCategory.AMBIENT_SNAIL_SOUNDS,
                2000); // Reduced from 2000ms to 1500ms
        System.out.println("CallSoundManager: Started ambient ringtone at snail position " + snailLocation);
    }

    public void playLocationalRingToneAtPosition(Level level, BlockPos snailLocation) {
        UUID soundId = UUID.randomUUID();
        playSnailPositionRepeatingSound(level, snailLocation, SNAIL_RINGING_SOUND,
                soundId, SoundType.RING_TONE, SoundCategory.AMBIENT_SNAIL_SOUNDS,
                2000); // Reduced from 2000ms to 1500ms
        System.out.println("CallSoundManager: Started ambient ringtone at snail position " + snailLocation + " (no player required)");
    }

    /**
     * Play ringtone sound for a player with a handheld snail
     * The sound follows the player as they move
     */
    public void playRingToneForPlayer(ServerPlayer player) {
        UUID soundId = UUID.randomUUID();
        playPlayerFollowingRepeatingSound(player, SNAIL_RINGING_SOUND, soundId,
                SoundType.RING_TONE, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Started ringtone for player " + player.getName().getString());
    }

    /**
     * Play connection sound at snail location - AMBIENT sound that affects blockstate
     */
    public void playCallConnectedSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, SNAIL_CALL_CONNECTION_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played ambient connection sound at snail position " + snailPos);
    }

    /**
     * Play connected sound for a player with a handheld snail
     */
    public void playConnectedSoundForPlayer(ServerPlayer player) {
        playPlayerSound(player, SNAIL_CALL_CONNECTION_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played connected sound for player " + player.getName().getString());
    }

    /**
     * Play disconnection sound at snail location - AMBIENT sound that affects blockstate
     */
    public void playCallDisconnectedSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, SNAIL_CALL_DISCONNECTED_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played ambient disconnection sound at snail position " + snailPos);
    }

    /**
     * Play disconnected sound for a player with a handheld snail
     */
    public void playDisconnectedSoundForPlayer(ServerPlayer player) {
        playPlayerSound(player, SNAIL_CALL_DISCONNECTED_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played disconnected sound for player " + player.getName().getString());
    }

    /**
     * Play pick up sound at snail location - INTERACTION sound that does NOT affect blockstate
     */
    public void playPickUpSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, HANDSET_CALL_PICK_UP_SOUND, SoundCategory.INTERACTION_SOUNDS);
        System.out.println("CallSoundManager: Played interaction pick up sound at snail position " + snailPos);
    }

    /**
     * Play pick up sound for a player with a handheld snail
     */
    public void playPickUpSoundForPlayer(ServerPlayer player) {
        playPlayerSound(player, HANDSET_CALL_PICK_UP_SOUND, SoundCategory.INTERACTION_SOUNDS);
        System.out.println("CallSoundManager: Played pick up sound for player " + player.getName().getString());
    }

    /**
     * Play hang up sound at snail location - INTERACTION sound that does NOT affect blockstate
     */
    public void playHangUpSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, HANDSET_CALL_HANG_UP_SOUND, SoundCategory.INTERACTION_SOUNDS);
        System.out.println("CallSoundManager: Played interaction hang up sound at snail position " + snailPos);
    }

    /**
     * Play hang up sound for a player with a handheld snail
     */
    public void playHangUpSoundForPlayer(ServerPlayer player) {
        playPlayerSound(player, HANDSET_CALL_HANG_UP_SOUND, SoundCategory.INTERACTION_SOUNDS);
        System.out.println("CallSoundManager: Played hang up sound for player " + player.getName().getString());
    }

    /**
     * Play busy sound at snail location - AMBIENT sound that affects blockstate
     */
    public void playBusySoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, SNAIL_CALL_BUSY_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played ambient busy sound at snail position " + snailPos);
    }

    /**
     * Play busy sound for a player with a handheld snail
     */
    public void playBusySoundForPlayer(ServerPlayer player) {
        playPlayerSound(player, SNAIL_CALL_BUSY_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played busy sound for player " + player.getName().getString());
    }

    // =================== CORE SOUND METHODS WITH FASTER TIMING ===================

    /**
     * Enhanced play one-shot sound with faster cleanup for ambient sounds
     */
    private void playSnailPositionSound(Level level, BlockPos snailPos, ResourceLocation soundLocation, SoundCategory category) {
        try {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);
            float volume = 1.0f;
            float pitch = 1.0f;

            level.playSound(
                    null,
                    snailPos.getX() + 0.5,
                    snailPos.getY() + 0.5,
                    snailPos.getZ() + 0.5,
                    soundEvent,
                    SoundSource.BLOCKS,
                    volume,
                    pitch
            );

            // Handle ambient sound tracking for blockstate updates with faster cleanup
            if (category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
                activeAmbientSounds.computeIfAbsent(snailPos, k -> ConcurrentHashMap.newKeySet()).add(category);
                notifyBlockstateUpdate(snailPos, true);

                // Schedule faster removal for one-shot sounds
                scheduler.schedule(() -> {
                    Set<SoundCategory> sounds = activeAmbientSounds.get(snailPos);
                    if (sounds != null) {
                        sounds.remove(category);
                        if (sounds.isEmpty()) {
                            activeAmbientSounds.remove(snailPos);
                            notifyBlockstateUpdate(snailPos, false);
                        }
                    }
                }, 1000, TimeUnit.MILLISECONDS); // Reduced from 2000ms to 1000ms for faster cleanup
            }

            System.out.println("CallSoundManager: Played spatial sound " + soundLocation + " (" + category + ") at " + snailPos);

        } catch (Exception e) {
            System.err.println("CallSoundManager: Failed to play snail position sound " + soundLocation + " at " + snailPos);
            e.printStackTrace();
        }
    }

    /**
     * Enhanced repeating sound with customizable interval
     */
    private void playSnailPositionRepeatingSound(Level level, BlockPos snailPos, ResourceLocation soundLocation,
                                                 UUID soundId, SoundType type, SoundCategory category, int intervalMs) {
        // Stop any existing sounds of this type at this location
        stopSnailPositionSounds(snailPos, type);

        // Create sound instance with category
        SoundInstance instance = new SoundInstance(snailPos, level, soundLocation, type, category);
        activeSounds.put(soundId, instance);

        // Track ambient sounds for blockstate updates
        if (category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
            activeAmbientSounds.computeIfAbsent(snailPos, k -> ConcurrentHashMap.newKeySet()).add(category);
            notifyBlockstateUpdate(snailPos, true);
        }

        // Play the sound initially
        playSnailPositionSoundDirect(level, snailPos, soundLocation);

        // Schedule repeating playback with custom interval
        instance.stopTask = scheduler.scheduleAtFixedRate(() -> {
            if (activeSounds.containsKey(soundId)) {
                playSnailPositionSoundDirect(level, snailPos, soundLocation);
            } else {
                System.out.println("CallSoundManager: Repeating sound " + soundLocation + " at " + snailPos + " was stopped, cleaning up");
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        System.out.println("CallSoundManager: Started repeating sound " + soundLocation + " (" + category + ") at " + snailPos + " with " + intervalMs + "ms interval");
    }

    /**
     * Play a one-shot sound at player's position
     */
    private void playPlayerSound(ServerPlayer player, ResourceLocation soundLocation, SoundCategory category) {
        try {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);

            // Play sound at player's position
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY() + 1.5, // At head level
                    player.getZ(),
                    soundEvent,
                    SoundSource.PLAYERS,
                    1.0f, // volume
                    1.0f  // pitch
            );

            // Also send directly to the player to ensure they hear it
            player.playNotifySound(soundEvent, SoundSource.PLAYERS, 1.0f, 1.0f);

        } catch (Exception e) {
            System.err.println("CallSoundManager: Failed to play sound " + soundLocation + " for player " + player.getName().getString());
            e.printStackTrace();
        }
    }

    /**
     * Play a repeating sound that follows a player (for handheld snails)
     */
    private void playPlayerFollowingRepeatingSound(ServerPlayer player, ResourceLocation soundLocation,
                                                   UUID soundId, SoundType type, SoundCategory category) {
        // Stop any existing sounds of this type for this player
        stopPlayerSounds(player.getUUID(), type);

        // Create sound instance (use player UUID as position key)
        PlayerSoundInstance instance = new PlayerSoundInstance(player.getUUID(), player.level(),
                soundLocation, type, category);
        activeSounds.put(soundId, instance);

        // Play the sound initially
        playPlayerSound(player, soundLocation, category);

        // Schedule repeating playback
        instance.stopTask = scheduler.scheduleAtFixedRate(() -> {
            ServerPlayer currentPlayer = getPlayerById(player.getUUID());
            if (currentPlayer != null && activeSounds.containsKey(soundId)) {
                playPlayerSound(currentPlayer, soundLocation, category);
            } else {
                // Player disconnected or sound stopped
                activeSounds.remove(soundId);
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS); // 2 second intervals for ringtone

        System.out.println("CallSoundManager: Started repeating sound " + soundLocation + " for player " + player.getName().getString());
    }

    /**
     * Default repeating sound method with faster timing
     */
    private void playSnailPositionRepeatingSound(Level level, BlockPos snailPos, ResourceLocation soundLocation,
                                                 UUID soundId, SoundType type, SoundCategory category) {
        playSnailPositionRepeatingSound(level, snailPos, soundLocation, soundId, type, category, 1500); // Default faster interval
    }

    /**
     * Direct sound playback without category tracking (for repeating sounds)
     */
    private void playSnailPositionSoundDirect(Level level, BlockPos snailPos, ResourceLocation soundLocation) {
        try {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);
            level.playSound(null, snailPos.getX() + 0.5, snailPos.getY() + 0.5, snailPos.getZ() + 0.5, soundEvent, SoundSource.BLOCKS, 1.0f, 1.0f);
        } catch (Exception e) {
            System.err.println("CallSoundManager: Failed to play direct sound " + soundLocation + " at " + snailPos);
            e.printStackTrace();
        }
    }

    // =================== ENHANCED SOUND CLEANUP WITH BLOCKSTATE UPDATES ===================

    /**
     * Stop sounds with proper blockstate cleanup
     */
    public void stopSnailPositionSounds(BlockPos snailPos, SoundType type) {
        Iterator<Map.Entry<UUID, SoundInstance>> iterator = activeSounds.entrySet().iterator();
        int stoppedCount = 0;
        boolean hadAmbientSounds = false;

        while (iterator.hasNext()) {
            Map.Entry<UUID, SoundInstance> entry = iterator.next();
            SoundInstance instance = entry.getValue();

            if (instance.snailPosition.equals(snailPos) && instance.type == type) {
                if (instance.stopTask != null) {
                    instance.stopTask.cancel(false);
                }

                if (instance.category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
                    hadAmbientSounds = true;
                }

                iterator.remove();
                stoppedCount++;
            }
        }

        // Update blockstate if we stopped ambient sounds
        if (hadAmbientSounds) {
            updateAmbientSoundTracking(snailPos);
        }

        if (stoppedCount > 0) {
            System.out.println("CallSoundManager: Stopped " + stoppedCount + " " + type + " sound(s) at snail position " + snailPos);
        }
    }

    public void stopAllSnailPositionSounds(BlockPos snailPos) {
        Iterator<Map.Entry<UUID, SoundInstance>> iterator = activeSounds.entrySet().iterator();
        int stoppedCount = 0;
        boolean hadAmbientSounds = false;

        while (iterator.hasNext()) {
            Map.Entry<UUID, SoundInstance> entry = iterator.next();
            SoundInstance instance = entry.getValue();

            if (instance.snailPosition.equals(snailPos)) {
                if (instance.stopTask != null) {
                    instance.stopTask.cancel(false);
                }

                if (instance.category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
                    hadAmbientSounds = true;
                }

                iterator.remove();
                stoppedCount++;
            }
        }

        // Clean up ambient sound tracking
        if (hadAmbientSounds) {
            activeAmbientSounds.remove(snailPos);
            notifyBlockstateUpdate(snailPos, false);
        }

        if (stoppedCount > 0) {
            System.out.println("CallSoundManager: Stopped all " + stoppedCount + " sound(s) at snail position " + snailPos);
        }
    }

    /**
     * Stop all sounds of a specific type for a player
     */
    private void stopPlayerSounds(UUID playerId, SoundType type) {
        Iterator<Map.Entry<UUID, SoundInstance>> iterator = activeSounds.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, SoundInstance> entry = iterator.next();

            if (entry.getValue() instanceof PlayerSoundInstance) {
                PlayerSoundInstance instance = (PlayerSoundInstance) entry.getValue();
                if (instance.playerId.equals(playerId) && instance.type == type) {
                    if (instance.stopTask != null) {
                        instance.stopTask.cancel(false);
                    }
                    iterator.remove();
                }
            }
        }
    }

    // =================== BLOCKSTATE UPDATE METHODS ===================

    /**
     * Update ambient sound tracking after stopping sounds
     */
    private void updateAmbientSoundTracking(BlockPos snailPos) {
        // Check if any ambient sounds are still active at this position
        boolean hasAmbientSounds = activeSounds.values().stream()
                .anyMatch(instance -> instance.snailPosition.equals(snailPos) &&
                        instance.category == SoundCategory.AMBIENT_SNAIL_SOUNDS);

        if (!hasAmbientSounds) {
            activeAmbientSounds.remove(snailPos);
            notifyBlockstateUpdate(snailPos, false);
        }
    }

    /**
     * Notify registered callbacks about blockstate changes
     */
    private void notifyBlockstateUpdate(BlockPos pos, boolean hasAmbientSound) {
        for (BlockstateUpdateCallback callback : blockstateCallbacks) {
            try {
                callback.onSoundStateChanged(pos, hasAmbientSound);
            } catch (Exception e) {
                System.err.println("CallSoundManager: Error in blockstate callback: " + e.getMessage());
            }
        }
    }

    // =================== PUBLIC QUERY METHODS FOR BLOCKSTATE ===================

    /**
     * Check if position has ambient sounds playing (for blockstate)
     */
    public boolean hasAmbientSoundsAtPosition(BlockPos pos) {
        return activeAmbientSounds.containsKey(pos) && !activeAmbientSounds.get(pos).isEmpty();
    }

    /**
     * Get all positions with ambient sounds (for debugging)
     */
    public Set<BlockPos> getPositionsWithAmbientSounds() {
        return new HashSet<>(activeAmbientSounds.keySet());
    }

    // =================== EXISTING METHODS (enhanced with blockstate updates) ===================

    /**
     * Stop ringtone for a specific player (enhanced version)
     */
    public void stopRingTone(ServerPlayer player) {
        // Stop both positional ringtones (near blocks) and player-following ringtones
        Iterator<Map.Entry<UUID, SoundInstance>> iterator = activeSounds.entrySet().iterator();
        int stoppedCount = 0;

        while (iterator.hasNext()) {
            Map.Entry<UUID, SoundInstance> entry = iterator.next();
            SoundInstance instance = entry.getValue();

            if (instance.type == SoundType.RING_TONE) {
                boolean shouldStop = false;

                // Check if it's a player-following sound
                if (instance instanceof PlayerSoundInstance) {
                    PlayerSoundInstance playerInstance = (PlayerSoundInstance) instance;
                    if (playerInstance.playerId.equals(player.getUUID())) {
                        shouldStop = true;
                    }
                }
                // Check if it's a positional sound near the player
                else if (instance.snailPosition != null) {
                    double distance = Math.sqrt(
                            Math.pow(instance.snailPosition.getX() + 0.5 - player.getX(), 2) +
                                    Math.pow(instance.snailPosition.getY() + 0.5 - player.getY(), 2) +
                                    Math.pow(instance.snailPosition.getZ() + 0.5 - player.getZ(), 2)
                    );

                    if (distance <= VoiceChatConstants.getSnailInteractionRange()) {
                        shouldStop = true;
                    }
                }

                if (shouldStop) {
                    if (instance.stopTask != null) {
                        instance.stopTask.cancel(false);
                    }
                    iterator.remove();
                    stoppedCount++;
                }
            }
        }

        if (stoppedCount > 0) {
            System.out.println("CallSoundManager: Stopped " + stoppedCount + " ringtone(s) for player " + player.getName().getString());
        }
    }

    public void cleanup() {
        System.out.println("CallSoundManager: Cleaning up " + activeSounds.size() + " active sounds");

        for (SoundInstance instance : activeSounds.values()) {
            if (instance.stopTask != null) {
                instance.stopTask.cancel(false);
            }
        }

        activeSounds.clear();
        activeAmbientSounds.clear();
        blockstateCallbacks.clear();
        scheduler.shutdown();

        System.out.println("CallSoundManager: Cleanup complete");
    }

    /**
     * Extended sound instance for player-following sounds
     */
    private static class PlayerSoundInstance extends SoundInstance {
        final UUID playerId;

        PlayerSoundInstance(UUID playerId, Level level, ResourceLocation soundLocation,
                            SoundType type, SoundCategory category) {
            super(null, level, soundLocation, type, category); // null position for player sounds
            this.playerId = playerId;
        }
    }

    /**
     * Get player by UUID helper
     */
    private ServerPlayer getPlayerById(UUID playerId) {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(playerId);
    }

    // Utility methods
    public Map<UUID, String> getActiveSoundsInfo() {
        Map<UUID, String> info = new HashMap<>();
        for (Map.Entry<UUID, SoundInstance> entry : activeSounds.entrySet()) {
            SoundInstance instance = entry.getValue();
            long duration = System.currentTimeMillis() - instance.startTime;
            info.put(entry.getKey(),
                    instance.soundLocation + " (" + instance.type + "/" + instance.category + ") at " + instance.snailPosition + " - " + duration + "ms");
        }
        return info;
    }

    public int getActiveSoundCount(SoundType type) {
        return (int) activeSounds.values().stream()
                .filter(instance -> instance.type == type)
                .count();
    }

    public Set<BlockPos> getActiveSoundPositions() {
        return activeSounds.values().stream()
                .map(instance -> instance.snailPosition)
                .collect(java.util.stream.Collectors.toSet());
    }

    public boolean hasSoundsAtPosition(BlockPos pos) {
        return activeSounds.values().stream()
                .anyMatch(instance -> instance.snailPosition.equals(pos));
    }

    public boolean hasRingtonesAtPosition(BlockPos pos) {
        return activeSounds.values().stream()
                .anyMatch(instance -> instance.snailPosition.equals(pos) && instance.type == SoundType.RING_TONE);
    }
}