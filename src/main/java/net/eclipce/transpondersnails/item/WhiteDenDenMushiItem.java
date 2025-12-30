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

/**
 * White Den Den Mushi Item
 * White body (fixed, cannot be dyed) with dyeable shell
 * Shell uses same textures as regular Den Den Mushi
 */
public class WhiteDenDenMushiItem extends Item {

    // NBT tag keys
    private static final String SHELL_COLOR_TAG = "ShellColor";
    private static final String ENTITY_DATA_TAG = "EntityData";

    // Fixed white body color
    public static final int WHITE_BODY_COLOR = 0xFFFFFF; // Pure white

    public WhiteDenDenMushiItem(Properties properties) {
        super(properties);
    }

    /**
     * Create an item from an entity, preserving its shell color
     */
    public static ItemStack createFromEntity(DenDenMushiEntity entity) {
        ItemStack stack = new ItemStack(ModItems.WHITE_DEN_DEN_MUSHI.get());
        CompoundTag nbt = stack.getOrCreateTag();

        // Store shell color
        int entityShellColor = entity.getShellColor();
        nbt.putInt(SHELL_COLOR_TAG, entityShellColor);

        // Store entity data
        CompoundTag entityData = new CompoundTag();
        entity.addAdditionalSaveData(entityData);
        nbt.put(ENTITY_DATA_TAG, entityData);

        System.out.println("Created White Den Den Mushi item with shell color: " +
                DyeColor.byId(entityShellColor).getName() + " (ID: " + entityShellColor + ")");

        return stack;
    }

    /**
     * Create item with specific shell color
     */
    public static ItemStack createWithColor(int shellColor) {
        ItemStack stack = new ItemStack(ModItems.WHITE_DEN_DEN_MUSHI.get());
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt(SHELL_COLOR_TAG, shellColor);
        return stack;
    }

    /**
     * Apply item data to entity when placed
     */
    public static void applyToEntity(ItemStack stack, DenDenMushiEntity entity, Level level) {
        CompoundTag nbt = stack.getTag();

        // White Den Den Mushi always has white body
        entity.setBodyColor(WHITE_BODY_COLOR);

        // Apply shell color
        if (nbt != null && nbt.contains(SHELL_COLOR_TAG)) {
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
     * Set the shell color for the item
     */
    public static void setShellColor(ItemStack stack, int colorId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WhiteDenDenMushiItem)) {
            return;
        }
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt(SHELL_COLOR_TAG, colorId);
    }

    /**
     * Get the body color (always white for White Den Den Mushi)
     */
    public static int getBodyColor(ItemStack stack) {
        return WHITE_BODY_COLOR; // Always white
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

        // Check if the position is valid for spawning
        if (level.getBlockState(spawnPos).isAir() &&
                level.getBlockState(spawnPos.below()).isSolid()) {

            // Create and spawn entity
            DenDenMushiEntity entity = ModEntities.DEN_DEN_MUSHI.get().create(level);
            if (entity != null) {
                entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                // Apply stored data from item (shell color + white body)
                applyToEntity(context.getItemInHand(), entity, level);

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

        return InteractionResult.PASS;
    }

    /**
     * Add tooltip showing shell color
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("White Den Den Mushi")
                .withStyle(ChatFormatting.GRAY));

        if (hasStoredColor(stack)) {
            int shellColorId = getShellColor(stack);
            DyeColor shellColor = DyeColor.byId(shellColorId);

            // Capitalize first letter of color name
            String colorName = shellColor.getName();
            String displayName = colorName.substring(0, 1).toUpperCase() + colorName.substring(1);
            displayName = displayName.replace('_', ' '); // Convert underscores to spaces

            tooltip.add(Component.literal("Shell: " + displayName)
                    .withStyle(ChatFormatting.AQUA));

            if (flag.isAdvanced()) {
                tooltip.add(Component.literal("Color ID: " + shellColorId)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            // No color stored - will be random when placed
            tooltip.add(Component.literal("Shell: Random")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        tooltip.add(Component.literal("Right-click to place")
                .withStyle(ChatFormatting.GRAY));
    }
}