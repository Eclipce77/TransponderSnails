package net.eclipce.transpondersnails.entity;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiSpawnConditions;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntitySpawnPlacements {

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        System.out.println("=== REGISTERING DEN DEN MUSHI SPAWN PLACEMENT ===");

        event.register(
                ModEntities.DEN_DEN_MUSHI.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DenDenMushiSpawnConditions::checkDenDenMushiSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );

        System.out.println("=== DEN DEN MUSHI SPAWN PLACEMENT REGISTERED ===");
    }

}