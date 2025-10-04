// Enhanced DialingMenu with call system integration
package net.eclipce.transpondersnails.screen;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.network.packets.*;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class DialingMenu extends AbstractContainerMenu {
    private final TransponderSnailBlockEntity blockEntity;
    private final ItemStack snailStack; // For handheld snails
    private final Player player;

    // Client-side storage for dialed number (when blockEntity is null)
    private String clientDialedNumber = "";

    // Snail information - these will be synced from server to client
    private int ownSnailNumber = -1; // This snail's number
    private boolean numberAssigned = false;
    private boolean syncRequested = false; // Track if we've requested sync

    // Call state information
    private CallStateSyncPacket.CallState currentCallState = CallStateSyncPacket.CallState.IDLE;
    private UUID activeCallId = null;
    private int otherSnailNumber = -1;
    private String callStatusMessage = "";

    // Constructor for block entity (placed snail)
    public DialingMenu(int containerId, Inventory playerInventory, TransponderSnailBlockEntity blockEntity) {
        super(ModMenuTypes.DIALING_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.snailStack = ItemStack.EMPTY;
        this.player = playerInventory.player;

        // Initialize snail number on server side
        if (!player.level().isClientSide) {
            initializeSnailNumber();
            checkExistingCallState(); // Check if snail is already in a call
        } else {
            // On client side, request sync from server
            requestSnailNumberSync();
        }
    }

    // Constructor for handheld snail
    public DialingMenu(int containerId, Inventory playerInventory, ItemStack snailStack) {
        super(ModMenuTypes.DIALING_MENU.get(), containerId);
        this.blockEntity = null;
        this.snailStack = snailStack.copy();
        this.player = playerInventory.player;

        // Initialize snail number on server side
        if (!player.level().isClientSide) {
            initializeSnailNumber();
            checkExistingCallState(); // Check if player is already in a call
        } else {
            // On client side, request sync from server
            requestSnailNumberSync();
        }
    }

    // Client-side constructor (used by the screen factory)
    public DialingMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.DIALING_MENU.get(), containerId);
        this.blockEntity = null;
        this.snailStack = ItemStack.EMPTY;
        this.player = playerInventory.player;

        // On client side, we'll wait for sync from server
        if (player.level().isClientSide) {
            requestSnailNumberSync();
        }
    }

    // Add this static factory method for client-side menu creation
    public static DialingMenu createFromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        // Handle case where no buffer data is sent (block entity opens menu directly)
        if (buf == null) {
            return new DialingMenu(containerId, playerInventory);
        }

        boolean isHandheld = buf.readBoolean();
        ItemStack snailStack = buf.readItem();

        if (isHandheld) {
            return new DialingMenu(containerId, playerInventory, snailStack);
        } else {
            return new DialingMenu(containerId, playerInventory);
        }
    }

    /**
     * Check if this snail or player is already in a call when the menu opens
     */
    private void checkExistingCallState() {
        if (player.level().isClientSide) {
            return; // Server only
        }

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager == null) {
            return;
        }

        // Check if player is already in a call
        if (callManager.isInCall(player.getUUID())) {
            // Update call state to connected
            UUID callId = callManager.getPlayerCallId(player.getUUID());
            if (callId != null) {
                updateCallStateAndSync(CallStateSyncPacket.CallState.CONNECTED, callId, -1, "In call");
            }
        } else if (blockEntity != null) {
            // Check if the snail block is ringing
            if (blockEntity.isRinging()) {
                UUID callId = blockEntity.getActiveCallId();
                updateCallStateAndSync(CallStateSyncPacket.CallState.RINGING_IN, callId, -1, "Incoming call");
            }
        }
    }

    /**
     * Request snail number sync from server (client-side only)
     */
    private void requestSnailNumberSync() {
        if (!player.level().isClientSide || syncRequested) {
            return;
        }

        syncRequested = true;
        // Send a packet to server requesting the snail number
        ModPackets.sendToServer(new SnailNumberRequestPacket());
    }

    /**
     * Initializes the snail number - assigns one if not already assigned
     * This is called when the GUI opens for the first time (SERVER SIDE ONLY)
     */
    private void initializeSnailNumber() {
        if (player.level().isClientSide) {
            return; // Only run on server
        }

        ItemStack targetStack = getTargetSnailStack();
        if (targetStack.isEmpty() && blockEntity == null) {
            System.err.println("DialingMenu: No valid snail stack or block entity found for initialization");
            return;
        }

        // For block entities, get the number directly from the block entity
        if (blockEntity != null) {
            // Ensure the block entity has a number assigned
            if (!blockEntity.hasAssignedNumber()) {
                // This will assign a number if needed
                blockEntity.ensureSnailNumberAssigned(player);
            }

            this.ownSnailNumber = blockEntity.getSnailNumber();
            this.numberAssigned = (this.ownSnailNumber != -1);
        } else if (!targetStack.isEmpty()) {
            // For handheld snails, use the NBT handler system
            int existingNumber = SnailNBTHandler.getSnailNumber(targetStack);
            if (existingNumber != -1) {
                // Already has a number
                this.ownSnailNumber = existingNumber;
                this.numberAssigned = true;
                System.out.println("DialingMenu: Handheld snail already has number #" + existingNumber);
            } else {
                // Need to assign a number
                UUID snailUUID = SnailNBTHandler.getOrCreateSnailUUID(targetStack, player.getUUID());
                if (snailUUID != null) {
                    this.ownSnailNumber = SnailNBTHandler.getSnailNumber(targetStack);
                    this.numberAssigned = true;
                    System.out.println("DialingMenu: Assigned new number #" + this.ownSnailNumber + " to handheld snail");

                    // Send congratulations message to player
                    if (player instanceof ServerPlayer serverPlayer) {
                        Component message = Component.literal("Your Transponder Snail has been assigned number #" + this.ownSnailNumber)
                                .withStyle(net.minecraft.ChatFormatting.GREEN);
                        serverPlayer.sendSystemMessage(message);
                    }
                } else {
                    System.err.println("DialingMenu: Failed to assign snail number to handheld snail");
                    this.ownSnailNumber = -1;
                    this.numberAssigned = false;
                }
            }
        }

        // Sync to client immediately after initialization
        if (player instanceof ServerPlayer serverPlayer && this.ownSnailNumber != -1) {
            System.out.println("DialingMenu: Sending sync packet to client - number #" + this.ownSnailNumber);
            ModPackets.sendToPlayer(new SnailNumberSyncPacket(this.ownSnailNumber), serverPlayer);
        }
    }

    /**
     * Called every tick to handle ongoing synchronization
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        // On server side, validate and sync the snail number
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            validateAndSyncSnailNumber(serverPlayer);
            updateCallStateFromManager(serverPlayer);
        }
    }

    /**
     * Check call manager for call state updates and sync to client
     */
    private void updateCallStateFromManager(ServerPlayer serverPlayer) {
        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager == null) {
            return;
        }

        // Check if player's call state has changed
        boolean playerInCall = callManager.isInCall(serverPlayer.getUUID());
        UUID callId = callManager.getPlayerCallId(serverPlayer.getUUID());

        CallStateSyncPacket.CallState newState = CallStateSyncPacket.CallState.IDLE;
        int otherNumber = -1;
        String statusMsg = "";

        if (playerInCall && callId != null) {
            newState = CallStateSyncPacket.CallState.CONNECTED;
            // Try to get the other party's snail number
            // This would require the call manager to track snail numbers in calls
            statusMsg = "Connected";
        } else if (blockEntity != null && blockEntity.isRinging()) {
            newState = CallStateSyncPacket.CallState.RINGING_IN;
            callId = blockEntity.getActiveCallId();
            statusMsg = "Incoming call";
        }

        // Only sync if state changed
        if (newState != currentCallState ||
                (callId != null && !callId.equals(activeCallId)) ||
                otherNumber != this.otherSnailNumber) {

            updateCallStateAndSync(newState, callId, otherNumber, statusMsg);
        }
    }

    /**
     * Update call state and sync to client
     */
    private void updateCallStateAndSync(CallStateSyncPacket.CallState newState, UUID callId, int otherNumber, String statusMsg) {
        this.currentCallState = newState;
        this.activeCallId = callId;
        this.otherSnailNumber = otherNumber;
        this.callStatusMessage = statusMsg;

        // Sync to client
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            ModPackets.sendToPlayer(new CallStateSyncPacket(newState, callId, otherNumber, statusMsg), serverPlayer);
        }
    }

    /**
     * Called from client via CallStateSyncPacket to update call state
     */
    public void updateCallState(CallStateSyncPacket.CallState callState, UUID callId, int otherSnailNumber, String statusMessage) {
        this.currentCallState = callState;
        this.activeCallId = callId;
        this.otherSnailNumber = otherSnailNumber;
        this.callStatusMessage = statusMessage;
    }

    /**
     * Validates that our snail number is still valid in the registry and syncs to client
     */
    private void validateAndSyncSnailNumber(ServerPlayer serverPlayer) {
        if (blockEntity != null) {
            // For block entities, force revalidation
            blockEntity.ensureSnailNumberAssigned(serverPlayer);

            // Update our cached number from the block entity
            int blockEntityNumber = blockEntity.getSnailNumber();
            if (blockEntityNumber != this.ownSnailNumber) {
                this.ownSnailNumber = blockEntityNumber;
                this.numberAssigned = (blockEntityNumber != -1);

                if (blockEntityNumber != -1) {
                    ModPackets.sendToPlayer(new SnailNumberSyncPacket(blockEntityNumber), serverPlayer);
                }
            }
        } else if (!snailStack.isEmpty()) {
            // For handheld snails, validate against registry
            if (this.ownSnailNumber != -1) {
                SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
                if (registry != null) {
                    // Check if our number is still valid
                    UUID snailUUID = SnailNBTHandler.getSnailUUID(snailStack);
                    if (snailUUID != null) {
                        UUID registryUUID = registry.getSnailByNumber(this.ownSnailNumber);
                        if (registryUUID == null || !registryUUID.equals(snailUUID)) {
                            // Number is no longer valid - reassign
                            System.out.println("DialingMenu: Handheld snail number #" + this.ownSnailNumber + " no longer valid, reassigning");
                            initializeSnailNumber(); // This will reassign a new number
                        } else {
                            // Number is still valid - sync to client
                            ModPackets.sendToPlayer(new SnailNumberSyncPacket(this.ownSnailNumber), serverPlayer);
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets the ItemStack we're working with (either from block entity or handheld)
     */
    private ItemStack getTargetSnailStack() {
        if (blockEntity != null) {
            // For placed snails, we need to get/create an ItemStack representation
            return blockEntity.getSnailItemStack();
        } else if (!snailStack.isEmpty()) {
            // For handheld snails
            return snailStack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // No slots in this menu, so no quick move
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null && snailStack.isEmpty()) {
            return true; // Client side
        }

        if (blockEntity != null) {
            // Check if player is within 8 blocks of the block entity
            return player.distanceToSqr(
                    blockEntity.getBlockPos().getX() + 0.5,
                    blockEntity.getBlockPos().getY() + 0.5,
                    blockEntity.getBlockPos().getZ() + 0.5
            ) <= 64.0; // 8 blocks squared
        }

        // For handheld snails, always valid (player has the item)
        return true;
    }

    // =================== CALL MANAGEMENT METHODS ===================

    public void initiateCall() {
        String targetNumber = getDialedNumber();
        if (targetNumber == null || targetNumber.isEmpty()) {
            // No number dialed
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Please dial a number first!")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            return;
        }

        // Convert dialed string to integer
        int dialedNumber;
        try {
            dialedNumber = Integer.parseInt(targetNumber);
        } catch (NumberFormatException e) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Invalid number format!")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            return;
        }

        // Check if trying to call own number
        if (dialedNumber == this.ownSnailNumber) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("You cannot call your own snail!")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            return;
        }

        // Server-side call initiation
        if (!player.level().isClientSide) {
            initiateCallServerSide(dialedNumber);
        } else {
            // Send packet to server to initiate call
            ModPackets.sendToServer(new CallInitiationPacket(dialedNumber, this.ownSnailNumber));
        }
    }

    private void initiateCallServerSide(int targetNumber) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager == null) {
            serverPlayer.sendSystemMessage(Component.literal("Voice chat system not available!")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        // Update call state to dialing
        updateCallStateAndSync(CallStateSyncPacket.CallState.DIALING, null, targetNumber, "Dialing...");

        // Use the call manager to initiate the call
        boolean success = callManager.initiateCallBySnailNumber(serverPlayer, this.ownSnailNumber, targetNumber);

        if (success) {
            // Clear the dialed number after successful call initiation
            clearDialedNumber();

            // Call state will be updated by the call manager through callbacks
            serverPlayer.sendSystemMessage(Component.literal("Calling snail #" + targetNumber + "...")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        } else {
            // Reset to idle state if call failed
            updateCallStateAndSync(CallStateSyncPacket.CallState.IDLE, null, -1, "");
        }
    }

    /**
     * Accept an incoming call
     */
    public void acceptCall() {
        if (currentCallState != CallStateSyncPacket.CallState.RINGING_IN || activeCallId == null) {
            return;
        }

        if (!player.level().isClientSide) {
            // Server side - use call manager
            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager != null && player instanceof ServerPlayer serverPlayer) {
                callManager.acceptCall(serverPlayer, activeCallId);
            }
        } else {
            // Client side - send packet
            ModPackets.sendToServer(new CallResponsePacket(CallResponsePacket.Response.ACCEPT, activeCallId));
        }
    }

    /**
     * Reject an incoming call
     */
    public void rejectCall() {
        if (currentCallState != CallStateSyncPacket.CallState.RINGING_IN || activeCallId == null) {
            return;
        }

        if (!player.level().isClientSide) {
            // Server side - use call manager
            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager != null && player instanceof ServerPlayer serverPlayer) {
                callManager.rejectCall(serverPlayer, activeCallId);
            }
        } else {
            // Client side - send packet
            ModPackets.sendToServer(new CallResponsePacket(CallResponsePacket.Response.REJECT, activeCallId));
        }
    }

    /**
     * Hang up an active call
     */
    public void hangUpCall() {
        if (currentCallState != CallStateSyncPacket.CallState.CONNECTED || activeCallId == null) {
            return;
        }

        if (!player.level().isClientSide) {
            // Server side - use call manager
            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager != null && player instanceof ServerPlayer serverPlayer) {
                callManager.endCall(serverPlayer);
            }
        } else {
            // Client side - send packet
            ModPackets.sendToServer(new CallResponsePacket(CallResponsePacket.Response.HANG_UP, activeCallId));
        }
    }

    /**
     * Called when a call is initiated - for GUI updates
     */
    public void onCallInitiated() {
        // Update GUI to show calling state
        // You can add visual feedback here (disable dial buttons, show status, etc.)
        System.out.println("DialingMenu: Call initiated");
    }

    /**
     * Called when a call ends - for GUI updates
     */
    public void onCallEnded() {
        // Update GUI to show idle state
        updateCallStateAndSync(CallStateSyncPacket.CallState.IDLE, null, -1, "");

        if (blockEntity != null) {
            blockEntity.clearDialedNumber();
        } else {
            clearDialedNumber();
        }
        System.out.println("DialingMenu: Call ended");
    }

    /**
     * Called when number is cleared - for GUI updates
     */
    public void onNumberCleared() {
        // Update GUI as needed
        System.out.println("DialingMenu: Number cleared");
    }

    // =================== CALL STATE GETTERS ===================

    public CallStateSyncPacket.CallState getCurrentCallState() {
        return currentCallState;
    }

    public UUID getActiveCallId() {
        return activeCallId;
    }

    public int getOtherSnailNumber() {
        return otherSnailNumber;
    }

    public String getCallStatusMessage() {
        return callStatusMessage;
    }

    public boolean canInitiateCall() {
        return currentCallState == CallStateSyncPacket.CallState.IDLE &&
                ownSnailNumber != -1 &&
                !getDialedNumber().isEmpty();
    }

    public boolean canAcceptCall() {
        return currentCallState == CallStateSyncPacket.CallState.RINGING_IN;
    }

    public boolean canHangUp() {
        return currentCallState == CallStateSyncPacket.CallState.CONNECTED;
    }

    // =================== EXISTING DIALING METHODS ===================

    public String getDialedNumber() {
        if (blockEntity != null) {
            // Server side - get from block entity
            return blockEntity.getDialedNumber();
        } else {
            // Client side - return local storage
            return clientDialedNumber;
        }
    }

    // Methods for handling dialing from the GUI
    public void dialDigit(int digit) {
        if (blockEntity != null) {
            // Server side - delegate to block entity
            blockEntity.dialDigit(digit);
        } else {
            // Client side - store locally, or send packet to server for handheld
            if (player.level().isClientSide) {
                if (clientDialedNumber.length() < 4) {
                    clientDialedNumber += digit;
                }
            } else {
                // Server-side handheld dialing - store in menu
                if (clientDialedNumber.length() < 4) {
                    clientDialedNumber += digit;
                }
            }
        }
    }

    public void clearDialedNumber() {
        if (blockEntity != null) {
            // Server side - delegate to block entity
            blockEntity.clearDialedNumber();
        } else {
            // Client side - clear local storage
            clientDialedNumber = "";
        }
    }

    // Legacy method name for compatibility
    public void clearNumber() {
        clearDialedNumber();
    }

    public boolean isDialedNumberValid() {
        String dialed = getDialedNumber();

        if (dialed.length() != 4) {
            return false;
        }

        try {
            int number = Integer.parseInt(dialed);
            return number >= 1000 && number <= 9999;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // =================== GETTERS ===================

    public TransponderSnailBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public ItemStack getSnailStack() {
        return snailStack;
    }

    public int getOwnSnailNumber() {
        return ownSnailNumber;
    }

    public boolean isNumberAssigned() {
        return numberAssigned;
    }

    // Called from client via packet to set the snail number
    public void setOwnSnailNumber(int number) {
        System.out.println("DialingMenu: setOwnSnailNumber called with number #" + number);
        this.ownSnailNumber = number;
        this.numberAssigned = number != -1;
    }

    // Called from client via packet to set the dialed number
    public void setClientDialedNumber(String number) {
        if (player.level().isClientSide) {
            this.clientDialedNumber = number != null ? number : "";
            System.out.println("DialingMenu: setClientDialedNumber called with: '" + this.clientDialedNumber + "'");
        }
    }

    /**
     * Helper method to find a nearby player for call operations
     */
    private ServerPlayer findNearbyPlayer() {
        if (blockEntity == null || !(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }

        BlockPos pos = blockEntity.getBlockPos();
        return serverLevel.players().stream()
                .filter(player -> player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 9.0)
                .findFirst()
                .orElse(null);
    }
}