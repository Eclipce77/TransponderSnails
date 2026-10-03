package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.visual.ScreenLayout;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.mixin.LevelRendererAccessor;
import net.eclipce.transpondersnails.visual.mixin.ViewAreaAccessor;
import net.eclipce.transpondersnails.visual.network.VisualFeedStatusPacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client side of the video call: renders the world from a remote Visual Snail's eyes into an offscreen render target.
 *
 * The capture sequence (temporary camera entity, render target swap, panoramic 1:1 projection, restoring all touched
 * renderer state afterwards) follows the approach of SecurityCraft's FrameFeedHandler
 * (https://github.com/Geforce132/SecurityCraft, MIT License, Copyright (c) 2025 The SecurityCraft development team).
 * See THIRD_PARTY_NOTICE.txt. Unlike SecurityCraft, no distant chunks are loaded: the two snails are at most
 * {@link VisualCallConstants#CALL_RANGE} blocks apart, so the chunks around the remote snail are already loaded on any
 * client standing near its own snail.
 *
 * Must only be used on the render thread.
 */
@OnlyIn(Dist.CLIENT)
public final class VisualFeedManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Screen snail position -> what it shows. */
    private static final Map<BlockPos, ScreenLink> LINKS = new HashMap<>();
    /** Camera snail position -> its render target. */
    private static final Map<BlockPos, VisualFeed> FEEDS = new HashMap<>();

    private static boolean capturing = false;
    private static int feedCounter = 0;
    private static long lastPruneMs = 0L;
    private static Boolean sodiumLike = null;

    private VisualFeedManager() {}

    /** The screen of one snail shows the view of another. */
    public static final class ScreenLink {
        private final UUID callId;
        private final BlockPos screenPos;
        private final BlockPos cameraPos;
        private boolean ackSent = false;
        private boolean failedSent = false;
        // renderer diagnostics (package-private on purpose: VisualScreenRenderer logs these once per call)
        boolean drawLogged = false;
        boolean noSignalLogged = false;

        private ScreenLink(UUID callId, BlockPos screenPos, BlockPos cameraPos) {
            this.callId = callId;
            this.screenPos = screenPos.immutable();
            this.cameraPos = cameraPos.immutable();
        }

        public BlockPos cameraPos() {
            return cameraPos;
        }

        public BlockPos screenPos() {
            return screenPos;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Public API used by the renderer / events / packets
    // ------------------------------------------------------------------------------------------------

    /** True while a feed is being rendered. Screens must not draw during a capture (no screen-in-screen), and the
     *  LevelRendererMixin skips its own section setup. */
    public static boolean isCapturing() {
        return capturing;
    }

    /** Embeddium / Rubidium / Sodium replace the vanilla chunk visibility code, so the vanilla-only shortcuts are off. */
    public static boolean isSodiumLike() {
        if (sodiumLike == null) {
            ModList mods = ModList.get();
            sodiumLike = mods.isLoaded("embeddium") || mods.isLoaded("rubidium") || mods.isLoaded("sodium");
        }
        return sodiumLike;
    }

    @Nullable
    public static ScreenLink getLink(BlockPos screenPos) {
        return LINKS.get(screenPos);
    }

    /** All screens that currently belong to a running call (render thread only; do not modify). */
    static java.util.Collection<ScreenLink> activeLinks() {
        return java.util.Collections.unmodifiableCollection(LINKS.values());
    }

    /** Called by the block entity renderer each time it wants to draw: marks the feed as needed and returns it. */
    @Nullable
    static VisualFeed touchFeed(ScreenLink link) {
        VisualFeed feed = FEEDS.get(link.cameraPos);
        if (feed != null) {
            feed.lastTouchedMs = System.currentTimeMillis();
        }
        return feed;
    }

    static void onControl(UUID callId, BlockPos screenPos, BlockPos cameraPos, boolean start) {
        if (start) {
            LINKS.put(screenPos.immutable(), new ScreenLink(callId, screenPos, cameraPos));

            VisualFeed existing = FEEDS.get(cameraPos);
            if (existing != null && existing.failed) {
                existing.release();
                FEEDS.remove(cameraPos);
            }
            VisualFeed feed = FEEDS.computeIfAbsent(cameraPos.immutable(), VisualFeed::new);
            feed.lastTouchedMs = System.currentTimeMillis();
        } else {
            ScreenLink link = LINKS.get(screenPos);
            if (link != null && link.callId.equals(callId)) {
                LINKS.remove(screenPos);
                releaseUnlinkedFeeds(false);
            }
        }
    }

    public static void clearAll() {
        LINKS.clear();
        for (VisualFeed feed : FEEDS.values()) {
            try {
                feed.release();
            } catch (Exception e) {
                LOGGER.warn("VisualFeedManager: error releasing feed", e);
            }
        }
        FEEDS.clear();
        capturing = false;
    }

    // ------------------------------------------------------------------------------------------------
    // Per-frame scheduling
    // ------------------------------------------------------------------------------------------------

    /** Called at the end of every rendered frame (TickEvent.RenderTickEvent, phase END). At most one feed per frame. */
    public static void onRenderTickEnd(float partialTick) {
        if (capturing || (LINKS.isEmpty() && FEEDS.isEmpty())) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastPruneMs > 1000L) {
            lastPruneMs = nowMs;
            releaseUnlinkedFeeds(true);
        }

        long nowNs = System.nanoTime();
        long interval = 1_000_000_000L / VisualCallConstants.FEED_FPS;

        VisualFeed due = null;
        for (VisualFeed feed : FEEDS.values()) {
            if (feed.failed) continue;
            if (!isWanted(feed, nowMs)) continue;
            if (nowNs - feed.lastCaptureNs < interval) continue;
            if (due == null || feed.lastCaptureNs < due.lastCaptureNs) {
                due = feed;
            }
        }
        if (due == null) return;

        due.lastCaptureNs = nowNs;
        capture(mc, level, player, due, partialTick);
    }

    private static boolean isWanted(VisualFeed feed, long nowMs) {
        if (nowMs - feed.lastTouchedMs < VisualCallConstants.FEED_WANTED_WINDOW_MS) return true;
        // A screen that has not reported "video ready" yet keeps rendering even when nobody is looking at it,
        // otherwise the call could never finish connecting.
        for (ScreenLink link : LINKS.values()) {
            if (link.cameraPos.equals(feed.cameraPos) && !link.ackSent && !link.failedSent) return true;
        }
        return false;
    }

    private static void releaseUnlinkedFeeds(boolean onlyIdle) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, VisualFeed>> it = FEEDS.entrySet().iterator();
        while (it.hasNext()) {
            VisualFeed feed = it.next().getValue();
            boolean linked = false;
            for (ScreenLink link : LINKS.values()) {
                if (link.cameraPos.equals(feed.cameraPos)) {
                    linked = true;
                    break;
                }
            }
            if (linked) continue;
            if (onlyIdle && now - feed.lastTouchedMs < VisualCallConstants.FEED_IDLE_RELEASE_MS) continue;
            feed.release();
            it.remove();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Capture
    // ------------------------------------------------------------------------------------------------

    private static void capture(Minecraft mc, ClientLevel level, LocalPlayer player, VisualFeed feed, float partialTick) {
        BlockPos camPos = feed.cameraPos;

        if (!level.isLoaded(camPos)) {
            fail(feed, "remote snail is outside the loaded area");
            return;
        }
        BlockState camState = level.getBlockState(camPos);
        if (!(camState.getBlock() instanceof TransponderSnailBlock) || !camState.hasProperty(TransponderSnailBlock.FACING)) {
            fail(feed, "remote snail block not found");
            return;
        }

        Direction facing = camState.getValue(TransponderSnailBlock.FACING);
        double[] eye = ScreenLayout.eyeOffset(facing.getStepX(), facing.getStepZ());
        Vec3 eyePos = new Vec3(camPos.getX() + eye[0], camPos.getY() + eye[1], camPos.getZ() + eye[2]);

        try {
            feed.allocate(++feedCounter);
        } catch (Throwable t) {
            LOGGER.error("VisualFeedManager: could not allocate render target", t);
            fail(feed, "could not allocate render target");
            return;
        }

        GameRenderer gameRenderer = mc.gameRenderer;
        LevelRenderer levelRenderer = mc.levelRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Window window = mc.getWindow();
        boolean sodium = isSodiumLike();

        // ---- remember everything the capture touches ----
        Entity oldCameraEntity = mc.cameraEntity;
        RenderTarget oldMainTarget = mc.getMainRenderTarget();
        RenderTarget oldTranslucent = levelRenderer.translucentTarget;
        RenderTarget oldItemEntity = levelRenderer.itemEntityTarget;
        RenderTarget oldWeather = levelRenderer.weatherTarget;
        PostChain oldTransparencyChain = levelRenderer.transparencyChain;
        ObjectArrayList<LevelRenderer.RenderChunkInfo> oldSections = sodium ? null : levelRenderer.renderChunksInFrustum.clone();
        int oldWidth = window.getWidth();
        int oldHeight = window.getHeight();
        CameraType oldCameraType = mc.options.getCameraType();
        float oldEyeHeight = camera.eyeHeight;
        float oldEyeHeightOld = camera.eyeHeightOld;
        // GameRenderer#pick runs inside renderLevel and would leave the crosshair target pointing at whatever the
        // remote camera sees, which the next client tick would then interact with.
        HitResult oldHitResult = mc.hitResult;
        Entity oldCrosshairEntity = mc.crosshairPickEntity;

        Marker eyeEntity = new Marker(EntityType.MARKER, level);
        boolean ok = false;
        String error = null;

        capturing = true;
        try {
            mc.renderBuffers().bufferSource().endBatch(); // make sure earlier world rendering is flushed

            eyeEntity.setPos(eyePos.x, eyePos.y, eyePos.z);
            eyeEntity.setYRot(facing.toYRot());
            eyeEntity.setXRot(0.0F);

            mc.cameraEntity = eyeEntity; // field, not Minecraft#setCameraEntity: that would touch the entity post effect
            camera.eyeHeight = 0.0F;      // the Marker is placed exactly at the eye point
            camera.eyeHeightOld = 0.0F;

            gameRenderer.setRenderBlockOutline(false);
            gameRenderer.setRenderHand(false);
            gameRenderer.setPanoramicMode(true); // fixed 90 degree fov
            window.setWidth(100);                 // 1:1 aspect ratio for the projection matrix
            window.setHeight(100);
            mc.options.setCameraType(CameraType.FIRST_PERSON);

            // Fabulous graphics use extra targets sized for the main window; not supported inside a feed
            levelRenderer.translucentTarget = null;
            levelRenderer.itemEntityTarget = null;
            levelRenderer.weatherTarget = null;
            levelRenderer.transparencyChain = null;

            if (!sodium) {
                levelRenderer.renderChunksInFrustum.clear();
                levelRenderer.renderChunksInFrustum.addAll(feed.sections);
            }

            feed.target.clear(Minecraft.ON_OSX);
            feed.target.bindWrite(true);
            mc.mainRenderTarget = feed.target;

            gameRenderer.renderLevel(1.0F, 0L, new PoseStack());

            // renderLevel clears the target with alpha 0 and many passes never write alpha, but the picture is later
            // drawn through a shader that discards transparent pixels: force the whole alpha channel to 1.
            feed.target.bindWrite(true);
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            RenderSystem.clear(16384, Minecraft.ON_OSX); // GL_COLOR_BUFFER_BIT
            RenderSystem.colorMask(true, true, true, true);

            ok = true;
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
            LOGGER.error("VisualFeedManager: capturing the view of the snail at {} failed", camPos, t);
        } finally {
            try {
                feed.target.unbindWrite();
            } catch (Throwable ignored) {
                // restoring the rest matters more
            }
            eyeEntity.discard();
            mc.cameraEntity = oldCameraEntity;
            window.setWidth(oldWidth);
            window.setHeight(oldHeight);
            mc.mainRenderTarget = oldMainTarget;
            oldMainTarget.bindWrite(true);

            levelRenderer.translucentTarget = oldTranslucent;
            levelRenderer.itemEntityTarget = oldItemEntity;
            levelRenderer.weatherTarget = oldWeather;
            levelRenderer.transparencyChain = oldTransparencyChain;
            if (oldSections != null) {
                levelRenderer.renderChunksInFrustum.clear();
                levelRenderer.renderChunksInFrustum.addAll(oldSections);
            }

            camera.eyeHeight = oldEyeHeight;
            camera.eyeHeightOld = oldEyeHeightOld;
            mc.options.setCameraType(oldCameraType);
            gameRenderer.setRenderBlockOutline(true);
            gameRenderer.setRenderHand(true);
            gameRenderer.setPanoramicMode(false);

            mc.hitResult = oldHitResult;
            mc.crosshairPickEntity = oldCrosshairEntity;

            Entity restoreEntity = oldCameraEntity == null ? player : oldCameraEntity;
            camera.setup(level, restoreEntity, !oldCameraType.isFirstPerson(), oldCameraType.isMirrored(), partialTick);

            capturing = false;
        }

        if (!ok) {
            fail(feed, error == null ? "render error" : error);
            return;
        }

        feed.okFrames++;

        if (!sodium) {
            try {
                updateSections(mc, level, feed, eyePos);
            } catch (Throwable t) {
                LOGGER.error("VisualFeedManager: updating the visible sections failed", t);
                fail(feed, "section update error");
                return;
            }
        }

        // handshake: tell the server this screen has a working picture
        if (feed.okFrames >= VisualCallConstants.HANDSHAKE_OK_FRAMES) {
            for (ScreenLink link : LINKS.values()) {
                if (link.cameraPos.equals(feed.cameraPos) && !link.ackSent && !link.failedSent) {
                    link.ackSent = true;
                    LOGGER.info("VisualFeedManager: the view of the snail at {} is rendering - reporting the screen at {} as ready",
                            feed.cameraPos, link.screenPos);
                    VisualNetwork.CHANNEL.sendToServer(new VisualFeedStatusPacket(link.callId, link.screenPos, true, ""));
                }
            }
        }
    }

    /**
     * Vanilla renderer only: builds the list of sections drawn for this camera, using the section objects of the
     * player's own view area (so the main renderer also compiles / updates them). Occlusion culling is skipped: every
     * section inside the feed's radius that intersects the camera frustum is drawn.
     *
     * Runs right after a capture so {@code getFrustum()} is still the remote camera's frustum; the list is used by the
     * NEXT capture (the very first capture therefore shows only sky).
     */
    private static void updateSections(Minecraft mc, ClientLevel level, VisualFeed feed, Vec3 eyePos) {
        LevelRenderer levelRenderer = mc.levelRenderer;
        int viewDistance = mc.options.getEffectiveRenderDistance();

        feed.capturesSinceSectionRefresh++;
        boolean refresh = feed.capturesSinceSectionRefresh >= VisualCallConstants.FEED_SECTION_REFRESH_CAPTURES
                || feed.lastViewDistance != viewDistance
                || feed.sections.isEmpty();
        if (!refresh) return;

        ViewArea viewArea = ((LevelRendererAccessor) levelRenderer).transpondersnails$getViewArea();
        if (viewArea == null) return;

        Frustum frustum = new Frustum(levelRenderer.getFrustum()).offsetToFullyIncludeCameraCube(8);

        int camX = SectionPos.blockToSectionCoord(eyePos.x);
        int camY = SectionPos.blockToSectionCoord(eyePos.y);
        int camZ = SectionPos.blockToSectionCoord(eyePos.z);
        int radius = Math.min(VisualCallConstants.FEED_VIEW_CHUNKS, viewDistance);
        int minY = Math.max(level.getMinSection(), camY - VisualCallConstants.FEED_VIEW_SECTIONS_VERTICAL);
        int maxY = Math.min(level.getMaxSection() - 1, camY + VisualCallConstants.FEED_VIEW_SECTIONS_VERTICAL);

        List<LevelRenderer.RenderChunkInfo> list = new ArrayList<>();
        for (int cx = camX - radius; cx <= camX + radius; cx++) {
            for (int cz = camZ - radius; cz <= camZ + radius; cz++) {
                int dx = cx - camX;
                int dz = cz - camZ;
                if (dx * dx + dz * dz > radius * radius) continue;

                for (int cy = minY; cy <= maxY; cy++) {
                    BlockPos origin = new BlockPos(cx << 4, cy << 4, cz << 4);
                    ChunkRenderDispatcher.RenderChunk section = ((ViewAreaAccessor) viewArea).transpondersnails$getRenderChunkAt(origin);
                    if (section == null) continue;

                    // The view area is a ring buffer: getRenderChunkAt wraps around and can return the slot that
                    // currently belongs to a different (in range) section. Only accept an exact match.
                    BlockPos actual = section.getOrigin();
                    if (actual.getX() != origin.getX() || actual.getY() != origin.getY() || actual.getZ() != origin.getZ()) continue;

                    if (!frustum.isVisible(section.getBoundingBox())) continue;
                    list.add(new LevelRenderer.RenderChunkInfo(section, null, 0));
                }
            }
        }

        if (list.isEmpty()) {
            if (!feed.emptySectionsWarned) {
                feed.emptySectionsWarned = true;
                LOGGER.warn("VisualFeedManager: no render sections found around the snail at {} (radius {} chunks) - the feed will only show sky",
                        feed.cameraPos, radius);
            }
        } else if (!feed.sectionsLogged) {
            feed.sectionsLogged = true;
            LOGGER.info("VisualFeedManager: rendering {} sections around the snail at {} (radius {} chunks)",
                    list.size(), feed.cameraPos, radius);
        }

        feed.sections = list;
        feed.capturesSinceSectionRefresh = 0;
        feed.lastViewDistance = viewDistance;
    }

    private static void fail(VisualFeed feed, String reason) {
        LOGGER.warn("VisualFeedManager: feed for the snail at {} failed: {}", feed.cameraPos, reason);
        feed.failed = true;

        for (ScreenLink link : LINKS.values()) {
            if (link.cameraPos.equals(feed.cameraPos) && !link.failedSent) {
                link.failedSent = true;
                VisualNetwork.CHANNEL.sendToServer(new VisualFeedStatusPacket(link.callId, link.screenPos, false, reason));
            }
        }

        try {
            feed.release();
        } catch (Exception e) {
            LOGGER.warn("VisualFeedManager: error releasing failed feed", e);
        }
    }
}
