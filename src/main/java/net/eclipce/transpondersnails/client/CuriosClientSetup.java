package net.eclipce.transpondersnails.client;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.client.renderer.PortableBlackTransponderSnailCurioRenderer;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * Client-side setup for Curios rendering
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CuriosClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register the Curios renderer for Portable Black Transponder Snail
            CuriosRendererRegistry.register(
                    ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                    PortableBlackTransponderSnailCurioRenderer::new
            );

            System.out.println("TransponderSnails: Registered Curios renderer for Portable Black Transponder Snail");
        });
    }
}