package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.voice.server.AmplifiedSnailManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the Amplified Transponder Snail's world-side updates (blockstates, item NBT) on the server thread.
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AmplifiedSnailServerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        AmplifiedSnailManager manager = AmplifiedSnailManager.get();
        if (manager != null && ServerLifecycleHooks.getCurrentServer() != null) {
            try {
                manager.tick(ServerLifecycleHooks.getCurrentServer());
            } catch (Exception e) {
                System.err.println("AmplifiedSnailServerEvents: Error ticking amplified snails: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AmplifiedSnailManager.shutdown();
    }
}
