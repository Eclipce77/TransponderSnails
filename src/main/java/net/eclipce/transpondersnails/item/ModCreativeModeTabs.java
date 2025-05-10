package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TransponderSnails.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ONE_PIECE_MOD_TAB = CREATIVE_MODE_TABS.register( "transpondersnails_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.DEN_DEN_MUSHI.get()))
                    .title(Component.translatable("creativetab.transpondersnails_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.DEN_DEN_MUSHI.get());
                        pOutput.accept(ModBlocks.TRANSPONDER_SNAIL.get());
                        pOutput.accept(ModItems.BABY_TRANSPONDER_SNAIL.get());
                        pOutput.accept(ModItems.BLACK_TRANSPONDER_SNAIL.get());
                        pOutput.accept(ModBlocks.HORNED_TRANSPONDER_SNAIL.get());
                        pOutput.accept(ModBlocks.VISUAL_TRANSPONDER_SNAIL.get());
                        pOutput.accept(ModBlocks.SURVEILLANCE_TRANSPONDER_SNAIL.get());
                        pOutput.accept(ModBlocks.TRANSMISSION_TRANSPONDER_SNAIL.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }


}
