package net.eclipce.transpondersnails.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * Baby Black Transponder Snail - Dyeable handheld snail with shorter call interception range
 * Right-click opens/closes the snail to start/stop intercepting calls
 */
public class BabyBlackTransponderSnailItem extends Item implements ICurioItem {

    private static final String OPEN_STATE_TAG = "is_open";
    private static final String WAS_IN_HAND_TAG = "was_in_hand";
    private static final String SHELL_COLOR_TAG = "shell_color";

    // Default colors
    public static final DyeColor DEFAULT_CRAFTED_SHELL_COLOR = DyeColor.WHITE;
    public static final DyeColor DEFAULT_CREATIVE_SHELL_COLOR = DyeColor.YELLOW;

    public BabyBlackTransponderSnailItem(Properties properties) {
        super(properties);
    }

    // =================== ICurioItem Implementation ===================

    /**
     * CRITICAL: Prevent Curios from equipping this item on right-click
     * Without this, Curios intercepts right-click and blocks use() method!
     */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return false; // Must manually place in Curios slot - right-click opens/closes
    }

    /**
     * Prevent Creative Mode from auto-equipping this item to armor slots
     * Returns null to indicate this item has no equipment slot
     */
    @Override
    @Nullable
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return null;
    }

    /**
     * CRITICAL: Prevent vanilla from equipping this item to ANY armor slot
     * This fixes the armor slot duplication bug!
     */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        // Explicitly prevent equipping to any armor slot
        return false;
    }

    // =================== Item Functionality ===================

    /**
     * Right-click to open/close the snail
     * FIXED: Correct slot calculation for ClientboundContainerSetSlotPacket
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

            // ✅ FIX: Correct slot calculation for container ID -2 (raw player inventory)
            // Container -2 uses raw inventory indices:
            //   0-8: Hotbar
            //   9-35: Main inventory
            //   36-39: Armor (boots, legs, chest, head)
            //   40: Offhand
            // For MAIN_HAND: Use the selected slot directly (0-8)
            // For OFFHAND: Use slot 40
            int slot = hand == InteractionHand.MAIN_HAND ?
                    serverPlayer.getInventory().selected : 40;

            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                    -2,    // Player inventory container (raw indices)
                    0,     // State ID
                    slot,  // Correct slot number!
                    stack  // The updated stack
            ));

            System.out.println("[BABY-BLACK-SNAIL] Sent slot update for slot " + slot + " (hand: " + hand + ")");

            // Handle call interception
            var callManager = net.eclipce.transpondersnails.TransponderSnails.getCallManager();
            if (callManager != null) {
                if (newState) {
                    net.eclipce.transpondersnails.voice.server.InterceptionHelper.onSnailOpened(serverPlayer, callManager);
                } else {
                    net.eclipce.transpondersnails.voice.server.InterceptionHelper.onSnailClosed(serverPlayer, callManager);
                }
            }
        }

        return InteractionResultHolder.consume(stack);
    }

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

        if (isOpen(stack) && wasInHand(stack) && !inHand) {
            setOpen(stack, false);
            markInHand(stack, false);
        }

        if (inHand) {
            markInHand(stack, true);
        } else if (!isOpen(stack)) {
            markInHand(stack, false);
        }
    }

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

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        if (isOpen(stack)) {
            tooltip.add(Component.literal("Status: Open - Intercepting Calls").withStyle(net.minecraft.ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Status: Closed").withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        DyeColor shellColor = getShellColor(stack);
        tooltip.add(Component.literal("Shell: " + capitalize(shellColor.getName()))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

        double range = net.eclipce.transpondersnails.config.ModConfig.getBabyBlackSnailRange();
        tooltip.add(Component.literal("Interception Range: " + (int)range + " blocks")
                .withStyle(net.minecraft.ChatFormatting.BLUE));

        tooltip.add(Component.literal("Right-Click to Open/Close")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    public static ItemStack createFromEntity(net.eclipce.transpondersnails.entity.custom.BabyBlackTransponderSnailEntity entity) {
        ItemStack stack = new ItemStack(net.eclipce.transpondersnails.item.ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get());
        DyeColor shellColor = DyeColor.byId(entity.getShellColor());
        setShellColor(stack, shellColor);
        setOpen(stack, false);
        return stack;
    }
}