package net.eclipce.transpondersnails.entity;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.BabyBlackTransponderSnailEntity;
import net.eclipce.transpondersnails.entity.custom.BlackTransponderSnailEntity;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    // Baby Black Transponder Snail Entity
    public static final RegistryObject<EntityType<BabyBlackTransponderSnailEntity>> BABY_BLACK_TRANSPONDER_SNAIL =
            ENTITY_TYPES.register("baby_black_transponder_snail",
                    () -> EntityType.Builder.<BabyBlackTransponderSnailEntity>of(
                                    BabyBlackTransponderSnailEntity::new,
                                    MobCategory.CREATURE
                            )
                            .sized(0.16F, 0.12F) // Width and Height (hitbox size)
                            .clientTrackingRange(8) // How far away clients can see this entity
                            .updateInterval(3) // Update every 20 ticks (1 second)
                            .build("baby_black_transponder_snail")
            );

    // Baby Black Transponder Snail Entity
    public static final RegistryObject<EntityType<BlackTransponderSnailEntity>> BLACK_TRANSPONDER_SNAIL =
            ENTITY_TYPES.register("black_transponder_snail",
                    () -> EntityType.Builder.<BlackTransponderSnailEntity>of(
                                    BlackTransponderSnailEntity::new,
                                    MobCategory.CREATURE
                            )
                            .sized(0.75F, 0.35F) // Width and Height (hitbox size)
                            .clientTrackingRange(10) // How far away clients can see this entity
                            .updateInterval(3) // Update every 20 ticks (1 second)
                            .build("black_transponder_snail")
            );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    @SubscribeEvent
    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DEN_DEN_MUSHI.get(), DenDenMushiEntity.createAttributes().build());
    }

}
