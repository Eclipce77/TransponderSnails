package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * UNIFIED Black Transponder Snail - Both handheld and placeable
 *
 * Features:
 * - Handheld mode: Right-click while CROUCHING to open/close for interception
 * - Block mode: Right-click (not crouching) to place as a block
 * - Dyeable shell (16 colors) in both modes
 * - Drops itself when broken (preserving color)
 * - Curios compatible
 * - Lightning rod range extension (when placed)
 */
public class BlackTransponderSnailItem extends BlockItem implements ICurioItem {

    private static final String OPEN_STATE_TAG = "is_open";
    private static final String WAS_IN_HAND_TAG = "was_in_hand";
    private static final String SHELL_COLOR_TAG = "shell_color";

    // Default colors
    public static final DyeColor DEFAULT_CRAFTED_SHELL_COLOR = DyeColor.WHITE;
    public static final DyeColor DEFAULT_CREATIVE_SHELL_COLOR = DyeColor.YELLOW;

    public BlackTransponderSnailItem(Block block, Properties properties) {
        super(block, properties);
    }

    // =================== ICurioItem Implementation ===================

    /**
     * CRITICAL: Prevent Curios from equipping this item on right-click
     * Without this, Curios intercepts right-click and blocks use() method!
     */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return false; // Must manually place in Curios slot
    }

    /**
     * Prevent Creative Mode from auto-equipping this item to armor slots
     */
    @Override
    @Nullable
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return null;
    }

    /**
     * CRITICAL: Prevent vanilla from equipping this item to ANY armor slot
     */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        return false;
    }

    // =================== Unified Use Logic ===================

    /**
     * UNIFIED USE METHOD:
     * - If targeting block and not crouching: useOn() handles placement
     * - If targeting block and crouching: This method handles open/close
     * - If clicking in air: This method handles open/close
     */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();

        if (player != null && player.isCrouching()) {
            // Crouching — place the block
            InteractionResult result = super.useOn(context);
            if (result.consumesAction()) {
                return result;
            }
            // Placement failed (e.g. can't place here) — fall through to use()
            return InteractionResult.PASS;
        }

        // Not crouching — pass to use() for open/close
        return InteractionResult.PASS;
    }

    /**
     * Right-click in air OR when block placement fails:
     * Open/close the snail for handheld interception
     *
     * NO CROUCH REQUIRED - works whenever useOn doesn't consume the action
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        boolean currentState = isOpen(stack);
        boolean newState = !currentState;

        // Toggle state
        setOpen(stack, newState);
        markInHand(stack, true);

        // CLIENT SIDE: Just consume the interaction
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        // SERVER SIDE: Handle interception and sync
        if (player instanceof ServerPlayer serverPlayer) {
            // Force inventory sync
            serverPlayer.inventoryMenu.broadcastChanges();

            // Correct slot calculation for container ID -2 (raw player inventory)
            int slot = hand == InteractionHand.MAIN_HAND ?
                    serverPlayer.getInventory().selected : 40;

            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                    -2, 0, slot, stack
            ));

            // Handle call interception with InterceptionHelper messages
            var callManager = net.eclipce.transpondersnails.TransponderSnails.getCallManager();
            if (callManager != null) {
                if (newState) {
                    // Opening - InterceptionHelper handles all messages
                    net.eclipce.transpondersnails.voice.server.InterceptionHelper.onSnailOpened(serverPlayer, callManager);
                } else {
                    // Closing - InterceptionHelper handles all messages
                    net.eclipce.transpondersnails.voice.server.InterceptionHelper.onSnailClosed(serverPlayer, callManager);
                }
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    // =================== Block Placement Override ===================

    /**
     * Override place to transfer NBT from item to block
     */
    @Override
    protected boolean placeBlock(@NotNull BlockPlaceContext context, @NotNull net.minecraft.world.level.block.state.BlockState state) {
        // First, place the block normally
        boolean placed = super.placeBlock(context, state);

        if (placed && !context.getLevel().isClientSide()) {
            BlockPos pos = context.getClickedPos();
            Level level = context.getLevel();
            ItemStack stack = context.getItemInHand();

            // Update block state with shell color from item
            int shellColor = getShellColorId(stack);
            level.setBlock(pos, state.setValue(BlackTransponderSnailBlock.SHELL_COLOR, shellColor), 3);

            System.out.println("BlackTransponderSnailItem: Placed with shell color " +
                    DyeColor.byId(shellColor).getName());
        }

        return placed;
    }

    // =================== Inventory Tick ===================

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        ensureColorsInitialized(stack, false);

        boolean inMainHand = player.getMainHandItem() == stack;
        boolean inOffHand = player.getOffhandItem() == stack;
        boolean inHand = inMainHand || inOffHand;

        // Auto-close when removed from hand
        if (isOpen(stack) && wasInHand(stack) && !inHand) {
            setOpen(stack, false);
            markInHand(stack, false);

            // Stop interception when removed from hand
            if (player instanceof ServerPlayer serverPlayer) {
                var callManager = net.eclipce.transpondersnails.TransponderSnails.getCallManager();
                if (callManager != null) {
                    net.eclipce.transpondersnails.voice.server.InterceptionHelper.onSnailClosed(serverPlayer, callManager);
                }
            }
        }

        if (inHand) {
            markInHand(stack, true);
        } else if (!isOpen(stack)) {
            markInHand(stack, false);
        }
    }

    // =================== Creative Tab ===================

    public void fillItemCategory(@NotNull CreativeModeTab.Output output) {
        ItemStack stack = new ItemStack(this);
        setShellColor(stack, DEFAULT_CREATIVE_SHELL_COLOR);
        output.accept(stack);
    }

    // =================== NBT Data Methods ===================

    public static boolean isOpen(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(OPEN_STATE_TAG);
    }

    public static void setOpen(@NotNull ItemStack stack, boolean open) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(OPEN_STATE_TAG, open);
    }

    private static boolean wasInHand(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(WAS_IN_HAND_TAG);
    }

    public static void markInHand(@NotNull ItemStack stack, boolean inHand) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(WAS_IN_HAND_TAG, inHand);
    }

    @NotNull
    public static DyeColor getShellColor(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(SHELL_COLOR_TAG)) {
            try {
                return DyeColor.byId(tag.getInt(SHELL_COLOR_TAG));
            } catch (Exception e) {
                return DEFAULT_CRAFTED_SHELL_COLOR;
            }
        }
        return DEFAULT_CRAFTED_SHELL_COLOR;
    }

    public static int getShellColorId(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(SHELL_COLOR_TAG)) {
            return tag.getInt(SHELL_COLOR_TAG);
        }
        return DEFAULT_CRAFTED_SHELL_COLOR.getId();
    }

    public static void setShellColor(@NotNull ItemStack stack, @NotNull DyeColor color) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(SHELL_COLOR_TAG, color.getId());
    }

    public static boolean hasExplicitColor(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(SHELL_COLOR_TAG);
    }

    public static void ensureColorsInitialized(@NotNull ItemStack stack, boolean isCreative) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(SHELL_COLOR_TAG)) {
            DyeColor defaultShell = isCreative ? DEFAULT_CREATIVE_SHELL_COLOR : DEFAULT_CRAFTED_SHELL_COLOR;
            tag.putInt(SHELL_COLOR_TAG, defaultShell.getId());
        }
    }

    // =================== Tooltip ===================

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        if (isOpen(stack)) {
            tooltip.add(Component.literal("Status: Open - Intercepting")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Status: Closed")
                    .withStyle(ChatFormatting.GRAY));
        }

        DyeColor shellColor = getShellColor(stack);
        tooltip.add(Component.literal("Shell: " + capitalize(shellColor.getName()))
                .withStyle(ChatFormatting.DARK_GRAY));

        double range = net.eclipce.transpondersnails.config.ModConfig.getAdultBlackSnailDefaultRange();
        tooltip.add(Component.literal("Handheld Range: " + (int)range + " blocks")
                .withStyle(ChatFormatting.BLUE));

        tooltip.add(Component.literal("Placed Range: Base + Lightning Rods")
                .withStyle(ChatFormatting.BLUE));

        tooltip.add(Component.literal("Crouch + Right-Click: Open/Close")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        tooltip.add(Component.literal("Right-Click: Place as Block")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    // =================== Entity Creation Helper ===================

    public static ItemStack createFromEntity(net.eclipce.transpondersnails.entity.custom.BlackTransponderSnailEntity entity) {
        ItemStack stack = new ItemStack(net.eclipce.transpondersnails.item.ModItems.BLACK_TRANSPONDER_SNAIL.get());
        DyeColor shellColor = DyeColor.byId(entity.getShellColor());
        setShellColor(stack, shellColor);
        setOpen(stack, false);
        return stack;
    }
}