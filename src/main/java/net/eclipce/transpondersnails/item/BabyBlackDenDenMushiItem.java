package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.custom.BabyBlackTransponderSnailEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Baby Black Den Den Mushi as an item: the small creature, picked up. Like the adult one it is a crafting ingredient (the Baby Black Transponder
 * Snail is crafted from it), has no block form, and lets the creature go again when a block is right-clicked. The shell colour ({@code shell_color}) and the
 * release are inherited from {@link BlackDenDenMushiItem}; only the creature it lets go is the baby.
 */
public class BabyBlackDenDenMushiItem extends BlackDenDenMushiItem {

    public BabyBlackDenDenMushiItem(Properties properties) {
        super(properties);
    }

    /** A Baby Black Den Den Mushi with this shell colour. */
    public static ItemStack createBaby(int shellColorId) {
        ItemStack stack = new ItemStack(ModItems.BABY_BLACK_DEN_DEN_MUSHI.get());
        setShellColorId(stack, shellColorId);
        return stack;
    }

    /** Picking the creature up: the item keeps the creature's shell colour. */
    public static ItemStack createFromEntity(@NotNull BabyBlackTransponderSnailEntity entity) {
        return createBaby(entity.getShellColor());
    }

    @Override
    @Nullable
    protected Entity createCreature(@NotNull Level level, @NotNull ItemStack stack) {
        BabyBlackTransponderSnailEntity entity = ModEntities.BABY_BLACK_DEN_DEN_MUSHI.get().create(level);
        if (entity != null) entity.setShellColor(getShellColorId(stack));
        return entity;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        // not super: the adult tooltip names the adult snail
        tooltip.add(Component.literal("Used to craft the Baby Black Transponder Snail").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click a block to let it go").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
