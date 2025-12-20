package net.eclipce.transpondersnails.entity.client;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ModModelLayers {
    public static final ModelLayerLocation DEN_DEN_MUSHI_LAYER = new ModelLayerLocation(
            new ResourceLocation(TransponderSnails.MOD_ID, "den_den_mushi_layer"), "main");


    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.DEN_DEN_MUSHI_LAYER, DenDenMushiModel::createBodyLayer);
    }
}
