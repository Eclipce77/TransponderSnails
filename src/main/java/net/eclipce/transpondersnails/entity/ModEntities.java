package net.eclipce.transpondersnails.entity;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TransponderSnails.MOD_ID);

    public static final RegistryObject<EntityType<DenDenMushiEntity>> DEN_DEN_MUSHI =
            ENTITY_TYPES.register("den_den_mushi", () -> EntityType.Builder.of(DenDenMushiEntity::new, MobCategory.CREATURE)
                    .sized(0.77f, 0.77f)
                    .clientTrackingRange(10)
                    .build("den_den_mushi"));

    // White Den Den Mushi (FIXED: uses WhiteDenDenMushiEntity type)
    public static final RegistryObject<EntityType<WhiteDenDenMushiEntity>> WHITE_DEN_DEN_MUSHI =
            ENTITY_TYPES.register("white_den_den_mushi", () -> EntityType.Builder.of(WhiteDenDenMushiEntity::new, MobCategory.CREATURE)
                    .sized(0.77f, 0.77f)
                    .clientTrackingRange(10)
                    .build("white_den_den_mushi"));

    // Baby Black Transponder Snail Entity
    public static final RegistryObject<EntityType<BabyBlackTransponderSnailEntity>> BABY_BLACK_TRANSPONDER_SNAIL =
            ENTITY_TYPES.register("baby_black_transponder_snail",
                    () -> EntityType.Builder.<BabyBlackTransponderSnailEntity>of(
                                    BabyBlackTransponderSnailEntity::new,
                                    MobCategory.CREATURE
                            )
                            .sized(0.16F, 0.12F) // Width and Height (hitbox size)
                            .clientTrackingRange(8) // How far away clients can see this entity
                            .updateInterval(3) // Update every 3 ticks
                            .build("baby_black_transponder_snail")
            );

    // Black Transponder Snail Entity
    public static final RegistryObject<EntityType<BlackTransponderSnailEntity>> BLACK_TRANSPONDER_SNAIL =
            ENTITY_TYPES.register("black_transponder_snail",
                    () -> EntityType.Builder.<BlackTransponderSnailEntity>of(
                                    BlackTransponderSnailEntity::new,
                                    MobCategory.CREATURE
                            )
                            .sized(0.75F, 0.35F) // Width and Height (hitbox size)
                            .clientTrackingRange(10) // How far away clients can see this entity
                            .updateInterval(3) // Update every 3 ticks
                            .build("black_transponder_snail")
            );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    @SubscribeEvent
    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DEN_DEN_MUSHI.get(), DenDenMushiEntity.createAttributes().build());
        event.put(ModEntities.WHITE_DEN_DEN_MUSHI.get(), WhiteDenDenMushiEntity.createAttributes().build());
        event.put(ModEntities.BABY_BLACK_TRANSPONDER_SNAIL.get(), BabyBlackTransponderSnailEntity.createAttributes().build());
        event.put(ModEntities.BLACK_TRANSPONDER_SNAIL.get(), BlackTransponderSnailEntity.createAttributes().build());
    }

    /**
     * Register spawn placements for all snail entities
     * This determines WHERE entities can spawn (heightmap type, spawn conditions)
     */
    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        System.out.println("TransponderSnails: Registering spawn placements");

        // Den Den Mushi - spawns on surface (land or shallow water)
        event.register(
                ModEntities.DEN_DEN_MUSHI.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DenDenMushiSpawnConditions::checkDenDenMushiSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        System.out.println("  ✓ Den Den Mushi spawn placement registered");

        // White Den Den Mushi - spawns on surface (same as regular)
        event.register(
                ModEntities.WHITE_DEN_DEN_MUSHI.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WhiteDenDenMushiSpawnConditions::checkWhiteDenDenMushiSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        System.out.println("  ✓ White Den Den Mushi spawn placement registered");

        // Black Transponder Snail - spawns underwater
        event.register(
                ModEntities.BLACK_TRANSPONDER_SNAIL.get(),
                SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.OCEAN_FLOOR,
                BlackTransponderSnailSpawnConditions::checkBlackTransponderSnailSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        System.out.println("  ✓ Black Transponder Snail spawn placement registered");

        // Baby Black Transponder Snail - spawns underwater
        event.register(
                ModEntities.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.OCEAN_FLOOR,
                BlackTransponderSnailSpawnConditions::checkBabyBlackTransponderSnailSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        System.out.println("  ✓ Baby Black Transponder Snail spawn placement registered");
    }
}