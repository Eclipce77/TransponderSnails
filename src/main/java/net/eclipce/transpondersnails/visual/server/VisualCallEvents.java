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
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        net.eclipce.transpondersnails.voice.server.HornedDDMJammerManager.getInstance().tick(server);   // jam levels first: everything below reads them
        VisualJamSync.tick(server);
        VisualRelayManager.tick(server);
        VisualInterceptionManager.tick(server);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VisualInterceptionManager.clear();
        VisualJamSync.clear();
        net.eclipce.transpondersnails.voice.server.HornedDDMJammerManager.getInstance().clear();
        VisualCallManager.discard();
        VisualSnailRegistry.clear();
        VisualRelayManager.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VisualCallManager manager = VisualCallManager.peek();
        if (manager != null) manager.onPlayerLeft(event.getEntity().getUUID());
        VisualRelayManager.onPlayerLeft(event.getEntity().getUUID());
        VisualInterceptionManager.onPlayerLeft(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        VisualCallManager manager = VisualCallManager.peek();
        if (manager != null) manager.onPlayerLeft(event.getEntity().getUUID());
        VisualRelayManager.onPlayerLeft(event.getEntity().getUUID());
        VisualInterceptionManager.onPlayerLeft(event.getEntity().getUUID());
    }
}
