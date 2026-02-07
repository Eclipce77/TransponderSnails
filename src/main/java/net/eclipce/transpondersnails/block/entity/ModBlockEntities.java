package net.eclipce.transpondersnails.block.entity;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TransponderSnails.MOD_ID);

    public static final RegistryObject<BlockEntityType<TransponderSnailBlockEntity>> TRANSPONDER_SNAIL_BE =
            BLOCK_ENTITIES.register("transponder_snail_be", () ->
                    BlockEntityType.Builder.of(TransponderSnailBlockEntity::new,
                            ModBlocks.TRANSPONDER_SNAIL.get()).build(null));

    public static final RegistryObject<BlockEntityType<TransponderSnailBlockEntity>> TRANSPONDER_SNAIL_TRANSMITTER_BE =
            BLOCK_ENTITIES.register("transponder_snail_transmitter_be", () ->
                    BlockEntityType.Builder.of(TransponderSnailBlockEntity::new,
                            ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<BlackTransponderSnailBlockEntity>> BLACK_TRANSPONDER_SNAIL_BE =
            BLOCK_ENTITIES.register("black_transponder_snail_be",
                    () -> BlockEntityType.Builder.of(
                            BlackTransponderSnailBlockEntity::new,
                            ModBlocks.BLACK_TRANSPONDER_SNAIL_BLOCK.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<WhiteTransponderSnailBlockEntity>> WHITE_TRANSPONDER_SNAIL =
            BLOCK_ENTITIES.register("white_transponder_snail", () ->
                    BlockEntityType.Builder.of(WhiteTransponderSnailBlockEntity::new,
                            ModBlocks.WHITE_TRANSPONDER_SNAIL.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
