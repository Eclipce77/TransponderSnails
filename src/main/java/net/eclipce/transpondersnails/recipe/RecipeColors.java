package net.eclipce.transpondersnails.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Reads and writes the colours of a snail ITEM for the recipes, using {@link ColorCarry} so that every recipe treats them the same way: the colours
 * a snail has are the colours of whatever it is made into (or back into), never reset and never changed.
 */
public final class RecipeColors {

    private RecipeColors() {}

    /** The {body, shell} colours this item carries (wherever it keeps them), or null if it has no body colour. */
    @Nullable
    public static int[] read(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        CompoundTag be = tag.getCompound("BlockEntityTag");
        return ColorCarry.resolve(
                firstInt(tag, "body_color", "BodyColor"), firstInt(tag, "shell_color", "ShellColor"),
                firstInt(be, "BodyColor"), firstInt(be, "ShellColor"));
    }

    /** Writes colours onto a snail BLOCK item the way the snail crafting recipe always has: top-level, and in the BlockEntityTag a placed block reads. */
    public static void writeBlockItem(ItemStack result, int bodyColor, int shellColor) {
        CompoundTag nbt = result.getOrCreateTag();
        nbt.putInt("body_color", bodyColor);
        nbt.putInt("shell_color", shellColor);
        CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
        blockEntityTag.putInt("BodyColor", bodyColor);
        blockEntityTag.putInt("ShellColor", shellColor);
        blockEntityTag.putBoolean("ColorsInitialized", true);
        nbt.put("BlockEntityTag", blockEntityTag);
    }

    @Nullable
    private static Integer firstInt(CompoundTag tag, String... keys) {
        for (String key : keys) if (tag.contains(key)) return tag.getInt(key);
        return null;
    }
}
