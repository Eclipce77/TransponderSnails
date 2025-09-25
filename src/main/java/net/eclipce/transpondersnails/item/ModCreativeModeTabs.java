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

    public static final RegistryObject<CreativeModeTab> TRANSPONDERSNAILS_TAB = CREATIVE_MODE_TABS.register( "transpondersnails_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.TRANSPONDER_SNAIL.get()))
                    .title(Component.translatable("creativetab.transpondersnails"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.DEN_DEN_MUSHI_SPAWN_EGG.get());
                        pOutput.accept(ModItems.DEN_DEN_MUSHI.get());
                        pOutput.accept(ModBlocks.TRANSPONDER_SNAIL.get());
                        pOutput.accept(ModItems.ROTARY_DIAL.get());
                        pOutput.accept(ModItems.COPPER_WIRE.get());
                        pOutput.accept(ModItems.MICROPHONE_CAPSULE.get());
                        pOutput.accept(ModItems.TRANSMITTER.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }

}
