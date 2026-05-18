package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.custom.WhiteDenDenMushiEntity;
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
 * Fixed white body, dyeable shell
 */
public class WhiteDenDenMushiItem extends Item {

    private static final String SHELL_COLOR_TAG = "ShellColor";
    private static final String ENTITY_DATA_TAG = "EntityData";

    public WhiteDenDenMushiItem(Properties properties) {
        super(properties);
    }

    /**
     * Create item from entity (FIXED: uses WhiteDenDenMushiEntity)
     */
    public static ItemStack createFromEntity(WhiteDenDenMushiEntity entity) {
        ItemStack stack = new ItemStack(ModItems.WHITE_DEN_DEN_MUSHI.get());
        CompoundTag nbt = stack.getOrCreateTag();

        // Store shell color
        int entityShellColor = entity.getShellColor();
        nbt.putInt(SHELL_COLOR_TAG, entityShellColor);

        // Store entity data
        CompoundTag entityData = new CompoundTag();
        entity.addAdditionalSaveData(entityData);
        nbt.put(ENTITY_DATA_TAG, entityData);


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
     * Apply item data to entity when placed (FIXED: uses WhiteDenDenMushiEntity)
     */
    public static void applyToEntity(ItemStack stack, WhiteDenDenMushiEntity entity, Level level) {
        CompoundTag nbt = stack.getTag();

        // Apply shell color (body is always white in entity)
        if (nbt != null && nbt.contains(SHELL_COLOR_TAG)) {
            int storedColor = nbt.getInt(SHELL_COLOR_TAG);
            entity.setShellColor(storedColor);
        }

        // Apply other entity data if present
        if (nbt != null && nbt.contains(ENTITY_DATA_TAG)) {
            CompoundTag entityData = nbt.getCompound(ENTITY_DATA_TAG);
            entity.readAdditionalSaveData(entityData);
        }
    }

    public static boolean hasStoredColor(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt != null && nbt.contains(SHELL_COLOR_TAG);
    }

    public static int getShellColor(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(SHELL_COLOR_TAG)) {
            return nbt.getInt(SHELL_COLOR_TAG);
        }
        return 0;
    }

    public static void setShellColor(ItemStack stack, int colorId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WhiteDenDenMushiItem)) {
            return;
        }
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt(SHELL_COLOR_TAG, colorId);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos spawnPos = clickedPos.relative(face);

        if (!level.getBlockState(spawnPos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        if (level.getBlockState(spawnPos).isAir() &&
                level.getBlockState(spawnPos.below()).isSolid()) {

            // Create WHITE Den Den Mushi entity (FIXED: uses correct type)
            WhiteDenDenMushiEntity entity = ModEntities.WHITE_DEN_DEN_MUSHI.get().create(level);
            if (entity != null) {
                entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                // Apply stored data from item
                applyToEntity(context.getItemInHand(), entity, level);

                level.addFreshEntity(entity);

                level.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                        SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.5f,
                        0.8f + level.random.nextFloat() * 0.4f);

                if (!context.getPlayer().isCreative()) {
                    context.getItemInHand().shrink(1);
                }

                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("White Den Den Mushi")
                .withStyle(ChatFormatting.GRAY));

        if (hasStoredColor(stack)) {
            int shellColorId = getShellColor(stack);
            DyeColor shellColor = DyeColor.byId(shellColorId);

            String colorName = shellColor.getName();
            String displayName = colorName.substring(0, 1).toUpperCase() + colorName.substring(1);
            displayName = displayName.replace('_', ' ');

            tooltip.add(Component.literal("Shell: " + displayName)
                    .withStyle(ChatFormatting.AQUA));

            if (flag.isAdvanced()) {
                tooltip.add(Component.literal("Color ID: " + shellColorId)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.literal("Shell: Random")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        tooltip.add(Component.literal("Right-click to place")
                .withStyle(ChatFormatting.GRAY));
    }
}