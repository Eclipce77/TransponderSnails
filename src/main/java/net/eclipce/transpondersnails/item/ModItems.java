package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TransponderSnails.MOD_ID);

    public static final RegistryObject<Item> DEN_DEN_MUSHI = ITEMS.register("den_den_mushi",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BABY_TRANSPONDER_SNAIL = ITEMS.register("baby_transponder_snail",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BLACK_TRANSPONDER_SNAIL = ITEMS.register("black_transponder_snail",
            () -> new Item(new Item.Properties()));

    //Materials//
    //public static final RegistryObject<Item> RAWKAIROSEKI = ITEMS.register("raw_kairoseki",
            //() -> new Item(new Item.Properties()));
    //public static final RegistryObject<Item> KAIROSEKIINGOT = ITEMS.register("kairoseki_ingot",
            //() -> new Item(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}