package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.custom.BlackTransponderSnailEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Black Den Den Mushi as an item: the creature, picked up. It is an ingredient (the Black Transponder Snail is crafted from it) and it has NO block
 * form - right-clicking a block lets the creature go again, as an entity, never as a placed block.
 *
 * The shell colour is kept the way the Black Transponder Snail keeps it ({@code shell_color}, a dye id 0-15), so the shell dye recipe works on it
 * and the Black Transponder Snail made from it comes out with the same shell.
 */
public class BlackDenDenMushiItem extends Item {

    public static final String SHELL_COLOR_TAG = "shell_color";

    public BlackDenDenMushiItem(Properties properties) {
        super(properties);
    }

    // =================== Shell colour ===================

    /** The shell colour (a dye id, 0-15); white when the stack has none. */
    public static int getShellColorId(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(SHELL_COLOR_TAG) ? clampDye(tag.getInt(SHELL_COLOR_TAG)) : 0;
    }

    public static void setShellColorId(@NotNull ItemStack stack, int dyeId) {
        stack.getOrCreateTag().putInt(SHELL_COLOR_TAG, clampDye(dyeId));
    }

    private static int clampDye(int id) {
        return Math.max(0, Math.min(15, id));
    }

    // =================== Making one ===================

    /** A Black Den Den Mushi with this shell colour (what picking the creature up, and the stonecutter, give). */
    public static ItemStack create(int shellColorId) {
        ItemStack stack = new ItemStack(ModItems.BLACK_DEN_DEN_MUSHI.get());
        setShellColorId(stack, shellColorId);
        return stack;
    }

    /** Picking the creature up: the item keeps the creature's shell colour. */
    public static ItemStack createFromEntity(@NotNull BlackTransponderSnailEntity entity) {
        return create(entity.getShellColor());
    }

    // =================== Letting it go ===================

    /**
     * Right-click on a block: the creature is put back as an entity (in the air or in water, on something solid), with the shell the item has.
     * It never places a block.
     */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        BlockPos target = context.getClickedPos().relative(face);
        BlockState inTarget = level.getBlockState(target);

        boolean roomFor = inTarget.isAir() || inTarget.getFluidState().is(FluidTags.WATER);
        boolean ground = level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP);
        if (!roomFor || !ground) return InteractionResult.PASS;

        if (!level.isClientSide) {
            Entity entity = createCreature(level, context.getItemInHand());
            if (entity != null) {
                float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
                entity.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, yaw, 0.0F);
                level.addFreshEntity(entity);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.5F, 0.8F + level.random.nextFloat() * 0.4F);
                if (player == null || !player.isCreative()) context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The creature this item lets go, with the item's shell colour (set directly, not through finalizeSpawn, which would pick a random shell).
     * The baby item overrides this.
     */
    @Nullable
    protected Entity createCreature(@NotNull Level level, @NotNull ItemStack stack) {
        BlackTransponderSnailEntity entity = ModEntities.BLACK_DEN_DEN_MUSHI.get().create(level);
        if (entity != null) entity.setShellColor(getShellColorId(stack));
        return entity;
    }

    // =================== Tooltip ===================

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Used to craft the Black Transponder Snail").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click a block to let it go").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
