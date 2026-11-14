package net.eclipce.transpondersnails.visual.client;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client hooks for the video call system (auto-registered, no change to ModEventBusClientEvents needed). */
public final class VisualClientEvents {

    private VisualClientEvents() {}

    @Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                VisualFeedManager.onRenderTickEnd(event.renderTickTime);
            }
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            VisualScreenRenderer.onRenderLevelStage(event);
        }

        /**
         * Mine Mine no Mi compatibility (only when that mod is installed): it tweaks the camera from the LOCAL PLAYER's state (dizzy roll, camera lock,
         * morph offsets). Those handlers also fire for our nested feed render, where the camera is the snail's marker.
         * Running last, this puts the marker camera's own angles back so the feed is always a plain, level camera.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onFeedCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            if (!VisualFeedManager.isCapturing() || !MineMineNoMiCompat.present()) return;
            event.setYaw(event.getCamera().getYRot());
            event.setPitch(event.getCamera().getXRot());
            event.setRoll(0.0F);
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                VisualFeedManager.clearAll();
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            VisualFeedManager.clearAll();
        }
    }
}
