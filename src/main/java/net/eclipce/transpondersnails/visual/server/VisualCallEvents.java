package net.eclipce.transpondersnails.visual.server;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side hooks for the video call system (auto-registered, no change to the main mod class). */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VisualCallEvents {

    private VisualCallEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        VisualCallManager manager = VisualCallManager.peek();
        if (manager != null) manager.tick();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VisualCallManager.discard();
        VisualSnailRegistry.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VisualCallManager manager = VisualCallManager.peek();
        if (manager != null) manager.onPlayerLeft(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        VisualCallManager manager = VisualCallManager.peek();
        if (manager != null) manager.onPlayerLeft(event.getEntity().getUUID());
    }
}
