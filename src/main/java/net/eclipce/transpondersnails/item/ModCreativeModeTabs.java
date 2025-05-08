package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TransponderSnails.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB = CREATIVE_MODE_TABS.register( "one_piece_mod_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.RAWKAIROSEKI.get()))
                    .title(Component.translatable("creativetab.one_piece_mod_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.RAWKAIROSEKI.get());
                        pOutput.accept(ModItems.KAIROSEKIINGOT.get());
                        pOutput.accept(ModBlocks.KAIROSEKI_ORE.get());
                        pOutput.accept(ModBlocks.DEEPSLATE_KAIROSEKI_ORE.get());
                        pOutput.accept(ModBlocks.KAIROSEKI_BLOCK.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB2 = CREATIVE_MODE_TABS.register( "one_piece_mod_tab2",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.GOMU_GOMU_NO_MI.get()))
                    .title(Component.translatable("creativetab.one_piece_mod_tab2"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.GOMU_GOMU_NO_MI.get());
                        pOutput.accept(ModItems.MERA_MERA_NO_MI.get());
                        pOutput.accept(ModItems.OPE_OPE_NO_MI.get());
                        pOutput.accept(ModItems.PIKA_PIKA_NO_MI.get());
                        pOutput.accept(ModItems.SUNA_SUNA_NO_MI.get());
                        pOutput.accept(ModItems.TORI_TORI_NO_MI_MODEL_PHOENIX.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB3 = CREATIVE_MODE_TABS.register( "one_piece_mod_tab3",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.one_piece_mod_tab3"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(Items.DIAMOND_SWORD);
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB4 = CREATIVE_MODE_TABS.register( "one_piece_mod_tab4",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.one_piece_mod_tab4"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(Items.ARMOR_STAND);
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB5 = CREATIVE_MODE_TABS.register( "one_piece_mod_tab5",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.one_piece_mod_tab5"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.DEN_DEN_MUSHI.get());
                        pOutput.accept(ModItems.SOGEKING_THEME_SONG.get());
                        pOutput.accept(ModItems.LUFFY_BAKA_SONG.get());
                        pOutput.accept(ModItems.BINKS_SAKE.get());
                        pOutput.accept(ModItems.FRANKYS_THEME.get());
                        pOutput.accept(ModItems.WE_ARE.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB6 = CREATIVE_MODE_TABS.register( "one_piece_mod_tab6",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.one_piece_mod_tab6"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(Items.SQUID_SPAWN_EGG);
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB7 = CREATIVE_MODE_TABS.register( "one_piece_mod_tab7",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.SMILE.get()))
                    .title(Component.translatable("creativetab.one_piece_mod_tab7"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.SMILE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }


}
