package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.eclipce.transpondersnails.entity.custom.HornedDenDenMushiEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Pickup item for the Horned Den Den Mushi.
 *
 * Extends {@link DenDenMushiItem} for the shared static helpers
 * (getShellColor, getBodyColor, isCaptured, applyToEntity) but overrides
 * the three methods the base class hardcodes to the regular DDM:
 *
 *   - createFromEntity  → produces a HornedDenDenMushiItem stack
 *   - useOn             → spawns a HornedDenDenMushiEntity
 *   - appendHoverText   → shows the correct name in the tooltip
 */
public class HornedDenDenMushiItem extends DenDenMushiItem {

    // Must match the private constants in DenDenMushiItem exactly
    private static final String BODY_COLOR_TAG  = "BodyColor";
    private static final String SHELL_COLOR_TAG = "ShellColor";
    private static final String ENTITY_DATA_TAG = "EntityData";

    public HornedDenDenMushiItem(Properties properties) {
        super(properties);
    }

    // -----------------------------------------------------------------------
    // createFromEntity
    // Cannot delegate to DenDenMushiItem.createFromEntity because that method
    // hardcodes new ItemStack(ModItems.DEN_DEN_MUSHI.get()).
    // -----------------------------------------------------------------------

    public static ItemStack createFromEntity(DenDenMushiEntity entity) {
        ItemStack stack = new ItemStack(ModItems.HORNED_DEN_DEN_MUSHI.get());
        CompoundTag nbt = stack.getOrCreateTag();

        nbt.putInt(BODY_COLOR_TAG,  entity.getBodyColor());
        nbt.putInt(SHELL_COLOR_TAG, entity.getShellColor());

        // Store full entity save data (age, custom name, etc.)
        CompoundTag entityData = new CompoundTag();
        entity.addAdditionalSaveData(entityData);
        nbt.put(ENTITY_DATA_TAG, entityData);

        System.out.println("HornedDenDenMushiItem: created stack from entity");
        System.out.println("  Shell: " + DyeColor.byId(entity.getShellColor()).getName());
        System.out.println("  Body:  #" + Integer.toHexString(entity.getBodyColor()).toUpperCase());

        return stack;
    }

    // -----------------------------------------------------------------------
    // useOn — spawns HornedDenDenMushiEntity, not a regular DenDenMushiEntity
    // -----------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos      = context.getClickedPos();
            Direction face    = context.getClickedFace();
            BlockPos spawnPos = pos.relative(face);

            if (level.getBlockState(spawnPos).isAir() &&
                    level.getBlockState(spawnPos.below()).isSolid()) {

                HornedDenDenMushiEntity entity =
                        ModEntities.HORNED_DEN_DEN_MUSHI.get().create(level);

                if (entity != null) {
                    entity.setPos(
                            spawnPos.getX() + 0.5,
                            spawnPos.getY(),
                            spawnPos.getZ() + 0.5);

                    // applyToEntity is a public static method on the parent — reuse it
                    DenDenMushiItem.applyToEntity(context.getItemInHand(), entity);

                    level.addFreshEntity(entity);

                    level.playSound(null,
                            spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                            SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS,
                            0.5f, 0.8f + level.random.nextFloat() * 0.4f);

                    if (!context.getPlayer().isCreative()) {
                        context.getItemInHand().shrink(1);
                    }

                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    // -----------------------------------------------------------------------
    // appendHoverText — shows "Horned Den Den Mushi" instead of "Den Den Mushi"
    // -----------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        if (isCaptured(stack)) {
            tooltip.add(Component.literal("Captured Horned Den Den Mushi")
                    .withStyle(ChatFormatting.GRAY));

            if (flag.isAdvanced()) {
                int bodyColor    = getBodyColor(stack);
                int shellColorId = getShellColor(stack);
                DyeColor shellColor = DyeColor.byId(shellColorId);

                tooltip.add(Component.literal("Body: #" +
                                String.format("%06X", bodyColor).toUpperCase())
                        .withStyle(ChatFormatting.DARK_GRAY));

                tooltip.add(Component.literal("Shell: " +
                                shellColor.getName().substring(0, 1).toUpperCase() +
                                shellColor.getName().substring(1))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.literal("Wild Horned Den Den Mushi")
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("Right-click to place")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}