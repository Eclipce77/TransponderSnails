package net.eclipce.transpondersnails.entity;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TransponderSnails.MOD_ID);

    public static final RegistryObject<EntityType<DenDenMushiEntity>> DEN_DEN_MUSHI =
            ENTITY_TYPES.register("den_den_mushi", () -> EntityType.Builder.of(DenDenMushiEntity::new, MobCategory.CREATURE)
                    .sized(0.77f, 0.77f).build("den_den_mushi"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

}
