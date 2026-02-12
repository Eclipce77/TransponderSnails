package net.eclipce.transpondersnails.client;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.compat.CuriosCompat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side setup for Curios rendering (OPTIONAL)
 * This class safely handles Curios integration without crashing if Curios is absent
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CuriosClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Only register Curios renderer if Curios is loaded
        if (CuriosCompat.isCuriosLoaded()) {
            event.enqueueWork(() -> {
                try {
                    // Use helper class to avoid loading Curios classes when not present
                    CuriosRendererHelper.registerRenderers();
                    System.out.println("TransponderSnails: Successfully registered Curios renderers");
                } catch (Exception e) {
                    System.err.println("TransponderSnails: Failed to register Curios renderers: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            System.out.println("TransponderSnails: Skipping Curios renderer registration (Curios not loaded)");
        }
    }

    /**
     * Helper class that accesses Curios API directly.
     * This is in a separate class to prevent class loading errors when Curios is absent.
     * The Curios imports happen at method level, so this class is only loaded when needed.
     */
    private static class CuriosRendererHelper {

        public static void registerRenderers() {
            // These classes are only loaded when this method is called
            // Since we check if Curios is loaded first, this is safe

            // Register the Curios renderer for Portable Black Transponder Snail
            top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                    net.eclipce.transpondersnails.item.ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                    () -> new net.eclipce.transpondersnails.client.renderer.PortableBlackTransponderSnailCurioRenderer()
            );
        }
    }
}