package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Auto-registered (annotation scanning) - no change to the main mod class needed. */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VisualNetworkSetup {

    private VisualNetworkSetup() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(VisualNetwork::register);
    }
}
