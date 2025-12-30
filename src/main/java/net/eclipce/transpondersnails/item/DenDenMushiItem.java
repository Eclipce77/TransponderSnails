package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class DenDenMushiItem extends Item {

    // NBT tag keys
    private static final String BODY_COLOR_TAG = "BodyColor";
    private static final String SHELL_COLOR_TAG = "ShellColor";
    private static final String ENTITY_DATA_TAG = "EntityData";

    public DenDenMushiItem(Properties properties) {
        super(properties);
    }

    // 1. Add the missing createWithColors method to DenDenMushiItem first:
    // In DenDenMushiItem class, add:
    public static ItemStack createWithColors(int bodyColor, int shellColor) {
        ItemStack stack = new ItemStack(ModItems.DEN_DEN_MUSHI.get());
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt(BODY_COLOR_TAG, bodyColor);
        nbt.putInt(SHELL_COLOR_TAG, shellColor);
        return stack;
    }

    // Store entity data in item
    public static ItemStack createFromEntity(DenDenMushiEntity entity) {
        ItemStack stack = new ItemStack(ModItems.DEN_DEN_MUSHI.get());
        CompoundTag nbt = stack.getOrCreateTag();

        // Get colors from entity
        int entityShellColor = entity.getShellColor();
        int entityBodyColor = entity.getBodyColor();

        // Store colors in NBT
        nbt.putInt(BODY_COLOR_TAG, entityBodyColor);
        nbt.putInt(SHELL_COLOR_TAG, entityShellColor);

        // Store entity data
        CompoundTag entityData = new CompoundTag();
        entity.addAdditionalSaveData(entityData);
        nbt.put(ENTITY_DATA_TAG, entityData);

        System.out.println("===================================");

        return stack;
    }

    // FIXED: Apply item data to entity when placed
    // Prevents EntityData from overwriting freshly dyed colors
    public static void applyToEntity(ItemStack stack, DenDenMushiEntity entity) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null) return;

        System.out.println("=== applyToEntity DEBUG ===");
        System.out.println("Item NBT: " + nbt);
        System.out.println("Item has BodyColor: " + nbt.contains(BODY_COLOR_TAG));
        System.out.println("Item has ShellColor: " + nbt.contains(SHELL_COLOR_TAG));
        System.out.println("Item has EntityData: " + nbt.contains(ENTITY_DATA_TAG));

        // Apply colors from top-level NBT FIRST
        boolean hasTopLevelBodyColor = nbt.contains(BODY_COLOR_TAG);
        boolean hasTopLevelShellColor = nbt.contains(SHELL_COLOR_TAG);

        if (hasTopLevelBodyColor) {
            int bodyColor = nbt.getInt(BODY_COLOR_TAG);
            entity.setBodyColor(bodyColor);
            System.out.println("  Applied top-level body color: #" + Integer.toHexString(bodyColor));
        }
        if (hasTopLevelShellColor) {
            int shellColor = nbt.getInt(SHELL_COLOR_TAG);
            entity.setShellColor(shellColor);
            System.out.println("  Applied top-level shell color: " + shellColor);
        }

        // Apply other entity data, but ONLY if we didn't already set colors
        // This prevents EntityData from overwriting freshly dyed colors
        if (nbt.contains(ENTITY_DATA_TAG)) {
            CompoundTag entityData = nbt.getCompound(ENTITY_DATA_TAG);

            // If top-level colors exist, remove them from EntityData to prevent overwrite
            if (hasTopLevelBodyColor || hasTopLevelShellColor) {
                // Create a copy of entityData without color data
                CompoundTag sanitizedEntityData = entityData.copy();
                if (hasTopLevelBodyColor) {
                    sanitizedEntityData.remove("BodyColor");
                    System.out.println("  Removed BodyColor from EntityData to prevent overwrite");
                }
                if (hasTopLevelShellColor) {
                    sanitizedEntityData.remove("ShellColor");
                    System.out.println("  Removed ShellColor from EntityData to prevent overwrite");
                }
                entity.readAdditionalSaveData(sanitizedEntityData);
            } else {
                // No top-level colors, use EntityData as-is
                entity.readAdditionalSaveData(entityData);
                System.out.println("  Applied EntityData colors (no top-level override)");
            }
        }

        System.out.println("After applyToEntity:");
        System.out.println("  Entity body color: #" + Integer.toHexString(entity.getBodyColor()));
        System.out.println("  Entity shell color: " + entity.getShellColor());
        System.out.println("=========================");
    }

    // Get colors for rendering and tooltips
    public static int getBodyColor(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(BODY_COLOR_TAG)) {
            return nbt.getInt(BODY_COLOR_TAG);
        }
        return 0xF5E6A3; // Default pastel yellow
    }

    public static int getShellColor(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(SHELL_COLOR_TAG)) {
            int color = nbt.getInt(SHELL_COLOR_TAG);
            return color;
        }
        return 0; // Default white
    }

    /**
     * Sets the body and shell colors for a Den Den Mushi item
     */
    public static void setColors(ItemStack stack, int bodyColor, int shellColor) {
        if (stack.isEmpty() || !(stack.getItem() instanceof DenDenMushiItem)) {
            return;
        }

        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt("BodyColor", bodyColor);
        nbt.putInt("ShellColor", shellColor);
    }

    /**
     * Marks the Den Den Mushi as captured (has colors)
     */
    public static void setCaptured(ItemStack stack, boolean captured) {
        if (stack.isEmpty() || !(stack.getItem() instanceof DenDenMushiItem)) {
            return;
        }

        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putBoolean("IsCaptured", captured);
    }

    // Check if this item has been "captured" from an entity
    public static boolean isCaptured(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt != null && nbt.contains(BODY_COLOR_TAG);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos();
            Direction face = context.getClickedFace();
            BlockPos spawnPos = pos.relative(face);

            // Check if the position is valid for spawning
            if (level.getBlockState(spawnPos).isAir() &&
                    level.getBlockState(spawnPos.below()).isSolid()) {

                // Create and spawn entity
                DenDenMushiEntity entity = ModEntities.DEN_DEN_MUSHI.get().create(level);
                if (entity != null) {
                    entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                    // Apply stored data from item
                    applyToEntity(context.getItemInHand(), entity);

                    level.addFreshEntity(entity);

                    // Play placement sound
                    level.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                            SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.5f,
                            0.8f + level.random.nextFloat() * 0.4f);

                    // Remove item from player's hand
                    if (!context.getPlayer().isCreative()) {
                        context.getItemInHand().shrink(1);
                    }

                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    // Add tooltip showing colors and capture status
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isCaptured(stack)) {
            tooltip.add(Component.literal("Captured Den Den Mushi")
                    .withStyle(ChatFormatting.GRAY));

            if (flag.isAdvanced()) {
                // Show body color as hex
                int bodyColor = getBodyColor(stack);
                tooltip.add(Component.literal("Body: #" +
                                String.format("%06X", bodyColor).toUpperCase())
                        .withStyle(ChatFormatting.DARK_GRAY));

                // Show shell color by dye name
                int shellColorId = getShellColor(stack);
                DyeColor shellColor = DyeColor.byId(shellColorId);
                tooltip.add(Component.literal("Shell: " +
                                shellColor.getName().substring(0, 1).toUpperCase() +
                                shellColor.getName().substring(1))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.literal("Wild Den Den Mushi")
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("Right-click to place")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // Override to support shell color variants in model
    // This would need to be used with a custom item renderer or model predicate
    public String getShellVariant(ItemStack stack) {
        int shellColorId = getShellColor(stack);
        DyeColor shellColor = DyeColor.byId(shellColorId);
        return "den_den_mushi_shell_" + shellColor.getName();
    }
}