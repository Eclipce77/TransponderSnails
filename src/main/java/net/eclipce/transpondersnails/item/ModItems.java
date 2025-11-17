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

    public static final RegistryObject<Item> DEN_DEN_MUSHI = ITEMS.register("den_den_mushi",
            () -> new DenDenMushiItem(new Item.Properties().stacksTo(1)));

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

    public static final RegistryObject<Item> BABY_BLACK_TRANSPONDER_SNAIL = ITEMS.register("baby_black_transponder_snail",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PORTABLE_BLACK_TRANSPONDER_SNAIL = ITEMS.register("portable_black_transponder_snail",
            () -> new PortableBlackTransponderSnailItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

}
