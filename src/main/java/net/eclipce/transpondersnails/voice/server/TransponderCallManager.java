package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.item.TransponderSnailItem;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.eclipce.transpondersnails.voice.server.HornedDDMJammerManager;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Transponder Call Manager with CallSession integration and handheld snail support
 * FIXED: Proper lazy registration and call state tracking
 */
public class TransponderCallManager {

    private final VoicechatServerApi voiceChatApi;
    private final CallSoundManager soundManager;
    private final ScheduledExecutorService scheduler;

    private final Map<UUID, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToCallId = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> snailToCallId = new ConcurrentHashMap<>();

    private final Map<Integer, TransponderSnailBlockEntity> registeredSnailBlocks = new ConcurrentHashMap<>();

    private final Map<Integer, UUID> handheldSnailOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerHandheldSnails = new ConcurrentHashMap<>();
    private final Map<UUID, LocationalAudioChannel> playerMovingChannels = new ConcurrentHashMap<>();

    private final Set<UUID> playersInCall = ConcurrentHashMap.newKeySet();
    private final Map<Integer, UUID> ringingSnails = new ConcurrentHashMap<>();

    // Audio activity tracking for visual feedback
    private final Map<Integer, Long> lastAudioActivityTime = new ConcurrentHashMap<>();
    private static final long AUDIO_ACTIVITY_WINDOW_MS = 500; // Show "active" for 500ms after last audio

    private SnailAudioRelay audioRelay;

    // âœ¨ INTERCEPTION: Interception manager
    private CallInterceptionManager interceptionManager;

    public TransponderCallManager(VoicechatServerApi voiceChatApi) {
        this.voiceChatApi = voiceChatApi;
        this.soundManager = new CallSoundManager();
        this.scheduler = Executors.newScheduledThreadPool(2);

        // âœ¨ INTERCEPTION: Initialize interception manager
        this.interceptionManager = new CallInterceptionManager(voiceChatApi, this);

        scheduler.scheduleAtFixedRate(this::cleanupInactiveCalls, 30, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::updateHandheldAudioPositions, 250, 250, TimeUnit.MILLISECONDS);
        // âœ¨ INTERCEPTION: Validate interceptions every second
        scheduler.scheduleAtFixedRate(this::validateAllInterceptions, 1, 1, TimeUnit.SECONDS);
        // âœ¨ INTERCEPTION: Cleanup invalid interceptions every 5 seconds
        scheduler.scheduleAtFixedRate(this::cleanupInterceptions, 5, 5, TimeUnit.SECONDS);
        // âœ¨ INTERCEPTION: Process searching sessions every 250ms
        scheduler.scheduleAtFixedRate(this::processSearchingSessions, 250, 250, TimeUnit.MILLISECONDS);
        // âœ¨ CALL STATE: Update call states every 200ms (sync CALL state when no audio)
        scheduler.scheduleAtFixedRate(this::updateCallStates, 200, 200, TimeUnit.MILLISECONDS);
        // âœ¨ MESSAGE REFRESH: Keep status messages visible by refreshing every 2 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (interceptionManager != null) {
                interceptionManager.refreshStatusMessages();
            }
        }, 2, 2, TimeUnit.SECONDS);

        // ✨ JAMMING: End calls whose participants fall inside a Horned DDM jammer sphere
        scheduler.scheduleAtFixedRate(this::checkJammedCalls, 2, 2, TimeUnit.SECONDS);

        System.out.println("TransponderCallManager: Initialized with handheld snail support + interception");
    }

    // =================== RINGING STATE QUERY METHODS ===================

    public boolean isSnailRinging(int snailNumber) {
        return ringingSnails.containsKey(snailNumber);
    }

    @Nullable
    public UUID getRingingCallId(int snailNumber) {
        return ringingSnails.get(snailNumber);
    }

    public int getCallerSnailNumber(int snailNumber) {
        UUID callId = ringingSnails.get(snailNumber);
        if (callId == null) return -1;

        CallSession session = activeCalls.get(callId);
        if (session == null) return -1;

        for (CallSession.CallParticipant participant : session.getAllParticipants()) {
            if (participant.hasActivePlayer()) {
                return participant.getSnailNumber();
            }
        }

        return -1;
    }

    // =================== SNAIL REGISTRATION ===================

    public void registerSnailBlock(int snailNumber, TransponderSnailBlockEntity blockEntity) {
        registeredSnailBlocks.put(snailNumber, blockEntity);
        System.out.println("TransponderCallManager: Registered snail block #" + snailNumber);
    }

    public void unregisterSnailBlock(int snailNumber) {
        registeredSnailBlocks.remove(snailNumber);
        stopRingingAtSnail(snailNumber);
        if (!isInTransition(snailNumber)) {
            endCallBySnailNumber(snailNumber);
        }
        System.out.println("TransponderCallManager: Unregistered snail block #" + snailNumber);
    }

    private boolean isInTransition(int snailNumber) {
        return false;
    }

    @Nullable
    public TransponderSnailBlockEntity getRegisteredSnailBlock(int snailNumber) {
        return registeredSnailBlocks.get(snailNumber);
    }

    public boolean isSnailBlockRegistered(int snailNumber) {
        return registeredSnailBlocks.containsKey(snailNumber);
    }

    /**
     * âœ¨ IMPROVED: Better handheld registration with debug logging
     */
    public void registerHandheldSnail(int snailNumber, UUID playerId) {
        UUID previousOwner = handheldSnailOwners.get(snailNumber);
        if (previousOwner != null && !previousOwner.equals(playerId)) {
            playerHandheldSnails.remove(previousOwner);
            System.out.println("TransponderCallManager: Handheld snail #" + snailNumber +
                    " transferred from " + previousOwner.toString().substring(0, 8) +
                    " to " + playerId.toString().substring(0, 8));
        }

        handheldSnailOwners.put(snailNumber, playerId);
        playerHandheldSnails.put(playerId, snailNumber);

        System.out.println("TransponderCallManager: Registered handheld snail #" + snailNumber +
                " for player " + playerId.toString().substring(0, 8));
    }

    public void unregisterHandheldSnail(int snailNumber) {
        UUID owner = handheldSnailOwners.remove(snailNumber);
        if (owner != null) {
            playerHandheldSnails.remove(owner);
            System.out.println("TransponderCallManager: Unregistered handheld snail #" + snailNumber);
        }
    }

    @Nullable
    public UUID getHandheldSnailOwner(int snailNumber) {
        return handheldSnailOwners.get(snailNumber);
    }

    public boolean isHandheldSnail(int snailNumber) {
        return handheldSnailOwners.containsKey(snailNumber);
    }

    public int getPlayerHandheldSnail(UUID playerId) {
        return playerHandheldSnails.getOrDefault(playerId, -1);
    }

    @Nullable
    private UUID findHandheldSnailOwner(int snailNumber) {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return null;
        }

        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && SnailNBTHandler.getSnailNumber(stack) == snailNumber) {
                    System.out.println("TransponderCallManager: Found handheld snail #" + snailNumber + " in " + player.getName().getString() + "'s inventory");
                    return player.getUUID();
                }
            }

            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty() && SnailNBTHandler.getSnailNumber(mainHand) == snailNumber) {
                System.out.println("TransponderCallManager: Found handheld snail #" + snailNumber + " in " + player.getName().getString() + "'s main hand");
                return player.getUUID();
            }

            ItemStack offHand = player.getOffhandItem();
            if (!offHand.isEmpty() && SnailNBTHandler.getSnailNumber(offHand) == snailNumber) {
                System.out.println("TransponderCallManager: Found handheld snail #" + snailNumber + " in " + player.getName().getString() + "'s offhand");
                return player.getUUID();
            }
        }

        return null;
    }

    // =================== CALL INITIATION ===================

    /**
     * FIXED METHOD #3: initiateCallBySnailNumber
     * Location: Find the existing initiateCallBySnailNumber method and replace it entirely
     */
    public boolean initiateCallBySnailNumber(ServerPlayer caller, int callerSnailNumber, int targetSnailNumber) {
        try {
            System.out.println("DEBUG initiateCall: Caller=#" + callerSnailNumber + " â†’ Target=#" + targetSnailNumber);

            // Validation checks
            if (callerSnailNumber == targetSnailNumber) {
                caller.displayClientMessage(
                        Component.literal("Cannot call your own snail!"),
                        true
                );
                return false;
            }

            if (isInCall(caller.getUUID())) {
                caller.displayClientMessage(
                        Component.literal("You are already in a call!"),
                        true
                );
                return false;
            }

            if (!snailExists(targetSnailNumber)) {
                caller.displayClientMessage(
                        Component.literal("Snail #" + targetSnailNumber + " does not exist!"),
                        true
                );
                return false;
            }

            if (isSnailInCall(targetSnailNumber)) {
                handleTargetBusy(caller, callerSnailNumber, targetSnailNumber);
                return false;
            }

            // ── Jamming check ──────────────────────────────────────────────
            // Block call initiation if the caller or the target snail's
            // location falls inside any active Horned Den Den Mushi jammer.
            HornedDDMJammerManager jammerManager = HornedDDMJammerManager.getInstance();
            if (jammerManager.getActiveJammerCount() > 0) {

                // Check caller position
                if (jammerManager.isPlayerJammed(caller)) {
                    caller.displayClientMessage(
                            Component.literal("Failed to place call")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                    System.out.println("TransponderCallManager: Call blocked — caller is jammed.");
                    return false;
                }

                // Check target snail position (handheld owner OR block position)
                boolean targetJammed = false;
                if (isHandheldSnail(targetSnailNumber)) {
                    UUID targetOwner = getHandheldSnailOwner(targetSnailNumber);
                    if (targetOwner == null) targetOwner = findHandheldSnailOwner(targetSnailNumber);
                    if (targetOwner != null) {
                        ServerPlayer targetPlayer = getPlayerById(targetOwner);
                        if (targetPlayer != null && jammerManager.isPlayerJammed(targetPlayer)) {
                            targetJammed = true;
                        }
                    }
                } else if (isSnailBlockRegistered(targetSnailNumber)) {
                    TransponderSnailBlockEntity targetBlock = getRegisteredSnailBlock(targetSnailNumber);
                    if (targetBlock != null && targetBlock.getLevel() instanceof ServerLevel sl) {
                        if (jammerManager.isBlockPosJammed(targetBlock.getBlockPos(), sl)) {
                            targetJammed = true;
                        }
                    }
                }

                if (targetJammed) {
                    caller.displayClientMessage(
                            Component.literal("Failed to place call")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                    System.out.println("TransponderCallManager: Call blocked — target snail is jammed.");
                    return false;
                }
            }
            // ── End jamming check ──────────────────────────────────────────

            // Create call session
            UUID callId = UUID.randomUUID();

            // FIX: Create caller participant with player provided for proper handheld detection
            CallSession.CallParticipant callerParticipant = createParticipantForSnail(callerSnailNumber, caller);
            System.out.println("DEBUG initiateCall: Caller participant type: " + callerParticipant.getType());

            CallSession callSession = new CallSession(callId, callerSnailNumber, callerParticipant);
            callSession.setState(CallSession.CallState.INITIATING);
            updateBlockEntitiesForCall(callSession);

            // Create target participant
            CallSession.CallParticipant targetParticipant;
            try {
                targetParticipant = createParticipantForSnail(targetSnailNumber, null);
                System.out.println("DEBUG initiateCall: Target participant type: " + targetParticipant.getType());
            } catch (IllegalStateException e) {
                System.err.println("DEBUG initiateCall: Could not find target snail #" + targetSnailNumber);
                caller.displayClientMessage(
                        Component.literal("Snail #" + targetSnailNumber + " is not available!")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                return false;
            }

            callSession.addParticipant(targetSnailNumber, targetParticipant);

            // Store call in maps
            activeCalls.put(callId, callSession);

            // FIX: Add caller to playerToCallId IMMEDIATELY
            playerToCallId.put(caller.getUUID(), callId);
            System.out.println("DEBUG initiateCall: âœ… Added caller to playerToCallId");

            snailToCallId.put(callerSnailNumber, callId);
            snailToCallId.put(targetSnailNumber, callId);

            // Set to ringing
            callSession.setState(CallSession.CallState.RINGING);
            updateBlockEntitiesForCall(callSession);

            // Start ringing
            boolean ringingStarted = startRinging(callSession, callerSnailNumber, targetSnailNumber);
            if (!ringingStarted) {
                System.err.println("DEBUG initiateCall: Failed to start ringing");

                // Cleanup
                activeCalls.remove(callId);
                playerToCallId.remove(caller.getUUID());
                snailToCallId.remove(callerSnailNumber);
                snailToCallId.remove(targetSnailNumber);

                caller.displayClientMessage(
                        Component.literal("Could not reach snail #" + targetSnailNumber + "!")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                return false;
            }

            System.out.println("DEBUG initiateCall: âœ… Call initiated successfully");
            return true;

        } catch (Exception e) {
            System.err.println("DEBUG initiateCall: âŒ Exception: " + e.getMessage());
            e.printStackTrace();
            caller.displayClientMessage(
                    Component.literal("Failed to initiate call!"),
                    true
            );
            return false;
        }
    }

    private boolean startRinging(CallSession callSession, int callerSnailNumber, int targetSnailNumber) {
        ringingSnails.put(targetSnailNumber, callSession.getCallId());
        System.out.println("TransponderCallManager: Marked snail #" + targetSnailNumber + " as ringing (caller: #" + callerSnailNumber + ")");

        TransponderSnailBlockEntity targetBlock = getRegisteredSnailBlock(targetSnailNumber);
        if (targetBlock != null) {
            targetBlock.onIncomingCall(callSession.getCallId(), callerSnailNumber, callSession);
            BlockPos targetPos = targetBlock.getBlockPos();
            ServerLevel level = (ServerLevel) targetBlock.getLevel();
            soundManager.playLocationalRingToneAtPosition(level, targetPos);
            System.out.println("TransponderCallManager: Started ringtone at BLOCK snail #" + targetSnailNumber);

            scheduler.schedule(() -> {
                if (callSession.getState() == CallSession.CallState.RINGING) {
                    handleCallTimeout(callSession);
                }
            }, VoiceChatConstants.getRingTimeoutMs(), TimeUnit.MILLISECONDS);

            return true;
        } else {
            // HANDHELD SNAIL PATH
            UUID handheldOwner = handheldSnailOwners.get(targetSnailNumber);

            System.out.println("=== DEBUG startRinging HANDHELD ===");
            System.out.println("Target snail #" + targetSnailNumber);
            System.out.println("Caller snail #" + callerSnailNumber);
            System.out.println("Registered owner: " + (handheldOwner != null ? handheldOwner.toString().substring(0, 8) : "null"));

            if (handheldOwner == null) {
                handheldOwner = findHandheldSnailOwner(targetSnailNumber);
                System.out.println("After lazy search: " + (handheldOwner != null ? handheldOwner.toString().substring(0, 8) : "null"));
                if (handheldOwner != null) {
                    registerHandheldSnail(targetSnailNumber, handheldOwner);
                    System.out.println("Lazy-registered handheld snail #" + targetSnailNumber);
                }
            }

            if (handheldOwner != null) {
                ServerPlayer owner = getPlayerById(handheldOwner);
                System.out.println("Owner player: " + (owner != null ? owner.getName().getString() : "null"));

                if (owner != null) {
                    System.out.println("ABOUT TO CALL updateAllSnailItemInstances:");
                    System.out.println("  - owner: " + owner.getName().getString());
                    System.out.println("  - targetSnailNumber: " + targetSnailNumber);
                    System.out.println("  - callId: " + callSession.getCallId().toString().substring(0, 8));
                    System.out.println("  - callerSnailNumber: " + callerSnailNumber + " (should NOT be -1)");

                    updateAllSnailItemInstances(owner, targetSnailNumber, callSession.getCallId(), callerSnailNumber);

                    owner.displayClientMessage(
                            Component.literal("Incoming call on snail #" + targetSnailNumber + " from #" + callerSnailNumber)
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW),
                            true
                    );

                    soundManager.playRingToneForPlayer(owner);

                    System.out.println("TransponderCallManager: Started ringtone for HANDHELD snail #" + targetSnailNumber);

                    scheduler.schedule(() -> {
                        if (callSession.getState() == CallSession.CallState.RINGING) {
                            handleCallTimeout(callSession);
                        }
                    }, VoiceChatConstants.getRingTimeoutMs(), TimeUnit.MILLISECONDS);

                    return true;
                } else {
                    System.err.println("ERROR: Could not find owner player for handheld snail #" + targetSnailNumber);
                    ringingSnails.remove(targetSnailNumber);
                    return false;
                }
            } else {
                System.err.println("ERROR: Could not find handheld snail #" + targetSnailNumber);
                ringingSnails.remove(targetSnailNumber);
                return false;
            }
        }
    }

    /**
     * FIX #3: Update handheld item instances with proper state management
     * IMPROVED: Now accepts callId and callerSnailNumber for different states
     */
    private void updateAllSnailItemInstances(ServerPlayer player, int snailNumber, UUID callId, int callerSnailNumber) {
        System.out.println("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        System.out.println("â•‘ DEBUG updateAllSnailItemInstances");
        System.out.println("â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        System.out.println("â•‘ Player: " + player.getName().getString());
        System.out.println("â•‘ Target Snail: #" + snailNumber);
        System.out.println("â•‘ CallID: " + (callId != null ? callId.toString().substring(0, 8) : "null"));
        System.out.println("â•‘ Caller Snail: #" + callerSnailNumber);
        System.out.println("â•‘ Is Ringing: " + (callerSnailNumber != -1));
        System.out.println("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");

        int updatedCount = 0;
        int checkedCount = 0;

        // Check all inventory slots
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                int stackSnailNum = SnailNBTHandler.getSnailNumber(stack);
                if (stackSnailNum != -1) {
                    checkedCount++;
                    System.out.println("  Slot " + i + ": Snail #" + stackSnailNum);

                    if (stackSnailNum == snailNumber) {
                        System.out.println("    âœ“âœ“âœ“ MATCH FOUND! Updating...");

                        if (callerSnailNumber != -1) {
                            System.out.println("    â†’ Setting RINGING state");
                            updateSnailItemRingingState(stack, callId, callerSnailNumber);

                            // Verify it was set
                            CompoundTag verify = stack.getTag();
                            if (verify != null) {
                                System.out.println("    â†’ Verified: call_state = '" + verify.getString("call_state") + "'");
                            }
                        } else {
                            System.out.println("    â†’ Setting CONNECTED state");
                            updateSnailItemConnectedState(stack, callId);
                        }
                        updatedCount++;
                    }
                }
            }
        }

        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) {
            int mainHandSnailNum = SnailNBTHandler.getSnailNumber(mainHand);
            if (mainHandSnailNum != -1) {
                checkedCount++;
                System.out.println("  MainHand: Snail #" + mainHandSnailNum);

                if (mainHandSnailNum == snailNumber) {
                    System.out.println("    âœ“âœ“âœ“ MATCH FOUND! Updating...");

                    if (callerSnailNumber != -1) {
                        System.out.println("    â†’ Setting RINGING state");
                        updateSnailItemRingingState(mainHand, callId, callerSnailNumber);

                        // Verify it was set
                        CompoundTag verify = mainHand.getTag();
                        if (verify != null) {
                            System.out.println("    â†’ Verified: call_state = '" + verify.getString("call_state") + "'");
                        }
                    } else {
                        System.out.println("    â†’ Setting CONNECTED state");
                        updateSnailItemConnectedState(mainHand, callId);
                    }
                    updatedCount++;
                }
            }
        }

        // Check offhand
        ItemStack offHand = player.getOffhandItem();
        if (!offHand.isEmpty()) {
            int offHandSnailNum = SnailNBTHandler.getSnailNumber(offHand);
            if (offHandSnailNum != -1) {
                checkedCount++;
                System.out.println("  OffHand: Snail #" + offHandSnailNum);

                if (offHandSnailNum == snailNumber) {
                    System.out.println("    âœ“âœ“âœ“ MATCH FOUND! Updating...");

                    if (callerSnailNumber != -1) {
                        System.out.println("    â†’ Setting RINGING state");
                        updateSnailItemRingingState(offHand, callId, callerSnailNumber);

                        // Verify it was set
                        CompoundTag verify = offHand.getTag();
                        if (verify != null) {
                            System.out.println("    â†’ Verified: call_state = '" + verify.getString("call_state") + "'");
                        }
                    } else {
                        System.out.println("    â†’ Setting CONNECTED state");
                        updateSnailItemConnectedState(offHand, callId);
                    }
                    updatedCount++;
                }
            }
        }

        System.out.println("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
        System.out.println("â•‘ RESULT: Checked " + checkedCount + " snails, Updated " + updatedCount + " items");
        System.out.println("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
    }

    private void updateSnailItemRingingState(ItemStack stack, UUID callId, int callerSnailNumber) {
        System.out.println("      [updateSnailItemRingingState] Writing NBT...");
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putString("call_state", "ringing");
        nbt.putUUID("active_call_id", callId);
        nbt.putInt("other_snail_number", callerSnailNumber);
        nbt.putLong("call_start_time", System.currentTimeMillis());
        System.out.println("      [updateSnailItemRingingState] NBT written: call_state='ringing', caller=#" + callerSnailNumber);
    }

    private void updateSnailItemConnectedState(ItemStack stack, UUID callId) {
        System.out.println("      [updateSnailItemConnectedState] Writing NBT...");
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putString("call_state", "connected");
        nbt.putUUID("active_call_id", callId);
        nbt.putLong("call_start_time", System.currentTimeMillis());
        nbt.remove("other_snail_number");
        System.out.println("      [updateSnailItemConnectedState] NBT written: call_state='connected'");
    }

    /**
     * FIX #1 & #2: Create participant with correct type priority
     * FIXED: Now checks handheld FIRST when player is provided, preventing wrong participant types
     */
    private CallSession.CallParticipant createParticipantForSnail(int snailNumber, @Nullable ServerPlayer player) {
        System.out.println("DEBUG createParticipantForSnail: Snail #" + snailNumber +
                ", Player: " + (player != null ? player.getName().getString() : "null"));

        // FIX #1: When player is provided, check handheld FIRST
        if (player != null) {
            UUID handheldOwner = getHandheldSnailOwner(snailNumber);

            // If this snail is registered as handheld to this player, it's definitely handheld
            if (handheldOwner != null && handheldOwner.equals(player.getUUID())) {
                System.out.println("DEBUG: Creating HANDHELD participant for player's snail #" + snailNumber);
                return CallSession.CallParticipant.handheld(player.getUUID(), snailNumber);
            }
        }

        // Check if it's a block snail
        TransponderSnailBlockEntity blockEntity = getRegisteredSnailBlock(snailNumber);
        if (blockEntity != null) {
            System.out.println("DEBUG: Creating BLOCK participant for snail #" + snailNumber);
            if (player != null) {
                return CallSession.CallParticipant.blockWithPlayer(
                        player.getUUID(), snailNumber, blockEntity.getBlockPos());
            } else {
                return CallSession.CallParticipant.block(snailNumber, blockEntity.getBlockPos());
            }
        }

        // Not registered as block, try to find as handheld
        UUID handheldOwner = getHandheldSnailOwner(snailNumber);
        if (handheldOwner == null) {
            handheldOwner = findHandheldSnailOwner(snailNumber);
            if (handheldOwner != null) {
                registerHandheldSnail(snailNumber, handheldOwner);
                System.out.println("DEBUG: Lazy-registered handheld snail #" + snailNumber);
            }
        }

        if (handheldOwner != null) {
            System.out.println("DEBUG: Creating HANDHELD participant (no player provided) for snail #" + snailNumber);
            return CallSession.CallParticipant.handheld(handheldOwner, snailNumber);
        }

        throw new IllegalStateException("Snail #" + snailNumber + " not found as block or handheld");
    }

    private void updateBlockEntitiesForCall(CallSession callSession) {
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity blockEntity = getRegisteredSnailBlock(snailNumber);
            if (blockEntity != null) {
                blockEntity.setCallSession(callSession);
            }
        }
    }

    private void clearCallSessionFromBlockEntities(CallSession callSession) {
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity blockEntity = getRegisteredSnailBlock(snailNumber);
            if (blockEntity != null) {
                blockEntity.clearCallSession();
            }
        }
    }

    // =================== CALL ACCEPTANCE ===================

    /**
     * FIX #2 & #3: Improved accept call with better detection and immediate state sync
     */
    public boolean acceptCall(ServerPlayer player, UUID callId) {
        CallSession callSession = activeCalls.get(callId);
        if (callSession == null || callSession.getState() != CallSession.CallState.RINGING) {
            System.out.println("DEBUG acceptCall: Invalid call state");
            return false;
        }

        try {
            System.out.println("DEBUG acceptCall: Player " + player.getName().getString() +
                    " accepting call " + callId.toString().substring(0, 8));

            // FIX #3: Stop ringing IMMEDIATELY to prevent state confusion
            stopRingingForCall(callSession);
            System.out.println("DEBUG acceptCall: Ringing stopped");

            // FIX #2: Better answering snail detection
            int answeringSnailNumber = -1;
            CallSession.CallParticipant answeringParticipant = null;

            for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
                CallSession.CallParticipant participant = callSession.getParticipant(snailNumber);

                System.out.println("DEBUG acceptCall: Checking snail #" + snailNumber +
                        " (type: " + (participant != null ? participant.getType() : "null") + ")");

                // Check handheld snails FIRST
                if (isHandheldSnail(snailNumber)) {
                    UUID owner = getHandheldSnailOwner(snailNumber);
                    System.out.println("DEBUG acceptCall: Snail #" + snailNumber + " is HANDHELD, owner: " +
                            (owner != null ? owner.toString().substring(0, 8) : "null"));

                    if (owner != null && owner.equals(player.getUUID())) {
                        answeringSnailNumber = snailNumber;
                        answeringParticipant = participant;
                        System.out.println("DEBUG acceptCall: âœ… Found answering HANDHELD snail #" + snailNumber);
                        break;
                    }
                }
                // Check block snails
                else if (isSnailBlockRegistered(snailNumber)) {
                    TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
                    System.out.println("DEBUG acceptCall: Snail #" + snailNumber + " is BLOCK");

                    if (block != null) {
                        ServerPlayer nearbyPlayer = block.findNearbyPlayer();
                        if (nearbyPlayer == player) {
                            answeringSnailNumber = snailNumber;
                            answeringParticipant = participant;
                            System.out.println("DEBUG acceptCall: âœ… Found answering BLOCK snail #" + snailNumber);
                            break;
                        }
                    }
                }
            }

            if (answeringSnailNumber == -1) {
                System.err.println("DEBUG acceptCall: âŒ Could not find answering snail for player " +
                        player.getName().getString());
            }

            // FIX #2: Play pick up sound based on snail type
            if (answeringSnailNumber != -1) {
                if (isHandheldSnail(answeringSnailNumber)) {
                    soundManager.playPickUpSoundForPlayer(player);
                    System.out.println("DEBUG acceptCall: âœ… Played HANDHELD pick up sound for snail #" +
                            answeringSnailNumber);
                } else {
                    TransponderSnailBlockEntity block = getRegisteredSnailBlock(answeringSnailNumber);
                    if (block != null) {
                        soundManager.playPickUpSoundAtSnail(player, block.getBlockPos());
                        System.out.println("DEBUG acceptCall: âœ… Played BLOCK pick up sound for snail #" +
                                answeringSnailNumber);
                    }
                }
            }

            // Connect the call
            connectCall(callSession, player);
            System.out.println("DEBUG acceptCall: Call connected");

            // FIX #3: Update handheld item NBT immediately for state sync
            if (answeringSnailNumber != -1 && isHandheldSnail(answeringSnailNumber)) {
                updateAllSnailItemInstances(player, answeringSnailNumber, callSession.getCallId(), -1);
                System.out.println("DEBUG acceptCall: Updated handheld snail #" + answeringSnailNumber +
                        " NBT to connected state");
            }

            return true;

        } catch (Exception e) {
            System.err.println("DEBUG acceptCall: Error accepting call: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean rejectCall(ServerPlayer player, UUID callId) {
        CallSession callSession = activeCalls.get(callId);
        if (callSession == null) {
            return false;
        }

        stopRingingForCall(callSession);
        notifyCallRejected(callSession);
        endCall(callId);
        return true;
    }

    /**
     * FIXED METHOD #1: connectCall
     * Location: Find the existing connectCall method and replace it entirely
     */
    private void connectCall(CallSession callSession, ServerPlayer acceptingPlayer) {
        System.out.println("DEBUG connectCall: Connecting call for player " + acceptingPlayer.getName().getString());

        updateBlockEntitiesForCall(callSession);
        callSession.setState(CallSession.CallState.CONNECTED);

        // FIX: Always ensure accepting player is in playerToCallId
        boolean playerWasParticipant = callSession.isParticipant(acceptingPlayer.getUUID());

        if (!playerWasParticipant) {
            System.out.println("DEBUG connectCall: Player not yet participant, updating participant");

            // Find the participant without an active player and update it
            for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
                if (!participant.hasActivePlayer()) {
                    System.out.println("DEBUG connectCall: Found participant without player - Snail #" +
                            participant.getSnailNumber() + ", Type: " + participant.getType());

                    CallSession.CallParticipant updatedParticipant;

                    // FIX: Preserve the participant type when adding player!
                    if (participant.isHandheld()) {
                        // Keep it as handheld
                        updatedParticipant = CallSession.CallParticipant.handheld(
                                acceptingPlayer.getUUID(), participant.getSnailNumber());
                        System.out.println("DEBUG connectCall: âœ… Updated as HANDHELD participant");
                    } else {
                        // Keep it as block
                        updatedParticipant = CallSession.CallParticipant.blockWithPlayer(
                                acceptingPlayer.getUUID(), participant.getSnailNumber(), participant.getBlockPosition());
                        System.out.println("DEBUG connectCall: âœ… Updated as BLOCK participant");
                    }

                    callSession.removeParticipant(participant.getSnailNumber());
                    callSession.addParticipant(participant.getSnailNumber(), updatedParticipant);
                    break;
                }
            }
        } else {
            System.out.println("DEBUG connectCall: Player already participant (handheld owner)");
        }

        // FIX: ALWAYS add accepting player to playerToCallId, even if already a participant
        // This is critical for endCall(ServerPlayer) to work!
        if (!playerToCallId.containsKey(acceptingPlayer.getUUID())) {
            playerToCallId.put(acceptingPlayer.getUUID(), callSession.getCallId());
            System.out.println("DEBUG connectCall: âœ… Added player to playerToCallId");
        } else {
            System.out.println("DEBUG connectCall: Player already in playerToCallId");
        }

        // Create audio channels for all participants
        createAudioChannels(callSession);

        // Play connection sounds
        playConnectionSounds(callSession);

        // Notify block entities
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
            if (block != null) {
                block.onCallConnected(callSession.getCallId(), callSession);
            }
        }

        System.out.println("DEBUG connectCall: âœ… Call connected - Participants: " +
                callSession.getParticipantCount() + ", PlayerToCallId entries: " + playerToCallId.size());

        // Debug: Print all participants
        for (CallSession.CallParticipant p : callSession.getAllParticipants()) {
            System.out.println("  - Snail #" + p.getSnailNumber() + " (" + p.getType() + "), " +
                    "Player: " + (p.hasActivePlayer() ? p.getPlayerId().toString().substring(0, 8) : "none"));
        }
    }

    /**
     * FIX #1: Ensure BOTH participants get audio channels created
     */
    private void createAudioChannels(CallSession callSession) {
        System.out.println("DEBUG createAudioChannels: Creating channels for " +
                callSession.getParticipantCount() + " participants");

        int blockChannels = 0;
        int handheldChannels = 0;

        // Create channels for block snails
        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            createBlockAudioChannelAtPosition(callSession, pos);
            blockChannels++;
        }

        // FIX #1: Create channels for ALL handheld snails (both caller and receiver)
        for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
            if (participant.isHandheld() && participant.hasActivePlayer()) {
                createHandheldAudioChannel(callSession, participant.getSnailNumber(), participant.getPlayerId());
                handheldChannels++;
                System.out.println("DEBUG createAudioChannels: Created handheld channel for snail #" +
                        participant.getSnailNumber() + " (player " +
                        participant.getPlayerId().toString().substring(0, 8) + ")");
            }
        }

        System.out.println("DEBUG createAudioChannels: Created " + blockChannels + " block channels and " +
                handheldChannels + " handheld channels");
        System.out.println("DEBUG createAudioChannels: Total channels in session: " +
                callSession.getAllAudioChannels().size());
    }

    private void createBlockAudioChannelAtPosition(CallSession session, BlockPos pos) {
        try {
            TransponderSnailBlockEntity block = null;
            for (Integer snailNumber : session.getParticipantSnailNumbers()) {
                TransponderSnailBlockEntity testBlock = getRegisteredSnailBlock(snailNumber);
                if (testBlock != null && testBlock.getBlockPos().equals(pos)) {
                    block = testBlock;
                    break;
                }
            }

            if (block != null) {
                LocationalAudioChannel channel = voiceChatApi.createLocationalAudioChannel(
                        UUID.randomUUID(),
                        voiceChatApi.fromServerLevel((ServerLevel) block.getLevel()),
                        voiceChatApi.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                );

                if (channel != null) {
                    channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
                    channel.setDistance((float) VoiceChatConstants.getLocationalSnailRange());
                    session.addProximityChannel(pos, channel);
                    System.out.println("TransponderCallManager: Created block audio channel at " + pos);
                }
            }
        } catch (Exception e) {
            System.err.println("TransponderCallManager: Failed to create block audio channel: " + e.getMessage());
        }
    }

    /**
     * FIX #1: Store handheld channels in CallSession for audio forwarding
     */
    private void createHandheldAudioChannel(CallSession session, int snailNumber, UUID playerId) {
        try {
            ServerPlayer player = getPlayerById(playerId);
            if (player == null) {
                System.err.println("DEBUG createHandheldAudioChannel: Player not found for " +
                        playerId.toString().substring(0, 8));
                return;
            }

            LocationalAudioChannel channel = voiceChatApi.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    voiceChatApi.fromServerLevel(player.serverLevel()),
                    voiceChatApi.createPosition(player.getX(), player.getY() + 1.5, player.getZ())
            );

            if (channel != null) {
                channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
                channel.setDistance((float) VoiceChatConstants.getHandheldSnailRange());

                // Store in CallManager for position updates
                playerMovingChannels.put(playerId, channel);

                // FIX #1: CRITICAL - Store in CallSession for audio forwarding!
                session.addHandheldChannel(playerId, channel);

                System.out.println("DEBUG createHandheldAudioChannel: âœ… Created and stored handheld channel for player " +
                        player.getName().getString() + " (snail #" + snailNumber + ")");
                System.out.println("DEBUG createHandheldAudioChannel: Session now has " +
                        session.getHandheldChannels().size() + " handheld channels");
            } else {
                System.err.println("DEBUG createHandheldAudioChannel: âŒ Failed to create channel (API returned null)");
            }
        } catch (Exception e) {
            System.err.println("DEBUG createHandheldAudioChannel: âŒ Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if a snail has active audio transmission
     * Used by item model properties to determine active vs call state
     * NOW PROPERLY TRACKS AUDIO ACTIVITY FOR BOTH BLOCK AND HANDHELD!
     */
    public boolean hasActiveAudio(int snailNumber) {
        UUID callId = snailToCallId.get(snailNumber);
        if (callId == null) {
            return false;
        }

        CallSession session = activeCalls.get(callId);
        if (session == null || session.getState() != CallSession.CallState.CONNECTED) {
            return false;
        }

        // Check for recent audio activity (within last 500ms)
        Long lastActivity = lastAudioActivityTime.get(snailNumber);
        if (lastActivity != null) {
            long timeSinceActivity = System.currentTimeMillis() - lastActivity;
            if (timeSinceActivity < AUDIO_ACTIVITY_WINDOW_MS) {
                return true; // Audio was transmitted recently
            }
        }

        // Fallback: For block snails, also check the audioReady flag
        if (!isHandheldSnail(snailNumber)) {
            TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
            if (block != null && block.isAudioReady()) {
                return true;
            }
        }

        // Default: no active audio (show "call" state, not "active")
        return false;
    }

    /**
     * Mark that a snail has transmitted or received audio
     * This updates the visual state to show "active" for a brief period
     *
     * @param snailNumber The snail that transmitted/received audio
     */
    public void markAudioActivity(int snailNumber) {
        lastAudioActivityTime.put(snailNumber, System.currentTimeMillis());
    }

    /**
     * Mark audio activity for all participants in a call
     * Use this when audio is forwarded to all call participants
     *
     * @param callId The call where audio was transmitted
     */
    public void markAudioActivityForCall(UUID callId) {
        CallSession session = activeCalls.get(callId);
        if (session != null) {
            for (Integer snailNumber : session.getParticipantSnailNumbers()) {
                markAudioActivity(snailNumber);
            }
        }
    }

    // =================== CALL TERMINATION ===================

    public void endCall(UUID callId) {
        CallSession callSession = activeCalls.remove(callId);
        if (callSession == null) return;

        try {
            System.out.println("TransponderCallManager: Ending call " + callId.toString().substring(0, 8));

            callSession.setState(CallSession.CallState.ENDING);
            updateBlockEntitiesForCall(callSession);
            stopRingingForCall(callSession);

            // âœ¨ INTERCEPTION: Stop all interceptions for this call
            if (interceptionManager != null) {
                interceptionManager.stopAllInterceptionsForCall(callId);
            }

            if (audioRelay != null) {
                audioRelay.onCallEnded(callId);
            }

            cleanupCall(callSession);
            notifyCallEnded(callSession);

            scheduler.schedule(() -> {
                callSession.setState(CallSession.CallState.ENDED);
                updateBlockEntitiesForCall(callSession);

                scheduler.schedule(() -> {
                    clearCallSessionFromBlockEntities(callSession);
                }, 1000, TimeUnit.MILLISECONDS);
            }, 500, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error ending call: " + e.getMessage());
        }
    }

    /**
     * FIXED METHOD #2: endCall(ServerPlayer)
     * Location: Find the existing endCall(ServerPlayer player) method and replace it entirely
     */
    public void endCall(ServerPlayer player) {
        System.out.println("DEBUG endCall(player): Attempting to end call for " + player.getName().getString());
        System.out.println("DEBUG endCall(player): Player UUID: " + player.getUUID().toString().substring(0, 8));

        UUID callId = playerToCallId.get(player.getUUID());

        if (callId == null) {
            System.err.println("DEBUG endCall(player): âŒ Player not found in playerToCallId map!");
            System.err.println("DEBUG endCall(player): Current playerToCallId size: " + playerToCallId.size());

            // Debug: Try to find the call by checking all active calls
            for (CallSession session : activeCalls.values()) {
                if (session.isParticipant(player.getUUID())) {
                    System.err.println("DEBUG endCall(player): âš ï¸ Found player in call session " +
                            session.getCallId().toString().substring(0, 8) +
                            " but not in playerToCallId - attempting recovery");
                    callId = session.getCallId();
                    break;
                }
            }

            if (callId == null) {
                System.err.println("DEBUG endCall(player): âŒ Cannot find any call for this player");
                player.displayClientMessage(
                        Component.literal("Error: You are not in a call")
                                .withStyle(ChatFormatting.RED),
                        true
                );
                return;
            }
        }

        System.out.println("DEBUG endCall(player): Found callId: " + callId.toString().substring(0, 8));

        final UUID finalCallId = callId;

        CallSession callSession = activeCalls.get(callId);
        if (callSession != null) {
            // Update handheld NBT for all participants
            for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
                if (isHandheldSnail(snailNumber)) {
                    UUID owner = getHandheldSnailOwner(snailNumber);
                    ServerPlayer ownerPlayer = getPlayerById(owner);
                    if (ownerPlayer != null) {
                        ItemStack snailItem = findSnailItemInInventory(ownerPlayer, snailNumber);
                        if (!snailItem.isEmpty()) {
                            CompoundTag nbt = snailItem.getOrCreateTag();
                            nbt.remove("call_state");
                            nbt.remove("active_call_id");
                            nbt.remove("other_snail_number");
                            nbt.remove("call_start_time");
                        }
                    }
                }
            }

            playHangUpSoundForPlayer(player, callSession);

            scheduler.schedule(() -> {
                endCall(finalCallId);
            }, 800, TimeUnit.MILLISECONDS);

            System.out.println("DEBUG endCall(player): âœ… Scheduled call termination");
        } else {
            System.err.println("DEBUG endCall(player): âš ï¸ CallSession not found, calling endCall(callId) directly");
            endCall(callId);
        }
    }

    public void endCallBySnailNumber(int snailNumber) {
        UUID callId = snailToCallId.get(snailNumber);
        if (callId != null) {
            endCall(callId);
        }
    }

    /**
     * âœ¨ IMPROVED: Cleanup includes handheld channels
     */
    private void cleanupCall(CallSession callSession) {
        // Remove player mappings
        for (UUID playerId : callSession.getActivePlayerParticipants()) {
            playerToCallId.remove(playerId);
        }

        // Remove snail mappings
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            snailToCallId.remove(snailNumber);
            ringingSnails.remove(snailNumber);
            lastAudioActivityTime.remove(snailNumber); // NEW: Clean up audio activity tracking
        }

        // Clear proximity channels
        callSession.getProximityChannels().clear();

        // Clear handheld channels
        for (UUID playerId : callSession.getHandheldChannels().keySet()) {
            playerMovingChannels.remove(playerId);
        }
        callSession.getHandheldChannels().clear();

        System.out.println("TransponderCallManager: Cleaned up call " +
                callSession.getCallId().toString().substring(0, 8));
    }

    /**
     * FIX #3: Improved ringing stop with immediate state clearing and NBT sync
     */
    private void stopRingingForCall(CallSession callSession) {
        System.out.println("DEBUG stopRingingForCall: Stopping ringing for " +
                callSession.getParticipantSnailNumbers().size() + " snails");

        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            if (ringingSnails.containsKey(snailNumber)) {
                System.out.println("DEBUG stopRingingForCall: Stopping snail #" + snailNumber);

                // FIX #3: Remove from ringing map IMMEDIATELY
                ringingSnails.remove(snailNumber);

                // Stop block snail ringing
                TransponderSnailBlockEntity targetBlock = getRegisteredSnailBlock(snailNumber);
                if (targetBlock != null) {
                    BlockPos targetPos = targetBlock.getBlockPos();
                    soundManager.stopSnailPositionSounds(targetPos, CallSoundManager.SoundType.RING_TONE);
                    System.out.println("DEBUG stopRingingForCall: Stopped BLOCK ringing at " + targetPos);
                }

                // Stop handheld snail ringing
                if (isHandheldSnail(snailNumber)) {
                    UUID owner = getHandheldSnailOwner(snailNumber);
                    ServerPlayer ownerPlayer = getPlayerById(owner);
                    if (ownerPlayer != null) {
                        soundManager.stopRingTone(ownerPlayer);

                        // FIX #3: Clear NBT IMMEDIATELY to sync state
                        updateAllSnailItemIdleState(ownerPlayer, snailNumber);

                        System.out.println("DEBUG stopRingingForCall: Stopped HANDHELD ringing for snail #" +
                                snailNumber + " (player " + ownerPlayer.getName().getString() + ")");
                    }
                }
            }
        }

        System.out.println("DEBUG stopRingingForCall: Ringing stopped, remaining ringing snails: " +
                ringingSnails.size());
    }

    /**
     * FIX #3: Enhanced idle state clearing with debug logging
     */
    private void updateAllSnailItemIdleState(ServerPlayer player, int snailNumber) {
        System.out.println("DEBUG updateAllSnailItemIdleState: Clearing state for snail #" + snailNumber);

        int clearedCount = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && SnailNBTHandler.getSnailNumber(stack) == snailNumber) {
                CompoundTag nbt = stack.getOrCreateTag();
                if (nbt.contains("call_state")) {
                    nbt.remove("call_state");
                    nbt.remove("active_call_id");
                    nbt.remove("other_snail_number");
                    nbt.remove("call_start_time");
                    clearedCount++;
                }
            }
        }

        ItemStack mainHand = player.getMainHandItem();
        if (SnailNBTHandler.getSnailNumber(mainHand) == snailNumber) {
            CompoundTag nbt = mainHand.getOrCreateTag();
            if (nbt.contains("call_state")) {
                nbt.remove("call_state");
                nbt.remove("active_call_id");
                nbt.remove("other_snail_number");
                nbt.remove("call_start_time");
                clearedCount++;
            }
        }

        ItemStack offHand = player.getOffhandItem();
        if (SnailNBTHandler.getSnailNumber(offHand) == snailNumber) {
            CompoundTag nbt = offHand.getOrCreateTag();
            if (nbt.contains("call_state")) {
                nbt.remove("call_state");
                nbt.remove("active_call_id");
                nbt.remove("other_snail_number");
                nbt.remove("call_start_time");
                clearedCount++;
            }
        }

        System.out.println("DEBUG updateAllSnailItemIdleState: Cleared " + clearedCount + " item instances");
    }

    private void stopRingingAtSnail(int snailNumber) {
        UUID callId = ringingSnails.remove(snailNumber);
        if (callId != null) {
            TransponderSnailBlockEntity snailBlock = getRegisteredSnailBlock(snailNumber);
            if (snailBlock != null) {
                soundManager.stopSnailPositionSounds(snailBlock.getBlockPos(), CallSoundManager.SoundType.RING_TONE);
            }
        }
    }

    // =================== TRANSITIONS ===================

    public void transitionBlockToHandheld(int snailNumber, UUID playerId) {
        UUID callId = snailToCallId.get(snailNumber);
        if (callId == null) return;

        CallSession session = activeCalls.get(callId);
        if (session == null) return;

        unregisterSnailBlock(snailNumber);
        registerHandheldSnail(snailNumber, playerId);

        CallSession.CallParticipant oldParticipant = session.getParticipant(snailNumber);
        if (oldParticipant != null) {
            session.removeParticipant(snailNumber);
            CallSession.CallParticipant newParticipant = CallSession.CallParticipant.handheld(playerId, snailNumber);
            session.addParticipant(snailNumber, newParticipant);
            recreateAudioChannelsForTransition(session, snailNumber, playerId, true);
        }
    }

    public void transitionHandheldToBlock(int snailNumber, BlockPos blockPos, TransponderSnailBlockEntity blockEntity) {
        UUID callId = snailToCallId.get(snailNumber);
        if (callId == null) return;

        CallSession session = activeCalls.get(callId);
        if (session == null) return;

        UUID playerId = handheldSnailOwners.get(snailNumber);
        unregisterHandheldSnail(snailNumber);
        registerSnailBlock(snailNumber, blockEntity);

        CallSession.CallParticipant oldParticipant = session.getParticipant(snailNumber);
        if (oldParticipant != null) {
            session.removeParticipant(snailNumber);
            CallSession.CallParticipant newParticipant = CallSession.CallParticipant.blockWithPlayer(
                    playerId, snailNumber, blockPos);
            session.addParticipant(snailNumber, newParticipant);
            recreateAudioChannelsForTransition(session, snailNumber, playerId, false);
        }
    }

    /**
     * âœ¨ IMPROVED: Recreate channels for transitions with CallSession integration
     */
    private void recreateAudioChannelsForTransition(CallSession session, int snailNumber, UUID playerId, boolean toHandheld) {
        if (toHandheld) {
            // Remove block channels
            for (BlockPos pos : session.getInvolvedBlockPositions()) {
                session.removeProximityChannel(pos);
            }
            // Create handheld channel
            createHandheldAudioChannel(session, snailNumber, playerId);
            System.out.println("TransponderCallManager: Transitioned snail #" + snailNumber + " to handheld");
        } else {
            // Remove handheld channel
            session.removeHandheldChannel(playerId);
            playerMovingChannels.remove(playerId);

            // Create block channel
            TransponderSnailBlockEntity blockEntity = registeredSnailBlocks.get(snailNumber);
            if (blockEntity != null) {
                createBlockAudioChannelAtPosition(session, blockEntity.getBlockPos());
                System.out.println("TransponderCallManager: Transitioned snail #" + snailNumber + " to block");
            }
        }
    }

    // =================== UTILITIES ===================

    /**
     * âœ¨ IMPROVED: Position updates now use CallSession channels
     */
    private void updateHandheldAudioPositions() {
        try {
            // Update each active call's handheld channels
            for (CallSession session : activeCalls.values()) {
                if (session.getState() != CallSession.CallState.CONNECTED) {
                    continue;
                }

                // Update positions for all handheld participants
                for (Map.Entry<UUID, AudioChannel> entry : session.getHandheldChannels().entrySet()) {
                    UUID playerId = entry.getKey();
                    AudioChannel channel = entry.getValue();

                    if (channel instanceof LocationalAudioChannel locationalChannel) {
                        ServerPlayer player = getPlayerById(playerId);
                        if (player != null) {
                            // Update channel position to player's current location (head level)
                            de.maxhenkel.voicechat.api.Position newPosition = voiceChatApi.createPosition(
                                    player.getX(),
                                    player.getY() + 1.5,
                                    player.getZ()
                            );

                            locationalChannel.updateLocation(newPosition);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error updating handheld positions: " + e.getMessage());
        }
    }

    /**
     * FIX #2: Play connection sounds for BOTH block and handheld participants
     */
    private void playConnectionSounds(CallSession callSession) {
        System.out.println("DEBUG playConnectionSounds: Playing for " + callSession.getParticipantCount() + " participants");

        // Play for block snails
        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            List<ServerPlayer> nearbyPlayers = getPlayersNearSnail(
                    (ServerLevel) getWorldForPosition(pos), pos, VoiceChatConstants.getSnailInteractionRange());
            if (!nearbyPlayers.isEmpty()) {
                soundManager.playCallConnectedSoundAtSnail(nearbyPlayers.get(0), pos);
                System.out.println("DEBUG: Played connection sound at BLOCK " + pos);
            }
        }

        // FIX #2: Play for handheld snails
        for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
            if (participant.isHandheld() && participant.hasActivePlayer()) {
                ServerPlayer player = getPlayerById(participant.getPlayerId());
                if (player != null) {
                    soundManager.playConnectedSoundForPlayer(player);
                    System.out.println("DEBUG: Played connection sound for HANDHELD snail #" +
                            participant.getSnailNumber() + " (player " + player.getName().getString() + ")");
                }
            }
        }
    }

    /**
     * âœ¨ FIXED: Hang up sound now works for handheld snails!
     */
    private void playHangUpSoundForPlayer(ServerPlayer player, CallSession callSession) {
        // âœ¨ FIX: Check if player has handheld snail FIRST
        Integer handheldSnailNumber = playerHandheldSnails.get(player.getUUID());
        if (handheldSnailNumber != null) {
            // Player has handheld snail - play sound at player position
            soundManager.playHangUpSoundForPlayer(player);
            System.out.println("TransponderCallManager: Playing handheld hang up sound for player " +
                    player.getName().getString());
            return;
        }

        // Otherwise, find closest block snail for locational sound
        BlockPos playerPos = player.blockPosition();
        BlockPos closestSnailPos = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            double distance = playerPos.distSqr(pos);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestSnailPos = pos;
            }
        }

        if (closestSnailPos != null) {
            soundManager.playHangUpSoundAtSnail(player, closestSnailPos);
            System.out.println("TransponderCallManager: Playing block hang up sound at " + closestSnailPos);
        } else {
            // No block snails nearby - still play handheld sound as fallback
            soundManager.playHangUpSoundForPlayer(player);
            System.out.println("TransponderCallManager: No nearby snails - playing handheld hang up sound as fallback");
        }
    }


    /**
     * FIX #2: Handle busy signal for BOTH block and handheld callers
     */
    private void handleTargetBusy(ServerPlayer caller, int callerSnailNumber, int targetSnailNumber) {
        System.out.println("DEBUG handleTargetBusy: Caller snail #" + callerSnailNumber);

        // Check if caller has a handheld snail
        if (isHandheldSnail(callerSnailNumber)) {
            soundManager.playBusySoundForPlayer(caller);
            System.out.println("DEBUG: Played busy sound for HANDHELD caller #" + callerSnailNumber);
        } else {
            // Caller is using a block snail
            TransponderSnailBlockEntity callerBlock = getRegisteredSnailBlock(callerSnailNumber);
            if (callerBlock != null) {
                soundManager.playBusySoundAtSnail(caller, callerBlock.getBlockPos());
                System.out.println("DEBUG: Played busy sound at BLOCK caller #" + callerSnailNumber);
            }
        }

        caller.displayClientMessage(
                Component.literal("Snail #" + targetSnailNumber + " is busy!")
                        .withStyle(ChatFormatting.RED),
                true
        );
    }

    private void handleCallTimeout(CallSession callSession) {
        stopRingingForCall(callSession);
        endCall(callSession.getCallId());
    }

    private void notifyCallRejected(CallSession callSession) {
        // Notification removed - handled by debug output and other systems
    }

    /**
     * FIX #2: Notify call ended for BOTH block and handheld participants
     */
    private void notifyCallEnded(CallSession callSession) {
        System.out.println("DEBUG notifyCallEnded: Notifying " + callSession.getParticipantCount() + " participants");

        // Play disconnection sound for block snails
        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            List<ServerPlayer> nearbyPlayers = getPlayersNearSnail(
                    (ServerLevel) getWorldForPosition(pos), pos, VoiceChatConstants.getSnailInteractionRange());
            if (!nearbyPlayers.isEmpty()) {
                soundManager.playCallDisconnectedSoundAtSnail(nearbyPlayers.get(0), pos);
                System.out.println("DEBUG: Played disconnect sound at BLOCK " + pos);
            }
        }

        // FIX #2: Play for handheld snails
        for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
            if (participant.isHandheld() && participant.hasActivePlayer()) {
                ServerPlayer player = getPlayerById(participant.getPlayerId());
                if (player != null) {
                    soundManager.playDisconnectedSoundForPlayer(player);
                    System.out.println("DEBUG: Played disconnect sound for HANDHELD snail #" +
                            participant.getSnailNumber() + " (player " + player.getName().getString() + ")");
                }
            }
        }

        // Notify block entities
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
            if (block != null) {
                block.onCallEnded(callSession.getCallId());
            }
        }
    }

    private boolean snailExists(int snailNumber) {
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        return registry != null && registry.isNumberAssigned(snailNumber);
    }

    private ItemStack findSnailItemInInventory(ServerPlayer player, int snailNumber) {
        ItemStack mainHand = player.getMainHandItem();
        if (isSnailItem(mainHand) && SnailNBTHandler.getSnailNumber(mainHand) == snailNumber) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        if (isSnailItem(offHand) && SnailNBTHandler.getSnailNumber(offHand) == snailNumber) {
            return offHand;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isSnailItem(stack) && SnailNBTHandler.getSnailNumber(stack) == snailNumber) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private boolean isSnailItem(ItemStack stack) {
        return !stack.isEmpty() &&
                (stack.getItem() instanceof TransponderSnailItem ||
                        stack.getItem() instanceof BlockItem && ((BlockItem)stack.getItem()).getBlock() instanceof TransponderSnailBlock);
    }

    @Nullable
    public ServerPlayer getPlayerById(UUID playerId) {
        return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
    }

    public List<ServerPlayer> getPlayersNearSnail(ServerLevel level, BlockPos snailPos, double range) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(snailPos.getX() + 0.5, snailPos.getY() + 0.5, snailPos.getZ() + 0.5);
            if (distance <= range * range) {
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }

    @Nullable
    private ServerLevel getWorldForPosition(BlockPos pos) {
        for (TransponderSnailBlockEntity block : registeredSnailBlocks.values()) {
            if (block.getBlockPos().equals(pos)) {
                return (ServerLevel) block.getLevel();
            }
        }
        return null;
    }

    private void cleanupInactiveCalls() {
        List<UUID> toRemove = new ArrayList<>();
        for (CallSession session : activeCalls.values()) {
            if (session.shouldAutoEnd()) {
                toRemove.add(session.getCallId());
            }
        }
        for (UUID callId : toRemove) {
            endCall(callId);
        }

        // Clean up stale player-to-call mappings
        cleanupStalePlayerMappings();
    }

    /**
     * Clean up stale player-to-call mappings where the call no longer exists
     * or the player is not actually a participant
     */
    private void cleanupStalePlayerMappings() {
        List<UUID> stalePlayerIds = new ArrayList<>();

        for (Map.Entry<UUID, UUID> entry : playerToCallId.entrySet()) {
            UUID playerId = entry.getKey();
            UUID callId = entry.getValue();

            // Check if the call still exists
            CallSession session = activeCalls.get(callId);
            if (session == null) {
                // Call doesn't exist - stale mapping
                stalePlayerIds.add(playerId);
                System.out.println("TransponderCallManager: Found stale player mapping for " +
                        playerId.toString().substring(0, 8) + " (call doesn't exist)");
                continue;
            }

            // Check if player is actually a participant
            if (!session.isParticipant(playerId)) {
                // Player is mapped but not a participant - stale mapping
                stalePlayerIds.add(playerId);
                System.out.println("TransponderCallManager: Found stale player mapping for " +
                        playerId.toString().substring(0, 8) + " (not a participant)");
            }
        }

        // Remove stale mappings
        for (UUID playerId : stalePlayerIds) {
            playerToCallId.remove(playerId);
            playersInCall.remove(playerId);
        }

        if (!stalePlayerIds.isEmpty()) {
            System.out.println("TransponderCallManager: Cleaned up " + stalePlayerIds.size() + " stale player mappings");
        }
    }

    public void onPlayerDisconnect(UUID playerId) {
        playersInCall.remove(playerId);
        if (isInCall(playerId)) {
            ServerPlayer player = getPlayerById(playerId);
            if (player != null) {
                endCall(player);
            }
        }
    }

    // =================== PUBLIC QUERY METHODS ===================

    public boolean isInCall(UUID playerId) {
        return playerToCallId.containsKey(playerId);
    }

    public boolean isSnailInCall(int snailNumber) {
        return snailToCallId.containsKey(snailNumber);
    }

    @Nullable
    public UUID getPlayerCallId(UUID playerId) {
        return playerToCallId.get(playerId);
    }

    public Collection<CallSession> getActiveCalls() {
        return new ArrayList<>(activeCalls.values());
    }

    /**
     * Get the scheduler for delayed tasks (used by InterceptionHelper)
     */
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    /**
     * Get a specific CallSession by its ID
     */
    @Nullable
    public CallSession getCallSessionById(UUID callId) {
        return activeCalls.get(callId);
    }

    public CallSoundManager getSoundManager() {
        return soundManager;
    }

    public void setAudioRelay(SnailAudioRelay audioRelay) {
        this.audioRelay = audioRelay;
        // âœ¨ INTERCEPTION: Link interception manager to audio relay
        if (audioRelay != null && interceptionManager != null) {
            audioRelay.setInterceptionManager(interceptionManager);
        }
    }

    public SnailAudioRelay getAudioRelay() {
        return audioRelay;
    }

    // âœ¨ INTERCEPTION: Getter for interception manager
    public CallInterceptionManager getInterceptionManager() {
        return interceptionManager;
    }

    public Map<Integer, TransponderSnailBlockEntity> getRegisteredSnailBlocks() {
        return new HashMap<>(registeredSnailBlocks);
    }

    /**
     * NEW METHOD #4: debugPrintCallState
     * Location: Add this as a new public method anywhere in the class (suggest near the end)
     */
    public void debugPrintCallState() {
        System.out.println("=== TransponderCallManager Debug ===");
        System.out.println("Active calls: " + activeCalls.size());
        System.out.println("PlayerToCallId entries: " + playerToCallId.size());

        for (Map.Entry<UUID, UUID> entry : playerToCallId.entrySet()) {
            ServerPlayer player = getPlayerById(entry.getKey());
            String playerName = player != null ? player.getName().getString() : "Unknown";
            System.out.println("  Player " + playerName + " (" + entry.getKey().toString().substring(0, 8) +
                    ") â†’ Call " + entry.getValue().toString().substring(0, 8));
        }

        System.out.println("SnailToCallId entries: " + snailToCallId.size());
        for (Map.Entry<Integer, UUID> entry : snailToCallId.entrySet()) {
            System.out.println("  Snail #" + entry.getKey() + " â†’ Call " +
                    entry.getValue().toString().substring(0, 8));
        }

        System.out.println("Call Sessions:");
        for (CallSession session : activeCalls.values()) {
            System.out.println("  " + session.toString());
            for (CallSession.CallParticipant p : session.getAllParticipants()) {
                System.out.println("    - " + p.toString());
            }
            System.out.println("    Audio channels: Block=" + session.getProximityChannels().size() +
                    ", Handheld=" + session.getHandheldChannels().size());
        }
        System.out.println("====================================");
    }

    // =================== âœ¨ INTERCEPTION MANAGEMENT ===================

    /**
     * Validate all active interceptions - called periodically by scheduler
     */
    private void validateAllInterceptions() {
        if (interceptionManager == null) return;

        try {
            // Get all players with active interceptions
            for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (interceptionManager.isIntercepting(player.getUUID())) {
                    interceptionManager.validateInterceptions(player);
                }
            }
        } catch (Exception e) {
            System.err.println("Error validating interceptions: " + e.getMessage());
        }
    }

    /**
     * Clean up invalid interceptions - called periodically by scheduler
     */
    private void cleanupInterceptions() {
        if (interceptionManager != null) {
            try {
                interceptionManager.cleanupInvalidInterceptions();
            } catch (Exception e) {
                System.err.println("Error cleaning up interceptions: " + e.getMessage());
            }
        }
    }

    /**
     * Process searching sessions - called periodically by scheduler
     */
    private void processSearchingSessions() {
        if (interceptionManager != null) {
            try {
                interceptionManager.processSearchingSessions();
            } catch (Exception e) {
                System.err.println("Error processing searching sessions: " + e.getMessage());
            }
        }
    }

    /**
     * Update call states for all interceptors (sync CALL state when no audio)
     * Called every 200ms by scheduler
     */
    private void updateCallStates() {
        if (interceptionManager != null) {
            try {
                interceptionManager.updateCallStates();
            } catch (Exception e) {
                System.err.println("Error updating call states: " + e.getMessage());
            }
        }
    }

    /**
     * Start intercepting a call - called when player opens Black Transponder Snail
     */
    public boolean startInterception(ServerPlayer player, UUID targetCallId) {
        if (interceptionManager == null) {
            System.err.println("Cannot start interception: interception manager not initialized");
            return false;
        }
        return interceptionManager.startInterception(player, targetCallId);
    }

    /**
     * Stop intercepting - called when player closes Black Transponder Snail
     */
    public void stopInterception(UUID playerId) {
        if (interceptionManager != null) {
            interceptionManager.stopInterception(playerId);
        }
    }

    /**
     * Switch to next call - called when player crouches and right-clicks while intercepting
     */
    public boolean switchToNextCall(ServerPlayer player) {
        if (interceptionManager == null) {
            return false;
        }
        return interceptionManager.switchToNextCall(player);
    }

    /**
     * Check if a player is currently intercepting
     */
    public boolean isPlayerIntercepting(UUID playerId) {
        return interceptionManager != null && interceptionManager.isIntercepting(playerId);
    }

    /**
     * Check if a call is being intercepted
     */
    public boolean isCallBeingIntercepted(UUID callId) {
        return interceptionManager != null && interceptionManager.isCallBeingIntercepted(callId);
    }

    // =================== ✨ JAMMING ===================

    /**
     * ✨ JAMMING: Periodically checks all active and ringing calls to see if any
     * participant has moved into (or was already in) a Horned Den Den Mushi
     * jammer's radius.  If so, the call is terminated and participants notified.
     *
     * Called every 2 seconds by the constructor scheduler.
     */
    private void checkJammedCalls() {
        try {
            HornedDDMJammerManager jammerManager = HornedDDMJammerManager.getInstance();
            if (jammerManager.getActiveJammerCount() == 0) return;

            for (CallSession session : new ArrayList<>(activeCalls.values())) {
                // Only check RINGING or CONNECTED calls
                if (session.getState() != CallSession.CallState.RINGING
                        && session.getState() != CallSession.CallState.CONNECTED) {
                    continue;
                }

                boolean shouldEnd = false;

                for (CallSession.CallParticipant participant : session.getAllParticipants()) {
                    if (participant.isHandheld() && participant.hasActivePlayer()) {
                        ServerPlayer player = getPlayerById(participant.getPlayerId());
                        if (player != null && jammerManager.isPlayerJammed(player)) {
                            shouldEnd = true;
                            System.out.println("TransponderCallManager: Ending call "
                                    + session.getCallId().toString().substring(0, 8)
                                    + " — handheld participant " + player.getName().getString()
                                    + " is inside a jammer's range.");
                            break;
                        }
                    } else if (participant.isBlock()) {
                        TransponderSnailBlockEntity blockEntity =
                                getRegisteredSnailBlock(participant.getSnailNumber());
                        if (blockEntity != null
                                && blockEntity.getLevel() instanceof ServerLevel sl
                                && jammerManager.isBlockPosJammed(blockEntity.getBlockPos(), sl)) {
                            shouldEnd = true;
                            System.out.println("TransponderCallManager: Ending call "
                                    + session.getCallId().toString().substring(0, 8)
                                    + " — block snail #" + participant.getSnailNumber()
                                    + " is inside a jammer's range.");
                            break;
                        }
                    }
                }

                if (shouldEnd) {
                    // Notify all active player participants before ending the call
                    for (UUID playerId : session.getActivePlayerParticipants()) {
                        ServerPlayer player = getPlayerById(playerId);
                        if (player != null) {
                            player.displayClientMessage(
                                    Component.literal("Call ended!")
                                            .withStyle(ChatFormatting.YELLOW),
                                    true
                            );
                        }
                    }
                    endCall(session.getCallId());
                }
            }
        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error in checkJammedCalls: " + e.getMessage());
        }
    }

    // =================== CLEANUP ===================

    public void cleanup() {
        // ✨ INTERCEPTION: Cleanup interception manager
        if (interceptionManager != null) {
            interceptionManager.cleanup();
        }

        // ✨ JAMMING: Clear all jammer registrations on server stop
        HornedDDMJammerManager.getInstance().clear();

        for (UUID callId : new HashSet<>(activeCalls.keySet())) {
            try {
                endCall(callId);
            } catch (Exception e) {
                System.err.println("Error ending call during cleanup: " + e.getMessage());
            }
        }
        activeCalls.clear();
        registeredSnailBlocks.clear();
        playerToCallId.clear();
        snailToCallId.clear();
        playersInCall.clear();
        ringingSnails.clear();
        if (soundManager != null) {
            soundManager.cleanup();
        }
    }

    public void shutdown() {
        cleanup();
        scheduler.shutdown();
    }
}