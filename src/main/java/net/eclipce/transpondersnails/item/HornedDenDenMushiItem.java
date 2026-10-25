package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.block.entity.HornedDenDenMushiBlockEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Pickup item for the Horned Den Den Mushi.
 *
 * Extends {@link DenDenMushiItem} for the shared static helpers
 * (getShellColor, getBodyColor, isCaptured, applyToEntity) but overrides
 * the three methods the base class hardcodes to the regular DDM:
 *
 *   - createFromEntity  → produces a HornedDenDenMushiItem stack
 *   - createFromColors  → produces a HornedDenDenMushiItem stack from raw colour values
 *                         (used by HornedDenDenMushiBlock.getDrops)
 *   - useOn             → two modes:
 *                           Normal right-click   → spawn HornedDenDenMushiEntity
 *                           (the jammer block is the separate Horned Transponder Snail)
 *   - appendHoverText   → shows the correct name and placement tips in the tooltip
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


        return stack;
    }

    // -----------------------------------------------------------------------
    // createFromColors
    // Used by HornedDenDenMushiBlock.getDrops() to rebuild the item stack
    // with the correct colours when the placed block is broken.
    // -----------------------------------------------------------------------

    public static ItemStack createFromColors(int shellColor, int bodyColor) {
        ItemStack stack = new ItemStack(ModItems.HORNED_DEN_DEN_MUSHI.get());
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt(SHELL_COLOR_TAG, shellColor);
        nbt.putInt(BODY_COLOR_TAG,  bodyColor);
        return stack;
    }

    // -----------------------------------------------------------------------
    // useOn
    //
    //  Normal right-click  → spawns HornedDenDenMushiEntity  (existing behaviour)
    //  (the jammer block is the separate Horned Transponder Snail item)
    // -----------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level   = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos  = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos target = pos.relative(face);

        // ── Shared pre-checks ──────────────────────────────────────────────
        // Validate on both sides so the client gets the right result for the
        // arm-swing animation.
        boolean canPlace = level.getBlockState(target).isAir()
                && level.getBlockState(target.below()).isSolid();

        if (!canPlace) return InteractionResult.PASS;

        // ── Spawn the creature (the jammer BLOCK is its own item now: the Horned Transponder Snail) ──
        if (!level.isClientSide) {
            spawnEntity(context, level, target, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Spawns a HornedDenDenMushiEntity at the target position and consumes
     * one item from the player's stack.
     */
    private void spawnEntity(UseOnContext context, Level level,
                             BlockPos target, Player player) {
        HornedDenDenMushiEntity entity =
                ModEntities.HORNED_DEN_DEN_MUSHI.get().create(level);

        if (entity != null) {
            entity.setPos(
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5
            );

            // applyToEntity is a public static method on the parent — reuse it
            DenDenMushiItem.applyToEntity(context.getItemInHand(), entity);

            level.addFreshEntity(entity);

            level.playSound(
                    null,
                    target.getX(), target.getY(), target.getZ(),
                    SoundEvents.SLIME_SQUISH_SMALL,
                    SoundSource.BLOCKS,
                    0.5f,
                    0.8f + level.random.nextFloat() * 0.4f
            );

            if (player != null && !player.isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
    }

    // -----------------------------------------------------------------------
    // appendHoverText
    // -----------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        if (isCaptured(stack)) {
            tooltip.add(Component.literal("Captured Horned Den Den Mushi")
                    .withStyle(ChatFormatting.DARK_GRAY));

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
        }

        tooltip.add(Component.literal("Sneak + Right-click to activate as jammer")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}