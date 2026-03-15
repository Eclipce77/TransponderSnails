package net.eclipce.transpondersnails.entity;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiSpawnConditions;
import net.eclipce.transpondersnails.entity.custom.HornedDenDenMushiSpawnConditions;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntitySpawnPlacements {

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        System.out.println("=== REGISTERING SPAWN PLACEMENTS ===");

        event.register(
                ModEntities.DEN_DEN_MUSHI.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DenDenMushiSpawnConditions::checkDenDenMushiSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        System.out.println("=== DEN DEN MUSHI SPAWN PLACEMENT REGISTERED ===");

        event.register(
                ModEntities.HORNED_DEN_DEN_MUSHI.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HornedDenDenMushiSpawnConditions::checkHornedDenDenMushiSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        System.out.println("=== HORNED DEN DEN MUSHI SPAWN PLACEMENT REGISTERED ===");
    }

}