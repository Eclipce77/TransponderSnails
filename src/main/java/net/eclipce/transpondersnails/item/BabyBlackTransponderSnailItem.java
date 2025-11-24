package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.entity.custom.BabyBlackTransponderSnailEntity;
import net.eclipce.transpondersnails.entity.ModEntities;
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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Baby Black Transponder Snail Item
 * Right-click on a block to place the entity
 */
public class BabyBlackTransponderSnailItem extends Item {

    // NBT tag keys
    private static final String SHELL_COLOR_TAG = "ShellColor";
    private static final String ENTITY_DATA_TAG = "EntityData";

    public BabyBlackTransponderSnailItem(Properties properties) {
        super(properties);
    }

    /**
     * Create an item from an entity, preserving its shell color
     */
    public static ItemStack createFromEntity(BabyBlackTransponderSnailEntity entity) {
        ItemStack stack = new ItemStack(ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get());
        CompoundTag nbt = stack.getOrCreateTag();

        // Store shell color
        int entityShellColor = entity.getShellColor();
        nbt.putInt(SHELL_COLOR_TAG, entityShellColor);

        // Store entity data
        CompoundTag entityData = new CompoundTag();
        entity.addAdditionalSaveData(entityData);
        nbt.put(ENTITY_DATA_TAG, entityData);

        System.out.println("Created Baby Black Snail item with shell color: " +
                DyeColor.byId(entityShellColor).getName() + " (ID: " + entityShellColor + ")");

        return stack;
    }

    /**
     * Apply item data to entity when placed.
     * If item has no color stored (e.g., from creative menu), generates random color.
     */
    public static void applyToEntity(ItemStack stack, BabyBlackTransponderSnailEntity entity, Level level) {
        CompoundTag nbt = stack.getTag();

        // Check if item has shell color stored
        if (nbt != null && nbt.contains(SHELL_COLOR_TAG)) {
            // Apply stored color
            int storedColor = nbt.getInt(SHELL_COLOR_TAG);
            entity.setShellColor(storedColor);
            System.out.println("Applied stored shell color to entity: " +
                    DyeColor.byId(storedColor).getName() + " (ID: " + storedColor + ")");
        } else {
            // No color stored (creative menu item) - generate random
            int randomColor = level.random.nextInt(16);
            entity.setShellColor(randomColor);
            System.out.println("Generated random shell color for entity: " +
                    DyeColor.byId(randomColor).getName() + " (ID: " + randomColor + ")");
        }

        // Apply other entity data if present
        if (nbt != null && nbt.contains(ENTITY_DATA_TAG)) {
            CompoundTag entityData = nbt.getCompound(ENTITY_DATA_TAG);
            entity.readAdditionalSaveData(entityData);
        }
    }

    /**
     * Check if item has a stored shell color
     */
    public static boolean hasStoredColor(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt != null && nbt.contains(SHELL_COLOR_TAG);
    }

    /**
     * Get the shell color from the item (DyeColor ID 0-15)
     */
    public static int getShellColor(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(SHELL_COLOR_TAG)) {
            return nbt.getInt(SHELL_COLOR_TAG);
        }
        return 0; // Default white
    }

    /**
     * Get the shell color name for this item
     */
    public static String getShellColorName(ItemStack stack) {
        int colorId = getShellColor(stack);
        return DyeColor.byId(colorId).getName();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Get the position to spawn the entity
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos spawnPos = clickedPos.relative(face);

        // Check if there's space to spawn the entity
        if (!level.getBlockState(spawnPos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        // Create the entity
        BabyBlackTransponderSnailEntity snailEntity = new BabyBlackTransponderSnailEntity(
                ModEntities.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                level
        );

        // Position the entity
        Vec3 spawnPosition = Vec3.atBottomCenterOf(spawnPos);
        snailEntity.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);

        // Set rotation based on player's facing direction
        float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0F;
        snailEntity.setYRot(yaw);
        snailEntity.yRotO = yaw;

        // Apply stored data from item (including shell color)
        // This now handles creative menu items by generating random color
        applyToEntity(context.getItemInHand(), snailEntity, level);

        // Spawn the entity
        level.addFreshEntity(snailEntity);

        // Play placement sound
        level.playSound(
                null,
                spawnPos,
                SoundEvents.SLIME_BLOCK_PLACE,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );

        // Decrease item stack count
        context.getItemInHand().shrink(1);

        return InteractionResult.CONSUME;
    }

    /**
     * Add tooltip showing shell color
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (hasStoredColor(stack)) {
            int shellColorId = getShellColor(stack);
            DyeColor shellColor = DyeColor.byId(shellColorId);

            // Capitalize first letter of color name
            String colorName = shellColor.getName();
            String displayName = colorName.substring(0, 1).toUpperCase() + colorName.substring(1);
            displayName = displayName.replace('_', ' '); // Convert underscores to spaces

            tooltip.add(Component.literal("Shell: " + displayName)
                    .withStyle(ChatFormatting.GRAY));

            if (flag.isAdvanced()) {
                tooltip.add(Component.literal("Color ID: " + shellColorId)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            // No color stored - will be random when placed
            tooltip.add(Component.literal("Shell: Random")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}