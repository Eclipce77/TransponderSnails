package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ForgeVoicechatPlugin
public class TransponderCallManager implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "transpondersnails";
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private final Map<UUID, TransponderCall> activeCalls = new ConcurrentHashMap<>(); // callId -> call
    private final Map<UUID, UUID> playerToCall = new ConcurrentHashMap<>(); // playerId -> callId
    private final Map<UUID, UUID> callRequests = new ConcurrentHashMap<>(); // caller -> callId
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private VoicechatServerApi voicechatApi;
    private AudioChannelManager audioChannelManager;
    private PlayerAudioForwarder audioForwarder;
    private CallSoundManager soundManager;

    // Constructor for direct instantiation
    public TransponderCallManager(VoicechatServerApi api) {
        initialize(api);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        initialize(event.getVoicechat()); // Fixed: use getVoicechat() instead of getServerApi()
    }

    private void initialize(VoicechatServerApi api) {
        this.voicechatApi = api;
        this.audioChannelManager = new AudioChannelManager(api);
        this.audioForwarder = new PlayerAudioForwarder(api);
        this.soundManager = new CallSoundManager();

        System.out.println("TransponderCallManager: Initialized with audio management systems");
    }

    /**
     * Create a new call with full audio support
     */
    public UUID createCall(ServerPlayer initiator, List<ServerPlayer> invitees, CallType callType) {
        if (voicechatApi == null || audioChannelManager == null || audioForwarder == null) {
            System.err.println("TransponderCallManager: Cannot create call - components not initialized");
            return null;
        }

        UUID callId = UUID.randomUUID();

        try {
            // Create the call with audio management components
            TransponderCall call = new TransponderCall(
                    callId,
                    callType,
                    voicechatApi,
                    VoiceChatConstants.SNAIL_VOLUME_CATEGORY,
                    audioChannelManager,
                    audioForwarder
            );

            // Add initiator as host
            if (!call.addParticipant(initiator, ParticipantRole.HOST)) {
                System.err.println("TransponderCallManager: Failed to add initiator to call");
                return null;
            }

            // Store call and player mapping
            activeCalls.put(callId, call);
            playerToCall.put(initiator.getUUID(), callId);

            // Send invites to other players
            for (ServerPlayer invitee : invitees) {
                sendCallInvite(initiator, invitee, callId);
            }

            System.out.println("TransponderCallManager: Created " + callType + " call " + callId + " with " + invitees.size() + " invitees");
            return callId;

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Failed to create call: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Initiate a simple 2-person call
     */
    public boolean initiateCall(ServerPlayer caller, ServerPlayer callee) {
        return initiateCall(caller, callee, null, CallType.PERSONAL);
    }

    /**
     * Initiate a call from a specific location (block entity)
     */
    public boolean initiateCall(ServerPlayer caller, ServerPlayer callee, BlockPos snailLocation) {
        return initiateCall(caller, callee, snailLocation, CallType.LOCATIONAL);
    }

    /**
     * Initiate a handheld call with proximity audio
     */
    public boolean initiateHandheldCall(ServerPlayer caller, ServerPlayer callee) {
        return initiateCall(caller, callee, caller.blockPosition(), CallType.HANDHELD);
    }

    /**
     * Main initiate call method with audio support
     */
    private boolean initiateCall(ServerPlayer caller, ServerPlayer callee, BlockPos location, CallType callType) {
        if (isInCall(caller.getUUID()) || isInCall(callee.getUUID())) {
            caller.sendSystemMessage(Component.literal("Someone is already in a call!"));
            if (soundManager != null) {
                soundManager.playBusySound(caller);
            }
            return false;
        }

        try {
            UUID callId = createCall(caller, List.of(callee), callType);

            if (callId == null) {
                caller.sendSystemMessage(Component.literal("Failed to create call!"));
                return false;
            }

            // Set location for locational or handheld calls
            if (location != null) {
                TransponderCall call = activeCalls.get(callId);
                if (call != null) {
                    call.setLocation(caller.serverLevel(), location);
                }
            }

            // Play calling sound for initiator
            if (soundManager != null) {
                soundManager.playCallerRingTone(caller);
            }

            System.out.println("TransponderCallManager: Initiated " + callType + " call from " +
                    caller.getName().getString() + " to " + callee.getName().getString());
            return true;

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Failed to initiate call: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Send call invitation with sound effects
     */
    private void sendCallInvite(ServerPlayer caller, ServerPlayer invitee, UUID callId) {
        callRequests.put(invitee.getUUID(), callId);
        invitee.sendSystemMessage(Component.literal(
                caller.getName().getString() + " is calling you! Use /call accept or right-click your Transponder Snail to answer."
        ));

        // Play ringing sound for recipient
        if (soundManager != null) {
            soundManager.playRecipientRingTone(invitee);
        }

        // Auto-timeout after configured time
        scheduleCallTimeout(invitee.getUUID(), callId, VoiceChatConstants.CALL_TIMEOUT_MS);
    }

    /**
     * Accept a call invitation with full audio setup
     */
    public boolean acceptCall(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UUID callId = callRequests.get(playerId);

        if (callId == null) {
            player.sendSystemMessage(Component.literal("No incoming call!"));
            return false;
        }

        TransponderCall call = activeCalls.get(callId);
        if (call == null) {
            callRequests.remove(playerId);
            player.sendSystemMessage(Component.literal("Call no longer exists!"));
            return false;
        }

        try {
            // Stop ringing sounds
            if (soundManager != null) {
                soundManager.stopRingTone(player);
            }

            // Add participant to call with audio setup
            if (call.addParticipant(player, ParticipantRole.PARTICIPANT)) {
                callRequests.remove(playerId);
                playerToCall.put(playerId, callId);

                // Play connection sounds
                if (soundManager != null) {
                    soundManager.playCallConnectedSound(player);
                    soundManager.playPickUpSound(player);
                }

                // Stop caller's ring tone and play connected sound
                for (UUID participantId : call.getParticipants()) {
                    if (!participantId.equals(playerId)) {
                        ServerPlayer participant = getPlayerById(participantId);
                        if (participant != null && soundManager != null) {
                            soundManager.stopRingTone(participant);
                            soundManager.playCallConnectedSound(participant);
                        }
                    }
                }

                System.out.println("TransponderCallManager: " + player.getName().getString() + " accepted call " + callId);
                return true;
            }

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error accepting call: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Leave a call with full cleanup
     */
    public void leaveCall(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UUID callId = playerToCall.get(playerId);

        if (callId == null) {
            player.sendSystemMessage(Component.literal("Not in a call!"));
            return;
        }

        try {
            TransponderCall call = activeCalls.get(callId);
            if (call != null) {
                // Play hang up sound
                if (soundManager != null) {
                    soundManager.playHangUpSound(player);
                }

                // Remove participant (handles audio cleanup)
                call.removeParticipant(player);
                playerToCall.remove(playerId);

                // End call if only one person left or host left
                if (call.getParticipantCount() <= 1 || call.hostLeft()) {
                    endCall(callId);
                } else {
                    // Notify remaining participants
                    call.broadcastMessage(player.getName().getString() + " left the call");

                    // Play end sound for remaining participants
                    if (soundManager != null) {
                        for (UUID participantId : call.getParticipants()) {
                            ServerPlayer participant = getPlayerById(participantId);
                            if (participant != null) {
                                soundManager.playCallDisconnectedSound(participant);
                            }
                        }
                    }
                }
            }

            System.out.println("TransponderCallManager: " + player.getName().getString() + " left call " + callId);

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error leaving call: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * End entire call with full cleanup
     */
    public void endCall(UUID callId) {
        TransponderCall call = activeCalls.remove(callId);
        if (call == null) {
            return;
        }

        try {
            // Play end sounds for all participants
            if (soundManager != null) {
                for (UUID participantId : call.getParticipants()) {
                    ServerPlayer player = getPlayerById(participantId);
                    if (player != null) {
                        soundManager.playCallDisconnectedSound(player);
                        soundManager.stopAllSoundsForPlayer(participantId);
                    }
                    playerToCall.remove(participantId);
                }
            }

            call.broadcastMessage("Call ended");

            // Full cleanup of audio resources
            call.cleanup();

            System.out.println("TransponderCallManager: Ended call " + callId);

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error ending call: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Invite a player to an existing call
     */
    public boolean inviteToCall(ServerPlayer inviter, ServerPlayer invitee) {
        UUID inviterId = inviter.getUUID();
        UUID callId = playerToCall.get(inviterId);

        if (callId == null) {
            inviter.sendSystemMessage(Component.literal("You're not in a call!"));
            return false;
        }

        TransponderCall call = activeCalls.get(callId);
        if (call == null) {
            return false;
        }

        if (call.getParticipantCount() < 2) {
            inviter.sendSystemMessage(Component.literal("Call must have at least 2 people to invite others!"));
            return false;
        }

        if (isInCall(invitee.getUUID())) {
            inviter.sendSystemMessage(Component.literal(invitee.getName().getString() + " is already in a call!"));
            return false;
        }

        // Send invite
        sendCallInvite(inviter, invitee, callId);
        inviter.sendSystemMessage(Component.literal("Invited " + invitee.getName().getString() + " to the call"));

        return true;
    }

    /**
     * Mute/unmute a participant in a call
     */
    public boolean setParticipantMuted(ServerPlayer player, UUID targetPlayerId, boolean muted) {
        UUID callId = playerToCall.get(player.getUUID());
        if (callId == null) {
            player.sendSystemMessage(Component.literal("You're not in a call!"));
            return false;
        }

        TransponderCall call = activeCalls.get(callId);
        if (call == null) {
            return false;
        }

        return call.setParticipantMuted(targetPlayerId, muted);
    }

    /**
     * Update handheld call location (for moving players)
     */
    public void updateHandheldCallLocation(ServerPlayer player) {
        UUID callId = playerToCall.get(player.getUUID());
        if (callId == null) {
            return;
        }

        TransponderCall call = activeCalls.get(callId);
        if (call != null && call.getCallType() == CallType.HANDHELD) {
            call.updateHandheldLocation(player);
        }
    }

    /**
     * Get players near a snail who can hear the call
     */
    public List<ServerPlayer> getPlayersNearSnail(ServerLevel level, BlockPos pos, double range) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= range * range) {
                nearbyPlayers.add(player);
            }
        }

        return nearbyPlayers;
    }

    /**
     * Schedule call timeout
     */
    private void scheduleCallTimeout(UUID playerId, UUID callId, long delayMs) {
        scheduler.schedule(() -> {
            try {
                // Check if the call request still exists
                UUID existingCallId = callRequests.get(playerId);
                if (existingCallId != null && existingCallId.equals(callId)) {
                    // Remove the expired request
                    callRequests.remove(playerId);

                    // Get player and notify of timeout
                    ServerPlayer player = getPlayerById(playerId);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("Call timed out"));
                        if (soundManager != null) {
                            soundManager.stopRingTone(player);
                            soundManager.playCallDisconnectedSound(player);
                        }
                    }

                    System.out.println("TransponderCallManager: Call " + callId + " to player " + playerId + " timed out");
                }
            } catch (Exception e) {
                System.err.println("TransponderCallManager: Error in call timeout: " + e.getMessage());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Check if player is in a call
     */
    public boolean isInCall(UUID playerId) {
        return playerToCall.containsKey(playerId);
    }

    /**
     * Get call for player
     */
    public TransponderCall getCallForPlayer(UUID playerId) {
        UUID callId = playerToCall.get(playerId);
        return callId != null ? activeCalls.get(callId) : null;
    }

    /**
     * Get active call by ID
     */
    public TransponderCall getCall(UUID callId) {
        return activeCalls.get(callId);
    }

    /**
     * Get all active calls
     */
    public Collection<TransponderCall> getActiveCalls() {
        return new ArrayList<>(activeCalls.values());
    }

    /**
     * Handle player disconnect with full cleanup
     */
    public void handlePlayerDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();

        try {
            // Remove call requests
            callRequests.remove(playerId);

            // Stop all sounds for this player
            if (soundManager != null) {
                soundManager.stopAllSoundsForPlayer(playerId);
            }

            // Leave any active call
            if (isInCall(playerId)) {
                leaveCall(player);
            }

            System.out.println("TransponderCallManager: Handled disconnect for " + player.getName().getString());

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error handling player disconnect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get comprehensive debug information
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> info = new HashMap<>();

        info.put("activeCalls", activeCalls.size());
        info.put("playersInCalls", playerToCall.size());
        info.put("pendingInvites", callRequests.size());

        // Audio system debug info
        if (audioChannelManager != null) {
            info.put("audioChannels", audioChannelManager.getChannelDebugInfo());
        }
        if (audioForwarder != null) {
            info.put("audioSessions", audioForwarder.getAudioDebugInfo());
        }
        if (soundManager != null) {
            info.put("activeSounds", soundManager.getActiveSoundsInfo());
        }

        // Individual call details
        Map<String, Object> callDetails = new HashMap<>();
        for (Map.Entry<UUID, TransponderCall> entry : activeCalls.entrySet()) {
            callDetails.put(entry.getKey().toString(), entry.getValue().getDebugInfo());
        }
        info.put("callDetails", callDetails);

        return info;
    }

    /**
     * Cleanup all resources (for server shutdown)
     */
    public void cleanup() {
        try {
            // End all active calls
            Set<UUID> callIds = new HashSet<>(activeCalls.keySet());
            for (UUID callId : callIds) {
                endCall(callId);
            }

            // Clean up audio systems
            if (audioChannelManager != null) {
                audioChannelManager.cleanup();
            }
            if (audioForwarder != null) {
                audioForwarder.cleanup();
            }
            if (soundManager != null) {
                soundManager.cleanup();
            }

            // Shutdown scheduler
            scheduler.shutdown();

            // Clear all mappings
            activeCalls.clear();
            playerToCall.clear();
            callRequests.clear();

            System.out.println("TransponderCallManager: Full cleanup completed");

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get player by UUID
     */
    private ServerPlayer getPlayerById(UUID playerId) {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(playerId);
    }
}