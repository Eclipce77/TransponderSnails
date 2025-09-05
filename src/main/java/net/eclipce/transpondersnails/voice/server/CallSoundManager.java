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
 * Enhanced with sound categorization for blockstate texture changes
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

    // NEW: Track ambient snail sounds per position for blockstate updates
    private final Map<BlockPos, Set<SoundCategory>> activeAmbientSounds = new ConcurrentHashMap<>();

    // NEW: Sound categorization for blockstate logic
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
        final SoundCategory category;  // NEW
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

    // NEW: Callback interface for blockstate updates - this is the interface your BlockEntity implements
    public interface BlockstateUpdateCallback {
        void onSoundStateChanged(BlockPos pos, boolean hasAmbientSound);
    }

    private final Set<BlockstateUpdateCallback> blockstateCallbacks = ConcurrentHashMap.newKeySet();

    // NEW: Register for blockstate updates
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

    // =================== SPATIAL AUDIO METHODS WITH CATEGORIES ===================

    /**
     * Play ringtone at snail location - AMBIENT sound that affects blockstate
     */
    public void playLocationalRingTone(ServerPlayer player, BlockPos snailLocation) {
        UUID soundId = UUID.randomUUID();
        playSnailPositionRepeatingSound(player.level(), snailLocation, SNAIL_RINGING_SOUND, soundId, SoundType.RING_TONE, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Started ambient ringtone at snail position " + snailLocation);
    }

    public void playLocationalRingToneAtPosition(Level level, BlockPos snailLocation) {
        UUID soundId = UUID.randomUUID();
        playSnailPositionRepeatingSound(level, snailLocation, SNAIL_RINGING_SOUND, soundId, SoundType.RING_TONE, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Started ambient ringtone at snail position " + snailLocation + " (no player required)");
    }

    /**
     * Play connection sound at snail location - AMBIENT sound that affects blockstate
     */
    public void playCallConnectedSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, SNAIL_CALL_CONNECTION_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played ambient connection sound at snail position " + snailPos);
    }

    /**
     * Play disconnection sound at snail location - AMBIENT sound that affects blockstate
     */
    public void playCallDisconnectedSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, SNAIL_CALL_DISCONNECTED_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played ambient disconnection sound at snail position " + snailPos);
    }

    /**
     * Play pick up sound at snail location - INTERACTION sound that does NOT affect blockstate
     */
    public void playPickUpSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, HANDSET_CALL_PICK_UP_SOUND, SoundCategory.INTERACTION_SOUNDS);
        System.out.println("CallSoundManager: Played interaction pick up sound at snail position " + snailPos);
    }

    /**
     * Play hang up sound at snail location - INTERACTION sound that does NOT affect blockstate
     */
    public void playHangUpSoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, HANDSET_CALL_HANG_UP_SOUND, SoundCategory.INTERACTION_SOUNDS);
        System.out.println("CallSoundManager: Played interaction hang up sound at snail position " + snailPos);
    }

    /**
     * Play busy sound at snail location - AMBIENT sound that affects blockstate
     */
    public void playBusySoundAtSnail(ServerPlayer player, BlockPos snailPos) {
        playSnailPositionSound(player.level(), snailPos, SNAIL_CALL_BUSY_SOUND, SoundCategory.AMBIENT_SNAIL_SOUNDS);
        System.out.println("CallSoundManager: Played ambient busy sound at snail position " + snailPos);
    }

    // =================== CORE SOUND METHODS WITH CATEGORY TRACKING ===================

    /**
     * Play a one-shot sound with category tracking for blockstate updates
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

            // NEW: Handle ambient sound tracking for blockstate updates
            if (category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
                // Add short-duration tracking for one-shot ambient sounds
                activeAmbientSounds.computeIfAbsent(snailPos, k -> ConcurrentHashMap.newKeySet()).add(category);
                notifyBlockstateUpdate(snailPos, true);

                // Schedule removal after sound duration (estimated)
                scheduler.schedule(() -> {
                    Set<SoundCategory> sounds = activeAmbientSounds.get(snailPos);
                    if (sounds != null) {
                        sounds.remove(category);
                        if (sounds.isEmpty()) {
                            activeAmbientSounds.remove(snailPos);
                            notifyBlockstateUpdate(snailPos, false);
                        }
                    }
                }, 2000, TimeUnit.MILLISECONDS); // 2 second estimated duration for one-shot sounds
            }

            System.out.println("CallSoundManager: Played spatial sound " + soundLocation + " (" + category + ") at " + snailPos);

        } catch (Exception e) {
            System.err.println("CallSoundManager: Failed to play snail position sound " + soundLocation + " at " + snailPos);
            e.printStackTrace();
        }
    }

    /**
     * Play a repeating sound with category tracking
     */
    private void playSnailPositionRepeatingSound(Level level, BlockPos snailPos, ResourceLocation soundLocation, UUID soundId, SoundType type, SoundCategory category) {
        // Stop any existing sounds of this type at this location
        stopSnailPositionSounds(snailPos, type);

        // Create sound instance with category
        SoundInstance instance = new SoundInstance(snailPos, level, soundLocation, type, category);
        activeSounds.put(soundId, instance);

        // NEW: Track ambient sounds for blockstate updates
        if (category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
            activeAmbientSounds.computeIfAbsent(snailPos, k -> ConcurrentHashMap.newKeySet()).add(category);
            notifyBlockstateUpdate(snailPos, true);
        }

        // Play the sound initially
        playSnailPositionSoundDirect(level, snailPos, soundLocation);

        // Schedule repeating playback
        instance.stopTask = scheduler.scheduleAtFixedRate(() -> {
            if (activeSounds.containsKey(soundId)) {
                playSnailPositionSoundDirect(level, snailPos, soundLocation);
            } else {
                System.out.println("CallSoundManager: Repeating sound " + soundLocation + " at " + snailPos + " was stopped, cleaning up");
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS);

        System.out.println("CallSoundManager: Started repeating sound " + soundLocation + " (" + category + ") at " + snailPos);
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

                // NEW: Track if we're stopping ambient sounds
                if (instance.category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
                    hadAmbientSounds = true;
                }

                iterator.remove();
                stoppedCount++;
            }
        }

        // NEW: Update blockstate if we stopped ambient sounds
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

        // NEW: Clean up ambient sound tracking
        if (hadAmbientSounds) {
            activeAmbientSounds.remove(snailPos);
            notifyBlockstateUpdate(snailPos, false);
        }

        if (stoppedCount > 0) {
            System.out.println("CallSoundManager: Stopped all " + stoppedCount + " sound(s) at snail position " + snailPos);
        }
    }

    // =================== NEW BLOCKSTATE UPDATE METHODS ===================

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

    public void stopRingTone(ServerPlayer player) {
        Iterator<Map.Entry<UUID, SoundInstance>> iterator = activeSounds.entrySet().iterator();
        int stoppedCount = 0;

        while (iterator.hasNext()) {
            Map.Entry<UUID, SoundInstance> entry = iterator.next();
            SoundInstance instance = entry.getValue();

            if (instance.type == SoundType.RING_TONE) {
                double distance = Math.sqrt(
                        Math.pow(instance.snailPosition.getX() + 0.5 - player.getX(), 2) +
                                Math.pow(instance.snailPosition.getY() + 0.5 - player.getY(), 2) +
                                Math.pow(instance.snailPosition.getZ() + 0.5 - player.getZ(), 2)
                );

                if (distance <= VoiceChatConstants.SNAIL_INTERACTION_RANGE) {
                    if (instance.stopTask != null) {
                        instance.stopTask.cancel(false);
                    }

                    // Update blockstate tracking
                    if (instance.category == SoundCategory.AMBIENT_SNAIL_SOUNDS) {
                        updateAmbientSoundTracking(instance.snailPosition);
                    }

                    iterator.remove();
                    stoppedCount++;
                }
            }
        }

        if (stoppedCount > 0) {
            System.out.println("CallSoundManager: Stopped " + stoppedCount + " nearby ringtone(s) for player " + player.getName().getString());
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

    // Existing utility methods remain unchanged
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