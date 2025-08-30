package net.eclipce.transpondersnails.block.entity;

import net.eclipce.transpondersnails.screen.DialingMenu;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.network.packets.SnailNumberSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class TransponderSnailBlockEntity extends BlockEntity implements MenuProvider {
    private UUID activeCallId;
    private boolean isRinging = false;
    private String dialedNumber = "";

    // Snail identity - persisted directly in the block entity
    private UUID snailUUID = null;
    private int assignedSnailNumber = -1;
    private boolean initialized = false;

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
        if (!pPlayer.level().isClientSide && !initialized) {
            ensureSnailNumberAssigned(pPlayer);
        }
        return new DialingMenu(containerId, pInventory, this);
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
     */
    public InteractionResult onUse(ServerPlayer player) {
        if (!isCallManagerAvailable()) {
            player.sendSystemMessage(Component.literal("Voice chat system not available!"));
            return InteractionResult.FAIL;
        }

        TransponderCallManager callManager = getCallManager();

        if (isRinging) {
            // Answer incoming call
            if (callManager.acceptCall(player)) {
                setRinging(false);
                player.sendSystemMessage(Component.literal("Call answered!"));
                return InteractionResult.SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Failed to answer call!"));
                return InteractionResult.FAIL;
            }
        } else if (activeCallId != null) {
            // Check if this player is in the call
            if (callManager.isInCall(player.getUUID())) {
                // Player is in call - they can hang up
                callManager.leaveCall(player);
                return InteractionResult.SUCCESS;
            } else {
                // Snail is busy with existing call
                player.sendSystemMessage(Component.literal("Transponder Snail is busy!"));
                return InteractionResult.FAIL;
            }
        } else {
            // No active call - open dialing GUI
            player.openMenu(this);
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * Initiate a call to the dialed number
     */
    public boolean initiateCall() {
        if (!isCallManagerAvailable() || dialedNumber.isEmpty()) {
            return false;
        }

        try {
            int targetNumber = Integer.parseInt(dialedNumber);

            // Look up target snail
            SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
            if (registry == null) {
                return false;
            }

            UUID targetSnailUUID = registry.getSnailByNumber(targetNumber);
            if (targetSnailUUID == null) {
                return false;
            }

            // Find a nearby player to initiate the call
            ServerPlayer caller = findNearbyPlayer();
            if (caller == null) {
                return false;
            }

            // Find the target player or snail location
            ServerPlayer targetPlayer = findPlayerWithSnail(targetSnailUUID);
            if (targetPlayer == null) {
                caller.sendSystemMessage(Component.literal("Target snail #" + targetNumber + " not available!"));
                return false;
            }

            TransponderCallManager callManager = getCallManager();

            // Try to initiate locational call from this snail to target
            boolean success = callManager.initiateCall(caller, targetPlayer, worldPosition);

            if (success) {
                caller.sendSystemMessage(Component.literal("Calling snail #" + targetNumber + "..."));
                clearDialedNumber();
                return true;
            } else {
                caller.sendSystemMessage(Component.literal("Failed to initiate call!"));
                return false;
            }

        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Find a player with a specific snail UUID
     */
    private ServerPlayer findPlayerWithSnail(UUID targetSnailUUID) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        // First, check if the target snail is a placed block entity
        // TODO: You might need to maintain a registry of placed snail locations
        // For now, we'll search all online players with handheld snails

        for (ServerPlayer player : serverLevel.players()) {
            // Check player's inventory for the target snail
            for (ItemStack stack : player.getInventory().items) {
                if (stack.hasTag() && stack.getTag().hasUUID("snail_uuid")) {
                    UUID snailUUID = stack.getTag().getUUID("snail_uuid");
                    if (targetSnailUUID.equals(snailUUID)) {
                        return player;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Handle incoming call invitation
     */
    public void onIncomingCall(UUID callId) {
        this.activeCallId = callId;
        setRinging(true);

        // Notify nearby players
        List<ServerPlayer> nearbyPlayers = getNearbyListeners();
        for (ServerPlayer player : nearbyPlayers) {
            player.sendSystemMessage(Component.literal("Transponder Snail is ringing!"));
        }
    }

    /**
     * Handle call end
     */
    public void onCallEnded() {
        this.activeCallId = null;
        setRinging(false);

        // Notify nearby players
        List<ServerPlayer> nearbyPlayers = getNearbyListeners();
        for (ServerPlayer player : nearbyPlayers) {
            player.sendSystemMessage(Component.literal("Call ended."));
        }
    }

    // =================== SNAIL NUMBERING SYSTEM ===================

    /**
     * Ensures this placed snail has a number assigned.
     * Called when the GUI is opened for the first time.
     * Made public so DialingMenu can call it if needed.
     */
    public void ensureSnailNumberAssigned(Player player) {
        // Check if our current number is still valid in the registry
        if (initialized && snailUUID != null && assignedSnailNumber != -1) {
            SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
            if (registry != null) {
                UUID registryUUID = registry.getSnailByNumber(assignedSnailNumber);
                if (registryUUID != null && registryUUID.equals(snailUUID)) {
                    // Number is still valid - just sync to client
                    if (player instanceof ServerPlayer serverPlayer) {
                        ModPackets.sendToPlayer(new SnailNumberSyncPacket(assignedSnailNumber), serverPlayer);
                        System.out.println("TransponderSnailBlockEntity: Re-syncing existing number #" + assignedSnailNumber + " to client");
                    }
                    return;
                } else {
                    // Number is no longer valid - reset and reassign
                    System.out.println("TransponderSnailBlockEntity: Number #" + assignedSnailNumber + " no longer valid in registry, resetting");
                    this.initialized = false;
                    this.assignedSnailNumber = -1;
                    // Keep the UUID and try to reassign below
                }
            }
        }

        try {
            // Check if we have a UUID but it's not in the registry (data recovery)
            if (snailUUID != null) {
                SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
                if (registry != null) {
                    int registryNumber = registry.getSnailNumber(snailUUID);
                    if (registryNumber != -1) {
                        // UUID exists in registry, sync the number
                        this.assignedSnailNumber = registryNumber;
                        this.initialized = true;
                        setChanged();
                        System.out.println("TransponderSnailBlockEntity: Recovered number #" + assignedSnailNumber + " for UUID " + snailUUID);

                        // Send sync to client
                        if (player instanceof ServerPlayer serverPlayer) {
                            ModPackets.sendToPlayer(new SnailNumberSyncPacket(assignedSnailNumber), serverPlayer);
                        }
                        return;
                    }
                }
            }

            // Generate new UUID and number
            if (snailUUID == null) {
                snailUUID = UUID.randomUUID();
            }

            SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
            if (registry != null) {
                int newNumber = registry.assignNumberToSnail(snailUUID);
                if (newNumber != -1) {
                    this.assignedSnailNumber = newNumber;
                    this.initialized = true;
                    setChanged(); // Mark for saving

                    // Send congratulations message
                    if (player instanceof ServerPlayer serverPlayer) {
                        Component message = Component.literal("Your Transponder Snail has been assigned number #" + assignedSnailNumber)
                                .withStyle(net.minecraft.ChatFormatting.GREEN);
                        serverPlayer.sendSystemMessage(message);

                        // Send sync packet to client
                        ModPackets.sendToPlayer(new SnailNumberSyncPacket(assignedSnailNumber), serverPlayer);
                        System.out.println("TransponderSnailBlockEntity: Assigned new number #" + assignedSnailNumber + " and synced to client");
                    }

                    System.out.println("TransponderSnailBlockEntity: Assigned new number #" + assignedSnailNumber + " to snail at " + worldPosition);
                } else {
                    System.err.println("TransponderSnailBlockEntity: Failed to assign number - registry may be full");
                }
            }
        } catch (Exception e) {
            System.err.println("TransponderSnailBlockEntity: Error assigning snail number: " + e.getMessage());
            e.printStackTrace();
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
            // Use the same keys that SnailNBTHandler expects
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
    public void saveToItem(ItemStack stack) {
        if (hasAssignedNumber()) {
            CompoundTag nbt = stack.getOrCreateTag();
            // Use the same keys that SnailNBTHandler expects
            nbt.putUUID("snail_uuid", snailUUID);
            nbt.putInt("cached_snail_number", assignedSnailNumber);
            nbt.putLong("activation_time", System.currentTimeMillis());
            nbt.putString("snail_type", "BLOCK");

            // Also store in BlockEntityTag for when placed again
            CompoundTag blockEntityTag = new CompoundTag();
            blockEntityTag.putUUID("SnailUUID", snailUUID);
            blockEntityTag.putInt("AssignedNumber", assignedSnailNumber);
            blockEntityTag.putBoolean("Initialized", initialized);
            nbt.put("BlockEntityTag", blockEntityTag);
        }
    }

    /**
     * When the block is placed, restores data from the item if available
     */
    public void loadFromItem(ItemStack stack) {
        if (stack.hasTag()) {
            CompoundTag nbt = stack.getTag();

            // Try to load from BlockEntityTag first (for items that were broken and placed again)
            if (nbt.contains("BlockEntityTag")) {
                CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
                if (blockEntityTag.hasUUID("SnailUUID")) {
                    this.snailUUID = blockEntityTag.getUUID("SnailUUID");
                    this.assignedSnailNumber = blockEntityTag.getInt("AssignedNumber");
                    this.initialized = blockEntityTag.getBoolean("Initialized");
                    setChanged();
                    System.out.println("TransponderSnailBlockEntity: Restored from BlockEntityTag - #" + assignedSnailNumber);
                    return;
                }
            }

            // Fall back to SnailNBTHandler format
            if (nbt.hasUUID("snail_uuid")) {
                this.snailUUID = nbt.getUUID("snail_uuid");
                this.assignedSnailNumber = nbt.getInt("cached_snail_number");
                this.initialized = true;
                setChanged();
                System.out.println("TransponderSnailBlockEntity: Restored from NBT - #" + assignedSnailNumber);
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

    private ServerPlayer findNearbyPlayer() {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.players().stream()
                    .filter(player -> player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) <= 9.0)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public List<ServerPlayer> getNearbyListeners() {
        if (isCallManagerAvailable() && level instanceof ServerLevel serverLevel) {
            return getCallManager().getPlayersNearSnail(serverLevel, worldPosition, 10.0);
        }
        return List.of();
    }

    public void setRinging(boolean ringing) {
        this.isRinging = ringing;
        setChanged();
    }

    public void setActiveCall(UUID callId) {
        this.activeCallId = callId;
        setChanged();
    }

    public UUID getActiveCallId() {
        return activeCallId;
    }

    public boolean isRinging() {
        return isRinging;
    }

    // =================== NBT SERIALIZATION ===================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        // Save snail identity
        if (snailUUID != null) {
            tag.putUUID("SnailUUID", snailUUID);
        }
        tag.putInt("AssignedNumber", assignedSnailNumber);
        tag.putBoolean("Initialized", initialized);

        // Save call state
        if (activeCallId != null) {
            tag.putUUID("ActiveCallId", activeCallId);
        }
        tag.putBoolean("IsRinging", isRinging);
        tag.putString("DialedNumber", dialedNumber);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        // Load snail identity
        if (tag.hasUUID("SnailUUID")) {
            this.snailUUID = tag.getUUID("SnailUUID");
        }
        this.assignedSnailNumber = tag.getInt("AssignedNumber");
        this.initialized = tag.getBoolean("Initialized");

        // Load call state
        if (tag.hasUUID("ActiveCallId")) {
            this.activeCallId = tag.getUUID("ActiveCallId");
        }
        this.isRinging = tag.getBoolean("IsRinging");
        this.dialedNumber = tag.getString("DialedNumber");
    }
}