package net.eclipce.transpondersnails.block;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock;
import net.eclipce.transpondersnails.block.custom.HornedDenDenMushiBlock;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.custom.WhiteTransponderSnailBlock;
import net.eclipce.transpondersnails.block.custom.WireBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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

    public static final RegistryObject<Block> WHITE_TRANSPONDER_SNAIL = BLOCKS.register("white_transponder_snail",
            () -> new WhiteTransponderSnailBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.CORAL_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final RegistryObject<Block> WIRE = BLOCKS.register("wire",
            () -> new WireBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .sound(SoundType.COPPER)
                    .pushReaction(PushReaction.DESTROY)
            ));

    public static final RegistryObject<Block> BLACK_TRANSPONDER_SNAIL_BLOCK = BLOCKS.register("black_transponder_snail_block",
            () -> new BlackTransponderSnailBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.5F)
                    .sound(SoundType.CORAL_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> {
                        int callState = state.getValue(BlackTransponderSnailBlock.CALL_STATE);
                        return callState == 3 ? 7 : (callState > 0 ? 3 : 0);
                    })));

    /**
     * The Horned Den Den Mushi jammer block.
     *
     * - No block item is registered here — the existing HornedDenDenMushiItem
     *   places this block on sneak+right-click.
     * - noOcclusion() so the BER is not culled by neighbouring blocks.
     * - strength(0.5F) so it can be broken easily (same as TransponderSnailBlock).
     * - pushReaction(DESTROY) so pistons don't move it (they break it and drop the item).
     */
    public static final RegistryObject<Block> HORNED_DEN_DEN_MUSHI_BLOCK = BLOCKS.register("horned_den_den_mushi_block",
            () -> new HornedDenDenMushiBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.CORAL_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
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