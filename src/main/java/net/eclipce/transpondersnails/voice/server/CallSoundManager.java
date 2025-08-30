package net.eclipce.transpondersnails.voice.server;

import net.eclipce.transpondersnails.sound.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages sound effects for Transponder Snail calls
 * Handles ringtones, connection sounds, and call end sounds
 */
public class CallSoundManager {

    // Use ModSounds for sound events (excluding DIAL_BUTTON & CLEAR_BUTTON)
    public static final ResourceLocation RING_CALLER_SOUND = ModSounds.SNAIL_RINGING.getId();
    public static final ResourceLocation RING_RECIPIENT_SOUND = ModSounds.SNAIL_RINGING.getId();
    public static final ResourceLocation CALL_CONNECTED_SOUND = ModSounds.SNAIL_CONNECTED.getId();
    public static final ResourceLocation CALL_DISCONNECTED_SOUND = ModSounds.SNAIL_DISCONNECTED.getId();
    public static final ResourceLocation CALL_BUSY_SOUND = ModSounds.SNAIL_BUSY.getId();

    // Pick up and hang up sounds
    public static final ResourceLocation CALL_PICK_UP_SOUND = ModSounds.SNAIL_PICK_UP.getId();
    public static final ResourceLocation CALL_HANG_UP_SOUND = ModSounds.SNAIL_HANG_UP.getId();

    // Tracking active sounds
    private final Map<UUID, SoundInstance> activeSounds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Inner class to track sound instances
    private static class SoundInstance {
        final UUID playerId;
        final ResourceLocation soundLocation;
        final SoundType type;
        final long startTime;
        java.util.concurrent.ScheduledFuture<?> stopTask;

        SoundInstance(UUID playerId, ResourceLocation soundLocation, SoundType type) {
            this.playerId = playerId;
            this.soundLocation = soundLocation;
            this.type = type;
            this.startTime = System.currentTimeMillis();
        }
    }

    public enum SoundType {
        RING_TONE,      // Repeating ring sound
        ONE_SHOT,       // Single play sound
        AMBIENT         // Continuous ambient sound
    }

    /**
     * Play ringtone for caller (waiting sound)
     */
    public void playCallerRingTone(ServerPlayer caller) {
        UUID soundId = UUID.randomUUID();
        playRepeatingSound(caller, RING_CALLER_SOUND, soundId, SoundType.RING_TONE);
    }

    /**
     * Play ringtone for recipient (incoming call sound)
     */
    public void playRecipientRingTone(ServerPlayer recipient) {
        UUID soundId = UUID.randomUUID();
        playRepeatingSound(recipient, RING_RECIPIENT_SOUND, soundId, SoundType.RING_TONE);
    }

    /**
     * Play ringtone for locational calls (at the snail block)
     */
    public void playLocationalRingTone(ServerPlayer player, BlockPos snailLocation) {
        UUID soundId = UUID.randomUUID();
        playLocationalRepeatingSound(player, snailLocation, RING_RECIPIENT_SOUND, soundId, SoundType.RING_TONE);
    }

    /**
     * Stop ringtones for a specific player
     */
    public void stopRingTone(ServerPlayer player) {
        stopPlayerSounds(player.getUUID(), SoundType.RING_TONE);
    }

    /**
     * Play call connected sound (one-shot)
     */
    public void playCallConnectedSound(ServerPlayer player) {
        playOneShotSound(player, CALL_CONNECTED_SOUND);
    }

    /**
     * Play call ended sound (one-shot)
     */
    public void playCallDisconnectedSound(ServerPlayer player) {
        playOneShotSound(player, CALL_DISCONNECTED_SOUND);
    }

    /**
     * Play busy signal sound (one-shot)
     */
    public void playBusySound(ServerPlayer player) {
        playOneShotSound(player, CALL_BUSY_SOUND);
    }

    /**
     * Play call pick up sound (one-shot)
     */
    public void playPickUpSound(ServerPlayer player) {
        playOneShotSound(player, CALL_PICK_UP_SOUND);
    }

    /**
     * Play call hang up sound (one-shot)
     */
    public void playHangUpSound(ServerPlayer player) {
        playOneShotSound(player, CALL_HANG_UP_SOUND);
    }

    /**
     * Play a one-shot sound effect
     */
    private void playOneShotSound(ServerPlayer player, ResourceLocation soundLocation) {
        try {
            // Create SoundEvent from ResourceLocation
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);

            // Play sound at player's location
            player.level().playSound(
                    null, // null = all players nearby can hear
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    soundEvent,
                    SoundSource.PLAYERS,
                    1.0f, // volume
                    1.0f  // pitch
            );

            // Also send directly to player for guarantee they hear it
            ClientboundSoundPacket packet = new ClientboundSoundPacket(
                    net.minecraft.core.Holder.direct(soundEvent),
                    SoundSource.PLAYERS,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    1.0f,
                    1.0f,
                    player.getRandom().nextLong()
            );
            player.connection.send(packet);

        } catch (Exception e) {
            System.err.println("Failed to play sound " + soundLocation + " for player " + player.getName().getString());
            e.printStackTrace();
        }
    }

    /**
     * Play a repeating sound (like ringtones)
     */
    private void playRepeatingSound(ServerPlayer player, ResourceLocation soundLocation, UUID soundId, SoundType type) {
        UUID playerId = player.getUUID();

        // Stop any existing ringtones for this player
        stopPlayerSounds(playerId, type);

        // Create sound instance
        SoundInstance instance = new SoundInstance(playerId, soundLocation, type);
        activeSounds.put(soundId, instance);

        // Play the sound initially
        playOneShotSound(player, soundLocation);

        // Schedule repeating playback (every 2 seconds for ringtone)
        instance.stopTask = scheduler.scheduleAtFixedRate(() -> {
            ServerPlayer currentPlayer = getPlayerById(playerId);
            if (currentPlayer != null && activeSounds.containsKey(soundId)) {
                playOneShotSound(currentPlayer, soundLocation);
            } else {
                // Player disconnected or sound stopped, clean up
                activeSounds.remove(soundId);
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS); // 2 second intervals
    }

    /**
     * Play a repeating sound at a specific location (for placed snails)
     */
    private void playLocationalRepeatingSound(ServerPlayer player, BlockPos location, ResourceLocation soundLocation, UUID soundId, SoundType type) {
        UUID playerId = player.getUUID();

        // Stop any existing ringtones for this player
        stopPlayerSounds(playerId, type);

        // Create sound instance
        SoundInstance instance = new SoundInstance(playerId, soundLocation, type);
        activeSounds.put(soundId, instance);

        // Play the sound initially at the location
        playLocationalSound(player, location, soundLocation);

        // Schedule repeating playback
        instance.stopTask = scheduler.scheduleAtFixedRate(() -> {
            ServerPlayer currentPlayer = getPlayerById(playerId);
            if (currentPlayer != null && activeSounds.containsKey(soundId)) {
                playLocationalSound(currentPlayer, location, soundLocation);
            } else {
                activeSounds.remove(soundId);
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    /**
     * Play sound at specific location
     */
    private void playLocationalSound(ServerPlayer player, BlockPos location, ResourceLocation soundLocation) {
        try {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);

            player.level().playSound(
                    null,
                    location.getX() + 0.5,
                    location.getY() + 0.5,
                    location.getZ() + 0.5,
                    soundEvent,
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f
            );
        } catch (Exception e) {
            System.err.println("Failed to play locational sound " + soundLocation + " at " + location);
            e.printStackTrace();
        }
    }

    /**
     * Stop all sounds of a specific type for a player
     */
    private void stopPlayerSounds(UUID playerId, SoundType type) {
        Iterator<Map.Entry<UUID, SoundInstance>> iterator = activeSounds.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, SoundInstance> entry = iterator.next();
            SoundInstance instance = entry.getValue();

            if (instance.playerId.equals(playerId) && instance.type == type) {
                // Cancel scheduled task
                if (instance.stopTask != null) {
                    instance.stopTask.cancel(false);
                }

                // Send stop sound packet to player
                ServerPlayer player = getPlayerById(playerId);
                if (player != null) {
                    ClientboundStopSoundPacket stopPacket = new ClientboundStopSoundPacket(
                            instance.soundLocation,
                            SoundSource.PLAYERS
                    );
                    player.connection.send(stopPacket);
                }

                iterator.remove();
            }
        }
    }

    /**
     * Stop all sounds for a player (when they disconnect or leave call)
     */
    public void stopAllSoundsForPlayer(UUID playerId) {
        Iterator<Map.Entry<UUID, SoundInstance>> iterator = activeSounds.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, SoundInstance> entry = iterator.next();
            SoundInstance instance = entry.getValue();

            if (instance.playerId.equals(playerId)) {
                if (instance.stopTask != null) {
                    instance.stopTask.cancel(false);
                }

                ServerPlayer player = getPlayerById(playerId);
                if (player != null) {
                    ClientboundStopSoundPacket stopPacket = new ClientboundStopSoundPacket(
                            instance.soundLocation,
                            SoundSource.PLAYERS
                    );
                    player.connection.send(stopPacket);
                }

                iterator.remove();
            }
        }
    }

    /**
     * Get player by UUID
     */
    private ServerPlayer getPlayerById(UUID playerId) {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(playerId);
    }

    /**
     * Cleanup all active sounds (for server shutdown)
     */
    public void cleanup() {
        for (SoundInstance instance : activeSounds.values()) {
            if (instance.stopTask != null) {
                instance.stopTask.cancel(false);
            }
        }
        activeSounds.clear();
        scheduler.shutdown();
    }

    /**
     * Get debug info about active sounds
     */
    public Map<UUID, String> getActiveSoundsInfo() {
        Map<UUID, String> info = new HashMap<>();
        for (Map.Entry<UUID, SoundInstance> entry : activeSounds.entrySet()) {
            SoundInstance instance = entry.getValue();
            long duration = System.currentTimeMillis() - instance.startTime;
            info.put(entry.getKey(), instance.soundLocation + " (" + instance.type + ") - " + duration + "ms");
        }
        return info;
    }
}