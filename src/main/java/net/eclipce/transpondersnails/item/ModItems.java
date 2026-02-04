package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.entity.ModEntities;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TransponderSnails.MOD_ID);

    public static final RegistryObject<Item> DEN_DEN_MUSHI_SPAWN_EGG = ITEMS.register("den_den_mushi_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.DEN_DEN_MUSHI, 0xffbf66, 0xff526b,
                    new Item.Properties()));

    public static final RegistryObject<Item> BABY_DEN_DEN_MUSHI_SPAWN_EGG = ITEMS.register("baby_den_den_mushi_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BABY_BLACK_TRANSPONDER_SNAIL, 0xffbf66, 0xff526b,
                    new Item.Properties()));

    public static final RegistryObject<Item> BLACK_TRANSPONDER_SNAIL_SPAWN_EGG = ITEMS.register("black_transponder_snail_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BLACK_TRANSPONDER_SNAIL, 0x38343b, 0x48424f,
                    new Item.Properties()));

    public static final RegistryObject<Item> BABY_BLACK_TRANSPONDER_SNAIL_SPAWN_EGG = ITEMS.register("baby_black_transponder_snail_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BABY_BLACK_TRANSPONDER_SNAIL, 0x544d59, 0x635b6c,
                    new Item.Properties()));

    public static final RegistryObject<Item> DEN_DEN_MUSHI = ITEMS.register("den_den_mushi",
            () -> new DenDenMushiItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BABY_DEN_DEN_MUSHI = ITEMS.register("baby_den_den_mushi",
            () -> new BabyDenDenMushiItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ROTARY_DIAL = ITEMS.register("rotary_dial",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> COPPER_NUGGET = ITEMS.register("copper_nugget",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> RUBBERIZED_CLAY = ITEMS.register("rubberized_clay",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> WIRE = ITEMS.register("wire",
            () -> new BlockItem(ModBlocks.WIRE.get(), new Item.Properties()));

    public static final RegistryObject<Item> TRANSMITTER = ITEMS.register("transmitter",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MICROPHONE_CAPSULE = ITEMS.register("microphone_capsule",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> TRANSPONDER_SNAIL = ITEMS.register("transponder_snail",
            () -> new TransponderSnailItem(ModBlocks.TRANSPONDER_SNAIL.get(),
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TRANSPONDER_SNAIL_TRANSMITTER = ITEMS.register("transponder_snail_transmitter",
            () -> new TransponderSnailItem(ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get(),
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BLACK_TRANSPONDER_SNAIL = ITEMS.register("black_transponder_snail",
            () -> new BlackTransponderSnailItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BLACK_TRANSPONDER_SNAIL_BLOCK_ITEM = ITEMS.register("black_transponder_snail_block",
            () -> new BlackTransponderSnailBlockItem(
                    ModBlocks.BLACK_TRANSPONDER_SNAIL_BLOCK.get(),
                    new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BABY_BLACK_TRANSPONDER_SNAIL = ITEMS.register("baby_black_transponder_snail",
            () -> new BabyBlackTransponderSnailItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PORTABLE_BLACK_TRANSPONDER_SNAIL = ITEMS.register("portable_black_transponder_snail",
            () -> new PortableBlackTransponderSnailItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> WHITE_DEN_DEN_MUSHI = ITEMS.register("white_den_den_mushi",
            () -> new WhiteDenDenMushiItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> WHITE_TRANSPONDER_SNAIL = ITEMS.register("white_transponder_snail",
            () -> new BlockItem(ModBlocks.WHITE_TRANSPONDER_SNAIL.get(), new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

}
