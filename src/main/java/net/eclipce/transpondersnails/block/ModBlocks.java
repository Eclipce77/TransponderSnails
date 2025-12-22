package net.eclipce.transpondersnails.block;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.custom.WireBlock;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.item.TransponderSnailItem;
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

import static net.eclipce.transpondersnails.item.ModItems.ITEMS;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TransponderSnails.MOD_ID);

    // Only register the BLOCK here, not the item
    public static final RegistryObject<Block> TRANSPONDER_SNAIL = BLOCKS.register("transponder_snail",
            () -> new TransponderSnailBlock(BlockBehaviour.Properties.copy(Blocks.BRAIN_CORAL).sound(SoundType.CORAL_BLOCK)));

    public static final RegistryObject<Block> TRANSPONDER_SNAIL_TRANSMITTER = BLOCKS.register("transponder_snail_transmitter",
            () -> new TransponderSnailBlock(BlockBehaviour.Properties.copy(Blocks.BRAIN_CORAL).sound(SoundType.CORAL_BLOCK)));

    public static final RegistryObject<Block> WIRE = BLOCKS.register("wire",
            () -> new WireBlock(BlockBehaviour.Properties.of()
                    .strength(0.0F) // Can be broken instantly
                    .sound(SoundType.COPPER)
                    .noCollission() // No collision
                    .noOcclusion() // Allows light through
                    .isRedstoneConductor((state, level, pos) -> true) // Redstone conductor
                    .isSuffocating((state, level, pos) -> false) // Doesn't suffocate
                    .isViewBlocking((state, level, pos) -> false) // Can see through
            )
    );

    private static <T extends Block>  RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<T> registerBlockWithCustomStack(String name, Supplier<T> block, int stackSize) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItemWithStack(name, toReturn, stackSize);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItemWithStack(String name, RegistryObject<T> block, int stackSize) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().stacksTo(stackSize)));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}