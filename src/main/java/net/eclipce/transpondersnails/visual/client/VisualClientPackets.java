package net.eclipce.transpondersnails.visual.client;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

/** Client-only entry points for packets (kept separate so the dedicated server never loads client classes). */
@OnlyIn(Dist.CLIENT)
public final class VisualClientPackets {

    private VisualClientPackets() {}

    public static void handleControl(UUID callId, BlockPos screenPos, BlockPos cameraPos, boolean start) {
        VisualFeedManager.onControl(callId, screenPos, cameraPos, start);
    }
}
