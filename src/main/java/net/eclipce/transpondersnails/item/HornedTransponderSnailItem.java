package net.eclipce.transpondersnails.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Horned Transponder Snail as an item: the jammer block, carried. It keeps its body and shell colours the way the other snails do, so
 * the snail crafting recipe (which writes {@code body_color} / {@code shell_color} and a {@code BlockEntityTag} onto its result) and the shell dye
 * recipe work on it, and a placed one keeps them when it is picked up again.
 */
public class HornedTransponderSnailItem extends BlockItem {

    private static final int DEFAULT_BODY_COLOR = 0xF5E6A3;   // the pastel yellow of a Den Den Mushi

    public HornedTransponderSnailItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** The shell colour (a dye id, 0-15): {@code shell_color}, else {@code ShellColor}, else the block entity data, else white. */
    public static int getShellColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        if (tag.contains("shell_color")) return clampDye(tag.getInt("shell_color"));
        if (tag.contains("ShellColor")) return clampDye(tag.getInt("ShellColor"));
        CompoundTag be = tag.getCompound("BlockEntityTag");
        return be.contains("ShellColor") ? clampDye(be.getInt("ShellColor")) : 0;
    }

    /** The body colour (RGB): {@code body_color}, else {@code BodyColor}, else the block entity data, else the Den Den Mushi's yellow. */
    public static int getBodyColor(ItemStack stack) {
        Integer carried = carriedBodyColor(stack);
        return carried != null ? carried : DEFAULT_BODY_COLOR;
    }

    /** The body colour this stack carries, or null if it has none (a creative-menu one, or one that was only dyed). */
    @Nullable
    public static Integer carriedBodyColor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        if (tag.contains("body_color")) return tag.getInt("body_color");
        if (tag.contains("BodyColor")) return tag.getInt("BodyColor");
        CompoundTag be = tag.getCompound("BlockEntityTag");
        return be.contains("BodyColor") ? be.getInt("BodyColor") : null;
    }

    /**
     * The body colour of the snail about to be placed from this stack: the colour the stack carries, else a NEW random pastel - drawn for this placement
     * alone and never written back to the stack. (Writing it onto the stack would give every snail placed from one creative stack the same colour.)
     */
    public static int bodyColorForPlacement(ItemStack stack, java.util.Random random) {
        return PastelColors.forPlacement(carriedBodyColor(stack), random);
    }

    private static int clampDye(int id) {
        return Math.max(0, Math.min(15, id));
    }

    /** A Horned Transponder Snail with these colours (what a broken block drops, and what the creative menu picks). */
    public static ItemStack create(int shellColor, int bodyColor) {
        ItemStack stack = new ItemStack(ModItems.HORNED_TRANSPONDER_SNAIL.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("shell_color", clampDye(shellColor));
        tag.putInt("body_color", bodyColor);
        CompoundTag be = new CompoundTag();
        be.putInt("ShellColor", clampDye(shellColor));
        be.putInt("BodyColor", bodyColor);
        be.putBoolean("ColorsInitialized", true);
        tag.put("BlockEntityTag", be);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Right click to switch on or off").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Fills calls with static, more the nearer you are").withStyle(ChatFormatting.DARK_GRAY));
    }
}
