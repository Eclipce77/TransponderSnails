package net.eclipce.transpondersnails.block.entity;

import net.eclipce.transpondersnails.item.DenDenMushiItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.screen.DialingMenu;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.eclipce.transpondersnails.voice.server.CallSoundManager;
import net.eclipce.transpondersnails.voice.server.CallSession;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.network.packets.SnailNumberSyncPacket;
import net.eclipce.transpondersnails.network.packets.CallStateSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TransponderSnailBlockEntity extends BlockEntity implements MenuProvider, CallSoundManager.BlockstateUpdateCallback {

    // Add these static fields to track global state
    private static boolean isServerShuttingDown = false;
    private static final Set<BlockPos> processedPositions = ConcurrentHashMap.newKeySet();

    private UUID activeCallId;
    private boolean isRinging = false;
    private String dialedNumber = "";

    // Snail identity - persisted directly in the block entity
    public UUID snailUUID = null;
    public int assignedSnailNumber = -1;
    public boolean initialized = false;
    private boolean needsValidation = false;

    // Call state tracking
    private CallStateSyncPacket.CallState currentCallState = CallStateSyncPacket.CallState.IDLE;
    private long ringStartTime = 0;

    // Visual state tracking for blockstates
    private boolean hasAmbientSound = false;
    private boolean inActiveCall = false;
    private long lastBlockstateUpdate = 0;

    // Instance fields for lifecycle tracking
    private boolean isUnloading = false;
    private boolean hasBeenLoaded = false;
    private boolean validationCompleted = false;

    // CallSession integration
    private CallSession currentCallSession = null;

    // Audio readiness tracking
    private boolean audioReady = false;

    // Snail color data
    public int bodyColor = -1; // RGB color for snail body (-1 = uninitialized)
    public int shellColor = 0; // Dye color index (0-15, default white)
    public boolean colorsInitialized = false;

    // UV mask texture path for eye protection
    private static final ResourceLocation UV_MASK_TEXTURE =
            new ResourceLocation("transpondersnails:block/transpondersnail/snail/transponder_snail_uv_mask");

    public TransponderSnailBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.TRANSPONDER_SNAIL_BE.get(), pPos, pBlockState);

    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.transpondersnails.dialing");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory pInventory, Player pPlayer) {
        // Ensure snail has a number when GUI opens
        if (!pPlayer.level().isClientSide) {
            ensureSnailNumberAssigned(pPlayer);
        }
        return new DialingMenu(containerId, pInventory, this);
    }

    // =================== CALLSESSION-BASED BLOCKSTATE MANAGEMENT ===================

    /**
     * Updates the visual blockstate based on CallSession state
     */
    private void updateBlockstateVisuals() {
        if (level == null || level.isClientSide()) {
            return; // Server side only
        }

        // Determine the correct model based on CallSession state
        String targetModel = determineModelFromCallSession();

        // Map model name to blockstate values
        boolean newHasSound = shouldHaveSoundState(targetModel);
        boolean newInCall = shouldHaveCallState(targetModel);

        // Get current blockstate values
        boolean currentHasSound = getCurrentVisualSoundState();
        boolean currentInCall = getCurrentVisualCallState();

        // Only update if changed or it's been a while since last update
        long now = System.currentTimeMillis();
        if (currentHasSound != newHasSound ||
                currentInCall != newInCall) {

            TransponderSnailBlock.updateVisualState(level, worldPosition, newHasSound, newInCall);
            lastBlockstateUpdate = now;

            System.out.println("TransponderSnailBlockEntity: Updated blockstate to " + targetModel +
                    " (Sound: " + newHasSound + ", Call: " + newInCall + ") for CallSession state: " +
                    (currentCallSession != null ? currentCallSession.getState() : "NO_SESSION") +
                    " at " + worldPosition);
        }
    }

    /**
     * Determines which model should be used based on CallSession state
     */
    private String determineModelFromCallSession() {
        // If no active call session, check for ambient sounds (like disconnect sound)
        if (currentCallSession == null) {
            if (hasAmbientSound) {
                return "transponder_snail_sound"; // Disconnect/end call sound
            }
            return "transponder_snail"; // Normal idle
        }

        // Use CallSession state to determine model
        switch (currentCallSession.getState()) {
            case INITIATING:
                // Calling snail - shows normal texture while setting up call
                return "transponder_snail";

            case RINGING:
                // Receiving snail - shows sound texture (ringing)
                if (isThisSnailBeingCalled()) {
                    return "transponder_snail_sound"; // Snail that is ringing
                } else {
                    return "transponder_snail"; // Snail that is calling
                }

            case CONNECTED:
                // In call - check for active sound transmission
                if (hasAmbientSound) {
                    return "transponder_snail_active"; // In call receiving sound
                } else {
                    return "transponder_snail_call"; // In call no sound
                }

            case ENDING:
                // Call ending - play disconnect sound
                return "transponder_snail_sound";

            case ENDED:
                // Call ended - brief disconnect sound then return to idle
                if (hasAmbientSound) {
                    return "transponder_snail_sound"; // On call ended/disconnected
                } else {
                    return "transponder_snail"; // Back to idle
                }

            default:
                return "transponder_snail"; // Fallback to idle
        }
    }

    /**
     * Check if this specific snail is the one being called (ringing)
     */
    private boolean isThisSnailBeingCalled() {
        if (currentCallSession == null || assignedSnailNumber == -1) {
            return false;
        }

        // Check if this snail is a participant and if it's in ringing state
        return currentCallSession.isParticipant(assignedSnailNumber) && isRinging;
    }

    /**
     * Maps model name to HAS_SOUND blockstate value
     */
    private boolean shouldHaveSoundState(String modelName) {
        switch (modelName) {
            case "transponder_snail":
                return false; // Idle, calling
            case "transponder_snail_sound":
                return true;  // Ringing, busy, disconnect
            case "transponder_snail_call":
                return false; // In call, no sound
            case "transponder_snail_active":
                return true;  // In call with sound
            default:
                return false;
        }
    }

    /**
     * Maps model name to IN_CALL blockstate value
     */
    private boolean shouldHaveCallState(String modelName) {
        switch (modelName) {
            case "transponder_snail":
                return false; // Idle, calling
            case "transponder_snail_sound":
                return false; // Ringing, busy, disconnect
            case "transponder_snail_call":
                return true;  // In call, no sound
            case "transponder_snail_active":
                return true;  // In call with sound
            default:
                return false;
        }
    }

    /**
     * Set the current call session for this snail
     */
    public void setCallSession(CallSession callSession) {
        this.currentCallSession = callSession;
        updateBlockstateVisuals();
        System.out.println("TransponderSnailBlockEntity: Call session set to " +
                (callSession != null ? callSession.getState() : "null") +
                " for snail #" + assignedSnailNumber);
    }

    /**
     * Clear the current call session
     */
    public void clearCallSession() {
        this.currentCallSession = null;
        updateBlockstateVisuals();
        System.out.println("TransponderSnailBlockEntity: Call session cleared for snail #" + assignedSnailNumber);
    }

    /**
     * Get the current call session
     */
    public CallSession getCallSession() {
        return currentCallSession;
    }

    /**
     * Check if block currently has the "sound" visual state
     */
    private boolean getCurrentVisualSoundState() {
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof TransponderSnailBlock) {
                return state.getValue(TransponderSnailBlock.HAS_SOUND);
            }
        }
        return false;
    }

    /**
     * Check if block currently has the "call" visual state
     */
    public boolean getCurrentVisualCallState() {
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof TransponderSnailBlock) {
                return state.getValue(TransponderSnailBlock.IN_CALL);
            }
        }
        return false;
    }

    // =================== BLOCKSTATE CALLBACK IMPLEMENTATION ===================

    @Override
    public void onSoundStateChanged(BlockPos pos, boolean hasAmbientSound) {
        if (pos.equals(worldPosition)) {
            this.hasAmbientSound = hasAmbientSound;
            updateBlockstateVisuals();

            // Log what type of sound event this is for debugging
            String soundType = "unknown";
            if (currentCallSession != null) {
                switch (currentCallSession.getState()) {
                    case RINGING:
                        soundType = hasAmbientSound ? "incoming ring" : "ring stopped";
                        break;
                    case CONNECTED:
                        soundType = hasAmbientSound ? "call audio" : "call audio stopped";
                        break;
                    case ENDING:
                    case ENDED:
                        soundType = hasAmbientSound ? "disconnect sound" : "disconnect sound ended";
                        break;
                }
            } else if (hasAmbientSound) {
                soundType = "ambient sound (no session)";
            }

            System.out.println("TransponderSnailBlockEntity: Sound state changed - " + soundType +
                    " hasAmbientSound: " + hasAmbientSound +
                    " sessionState: " + (currentCallSession != null ? currentCallSession.getState() : "NO_SESSION"));
        }
    }

    // =================== GLOBAL STATE MANAGEMENT ===================

    /**
     * Call this from your main mod class when server is shutting down
     */
    public static void setServerShuttingDown() {
        isServerShuttingDown = true;
        processedPositions.clear();
    }

    /**
     * Call this from your main mod class when server starts up
     */
    public static void setServerStartingUp() {
        isServerShuttingDown = false;
        processedPositions.clear();
    }

    // =================== CALL MANAGER HELPER METHODS ===================

    /**
     * Gets the call manager instance from the main mod class
     */
    private TransponderCallManager getCallManager() {
        return TransponderSnails.getCallManager();
    }

    /**
     * Safe method to check if call manager is available
     */
    private boolean isCallManagerAvailable() {
        return getCallManager() != null;
    }

    /**
     * Handle player interaction with the snail block
     * This should be called from TransponderSnailBlock.use() method
     *
     * @param player     The player interacting
     * @param isSneaking Whether the player is sneaking
     * @return The interaction result
     */
    public InteractionResult onPlayerInteraction(ServerPlayer player, boolean isSneaking) {
        if (!isCallManagerAvailable()) {
            player.displayClientMessage(
                    Component.literal("Voice chat system not available!")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        TransponderCallManager callManager = getCallManager();

        // Handle interactions based on current state and sneaking
        if (isRinging && activeCallId != null) {
            // Snail is ringing (incoming call)
            if (isSneaking) {
                // Sneak+Right Click while ringing: Open DialingMenu
                player.openMenu(this);
                return InteractionResult.SUCCESS;
            } else {
                // Right Click while ringing: Answer Call
                if (callManager.acceptCall(player, activeCallId)) {
                    setRinging(false);
                    setCallState(CallStateSyncPacket.CallState.CONNECTED);
                    player.displayClientMessage(
                            Component.literal("Call answered!")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN),
                            true
                    );
                    return InteractionResult.SUCCESS;
                } else {
                    player.displayClientMessage(
                            Component.literal("Failed to answer call!")
                                    .withStyle(net.minecraft.ChatFormatting.RED),
                            true
                    );
                    return InteractionResult.FAIL;
                }
            }
        } else if (activeCallId != null && currentCallState == CallStateSyncPacket.CallState.CONNECTED) {
            // Snail is in an active call
            if (isSneaking) {
                // Sneak+Right Click in call: Open DialingMenu
                player.openMenu(this);
                return InteractionResult.SUCCESS;
            } else {
                // Right Click in call: End Call (only if player is in the call)
                if (callManager.isInCall(player.getUUID())) {
                    callManager.endCall(player);
                    player.displayClientMessage(
                            Component.literal("Call ended!")
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW),
                            true
                    );
                    return InteractionResult.SUCCESS;
                } else {
                    // Player is not in the call - snail is busy
                    player.displayClientMessage(
                            Component.literal("Transponder Snail is busy!")
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW),
                            true
                    );
                    return InteractionResult.FAIL;
                }
            }
        } else if (currentCallState == CallStateSyncPacket.CallState.IDLE) {
            // Snail is idle (not in call, not ringing)
            // Both Right Click and Sneak+Right Click: Open DialingMenu
            player.openMenu(this);
            return InteractionResult.SUCCESS;
        } else {
            // Snail is in some other state (dialing, busy, etc.)
            if (isSneaking) {
                // Sneak+Right Click: Still allow GUI access for monitoring
                player.openMenu(this);
                return InteractionResult.SUCCESS;
            } else {
                // Right Click: Snail is busy
                player.displayClientMessage(
                        Component.literal("Transponder Snail is busy!")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW),
                        true
                );
                return InteractionResult.FAIL;
            }
        }
    }

    /**
     * Handle player interaction with the snail block
     */
    public InteractionResult onUse(ServerPlayer player) {
        return onPlayerInteraction(player, false);
    }

    /**
     * Reset audio readiness (called when call ends)
     */
    private void resetAudioReadiness() {
        this.audioReady = false;
    }

    // =================== ENHANCED CALL LIFECYCLE CALLBACKS ===================

    /**
     * Called by the call manager when this snail receives an incoming call
     */
    public void onIncomingCall(UUID callId, int callerSnailNumber, CallSession callSession) {
        this.activeCallId = callId;
        setRinging(true);
        setChanged();
        setCallState(CallStateSyncPacket.CallState.RINGING_IN);
        this.ringStartTime = System.currentTimeMillis();

        // Set the call session - this will trigger RINGING state
        setCallSession(callSession);

        // Notify nearby players
        List<ServerPlayer> nearbyPlayers = getNearbyListeners();
        for (ServerPlayer player : nearbyPlayers) {
            player.displayClientMessage(Component.literal("Transponder Snail is ringing!")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW),true);

            // Start ringtone sound for nearby players
            TransponderCallManager callManager = getCallManager();
            if (callManager != null) {
                CallSoundManager soundManager = callManager.getSoundManager();
                if (soundManager != null) {
                    soundManager.playLocationalRingTone(player, worldPosition);
                }
            }
        }

        // Sync call state to clients
        CallStateSyncPacket packet = new CallStateSyncPacket(
                CallStateSyncPacket.CallState.RINGING_IN,
                callId,
                callerSnailNumber,
                "Incoming from #" + callerSnailNumber
        );

        // Send packet to nearby players with dialing menus open
        if (level instanceof ServerLevel) {
            for (ServerPlayer player : nearbyPlayers) {
                if (player.containerMenu instanceof DialingMenu dialingMenu &&
                        dialingMenu.getBlockEntity() == this) {
                    ModPackets.sendToPlayer(packet, player);
                }
            }
        }

        System.out.println("TransponderSnailBlockEntity: Snail #" + assignedSnailNumber + " receiving call from #" + callerSnailNumber);
    }

    /**
     * Called by the call manager when a call is connected
     */
    public void onCallConnected(UUID callId, CallSession callSession) {
        this.activeCallId = callId;
        setRinging(false);
        setChanged();
        setCallState(CallStateSyncPacket.CallState.CONNECTED);

        // Update the call session state - this will trigger CONNECTED state
        setCallSession(callSession);

        List<ServerPlayer> nearbyPlayers = getNearbyListeners();

        // Notify nearby players
        for (ServerPlayer player : nearbyPlayers) {
            player.displayClientMessage(
                    Component.literal("Call connected!")
                            .withStyle(net.minecraft.ChatFormatting.GREEN),
                    true
            );
        }

        System.out.println("TransponderSnailBlockEntity: Snail #" + assignedSnailNumber + " call connected");
    }

    /**
     * Called when call is fully connected and audio channels are ready
     */
    public void onCallFullyConnected(UUID callId) {
        if (this.activeCallId != null && this.activeCallId.equals(callId)) {
            this.audioReady = true;
            setChanged();

            System.out.println("TransponderSnailBlockEntity: Snail #" + assignedSnailNumber +
                    " is now ready for audio transmission");
        }
    }

    /**
     * Called by the call manager when a call ends
     */
    public void onCallEnded(UUID callId) {
        boolean wasRinging = isRinging;

        this.activeCallId = null;
        setRinging(false);
        clearDialedNumber();
        resetAudioReadiness(); // Add this line
        setChanged();
        setCallState(CallStateSyncPacket.CallState.IDLE);

        // Clear the call session - this will return to idle state
        clearCallSession();

        List<ServerPlayer> nearbyPlayers = getNearbyListeners();

        // Notify nearby players
        for (ServerPlayer player : nearbyPlayers) {
            if (wasRinging) {
                player.displayClientMessage(
                        Component.literal("Incoming call ended.")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.literal("Call ended.")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        true
                );
            }
        }

        System.out.println("TransponderSnailBlockEntity: Snail #" + assignedSnailNumber + " call ended");
    }

    /**
     * Called by the call manager when a call attempt fails
     */
    public void onCallFailed(String reason) {
        setCallState(CallStateSyncPacket.CallState.IDLE);
        clearCallSession();

        // Notify nearby players
        List<ServerPlayer> nearbyPlayers = getNearbyListeners();
        for (ServerPlayer player : nearbyPlayers) {
            player.displayClientMessage(
                    Component.literal("Call failed: " + reason)
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true
            );
        }

        System.out.println("TransponderSnailBlockEntity: Snail #" + assignedSnailNumber + " call failed: " + reason);
    }

    /**
     * Called by the call manager when target is busy
     */
    public void onTargetBusy(int targetNumber) {
        setCallState(CallStateSyncPacket.CallState.BUSY);
        updateBlockstateVisuals();

        List<ServerPlayer> nearbyPlayers = getNearbyListeners();

        // Notify nearby players
        for (ServerPlayer player : nearbyPlayers) {
            player.displayClientMessage(
                    Component.literal("Snail #" + targetNumber + " is busy!")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW),
                    true
            );
        }

        // Reset to idle after a short delay
        if (level instanceof ServerLevel) {
            level.getServer().execute(() -> {
                try {
                    Thread.sleep(2000); // 2 second delay
                    setCallState(CallStateSyncPacket.CallState.IDLE);
                    clearCallSession();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    /**
     * Check if this snail is ready to transmit/receive audio
     */
    public boolean isAudioReady() {
        return audioReady &&
                currentCallState == CallStateSyncPacket.CallState.CONNECTED &&
                getCurrentVisualCallState();
    }

    // =================== CALL STATE MANAGEMENT ===================

    /**
     * Sets the current call state and syncs to nearby players with open GUIs
     */
    private void setCallState(CallStateSyncPacket.CallState newState) {
        if (newState != currentCallState) {
            CallStateSyncPacket.CallState oldState = currentCallState;
            this.currentCallState = newState;
            setChanged();

            updateBlockstateVisuals();

            // Sync call state to nearby players with open dialing menus
            if (level instanceof ServerLevel) {
                List<ServerPlayer> nearbyPlayers = getNearbyListeners();
                for (ServerPlayer player : nearbyPlayers) {
                    if (player.containerMenu instanceof DialingMenu dialingMenu &&
                            dialingMenu.getBlockEntity() == this) {
                        ModPackets.sendToPlayer(new CallStateSyncPacket(newState, activeCallId, -1,
                                getStateMessage(newState)), player);
                    }
                }
            }

            System.out.println("TransponderSnailBlockEntity: Call state changed from " + oldState + " to " + newState);
        }
    }

    /**
     * Gets a human-readable message for the current call state
     */
    private String getStateMessage(CallStateSyncPacket.CallState state) {
        switch (state) {
            case IDLE:
                return "";
            case DIALING:
                return "Dialing...";
            case RINGING_OUT:
                return "Calling...";
            case RINGING_IN:
                return "Incoming call";
            case CONNECTED:
                return "Connected";
            case BUSY:
                return "Busy";
            case DISCONNECTED:
                return "Disconnected";
            default:
                return "";
        }
    }

    public CallStateSyncPacket.CallState getCurrentCallState() {
        return currentCallState;
    }

    // =================== ENHANCED COLOR METHODS ===================

    /**
     * Transfer colors from a Den Den Mushi item to this block entity
     * Used during crafting or placement
     */
    public void transferColorsFromDenDenMushi(ItemStack denDenMushiItem) {
        if (denDenMushiItem.getItem() instanceof DenDenMushiItem && DenDenMushiItem.isCaptured(denDenMushiItem)) {
            this.bodyColor = DenDenMushiItem.getBodyColor(denDenMushiItem);
            this.shellColor = DenDenMushiItem.getShellColor(denDenMushiItem);
            this.colorsInitialized = true;
            setChanged();

            System.out.println("TransponderSnailBlockEntity: Transferred colors from Den Den Mushi - " +
                    "Body: #" + Integer.toHexString(bodyColor) +
                    ", Shell: " + DyeColor.byId(shellColor).getName());
        }
    }

    /**
     * Create a Den Den Mushi item with this block entity's colors
     * Used when breaking the block with colors preserved
     */
    public ItemStack createDenDenMushiWithColors() {
        ItemStack stack = new ItemStack(ModItems.DEN_DEN_MUSHI.get());

        if (colorsInitialized) {
            CompoundTag nbt = stack.getOrCreateTag();
            nbt.putInt("BodyColor", bodyColor);
            nbt.putInt("ShellColor", shellColor);
        }

        return stack;
    }

    public boolean isColorsInitialized() {
        return colorsInitialized;
    }

    /**
     * Check if this block entity can be dyed
     */
    public boolean canBeDyed() {
        return colorsInitialized || hasAssignedNumber();
    }

    /**
     * Apply dye to this block entity (enhanced version)
     */
    public boolean applyDye(Item dyeItem, @Nullable Player player) {
        if (dyeItem instanceof DyeItem dye) {
            // REMOVED: Don't call initializeSnailColors here - colors should already exist

            int newColor = dye.getDyeColor().getId();
            if (newColor != shellColor) {
                setShellColor(newColor);

                if (player instanceof ServerPlayer serverPlayer) {

                }

                return true;
            } else {
                if (player instanceof ServerPlayer serverPlayer) {

                }
            }
        }
        return false;
    }



    /**
     * Gets color data formatted for client-side rendering
     */
    public CompoundTag getColorData() {
        CompoundTag data = new CompoundTag();
        data.putInt("BodyColor", getBodyColor());
        data.putInt("ShellColor", shellColor);
        data.putBoolean("ColorsInitialized", colorsInitialized);
        return data;
    }

    /**
     * Enhanced version that includes colors in render data
     */
    public CompoundTag getBlockEntityRenderData() {
        CompoundTag data = new CompoundTag();
        data.putInt("BodyColor", getBodyColor());
        data.putInt("ShellColor", shellColor);
        data.putBoolean("ColorsInitialized", colorsInitialized);
        return data;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag); // This saves ALL data including colors
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag); // This loads ALL data including colors
    }

    // 3. Test method - Add this temporarily to debug
    public void testColorSystem() {
        System.out.println("=== TransponderSnail Color Debug ===");
        System.out.println("Colors initialized: " + colorsInitialized);
        System.out.println("Body color: #" + Integer.toHexString(bodyColor));
        System.out.println("Shell color: " + shellColor + " (" + DyeColor.byId(shellColor).getName() + ")");
        System.out.println("Level client side: " + (level != null ? level.isClientSide : "null"));
        System.out.println("===================================");
    }

    // =================== LIFECYCLE METHODS ===================

    /**
     * Called when the block entity is loaded (chunk load, server start, etc.)
     */
    @Override
    public void onLoad() {
        super.onLoad();

        if (level != null && level.isClientSide && !colorsInitialized) {
            // Request sync from server on client load
            setChanged();
        }

        if (!level.isClientSide && !isUnloading) {
            System.out.println("TransponderSnailBlockEntity: onLoad called for position " + worldPosition);

            // Prevent processing the same position multiple times
            if (processedPositions.contains(worldPosition)) {
                System.out.println("TransponderSnailBlockEntity: Skipping duplicate onLoad for position " + worldPosition);
                return;
            }

            processedPositions.add(worldPosition);
            hasBeenLoaded = true;

            // Register for sound state updates with the sound manager
            if (isCallManagerAvailable()) {
                CallSoundManager soundManager = getCallManager().getSoundManager();
                if (soundManager != null) {
                    soundManager.registerBlockstateCallback(this);
                    System.out.println("TransponderSnailBlockEntity: Registered blockstate callback for position " + worldPosition);
                }
            }

            // Handle deferred validation
            if (needsValidation && !validationCompleted) {
                // Schedule validation on next server tick (only once)
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.getServer().execute(() -> {
                        if (!isUnloading && !isServerShuttingDown && needsValidation && !validationCompleted) {
                            performDeferredValidation();
                        }
                    });
                }
            } else if (hasAssignedNumber() && !isUnloading) {
                // Re-register with call manager if we have valid data
                registerWithCallManager();
            }
        }
    }

    /**
     * Called when the block entity is unloaded or removed
     */
    @Override
    public void setRemoved() {
        if (isUnloading) {
            return;
        }

        isUnloading = true;
        processedPositions.remove(worldPosition);

        if (!level.isClientSide) {
            System.out.println("TransponderSnailBlockEntity: setRemoved called for position " + worldPosition);

            // Unregister from call manager
            unregisterFromCallManager();

            // Unregister sound callback
            if (isCallManagerAvailable()) {
                CallSoundManager soundManager = getCallManager().getSoundManager();
                if (soundManager != null) {
                    soundManager.unregisterBlockstateCallback(this);
                    System.out.println("TransponderSnailBlockEntity: Unregistered blockstate callback for position " + worldPosition);
                }
            }

            // End any active calls
            if (activeCallId != null && isCallManagerAvailable()) {
                TransponderCallManager callManager = getCallManager();
                callManager.endCallBySnailNumber(assignedSnailNumber);
            }

            // Reset blockstate to idle
            if (!isServerShuttingDown && level != null && level.getBlockState(worldPosition).getBlock() instanceof TransponderSnailBlock) {
                TransponderSnailBlock.updateVisualState(level, worldPosition, false, false);
            }
        }

        super.setRemoved();
    }

    // =================== ENHANCED SNAIL NUMBERING SYSTEM ===================

    /**
     * Enhanced deferred validation that prevents infinite loops
     */
    private void performDeferredValidation() {
        if (!needsValidation || level.isClientSide || isUnloading || isServerShuttingDown || validationCompleted) {
            return;
        }

        validationCompleted = true;

        System.out.println("TransponderSnailBlockEntity: Starting deferred validation for snail at " + worldPosition);

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            System.err.println("TransponderSnailBlockEntity: Registry not available - skipping validation");
            needsValidation = false;
            return;
        }

        boolean restored = false;

        // If we have both UUID and number, validate they match in registry
        if (snailUUID != null && assignedSnailNumber != -1 && initialized) {
            UUID registryUUID = registry.getSnailByNumber(assignedSnailNumber);
            if (registryUUID != null && registryUUID.equals(snailUUID)) {
                // Data is valid
                System.out.println("TransponderSnailBlockEntity: Validation passed - snail #" + assignedSnailNumber + " matches registry");
                restored = true;
            } else {
                // Data mismatch - try to restore
                System.out.println("TransponderSnailBlockEntity: Data mismatch detected - attempting restoration");
                int restoredNumber = registry.assignNumberToSnail(snailUUID);
                if (restoredNumber != -1) {
                    this.assignedSnailNumber = restoredNumber;
                    this.initialized = true;
                    setChanged();
                    restored = true;
                    System.out.println("TransponderSnailBlockEntity: Successfully restored with number #" + restoredNumber);
                }
            }
        }
        // If we have UUID but no number, try to restore
        else if (snailUUID != null && (assignedSnailNumber == -1 || !initialized)) {
            System.out.println("TransponderSnailBlockEntity: Found UUID without number - attempting restoration");
            int registryNumber = registry.getSnailNumber(snailUUID);
            if (registryNumber != -1) {
                // UUID exists in registry
                this.assignedSnailNumber = registryNumber;
                this.initialized = true;
                setChanged();
                restored = true;
                System.out.println("TransponderSnailBlockEntity: Restored existing assignment #" + registryNumber);
            } else {
                // UUID not in registry - try to assign new number
                int restoredNumber = registry.assignNumberToSnail(snailUUID);
                if (restoredNumber != -1) {
                    this.assignedSnailNumber = restoredNumber;
                    this.initialized = true;
                    setChanged();
                    restored = true;
                    System.out.println("TransponderSnailBlockEntity: Restored new assignment #" + restoredNumber);
                }
            }
        }

        if (restored && !isUnloading && !isServerShuttingDown) {
            // Register with call manager now that we have valid data
            registerWithCallManager();
            System.out.println("TransponderSnailBlockEntity: Deferred validation completed successfully");
        } else if (!restored) {
            System.err.println("TransponderSnailBlockEntity: Deferred validation failed - snail will need manual reassignment");
            // Reset to uninitialized state
            this.initialized = false;
            this.assignedSnailNumber = -1;
            setChanged();
        }

        needsValidation = false;
    }

    /**
     * Ensures this placed snail has a number assigned.
     */
    public void ensureSnailNumberAssigned(Player player) {
        System.out.println("TransponderSnailBlockEntity: ensureSnailNumberAssigned called for snail at " + worldPosition +
                " (initialized: " + initialized + ", number: " + assignedSnailNumber + ")");

        // If we need validation, do it now
        if (needsValidation) {
            performDeferredValidation();
        }

        // Check if our current number is still valid in the registry
        if (initialized && snailUUID != null && assignedSnailNumber != -1) {
            SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
            if (registry != null) {
                UUID registryUUID = registry.getSnailByNumber(assignedSnailNumber);
                if (registryUUID != null && registryUUID.equals(snailUUID)) {
                    // Number is still valid - just sync to client
                    if (player instanceof ServerPlayer serverPlayer) {
                        ModPackets.sendToPlayer(new SnailNumberSyncPacket(assignedSnailNumber), serverPlayer);
                        System.out.println("TransponderSnailBlockEntity: Re-syncing existing valid number #" + assignedSnailNumber + " to client");
                    }
                    return;
                } else {
                    // Number is no longer valid - attempt restoration
                    System.out.println("TransponderSnailBlockEntity: Number #" + assignedSnailNumber + " no longer valid, attempting restoration");
                    int restoredNumber = registry.assignNumberToSnail(snailUUID);
                    if (restoredNumber != -1) {
                        this.assignedSnailNumber = restoredNumber;
                        setChanged();
                        registerWithCallManager();

                        if (player instanceof ServerPlayer serverPlayer) {
                            ModPackets.sendToPlayer(new SnailNumberSyncPacket(restoredNumber), serverPlayer);
                            Component message = Component.literal("Your Transponder Snail number has been restored to #" + restoredNumber)
                                    .withStyle(net.minecraft.ChatFormatting.GREEN);
                            serverPlayer.sendSystemMessage(message);
                        }
                        return;
                    } else {
                        // Restoration failed - reset and continue to new assignment
                        System.err.println("TransponderSnailBlockEntity: Failed to restore number - will assign new one");
                        this.initialized = false;
                        this.assignedSnailNumber = -1;
                    }
                }
            }
        }

        try {
            // Generate new UUID if needed
            if (snailUUID == null) {
                snailUUID = UUID.randomUUID();
                System.out.println("TransponderSnailBlockEntity: Generated new UUID: " + snailUUID);
            }

            SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
            if (registry != null) {
                int newNumber = registry.assignNumberToSnail(snailUUID);
                if (newNumber != -1) {
                    this.assignedSnailNumber = newNumber;
                    this.initialized = true;
                    initializeSnailColors();
                    setChanged();

                    // Update blockstate with initial shell color
                    if (level != null && !level.isClientSide) {
                        BlockState currentState = level.getBlockState(worldPosition);
                        level.sendBlockUpdated(worldPosition, currentState, currentState, Block.UPDATE_ALL);
                        System.out.println("TransponderSnailBlock: Assigned number and forced color sync");
                    }

                    // Register with call manager
                    registerWithCallManager();

                    // Send congratulations message
                    if (player instanceof ServerPlayer serverPlayer) {
                        Component message = Component.literal("Your Transponder Snail has been assigned number #" + assignedSnailNumber)
                                .withStyle(net.minecraft.ChatFormatting.GREEN);
                        serverPlayer.sendSystemMessage(message);

                        // Send sync packet to client
                        ModPackets.sendToPlayer(new SnailNumberSyncPacket(assignedSnailNumber), serverPlayer);
                        System.out.println("TransponderSnailBlockEntity: Assigned new number #" + assignedSnailNumber + " and synced to client");
                    }

                    System.out.println("TransponderSnailBlockEntity: Successfully assigned new number #" + assignedSnailNumber + " to snail at " + worldPosition);
                } else {
                    System.err.println("TransponderSnailBlockEntity: Failed to assign number - registry may be full");
                }
            } else {
                System.err.println("TransponderSnailBlockEntity: Registry not available for number assignment");
            }
        } catch (Exception e) {
            System.err.println("TransponderSnailBlockEntity: Error assigning snail number: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register this snail block with the call manager for location tracking
     */
    private void registerWithCallManager() {
        if (!isCallManagerAvailable() || !hasAssignedNumber() || isUnloading) {
            return;
        }

        TransponderCallManager callManager = getCallManager();
        if (!callManager.isSnailBlockRegistered(assignedSnailNumber)) {
            callManager.registerSnailBlock(assignedSnailNumber, this);
            System.out.println("TransponderSnailBlockEntity: Registered snail #" + assignedSnailNumber + " with call manager");
        }
    }

    /**
     * Unregister this snail block from the call manager
     */
    public void unregisterFromCallManager() {
        if (!isCallManagerAvailable() || !hasAssignedNumber()) {
            return;
        }

        TransponderCallManager callManager = getCallManager();
        if (callManager.isSnailBlockRegistered(assignedSnailNumber)) {
            callManager.unregisterSnailBlock(assignedSnailNumber);
            System.out.println("TransponderSnailBlockEntity: Unregistered snail #" + assignedSnailNumber + " from call manager");
        }
    }

    /**
     * Gets the snail number for this block entity
     */
    public int getSnailNumber() {
        return assignedSnailNumber;
    }

    /**
     * Gets the snail UUID for this block entity
     */
    public UUID getSnailUUID() {
        return snailUUID;
    }

    /**
     * Check if this snail has been assigned a number
     */
    public boolean hasAssignedNumber() {
        return initialized && assignedSnailNumber != -1 && snailUUID != null;
    }

    // =================== ITEM STACK METHODS ===================

    /**
     * Returns an ItemStack representation with proper NBT for the handler system
     */
    public ItemStack getSnailItemStack() {
        ItemStack stack = new ItemStack(this.getBlockState().getBlock().asItem());

        if (hasAssignedNumber()) {
            CompoundTag nbt = stack.getOrCreateTag();
            nbt.putUUID("snail_uuid", snailUUID);
            nbt.putInt("cached_snail_number", assignedSnailNumber);
            nbt.putLong("activation_time", System.currentTimeMillis());
            nbt.putString("snail_type", "BLOCK");
        }

        return stack;
    }

    /**
     * When the block is broken, preserves the snail data in the dropped item
     */
    @Override
    public void saveToItem(ItemStack stack) {
        // Call existing saveToItem logic
        unregisterFromCallManager();

        if (activeCallId != null && isCallManagerAvailable()) {
            TransponderCallManager callManager = getCallManager();
            callManager.endCallBySnailNumber(assignedSnailNumber);
        }

        if (hasAssignedNumber()) {
            CompoundTag nbt = stack.getOrCreateTag();
            nbt.putUUID("snail_uuid", snailUUID);
            nbt.putInt("cached_snail_number", assignedSnailNumber);
            nbt.putLong("activation_time", System.currentTimeMillis());
            nbt.putString("snail_type", "BLOCK");

            CompoundTag blockEntityTag = new CompoundTag();
            blockEntityTag.putUUID("SnailUUID", snailUUID);
            blockEntityTag.putInt("AssignedNumber", assignedSnailNumber);
            blockEntityTag.putBoolean("Initialized", initialized);

            // Declare shellColorToSave outside the if block
            int shellColorToSave = shellColor;

            // Save colors to both item NBT and BlockEntityTag
            if (colorsInitialized) {
                // Get shell color from blockstate (authoritative source for rendering)
                if (level != null) {
                    BlockState state = level.getBlockState(worldPosition);
                    if (state.getBlock() instanceof TransponderSnailBlock) {
                        shellColorToSave = state.getValue(TransponderSnailBlock.SHELL_COLOR);
                    }
                }

                nbt.putInt("body_color", bodyColor);
                nbt.putInt("shell_color", shellColorToSave);

                blockEntityTag.putInt("BodyColor", bodyColor);
                blockEntityTag.putInt("ShellColor", shellColorToSave);
                blockEntityTag.putBoolean("ColorsInitialized", colorsInitialized);
            }

            nbt.put("BlockEntityTag", blockEntityTag);

            System.out.println("TransponderSnailBlockEntity: Saved snail data to item - UUID: " + snailUUID +
                    ", Number: #" + assignedSnailNumber +
                    (colorsInitialized ? ", Colors: #" + Integer.toHexString(bodyColor) +
                            "/" + DyeColor.byId(shellColorToSave).getName() : ""));
        }
    }

    /**
     * When the block is placed, restores data from the item if available
     */
    public void loadFromItem(ItemStack stack) {
        if (stack.hasTag()) {
            CompoundTag nbt = stack.getTag();

            if (nbt.contains("BlockEntityTag")) {
                CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
                if (blockEntityTag.hasUUID("SnailUUID")) {
                    this.snailUUID = blockEntityTag.getUUID("SnailUUID");
                    this.assignedSnailNumber = blockEntityTag.getInt("AssignedNumber");
                    this.initialized = blockEntityTag.getBoolean("Initialized");
                    this.needsValidation = true; // CHANGE THIS TO FALSE
                    setChanged();
                    System.out.println("TransponderSnailBlockEntity: Loaded from BlockEntityTag - UUID: " + snailUUID + ", Number: #" + assignedSnailNumber);
                    return;
                }
            }

            if (nbt.hasUUID("snail_uuid")) {
                this.snailUUID = nbt.getUUID("snail_uuid");
                this.assignedSnailNumber = nbt.getInt("cached_snail_number");
                this.initialized = true;
                this.needsValidation = true; // CHANGE THIS TO FALSE
                setChanged();
                System.out.println("TransponderSnailBlockEntity: Loaded from NBT - UUID: " + snailUUID + ", Number: #" + assignedSnailNumber);
            }

            if (nbt.contains("body_color")) {
                this.bodyColor = nbt.getInt("body_color");
                this.shellColor = nbt.getInt("shell_color");
                this.colorsInitialized = true;
            }
        }
    }

    // =================== DIALING METHODS ===================

    public void dialDigit(int digit) {
        if (dialedNumber.length() < 4) {
            dialedNumber += digit;
            setChanged();
        }
    }

    public void clearDialedNumber() {
        dialedNumber = "";
        setChanged();
    }

    public String getDialedNumber() {
        return dialedNumber;
    }

    public void setDialedNumber(String number) {
        this.dialedNumber = number;
        setChanged();
    }

    @Nullable
    public ServerPlayer findNearbyPlayer() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        BlockPos pos = getBlockPos();
        ServerPlayer closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (ServerPlayer player : serverLevel.players()) {
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance <= VoiceChatConstants.getSnailInteractionRange() * VoiceChatConstants.getSnailInteractionRange()) {
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = player;
                }
            }
        }

        return closestPlayer;
    }

    public List<ServerPlayer> getNearbyListeners() {
        if (isCallManagerAvailable() && level instanceof ServerLevel serverLevel) {
            return getCallManager().getPlayersNearSnail(serverLevel, worldPosition, VoiceChatConstants.getSnailInteractionRange());
        }
        return List.of();
    }

    public void setRinging(boolean ringing) {
        if (this.isRinging != ringing) {
            this.isRinging = ringing;
            if (ringing) {
                this.ringStartTime = System.currentTimeMillis();
            } else {
                this.ringStartTime = 0;
            }
            setChanged();
            updateBlockstateVisuals();
        }
    }

    public void setActiveCall(UUID callId) {
        this.activeCallId = callId;
        setChanged();
        updateBlockstateVisuals();
    }

    public UUID getActiveCallId() {
        return activeCallId;
    }

    public boolean isRinging() {
        return isRinging;
    }

    public long getRingStartTime() {
        return ringStartTime;
    }

    // =================== COLOR CUSTOMIZATION METHODS ===================

    /**
     * Sets the shell color (when dyed)
     * @param dyeColor The dye color to apply (0-15)
     */
    public void setShellColor(int dyeColor) {
        if (dyeColor >= 0 && dyeColor <= 15 && dyeColor != this.shellColor) {
            this.shellColor = dyeColor;
            setChanged();

            System.out.println("TransponderSnailBlockEntity: Shell color updated to " +
                    DyeColor.byId(dyeColor).getName());
        }
    }

    /**
     * Initializes random pastel colors for this snail
     * Called when the snail is first assigned a number
     */
    private void initializeSnailColors() {
        if (colorsInitialized) {
            return;
        }

        this.bodyColor = generateRandomPastelColor();
        this.shellColor = 0;
        this.colorsInitialized = true;
        setChanged(); // This marks the block entity as needing sync

        System.out.println("TransponderSnailBlockEntity: Generated colors - Body: #" +
                Integer.toHexString(bodyColor) + " at " + worldPosition);
    }

    /**
     * Generates a random pastel color
     * Uses HSL color space for better pastel generation
     */
    private int generateRandomPastelColor() {
        Random random = new Random();

        // Generate random hue (0-360 degrees)
        float hue = random.nextFloat();

        // Pastel colors have moderate saturation (30-60%)
        float saturation = 0.3f + (random.nextFloat() * 0.3f);

        // Pastel colors have high lightness (70-90%)
        float lightness = 0.7f + (random.nextFloat() * 0.2f);

        // Convert HSL to RGB
        return hslToRgb(hue, saturation, lightness);
    }

    /**
     * Converts HSL color values to RGB integer
     */
    private int hslToRgb(float h, float s, float l) {
        float r, g, b;

        if (s == 0) {
            r = g = b = l; // Achromatic
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hueToRgb(p, q, h + 1f/3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f/3f);
        }

        int red = Math.round(r * 255);
        int green = Math.round(g * 255);
        int blue = Math.round(b * 255);

        return (red << 16) | (green << 8) | blue;
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f/6f) return p + (q - p) * 6 * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6;
        return p;
    }

    /**
     * Gets the body color for this snail
     */
    public int getBodyColor() {
        // DON'T initialize colors here - just return what we have
        if (!colorsInitialized) {
            return 0xFFFFFF;
        }
        return bodyColor;
    }

    /**
     * Gets the shell color (dye index) for this snail
     */
    public int getShellColor() {
        // Try to read from blockstate first (authoritative for rendering)
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof TransponderSnailBlock) {
                return state.getValue(TransponderSnailBlock.SHELL_COLOR);
            }
        }
        // Fallback to stored value
        return shellColor;
    }

    public void ensureColorsInitialized() {
        if (!colorsInitialized) {
            initializeSnailColors();

            // Force sync to client immediately
            if (level != null && !level.isClientSide) {
                BlockState state = level.getBlockState(worldPosition);
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            }
        }
    }

    // =================== NBT SERIALIZATION ===================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        System.out.println("TransponderSnailBlockEntity: Saving additional data - UUID: " + snailUUID + ", Number: #" + assignedSnailNumber + ", Initialized: " + initialized);

        if (snailUUID != null) {
            tag.putUUID("SnailUUID", snailUUID);
        }
        tag.putInt("AssignedNumber", assignedSnailNumber);
        tag.putBoolean("Initialized", initialized);

        if (activeCallId != null) {
            tag.putUUID("ActiveCallId", activeCallId);
        }
        tag.putBoolean("IsRinging", isRinging);
        tag.putString("DialedNumber", dialedNumber);
        tag.putString("CallState", currentCallState.name());
        tag.putLong("RingStartTime", ringStartTime);

        tag.putBoolean("HasAmbientSound", hasAmbientSound);
        tag.putBoolean("InActiveCall", inActiveCall);
        tag.putBoolean("AudioReady", audioReady); // Add this line

        tag.putInt("BodyColor", bodyColor);
        tag.putInt("ShellColor", shellColor);
        tag.putBoolean("ColorsInitialized", colorsInitialized);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains("BodyColor")) {
            this.bodyColor = tag.getInt("BodyColor");
            this.shellColor = tag.getInt("ShellColor");
            this.colorsInitialized = tag.getBoolean("ColorsInitialized");

            System.out.println((level != null && level.isClientSide ? "CLIENT" : "SERVER") +
                    ": Loaded colors - Body: #" + Integer.toHexString(bodyColor) +
                    ", Shell: " + shellColor + ", Initialized: " + colorsInitialized);

            // CRITICAL: Force chunk re-render on client after loading colors
            if (level != null && level.isClientSide && colorsInitialized) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }

        if (tag.hasUUID("SnailUUID")) {
            this.snailUUID = tag.getUUID("SnailUUID");
        }

        this.assignedSnailNumber = tag.getInt("AssignedNumber");
        this.initialized = tag.getBoolean("Initialized");

        System.out.println("TransponderSnailBlockEntity: Loaded from NBT - UUID: " + snailUUID + ", Number: #" + assignedSnailNumber + ", Initialized: " + initialized);

        if (!isServerShuttingDown && !validationCompleted && snailUUID != null && (assignedSnailNumber != -1 || initialized)) {
            this.needsValidation = true;
            System.out.println("TransponderSnailBlockEntity: Marked for deferred validation");
        }

        if (tag.hasUUID("ActiveCallId")) {
            this.activeCallId = tag.getUUID("ActiveCallId");
        }
        this.isRinging = tag.getBoolean("IsRinging");
        this.dialedNumber = tag.getString("DialedNumber");
        this.ringStartTime = tag.getLong("RingStartTime");

        try {
            String stateStr = tag.getString("CallState");
            if (!stateStr.isEmpty()) {
                this.currentCallState = CallStateSyncPacket.CallState.valueOf(stateStr);
            }
        } catch (IllegalArgumentException e) {
            this.currentCallState = CallStateSyncPacket.CallState.IDLE;
        }

        this.hasAmbientSound = tag.getBoolean("HasAmbientSound");
        this.inActiveCall = tag.getBoolean("InActiveCall");
        this.audioReady = tag.getBoolean("AudioReady"); // Add this line
    }
}