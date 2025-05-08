package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.sound.ModSounds;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TransponderSnails.MOD_ID);

    //Materials//
    public static final RegistryObject<Item> RAWKAIROSEKI = ITEMS.register("raw_kairoseki",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KAIROSEKIINGOT = ITEMS.register("kairoseki_ingot",
            () -> new Item(new Item.Properties()));

    //Music Discs//
    public static final RegistryObject<Item> SOGEKING_THEME_SONG = ITEMS.register("sogeking_theme_song",
            () -> new RecordItem(6, ModSounds.SOGEKING_THEME_SONG, new Item.Properties().stacksTo(1), 1420));
    public static final RegistryObject<Item> LUFFY_BAKA_SONG = ITEMS.register("luffy_baka_song",
            () -> new RecordItem(6, ModSounds.LUFFY_BAKA_SONG, new Item.Properties().stacksTo(1), 560));
    public static final RegistryObject<Item> BINKS_SAKE = ITEMS.register("binks_sake",
            () -> new RecordItem(6, ModSounds.BINKS_SAKE, new Item.Properties().stacksTo(1), 3782));
    public static final RegistryObject<Item> FRANKYS_THEME = ITEMS.register("frankys_theme",
            () -> new RecordItem(6, ModSounds.FRANKYS_THEME, new Item.Properties().stacksTo(1), 3302));
    public static final RegistryObject<Item> WE_ARE = ITEMS.register("we_are",
            () -> new RecordItem(6, ModSounds.WE_ARE, new Item.Properties().stacksTo(1), 2202));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}