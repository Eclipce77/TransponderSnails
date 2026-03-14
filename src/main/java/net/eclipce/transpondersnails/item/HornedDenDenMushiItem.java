package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.custom.HornedDenDenMushiBlock;
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
 *                           Sneak + right-click  → place HornedDenDenMushiBlock (jammer)
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

        System.out.println("HornedDenDenMushiItem: created stack from entity");
        System.out.println("  Shell: " + DyeColor.byId(entity.getShellColor()).getName());
        System.out.println("  Body:  #" + Integer.toHexString(entity.getBodyColor()).toUpperCase());

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
    //  Sneak + right-click → places HornedDenDenMushiBlock   (jammer)
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

        // ── Branch: sneak → place jammer block ────────────────────────────
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                placeJammerBlock(context, level, target, player);
            }
            // sidedSuccess: SUCCESS on server, CONSUME on client (triggers arm swing)
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ── Branch: normal → spawn entity ──────────────────────────────────
        if (!level.isClientSide) {
            spawnEntity(context, level, target, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Places the HornedDenDenMushiBlock, writes shell/body colours to its
     * block entity, and consumes one item from the player's stack.
     */
    private void placeJammerBlock(UseOnContext context, Level level,
                                  BlockPos target, Player player) {
        Direction facing  = context.getHorizontalDirection().getOpposite();
        int shellColorId  = getShellColor(context.getItemInHand());

        // Build the block state with FACING and SHELL_COLOR set
        BlockState state = ModBlocks.HORNED_DEN_DEN_MUSHI_BLOCK.get()
                .defaultBlockState()
                .setValue(HornedDenDenMushiBlock.FACING, facing)
                .setValue(HornedDenDenMushiBlock.SHELL_COLOR, shellColorId);

        // Place the block (flags=3: notify neighbours + send to clients)
        level.setBlock(target, state, 3);

        // Write colour data into the freshly created block entity
        if (level.getBlockEntity(target) instanceof HornedDenDenMushiBlockEntity be) {
            be.setShellColor(shellColorId);
            be.setBodyColor(getBodyColor(context.getItemInHand()));
            // syncToClient() is called inside the setters above
        }

        // Placement sound
        level.playSound(
                null,
                target.getX(), target.getY(), target.getZ(),
                SoundEvents.SLIME_SQUISH_SMALL,
                SoundSource.BLOCKS,
                0.6f,
                0.9f + level.random.nextFloat() * 0.2f
        );

        System.out.println("HornedDenDenMushiItem: Placed jammer block at " + target
                + " (facing=" + facing + ", shell=" + shellColorId + ")");

        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
    }

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
        }

        tooltip.add(Component.literal("Right-click to place")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak + Right-click to activate as jammer")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}