// Enhanced DialingMenu with proper client-server sync
package net.eclipce.transpondersnails.screen;

import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.network.packets.CallInitiationPacket;
import net.eclipce.transpondersnails.network.packets.SnailNumberSyncPacket;
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

    // Constructor for block entity (placed snail)
    public DialingMenu(int containerId, Inventory playerInventory, TransponderSnailBlockEntity blockEntity) {
        super(ModMenuTypes.DIALING_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.snailStack = ItemStack.EMPTY;
        this.player = playerInventory.player;

        // Initialize snail number on server side
        if (!player.level().isClientSide) {
            initializeSnailNumber();
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

    /**
     * Request snail number sync from server (client-side only)
     */
    private void requestSnailNumberSync() {
        if (!player.level().isClientSide || syncRequested) {
            return;
        }

        syncRequested = true;
        // Send a packet to server requesting the snail number
        // You'll need to create a SnailNumberRequestPacket for this
        // For now, we'll rely on the server sending it automatically
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
        }
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

    // Methods for handling dialing from the GUI
    public void dialDigit(int digit) {
        if (blockEntity != null) {
            // Server side - delegate to block entity
            blockEntity.dialDigit(digit);
        } else {
            // Client side - store locally, or send packet to server for handheld
            if (player.level().isClientSide) {
                clientDialedNumber += digit;
            } else {
                // Server-side handheld dialing - store in menu
                clientDialedNumber += digit;
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

        // Look up target snail
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            serverPlayer.sendSystemMessage(Component.literal("Call system is not available!")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        UUID targetSnailUUID = registry.getSnailByNumber(targetNumber);
        if (targetSnailUUID == null) {
            serverPlayer.sendSystemMessage(Component.literal("No snail found with number #" + targetNumber)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        // TODO: Find the player who owns the target snail
        // This is complex because snails are tied to UUIDs, not players
        // You might need to track snail ownership or find online players with that snail

        // For now, show a message that the call system needs more implementation
        serverPlayer.sendSystemMessage(Component.literal("Found snail #" + targetNumber + "! Call system integration coming soon...")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));

        // Clear the dialed number after attempting call
        clearDialedNumber();
    }

    public String getDialedNumber() {
        if (blockEntity != null) {
            // Server side - get from block entity
            return blockEntity.getDialedNumber();
        } else {
            // Client side - return local storage
            return clientDialedNumber;
        }
    }
}