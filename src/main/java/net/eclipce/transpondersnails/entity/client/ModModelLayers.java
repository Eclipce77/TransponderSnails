package net.eclipce.transpondersnails.entity.client;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModModelLayers {
    public static final ModelLayerLocation DEN_DEN_MUSHI_LAYER = new ModelLayerLocation(
            new ResourceLocation(TransponderSnails.MOD_ID, "den_den_mushi_layer"), "main");

    public static final ModelLayerLocation WHITE_DEN_DEN_MUSHI_LAYER = new ModelLayerLocation(
            new ResourceLocation(TransponderSnails.MOD_ID, "white_den_den_mushi_layer"), "main");

    public static final ModelLayerLocation HORNED_DEN_DEN_MUSHI_LAYER = new ModelLayerLocation(
            new ResourceLocation(TransponderSnails.MOD_ID, "horned_den_den_mushi_layer"), "main");

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.DEN_DEN_MUSHI_LAYER, DenDenMushiModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.WHITE_DEN_DEN_MUSHI_LAYER, WhiteDenDenMushiModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.HORNED_DEN_DEN_MUSHI_LAYER, HornedDenDenMushiModel::createBodyLayer);
    }
}
