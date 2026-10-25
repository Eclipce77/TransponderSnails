package net.eclipce.transpondersnails.client;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The glow model of the Horned Transponder Snail's eyes (assets/.../models/block/horned_transponder_snail_glow_eyes.json): the two eye cubes,
 * a little bigger than the real ones, which the block renderer draws bright red over them while the snail is switched on.
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class HornedGlowModels {

    private HornedGlowModels() {}

    private static final ResourceLocation EYES = new ResourceLocation(TransponderSnails.MOD_ID, "block/horned_transponder_snail_glow_eyes");

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(EYES);
    }

    /** The baked eyes, or null if the model file is missing (then the snail simply does not glow). */
    @Nullable
    public static BakedModel eyes() {
        ModelManager manager = Minecraft.getInstance().getModelManager();
        BakedModel model = manager.getModel(EYES);
        return model == manager.getMissingModel() ? null : model;
    }
}
