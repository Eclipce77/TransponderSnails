package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.custom.BabyBlackTransponderSnailEntity;
import net.eclipce.transpondersnails.entity.custom.BlackTransponderSnailEntity;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.eclipce.transpondersnails.entity.custom.WhiteDenDenMushiEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * MOD BUS EVENTS ONLY
 * These events are fired during mod initialization
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventBusEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DEN_DEN_MUSHI.get(), DenDenMushiEntity.createAttributes().build());
        event.put(ModEntities.WHITE_DEN_DEN_MUSHI.get(), WhiteDenDenMushiEntity.createAttributes().build());
        event.put(ModEntities.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                BabyBlackTransponderSnailEntity.createAttributes().build());
        event.put(ModEntities.BLACK_TRANSPONDER_SNAIL.get(),
                BlackTransponderSnailEntity.createAttributes().build());
    }
}