package net.eclipce.transpondersnails.item;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * Portable Black Transponder Snail - Dyeable Version with Curios Support
 * Right-click opens/closes the snail. Must manually place in Curios slot to equip.
 *
 * FIXED: Armor slot duplication bug caused by incorrect slot calculation
 */
public class PortableBlackTransponderSnailItem extends Item implements ICurioItem {

    private static final String OPEN_STATE_TAG = "is_open";
    private static final String WAS_IN_HAND_TAG = "was_in_hand";
    private static final String SHELL_COLOR_TAG = "shell_color";
    private static final String BAND_COLOR_TAG = "band_color";

    // Default colors for crafted items
    public static final DyeColor DEFAULT_CRAFTED_SHELL_COLOR = DyeColor.WHITE;
    public static final DyeColor DEFAULT_CRAFTED_BAND_COLOR = DyeColor.GRAY;

    // Default colors for creative menu
    public static final DyeColor DEFAULT_CREATIVE_SHELL_COLOR = DyeColor.YELLOW;
    public static final DyeColor DEFAULT_CREATIVE_BAND_COLOR = DyeColor.LIGHT_GRAY;

    public PortableBlackTransponderSnailItem(Properties properties) {
        super(properties);
    }

    // =================== ICurioItem Implementation ===================

    /**
     * Called when the item is equipped in a Curio slot
     */
    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        ICurioItem.super.onEquip(slotContext, prevStack, stack);

        // Play equip sound
        slotContext.entity().playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);

        // Ensure colors are initialized
        ensureColorsInitialized(stack, false);

        // Debug log
    }

    /**
     * Called when the item is unequipped from a Curio slot
     */
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        ICurioItem.super.onUnequip(slotContext, newStack, stack);

        // Play unequip sound
        slotContext.entity().playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.8F);

        // Debug log
    }

    /**
     * Called every tick while equipped in a Curio slot
     */
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        ICurioItem.super.curioTick(slotContext, stack);

        // You can add calling functionality here later
        // For now, just ensure the snail maintains its state
    }

    /**
     * Can this item be equipped from using (right-clicking)?
     * Return false so right-click opens/closes the snail instead of equipping
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
     * This is an additional safeguard against the armor slot duplication bug
     */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        // Explicitly prevent equipping to any armor slot
        return false;
    }

    // =================== Original Item Functionality ===================

    /**
     * FIXED VERSION: Correct slot calculation for ClientboundContainerSetSlotPacket
     *
     * BUG FIX: The original code used `selected + 36` which sent the item to armor slots!
     * Container ID -2 uses raw inventory indices:
     *   0-8: Hotbar (selected is already 0-8, no offset needed!)
     *   9-35: Main inventory
     *   36-39: Armor (boots, legs, chest, head) - THIS WAS THE BUG!
     *   40: Offhand
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean currentState = isOpen(stack);
        boolean newState = !currentState;

        // Toggle state directly on the stack
        setOpen(stack, newState);
        markInHand(stack, true);

        // CLIENT SIDE: Just consume and return
        if (level.isClientSide) {
            // Return CONSUME to prevent vanilla from doing anything else with the item
            return InteractionResultHolder.consume(stack);
        }

        // SERVER SIDE: Messages and interception
        if (player instanceof ServerPlayer serverPlayer) {
            // Force inventory sync
            serverPlayer.inventoryMenu.broadcastChanges();

            // ✅ FIX: Correct slot calculation for container ID -2 (raw player inventory)
            // For MAIN_HAND: Use the selected slot directly (0-8) - NO +36 OFFSET!
            // For OFFHAND: Use slot 40 (not 45!)
            int slot = hand == InteractionHand.MAIN_HAND ?
                    serverPlayer.getInventory().selected : 40;

            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                    -2,    // Player inventory container (raw indices)
                    0,     // State ID
                    slot,  // Correct slot number!
                    stack  // The updated stack
            ));


            // ✨ INTERCEPTION: Handle interception when snail state changes
            var callManager = net.eclipce.transpondersnails.TransponderSnails.getCallManager();
            if (callManager != null) {
                if (newState) {
                    // Snail was just opened - attempt to start interception
                    net.eclipce.transpondersnails.voice.server.InterceptionHelper.onSnailOpened(serverPlayer, callManager);
                } else {
                    // Snail was just closed - stop interception
                    net.eclipce.transpondersnails.voice.server.InterceptionHelper.onSnailClosed(serverPlayer, callManager);
                }
            }
        }

        // Return CONSUME (not SUCCESS) to prevent vanilla equipping behavior
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

        // Ensure colors are initialized (for old items or items without colors)
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

    /**
     * Override to provide custom creative menu item with yellow/light gray colors
     */
    public void fillItemCategory(@NotNull CreativeModeTab.Output output) {
        ItemStack stack = new ItemStack(this);
        // Set creative menu colors (yellow shell, light gray band)
        setShellColor(stack, DEFAULT_CREATIVE_SHELL_COLOR);
        setBandColor(stack, DEFAULT_CREATIVE_BAND_COLOR);
        output.accept(stack);
    }

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

    /**
     * Gets the shell color, returning a default if not set
     */
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

    /**
     * Gets the shell color ID for model predicates (0-15)
     */
    public static int getShellColorId(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(SHELL_COLOR_TAG)) {
            return tag.getInt(SHELL_COLOR_TAG);
        }
        // Return the default color ID
        return DEFAULT_CRAFTED_SHELL_COLOR.getId();
    }

    public static void setShellColor(@NotNull ItemStack stack, @NotNull DyeColor color) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(SHELL_COLOR_TAG, color.getId());
    }

    /**
     * Gets the band color, returning a default if not set
     */
    @NotNull
    public static DyeColor getBandColor(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(BAND_COLOR_TAG)) {
            try {
                return DyeColor.byId(tag.getInt(BAND_COLOR_TAG));
            } catch (Exception e) {
                return DEFAULT_CRAFTED_BAND_COLOR;
            }
        }
        return DEFAULT_CRAFTED_BAND_COLOR;
    }

    /**
     * Gets the band color ID for model predicates (0-15)
     */
    public static int getBandColorId(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(BAND_COLOR_TAG)) {
            return tag.getInt(BAND_COLOR_TAG);
        }
        // Return the default color ID
        return DEFAULT_CRAFTED_BAND_COLOR.getId();
    }

    public static void setBandColor(@NotNull ItemStack stack, @NotNull DyeColor color) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(BAND_COLOR_TAG, color.getId());
    }

    /**
     * Checks if the stack has explicit dye colors set
     */
    public static boolean hasExplicitColors(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(SHELL_COLOR_TAG) && tag.contains(BAND_COLOR_TAG);
    }

    /**
     * Ensures colors are initialized with defaults
     * @param isCreative Whether this is for creative mode (different defaults)
     */
    public static void ensureColorsInitialized(@NotNull ItemStack stack, boolean isCreative) {
        CompoundTag tag = stack.getOrCreateTag();

        // Only initialize if not already set
        if (!tag.contains(SHELL_COLOR_TAG)) {
            DyeColor defaultShell = isCreative ? DEFAULT_CREATIVE_SHELL_COLOR : DEFAULT_CRAFTED_SHELL_COLOR;
            tag.putInt(SHELL_COLOR_TAG, defaultShell.getId());
        }

        if (!tag.contains(BAND_COLOR_TAG)) {
            DyeColor defaultBand = isCreative ? DEFAULT_CREATIVE_BAND_COLOR : DEFAULT_CRAFTED_BAND_COLOR;
            tag.putInt(BAND_COLOR_TAG, defaultBand.getId());
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        if (isOpen(stack)) {
            tooltip.add(Component.literal("Status: Open").withStyle(net.minecraft.ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Status: Closed").withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        DyeColor shellColor = getShellColor(stack);
        DyeColor bandColor = getBandColor(stack);

        tooltip.add(Component.literal("Shell: " + capitalize(shellColor.getName()))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Band: " + capitalize(bandColor.getName()))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

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

}