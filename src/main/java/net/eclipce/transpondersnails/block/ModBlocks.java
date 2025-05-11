package net.eclipce.transpondersnails.block;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TransponderSnails.MOD_ID);

    public static final RegistryObject<Block> TRANSPONDER_SNAIL = registerBlock("transponder_snail",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRAIN_CORAL).sound(SoundType.CORAL_BLOCK)));
    public static final RegistryObject<Block> HORNED_TRANSPONDER_SNAIL = registerBlock("horned_transponder_snail",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRAIN_CORAL).sound(SoundType.CORAL_BLOCK)));
    public static final RegistryObject<Block> VISUAL_TRANSPONDER_SNAIL = registerBlock("visual_transponder_snail",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRAIN_CORAL).sound(SoundType.CORAL_BLOCK)));
    public static final RegistryObject<Block> SURVEILLANCE_TRANSPONDER_SNAIL = registerBlock("surveillance_transponder_snail",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRAIN_CORAL).sound(SoundType.CORAL_BLOCK)));
    public static final RegistryObject<Block> TRANSMISSION_TRANSPONDER_SNAIL = registerBlock("transmission_transponder_snail",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BRAIN_CORAL).sound(SoundType.CORAL_BLOCK)));


    private static <T extends Block>  RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

}
