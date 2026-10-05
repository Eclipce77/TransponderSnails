package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * TV static ("snow"): a small texture of random gray pixels, made once when it is first needed, with horizontal streaks like a badly tuned
 * television. It is not animated by changing the texture: every few hundredths of a second a different random window of it is shown,
 * which looks like fresh noise and costs nothing.
 */
@OnlyIn(Dist.CLIENT)
final class VisualStaticTexture {

    private static final int SIZE = 128;
    /** How much of the texture one picture of static shows (0.5 = a quarter of it); the rest is where the random window can move. */
    static final float WINDOW = 0.5F;
    /** How often the static changes (ms): about 22 times a second, like an old set. */
    private static final long SLOT_MS = 45L;

    private static RenderType type;

    private VisualStaticTexture() {}

    /** Colored, textured quads with normal alpha blending: the static lies over the picture and lets it show through. */
    static RenderType renderType() {
        if (type == null) {
            type = create();
        }
        return type;
    }

    private static RenderType create() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
        RandomSource random = RandomSource.create(1337L);
        for (int y = 0; y < SIZE; y++) {
            float streak = 0.55F + 0.45F * random.nextFloat(); // each line is a little brighter or darker than its neighbors
            for (int x = 0; x < SIZE; x++) {
                int v = Math.min(255, Math.round(255.0F * streak * random.nextFloat()));
                image.setPixelRGBA(x, y, 0xFF000000 | (v << 16) | (v << 8) | v); // opaque gray (ABGR, all three the same)
            }
        }
        DynamicTexture texture = new DynamicTexture(image); // uploads the pixels
        texture.setFilter(false, false);                    // crisp, chunky pixels
        ResourceLocation location = new ResourceLocation(TransponderSnails.MOD_ID, "projector_static");
        Minecraft.getInstance().getTextureManager().register(location, texture);

        return RenderType.create(
                "transpondersnails_projector_static",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(location, false, false))
                        .setTransparencyState(VisualFeed.PROJECTOR_TRANSPARENCY)
                        .createCompositeState(false));
    }

    /** The random window of the static to show at this time: {u offset, v offset}; both in 0..1-WINDOW, so no wrapping is ever needed. */
    static float[] window(long nowMs) {
        RandomSource random = RandomSource.create((nowMs / SLOT_MS) * 7919L + 17L);
        float range = 1.0F - WINDOW;
        return new float[]{random.nextFloat() * range, random.nextFloat() * range};
    }
}
