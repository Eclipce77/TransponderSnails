package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.block.custom.AmplifiedTransponderSnailBlock;
import net.eclipce.transpondersnails.client.AmplifiedSnailClientExtensions;
import net.eclipce.transpondersnails.voice.server.AmplifiedSnailManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handheld Amplified Transponder Snail.
 *
 * Hold right-click to amplify your voice (megaphone pose), release to stop.
 * Sneak + right-click on a block to place it.
 *
 * The visual state (idle / sound / call / active) lives in the stack's NBT and is written ONLY on the
 * server; the "transpondersnails:amplifier_state" item predicate reads it on the client.
 */
public class AmplifiedTransponderSnailItem extends TransponderSnailItem {

    public static final String STATE_TAG = "amplifier_state";
    public static final String SOUND_UNTIL_TAG = "amplifier_sound_until";

    private static final int USE_DURATION = 72000; // Same "hold forever" duration vanilla uses for bows/horns

    public AmplifiedTransponderSnailItem(Block block, Properties properties) {
        super(block, properties);
    }

    // =================== USE (HOLD RIGHT-CLICK) ===================

    /**
     * Right-clicking a block without sneaking returns PASS here, so Minecraft falls through to use()
     * and the megaphone starts instead of the snail being placed.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            AmplifiedSnailManager manager = AmplifiedSnailManager.get();
            if (manager == null || !manager.startHandheld(serverPlayer, stack)) {
                serverPlayer.displayClientMessage(Component.literal("Voice chat is not available!")
                        .withStyle(ChatFormatting.RED), true);
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    /**
     * CUSTOM (added by Forge) turns off vanilla's goat horn animation in both first and third person;
     * AmplifiedSnailClientExtensions provides the megaphone animation instead.
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    /**
     * Only called on the client by Forge, so the client-only extensions class is never loaded on a
     * dedicated server.
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(AmplifiedSnailClientExtensions.INSTANCE);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        stopAmplifying(level, entity);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        stopAmplifying(level, entity);
        return stack;
    }

    private static void stopAmplifying(Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            AmplifiedSnailManager manager = AmplifiedSnailManager.get();
            if (manager != null) {
                manager.stopHandheld(serverPlayer);
            }
        }
    }

    // =================== NO RE-EQUIP BOB / KEEP USING ON STATE CHANGES ===================

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // State changes only touch NBT - no bob. Still animate real slot/item switches.
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        // The server rewrites this stack's NBT while it's in use; that must not interrupt the hold
        return oldStack.getItem() == newStack.getItem();
    }

    // =================== STATE UPKEEP ===================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (level.isClientSide) {
            return;
        }

        int state = getState(stack);
        if (state == AmplifiedTransponderSnailBlock.STATE_SOUND) {
            if (level.getGameTime() >= getSoundUntil(stack)) {
                setState(stack, AmplifiedTransponderSnailBlock.STATE_IDLE);
            }
        } else if (state == AmplifiedTransponderSnailBlock.STATE_CALL || state == AmplifiedTransponderSnailBlock.STATE_ACTIVE) {
            // Stale "on" state (relog, crash, restart...) with nothing actually amplifying
            AmplifiedSnailManager manager = AmplifiedSnailManager.get();
            boolean reallyActive = manager != null && manager.isActiveHandheldStack(entity.getUUID(), stack);
            if (!reallyActive) {
                enterSoundState(stack, level.getGameTime());
            }
        }
    }

    /** Dropped on the ground: still finish the "sound" state / clear stale states. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (!level.isClientSide) {
            int state = getState(stack);
            if (state == AmplifiedTransponderSnailBlock.STATE_CALL || state == AmplifiedTransponderSnailBlock.STATE_ACTIVE
                    || (state == AmplifiedTransponderSnailBlock.STATE_SOUND && level.getGameTime() >= getSoundUntil(stack))) {
                setState(stack, AmplifiedTransponderSnailBlock.STATE_IDLE);
                entity.setItem(stack.copy()); // new instance so the synched data actually updates
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Hold Right-Click to amplify yourself").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    // =================== NBT HELPERS ===================

    public static int getState(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getInt(STATE_TAG) : AmplifiedTransponderSnailBlock.STATE_IDLE;
    }

    public static void setState(ItemStack stack, int state) {
        if (stack.isEmpty()) {
            return;
        }
        if (state == AmplifiedTransponderSnailBlock.STATE_IDLE) {
            // Remove rather than store 0 so idle stacks keep clean NBT (removeTagKey drops an empty tag)
            stack.removeTagKey(STATE_TAG);
            stack.removeTagKey(SOUND_UNTIL_TAG);
        } else {
            stack.getOrCreateTag().putInt(STATE_TAG, state);
            if (state != AmplifiedTransponderSnailBlock.STATE_SOUND) {
                stack.removeTagKey(SOUND_UNTIL_TAG);
            }
        }
    }

    public static void enterSoundState(ItemStack stack, long gameTime) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(STATE_TAG, AmplifiedTransponderSnailBlock.STATE_SOUND);
        tag.putLong(SOUND_UNTIL_TAG, gameTime + AmplifiedTransponderSnailBlock.SOUND_STATE_TICKS);
    }

    private static long getSoundUntil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getLong(SOUND_UNTIL_TAG) : 0L;
    }
}