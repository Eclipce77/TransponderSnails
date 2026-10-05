package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.visual.ScreenLayout;
import net.eclipce.transpondersnails.visual.FrameGovernor;
import net.eclipce.transpondersnails.visual.ProjectorEffects;
import net.eclipce.transpondersnails.visual.ProjectorTransition;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualQuality;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.eclipce.transpondersnails.visual.VisualRoles;
import net.eclipce.transpondersnails.visual.VisualSnailRole;
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
import net.minecraft.client.renderer.LightTexture;
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
 * renderer state afterward) follows the approach of SecurityCraft's FrameFeedHandler
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

    // ---- performance protection ----
    /** Watches the viewer's frame rate while feeds render and steps the video quality down / up. */
    private static final FrameGovernor GOVERNOR = new FrameGovernor();
    private static int lastGovernorLevel = 0;
    private static long frameId = 0L;
    private static long lastFrameNs = 0L;
    private static long lastCaptureMs = 0L;
    /** Reused every frame / capture instead of allocating (these run up to 60 times a second). */
    private static final List<VisualFeed> CANDIDATES = new ArrayList<>();
    private static final ObjectArrayList<LevelRenderer.RenderChunkInfo> SAVED_SECTIONS = new ObjectArrayList<>();

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
        /** When the first fresh picture of this call arrived (start of the fade-in), or -1 while there is none. */
        long pictureStartMs = -1L;
        /**
         * When the call ended (the "stop" arrived), or -1 while it runs. A screen that has a picture does not vanish then: it stays for
         * FEED_FADE_OUT_MS, frozen on its last picture, and plays its fade-out (with static and a pop of light); then it is removed.
         */
        long endMs = -1L;

        boolean isEnding() {
            return endMs >= 0L;
        }

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

    /** The feed of a link WITHOUT marking it as wanted: for a screen that is fading out, which must not make the feed capture again. */
    @Nullable
    static VisualFeed peekFeed(ScreenLink link) {
        return FEEDS.get(link.cameraPos);
    }

    static void onControl(UUID callId, BlockPos screenPos, BlockPos cameraPos, boolean start) {
        if (start) {
            ScreenLink already = LINKS.get(screenPos);
            if (already != null && already.callId.equals(callId)) {
                return; // duplicate START for the same call
            }
            LINKS.put(screenPos.immutable(), new ScreenLink(callId, screenPos, cameraPos));

            VisualFeed existing = FEEDS.get(cameraPos);
            if (existing != null && existing.failed) {
                existing.release();
                FEEDS.remove(cameraPos);
            }
            VisualFeed feed = FEEDS.computeIfAbsent(cameraPos.immutable(), VisualFeed::new);
            feed.lastTouchedMs = System.currentTimeMillis();
            // The render target may still hold the last picture of an earlier call: require fresh frames, so nothing
            // stale is shown and the fade-in starts with the first real picture of THIS call.
            feed.okFrames = 0;
        } else {
            ScreenLink link = LINKS.get(screenPos);
            if (link != null && link.callId.equals(callId)) {
                VisualFeed feed = FEEDS.get(link.cameraPos);
                boolean hasPicture = link.pictureStartMs >= 0L && feed != null && feed.hasFrame() && !feed.failed;
                if (hasPicture) {
                    if (!link.isEnding()) {
                        link.endMs = System.currentTimeMillis(); // keep it: it fades out on its last picture, then pruneEndedLinks removes it
                    }
                } else {
                    LINKS.remove(screenPos); // never showed anything: nothing to fade out
                    releaseUnlinkedFeeds(false);
                }
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

    /**
     * Called at the end of every rendered frame (TickEvent.RenderTickEvent, phase END). At most ONE feed is rendered per frame,
     * and only the nearest few feeds that are actually in view; the quality of each depends on how big / far its screen is
     * and on how the viewer's frame rate is doing (see VisualQuality, FrameGovernor).
     */
    public static void onRenderTickEnd(float partialTick) {
        frameId++;
        if (capturing) return;
        pruneEndedLinks(System.currentTimeMillis());
        if (LINKS.isEmpty() && FEEDS.isEmpty()) {
            lastFrameNs = 0L;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) {
            lastFrameNs = 0L; // a pause is not a slow frame
            return;
        }

        long nowNs = System.nanoTime();
        long nowMs = System.currentTimeMillis();

        // frame time -> governor (the gap after a pause / idle period is not a frame time)
        if (lastFrameNs != 0L) {
            double frameMs = (nowNs - lastFrameNs) / 1_000_000.0;
            if (frameMs < 500.0) {
                GOVERNOR.onFrame(nowMs, frameMs, nowMs - lastCaptureMs < 1000L);
                if (GOVERNOR.level() != lastGovernorLevel) {
                    LOGGER.info("VisualFeedManager: video quality level {} -> {} (smoothed frame time {} ms): max {} px, {} feed(s), {}% fps",
                            lastGovernorLevel, GOVERNOR.level(), Math.round(GOVERNOR.smoothedFrameMs()),
                            GOVERNOR.maxResolution(VisualSettings.maxFeedResolution()),
                            GOVERNOR.maxActiveFeeds(VisualSettings.maxActiveFeeds()), Math.round(GOVERNOR.fpsScale() * 100.0));
                    lastGovernorLevel = GOVERNOR.level();
                }
            }
        }
        lastFrameNs = nowNs;

        if (nowMs - lastPruneMs > 1000L) {
            lastPruneMs = nowMs;
            releaseUnlinkedFeeds(true);
        }

        boolean adaptive = VisualSettings.adaptive();
        int maxActive = adaptive ? GOVERNOR.maxActiveFeeds(VisualSettings.maxActiveFeeds()) : VisualSettings.maxActiveFeeds();

        // The nearest feeds render, the rest keep their last picture. A feed whose screen has not reported "ready" yet (the
        // call is still connecting) always renders, otherwise the call could never finish connecting.
        CANDIDATES.clear();
        for (VisualFeed feed : FEEDS.values()) {
            if (!feed.failed && isWanted(feed, nowMs)) {
                CANDIDATES.add(feed);
            }
        }
        if (CANDIDATES.isEmpty()) return;
        CANDIDATES.sort((a, b) -> Double.compare(a.lodDistance, b.lodDistance));

        VisualFeed due = null;
        for (int i = 0; i < CANDIDATES.size(); i++) {
            VisualFeed feed = CANDIDATES.get(i);
            if (i >= maxActive && !isHandshakePending(feed)) continue;
            feed.fps = effectiveFps(feed, adaptive);
            if (nowNs - feed.lastCaptureNs < 1_000_000_000L / Math.max(1, feed.fps)) continue; // each feed has its own frame rate
            if (due == null || feed.lastCaptureNs < due.lastCaptureNs) {
                due = feed;
            }
        }
        CANDIDATES.clear();
        if (due == null) return;

        due.lastCaptureNs = nowNs;
        lastCaptureMs = nowMs;
        capture(mc, level, player, due, partialTick);
    }

    private static boolean isWanted(VisualFeed feed, long nowMs) {
        if (!hasRunningLink(feed)) return false; // every screen of this feed is fading out: its last picture stays frozen
        if (nowMs - feed.lastTouchedMs < VisualCallConstants.FEED_WANTED_WINDOW_MS) return true;
        return isHandshakePending(feed);
    }

    /** Is there a screen of this feed whose call has not ended? */
    private static boolean hasRunningLink(VisualFeed feed) {
        for (ScreenLink link : LINKS.values()) {
            if (link.cameraPos.equals(feed.cameraPos) && !link.isEnding()) return true;
        }
        return false;
    }

    /** A screen of this feed has not reported "video ready" yet: it must render even when nobody is looking at it. */
    private static boolean isHandshakePending(VisualFeed feed) {
        for (ScreenLink link : LINKS.values()) {
            if (link.cameraPos.equals(feed.cameraPos) && !link.ackSent && !link.failedSent) return true;
        }
        return false;
    }

    /**
     * The screen renderer reports every screen it draws: how many pixels of the viewer's display it covers and how far away it
     * is. Adaptive quality turns that into a resolution tier, frame rate and view radius for the feed.
     */
    static void reportView(ScreenLink link, double pixels, double distance) {
        VisualFeed feed = FEEDS.get(link.cameraPos);
        if (feed == null) return;
        int wanted = VisualQuality.pickResolution(pixels, VisualCallConstants.FEED_MIN_RESOLUTION,
                VisualQuality.TIERS[VisualQuality.TIERS.length - 1]);
        feed.reportView(frameId, wanted, distance);
    }

    /** Frame rate of a feed: its role's rate, limited by the server, lower for far screens and when the governor asks for it. */
    private static int effectiveFps(VisualFeed feed, boolean adaptive) {
        int cap = Math.max(1, Math.min(feed.role.feedFps(), VisualSettings.maxFeedFps()));
        if (!adaptive) return cap;
        int fps = VisualQuality.pickFps(feed.lodDistance, cap);
        return Math.max(1, (int) Math.round(fps * GOVERNOR.fpsScale()));
    }

    /**
     * Resolution tier for the next capture of a feed. Limits (server, player, governor) apply at once; otherwise the tier the
     * screens want is applied only after it has been wanted for a while (ResolutionPlanner), so walking back and forth does not
     * re-create the render target all the time. A feed that no screen has been seen showing yet renders small.
     */
    private static int chooseResolution(VisualFeed feed, VisualSnailRole role, long nowMs) {
        int cap = VisualQuality.floorTier(Math.min(role.feedResolution(), VisualSettings.maxFeedResolution()));
        if (!VisualSettings.adaptive()) return cap;

        cap = Math.min(cap, GOVERNOR.maxResolution(cap));
        int min = Math.min(VisualCallConstants.FEED_MIN_RESOLUTION, cap);
        int wanted = feed.lodResolution > 0 ? feed.lodResolution : min;
        return feed.planner.choose(feed.isAllocated() ? feed.resolution : 0, wanted, cap, min, nowMs);
    }

    /** Removes the screens whose fade-out has played completely (and frees their feed if nothing else uses it). */
    private static void pruneEndedLinks(long nowMs) {
        boolean removed = false;
        Iterator<ScreenLink> it = LINKS.values().iterator();
        while (it.hasNext()) {
            ScreenLink link = it.next();
            if (link.isEnding() && ProjectorTransition.finished(nowMs - link.endMs)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            releaseUnlinkedFeeds(false);
        }
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
        // the snail films what is behind it (or in front, see VisualCallConstants.FEED_LOOKS_BEHIND)
        Direction lookDir = VisualCallConstants.FEED_LOOKS_BEHIND ? facing.getOpposite() : facing;
        double[] eye = VisualCallConstants.FEED_LOOKS_BEHIND
                ? ScreenLayout.eyeOffsetBehind(facing.getStepX(), facing.getStepZ())
                : ScreenLayout.eyeOffset(facing.getStepX(), facing.getStepZ());
        Vec3 eyePos = new Vec3(camPos.getX() + eye[0], camPos.getY() + eye[1], camPos.getZ() + eye[2]);

        // quality depends on the filming snail: a camera snail is sharper and sees further than a standard video call
        VisualSnailRole role = VisualRoles.of(camState.getBlock());
        if (role == null) role = VisualSnailRole.DUPLEX;
        feed.applyQuality(role, chooseResolution(feed, role, System.currentTimeMillis()), feed.fps);

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
        ObjectArrayList<LevelRenderer.RenderChunkInfo> oldSections = null;
        if (!sodium) {
            SAVED_SECTIONS.clear();
            SAVED_SECTIONS.addAll(levelRenderer.renderChunksInFrustum); // reused list: no allocation per capture
            oldSections = SAVED_SECTIONS;
        }
        int oldWidth = window.getWidth();
        int oldHeight = window.getHeight();
        CameraType oldCameraType = mc.options.getCameraType();
        float oldEyeHeight = camera.eyeHeight;
        float oldEyeHeightOld = camera.eyeHeightOld;
        // GameRenderer#pick runs inside renderLevel and would leave the crosshair target pointing at whatever the
        // remote camera sees, which the next client tick would then interact with.
        HitResult oldHitResult = mc.hitResult;
        Entity oldCrosshairEntity = mc.crosshairPickEntity;

        // Camera exposure: a camera exposes for the dark, so the feed is lit as if the Brightness option were at least
        // FEED_CAPTURE_BRIGHTNESS (never below the player's own setting). Put back right after the capture, see the finally block.
        LightTexture lightTexture = gameRenderer.lightTexture();
        double playerBrightness = mc.options.gamma().get();
        double exposure = ProjectorEffects.exposureFor(playerBrightness);
        boolean exposed = false;

        Marker eyeEntity = new Marker(EntityType.MARKER, level);
        boolean ok = false;
        String error = null;

        capturing = true;
        try {
            mc.renderBuffers().bufferSource().endBatch(); // make sure earlier world rendering is flushed

            eyeEntity.setPos(eyePos.x, eyePos.y, eyePos.z);
            eyeEntity.setYRot(lookDir.toYRot());
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

            if (exposure > playerBrightness) {
                exposed = true; // from here on the finally block puts the player's setting back, whatever happens below
                mc.options.gamma().set(exposure);
                lightTexture.tick();                           // marks the light map as outdated (otherwise the update below does nothing)
                lightTexture.updateLightTexture(partialTick);  // the light map the world is rendered with now has the camera's exposure
            }

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
            if (exposed) {
                mc.options.gamma().set(playerBrightness); // the player's own setting, always
                try {
                    lightTexture.tick();
                    lightTexture.updateLightTexture(partialTick); // the player's own light map again
                } catch (Throwable ignored) {
                    // the next client tick refreshes the light map anyway
                }
            }
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
                oldSections.clear(); // do not keep render chunks alive through the static list
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
        long nowMs = System.currentTimeMillis();
        for (ScreenLink link : LINKS.values()) {
            if (link.cameraPos.equals(feed.cameraPos) && link.pictureStartMs < 0L && !link.isEnding()) {
                link.pictureStartMs = nowMs; // the fade-in of this screen starts now
            }
        }

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
                if (link.cameraPos.equals(feed.cameraPos) && !link.ackSent && !link.failedSent && !link.isEnding()) {
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

        // View radius: the feed's own, never more than this client has loaded (its render distance). With adaptive quality it
        // is also shorter for low resolutions (they cannot show distant detail anyway) and when the frame governor asks for it.
        int radius = Math.min(feed.viewChunks, viewDistance);
        if (VisualSettings.adaptive()) {
            radius = Math.min(radius, GOVERNOR.maxViewChunks(feed.viewChunks));
            radius = Math.min(radius, VisualQuality.viewRadiusFor(feed.resolution, feed.viewChunks));
        }
        radius = Math.max(2, radius);

        // The sections are the player's own ViewArea slots; they are re-assigned when the PLAYER moves to another section, so
        // the list is rebuilt at once then (and when the radius changes), and otherwise only now and then.
        long playerSection = SectionPos.asLong(
                SectionPos.blockToSectionCoord(mc.player.getX()),
                SectionPos.blockToSectionCoord(mc.player.getY()),
                SectionPos.blockToSectionCoord(mc.player.getZ()));
        feed.capturesSinceSectionRefresh++;
        boolean refresh = feed.capturesSinceSectionRefresh >= VisualCallConstants.FEED_SECTION_REFRESH_CAPTURES
                || feed.lastViewDistance != viewDistance
                || feed.lastRadius != radius
                || feed.lastPlayerSection != playerSection
                || feed.sections.isEmpty();
        if (!refresh) return;

        ViewArea viewArea = ((LevelRendererAccessor) levelRenderer).transpondersnails$getViewArea();
        if (viewArea == null) return;

        Frustum frustum = new Frustum(levelRenderer.getFrustum()).offsetToFullyIncludeCameraCube(8);

        int camX = SectionPos.blockToSectionCoord(eyePos.x);
        int camY = SectionPos.blockToSectionCoord(eyePos.y);
        int camZ = SectionPos.blockToSectionCoord(eyePos.z);
        int minY = Math.max(level.getMinSection(), camY - feed.viewVertical);
        int maxY = Math.min(level.getMaxSection() - 1, camY + feed.viewVertical);

        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos(); // one object for the whole scan, not one per section
        List<LevelRenderer.RenderChunkInfo> list = feed.sections;
        list.clear();
        for (int cx = camX - radius; cx <= camX + radius; cx++) {
            for (int cz = camZ - radius; cz <= camZ + radius; cz++) {
                int dx = cx - camX;
                int dz = cz - camZ;
                if (dx * dx + dz * dz > radius * radius) continue;

                for (int cy = minY; cy <= maxY; cy++) {
                    origin.set(cx << 4, cy << 4, cz << 4);
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
            LOGGER.info("VisualFeedManager: rendering {} sections around the snail at {} ({}x{} px, view radius {} of {} chunks, {} fps)",
                    list.size(), feed.cameraPos, feed.resolution, feed.resolution, radius, feed.viewChunks, feed.fps);
        }

        feed.capturesSinceSectionRefresh = 0;
        feed.lastViewDistance = viewDistance;
        feed.lastRadius = radius;
        feed.lastPlayerSection = playerSection;
    }

    private static void fail(VisualFeed feed, String reason) {
        LOGGER.warn("VisualFeedManager: feed for the snail at {} failed: {}", feed.cameraPos, reason);
        feed.failed = true;

        for (ScreenLink link : LINKS.values()) {
            if (link.cameraPos.equals(feed.cameraPos) && !link.failedSent && !link.isEnding()) {
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
