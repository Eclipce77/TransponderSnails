package net.eclipce.transpondersnails.gary;

import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Asks Minecraft's own naming of creatures and items whether something is Gary (see {@link GaryName}). Being Gary is only ever a matter of the current
 * NAME: it changes how the thing is drawn and nothing else, so its shell and body colours are never touched, and call it anything else and it is drawn
 * as the snail it always was, in the colours it always had.
 */
public final class GaryHelper {

    private GaryHelper() {}

    public static boolean isGary(@Nullable Component name) {
        return name != null && GaryName.matches(name.getString());
    }

    /** A creature named with a name tag, or spawned from a renamed spawn egg or Den Den Mushi item. */
    public static boolean isGary(@Nullable Entity entity) {
        return entity != null && entity.hasCustomName() && isGary(entity.getCustomName());
    }

    /** An item renamed (at an anvil, say). Only a name somebody gave it counts, never the item's own. */
    public static boolean isGary(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasCustomHoverName() && isGary(stack.getHoverName());
    }

    /**
     * Does this placed block look like Gary? Only the plain Transponder Snail can (the gary state is only ever set on it), so this is the same question the
     * block's model asks - used so that the sounds it makes follow its look.
     */
    public static boolean isGaryTransponderSnail(@Nullable BlockState state) {
        return state != null && state.getBlock() == ModBlocks.TRANSPONDER_SNAIL.get() && state.getValue(TransponderSnailBlock.GARY);
    }

    /** Does this item look like Gary? Only the plain Transponder Snail item does (the same test as its model's), and only while it is named Gary. */
    public static boolean isGaryTransponderSnail(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModBlocks.TRANSPONDER_SNAIL.get().asItem() && isGary(stack);
    }
}
