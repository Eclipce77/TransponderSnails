package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.eclipce.transpondersnails.visual.TileCodec;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.visual.ResolutionPlanner;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualSnailRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** The offscreen view from one Visual Snail's eyes. Keyed by the CAMERA snail's position. */
@OnlyIn(Dist.CLIENT)
final class VisualFeed {

    /** Normal alpha blending: the world behind the screen shows through according to the picture's alpha. */
    static final RenderStateShard.TransparencyStateShard PROJECTOR_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard(
                    "transpondersnails_projector_transparency",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    final BlockPos cameraPos;

    // Quality of this feed. Set from the role of the filming snail (a camera snail is sharper and sees further than a
    // standard video call) by configure(), before the first capture.
    int resolution = VisualCallConstants.FEED_RESOLUTION;
    int fps = VisualCallConstants.FEED_FPS;
    int viewChunks = VisualCallConstants.FEED_VIEW_CHUNKS;
    int viewVertical = VisualCallConstants.FEED_VIEW_SECTIONS_VERTICAL;
    /** Role of the filming snail (decides the maximum quality). */
    VisualSnailRole role = VisualSnailRole.DUPLEX;

    // ---- adaptive quality bookkeeping (client only) ----
    /** Resolution tier the screens showing this feed would like right now (0 = no screen has reported yet). */
    int lodResolution = 0;
    /** Distance (blocks) from the viewer to the nearest screen showing this feed. */
    double lodDistance = Double.MAX_VALUE;
    private long lodFrame = -1L;
    private int lodResolutionAcc = 0;
    private double lodDistanceAcc = Double.MAX_VALUE;
    /** Decides when this feed may change resolution (hysteresis, see ResolutionPlanner). */
    final ResolutionPlanner planner = new ResolutionPlanner();
    /** What the section list was built for: a change of any of these forces a rebuild. */
    int lastRadius = -1;
    long lastPlayerSection = Long.MIN_VALUE;

    RenderTarget target;
    private ResourceLocation textureId;
    private RenderType renderType;

    long lastCaptureNs = 0L;
    volatile long lastTouchedMs = 0L;
    int okFrames = 0;
    boolean failed = false;

    // ---- relayed video: the camera is too far away to be drawn here, so its picture arrives over the network ----
    /** True while this feed shows relayed frames instead of capturing (the camera's surroundings are not loaded on this client). */
    boolean remote = false;
    /** When this client last asked the server to relay the picture. */
    long lastRelayRequestMs = 0L;

    /** Sections rendered for this camera (vanilla renderer path only). Rebuilt periodically. */
    List<LevelRenderer.RenderChunkInfo> sections = new ArrayList<>();
    int capturesSinceSectionRefresh = VisualCallConstants.FEED_SECTION_REFRESH_CAPTURES;
    int lastViewDistance = -1;
    boolean sectionsLogged = false;
    boolean emptySectionsWarned = false;

    VisualFeed(BlockPos cameraPos) {
        this.cameraPos = cameraPos.immutable();
    }

    boolean isAllocated() {
        return target != null;
    }

    /**
     * Applies the quality for the NEXT capture. If the resolution changes, the render target is re-created: the section list
     * and the "has a picture" state are kept, and the new target is filled by the capture that is about to run, so the
     * picture does not blink.
     */
    void applyQuality(VisualSnailRole role, int newResolution, int newFps) {
        this.role = role;
        if (target != null && newResolution != resolution) {
            releaseGl();
        }
        resolution = newResolution;
        fps = Math.max(1, newFps);
        viewChunks = role.feedViewChunks();
        viewVertical = role.feedViewSectionsVertical();
    }

    /**
     * A screen showing this feed was drawn: the resolution tier it would like and its distance. Several screens may show the
     * same feed; the highest wish and the shortest distance of a frame win. The result of a frame becomes visible in the next.
     */
    void reportView(long frameId, int wantedResolution, double distance) {
        if (lodFrame != frameId) {
            if (lodFrame >= 0L) {
                lodResolution = lodResolutionAcc;
                lodDistance = lodDistanceAcc;
            }
            lodFrame = frameId;
            lodResolutionAcc = 0;
            lodDistanceAcc = Double.MAX_VALUE;
        }
        lodResolutionAcc = Math.max(lodResolutionAcc, wantedResolution);
        lodDistanceAcc = Math.min(lodDistanceAcc, distance);
    }

    void allocate(int uniqueId) {
        if (target != null) return;
        int res = resolution;
        target = new TextureTarget(res, res, true, Minecraft.ON_OSX);
        target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);

        textureId = new ResourceLocation(TransponderSnails.MOD_ID, "visual_feed/" + uniqueId);
        Minecraft.getInstance().getTextureManager().register(textureId, new VisualFeedTexture(target));

        renderType = RenderType.create(
                "transpondersnails_visual_feed_" + uniqueId,
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(textureId, true, false))
                        .setTransparencyState(PROJECTOR_TRANSPARENCY)
                        .createCompositeState(false));
    }

    boolean hasFrame() {
        return target != null && okFrames > 0 && !failed;
    }

    /**
     * Shows what arrived over the network: the changed tiles (or, the first time and after a size change, the whole picture) go into the
     * same render target a capture would draw into, so the screen cannot tell the difference. Only the tiles that changed are uploaded to
     * the GPU, as a few strips (neighbouring tiles of a row together).
     */
    void acceptRemote(int uniqueId, TileCodec.Update update) {
        int n = update.size();
        if (!TileCodec.validSize(n)) return;
        if (target != null && resolution != n) releaseGl();       // a different size: a new target (the decoder always sends the whole picture next)
        boolean created = target == null;
        resolution = n;
        allocate(uniqueId);
        if (created && !update.full()) return;                    // a new target needs the whole picture first

        RenderSystem.bindTexture(target.getColorTextureId());
        if (update.full()) {
            uploadStrip(update.pixels(), 0, n, n, 0, 0);
        } else {
            int tilesPerRow = n / TileCodec.TILE;
            int[] tiles = update.tiles();
            int i = 0;
            while (i < tiles.length) {
                int row = tiles[i] / tilesPerRow;
                int j = i + 1;
                while (j < tiles.length && tiles[j] == tiles[j - 1] + 1 && tiles[j] / tilesPerRow == row) j++;   // a run of neighbours in one row
                uploadTiles(update.pixels(), i, j - i, (tiles[i] % tilesPerRow) * TileCodec.TILE, row * TileCodec.TILE);
                i = j;
            }
        }
        okFrames++;
    }

    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /** Uploads {@code count} tiles that sit next to each other in a row (tile {@code first} of the update's pixels onwards) at (x, y). */
    private static void uploadTiles(int[] pixels, int first, int count, int x, int y) {
        int w = count * TileCodec.TILE;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, TileCodec.TILE, false);
        for (int t = 0; t < count; t++) {
            for (int py = 0; py < TileCodec.TILE; py++) {
                int src = ((first + t) * TileCodec.TILE + py) * TileCodec.TILE;
                for (int px = 0; px < TileCodec.TILE; px++) image.setPixelRGBA(t * TileCodec.TILE + px, py, toAbgr(pixels[src + px]));
            }
        }
        // blur false = nearest, clamp true = clamp to edge: what a render target's texture has; the image closes itself
        image.upload(0, x, y, 0, 0, w, TileCodec.TILE, false, true, false, true);
    }

    /** Uploads a whole picture of {@code w} x {@code h} pixels (row after row in {@code pixels}) at (x, y). */
    private static void uploadStrip(int[] pixels, int offset, int w, int h, int x, int y) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) image.setPixelRGBA(px, py, toAbgr(pixels[offset + py * w + px]));
        }
        image.upload(0, x, y, 0, 0, w, h, false, true, false, true);
    }

    RenderType renderType() {
        return renderType;
    }

    void release() {
        sections = new ArrayList<>();
        lastRadius = -1;
        lastPlayerSection = Long.MIN_VALUE;
        releaseGl();
    }

    /** Frees only the GPU side (render target, texture, render type). */
    private void releaseGl() {
        if (textureId != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            textureId = null;
        }
        renderType = null;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
