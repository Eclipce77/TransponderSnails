package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.HornedDenDenMushiEntity;
import net.eclipce.transpondersnails.voice.server.HornedDDMJammerManager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Horned Den Den Mushi creature jams passively wherever it walks: it is a jam source from the moment it is in a world until it leaves it. */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HornedJamEvents {

    private HornedJamEvents() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof HornedDenDenMushiEntity horned) {
            HornedDDMJammerManager.getInstance().registerEntity(horned);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof HornedDenDenMushiEntity horned) {
            HornedDDMJammerManager.getInstance().unregisterEntity(horned.getUUID());
        }
    }
}
