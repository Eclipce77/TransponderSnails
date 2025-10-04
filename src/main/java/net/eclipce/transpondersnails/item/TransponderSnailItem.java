package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.eclipce.transpondersnails.screen.DialingMenu;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

public class TransponderSnailItem extends BlockItem {

    public TransponderSnailItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        System.out.println("TransponderSnailItem.useOn() called - Block: " +
                context.getLevel().getBlockState(context.getClickedPos()).getBlock());
        // Check if we're actually holding THIS item
        ItemStack heldStack = context.getItemInHand();
        if (heldStack.getItem() != this) {
            return InteractionResult.PASS;
        }

        // Check if player clicked on a Transponder Snail block
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clickedBlock = level.getBlockState(pos);

        if (clickedBlock.getBlock() instanceof TransponderSnailBlock) {
            // Let the block handle the interaction (opens GUI)
            return InteractionResult.PASS;
        }

        // Otherwise, allow normal placement
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Client-side: always return success to prevent weird behavior
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // Check if we should handle this interaction
        if (!shouldHandleInteraction(player, hand)) {
            return InteractionResultHolder.pass(stack);
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        TransponderCallManager callManager = TransponderSnails.getCallManager();

        if (callManager == null) {
            serverPlayer.sendSystemMessage(Component.literal("Voice chat system not available!")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        // Check if player is in a call
        boolean isInCall = callManager.isInCall(serverPlayer.getUUID());
        boolean isCrouching = player.isShiftKeyDown();

        if (isInCall) {
            if (isCrouching) {
                // Crouching in call: Open GUI
                openDialingMenu(serverPlayer, stack);
                return InteractionResultHolder.success(stack);
            } else {
                // Not crouching in call: End call
                callManager.endCall(serverPlayer);
                serverPlayer.sendSystemMessage(Component.literal("Call ended.")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
                return InteractionResultHolder.success(stack);
            }
        } else {
            // Not in call: Always open GUI (crouching or not)
            openDialingMenu(serverPlayer, stack);
            return InteractionResultHolder.success(stack);
        }
    }

    /**
     * Determines if this interaction should be handled based on hand priority
     */
    private boolean shouldHandleInteraction(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            // Main hand always handles interaction
            return true;
        } else {
            // Off hand only handles if main hand is empty
            ItemStack mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
            return mainHandStack.isEmpty();
        }
    }

    /**
     * Opens the dialing menu for a handheld Transponder Snail
     */
    private void openDialingMenu(ServerPlayer player, ItemStack stack) {
        System.out.println("=== openDialingMenu (ITEM) START ===");
        System.out.println("Item NBT before: " + (stack.hasTag() ? stack.getTag() : "NONE"));
        System.out.println("Has UUID: " + (SnailNBTHandler.getSnailUUID(stack) != null));
        System.out.println("Current number: " + SnailNBTHandler.getSnailNumber(stack));

        if (SnailNBTHandler.getSnailUUID(stack) == null) {
            System.out.println("NO UUID - generating new one");
            SnailNBTHandler.getOrCreateSnailUUID(stack, player.getUUID());
        } else {
            System.out.println("HAS UUID - keeping existing: " + SnailNBTHandler.getSnailUUID(stack));
        }

        System.out.println("Final number: " + SnailNBTHandler.getSnailNumber(stack));
        System.out.println("=== openDialingMenu (ITEM) END ===");

        NetworkHooks.openScreen(player, new TransponderSnailMenuProvider(stack), buf -> {
            buf.writeBoolean(true);
            buf.writeItem(stack);
        });
    }

    /**
     * Menu provider for handheld Transponder Snails
     */
    private static class TransponderSnailMenuProvider implements net.minecraft.world.MenuProvider {
        private final ItemStack snailStack;

        public TransponderSnailMenuProvider(ItemStack snailStack) {
            this.snailStack = snailStack.copy();
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("gui.transpondersnails.dialing");
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId,
                                                                              net.minecraft.world.entity.player.Inventory playerInventory,
                                                                              Player player) {
            return new DialingMenu(containerId, playerInventory, snailStack);
        }
    }
}