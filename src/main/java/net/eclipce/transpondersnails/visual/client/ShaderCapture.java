package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * EXPERIMENTAL: drawing a snail's view while a shader pack is on, through the viewer's own pack.
 *
 * A shader pack sizes all of its buffers (g-buffers, composites, history) to the MAIN render target and notices when the main target is another
 * object (Oculus: RenderTargets#resizeIfNeeded). The normal capture swaps in the feed's small target, which under a pack made it throw away and
 * rebuild every buffer twice per capture. So under a pack nothing is swapped: the view is drawn into the real main target, exactly like the
 * player's own view (same size, same depth buffer), and then
 * <ol>
 *   <li>the centre square of the result is scaled down into the feed's target (the view is drawn with the window's real aspect ratio, so the
 *       centre square is the 1:1, 90 degree picture the other path draws directly), and</li>
 *   <li>the player's finished frame, saved before, is put back. Captures run at the end of the frame, before it goes to the screen.</li>
 * </ol>
 * The picture has the look of THIS player's pack. It is shown on this client, and it is also what this client sends when the server asks it to
 * draw a camera for far viewers - but the server asks a client with a pack only when no player without one can (ShaderCompat), so a shaded picture
 * is only seen by someone who would otherwise see a frozen one.
 *
 * Costs and risks, honestly: a capture is a full-size frame through the pack, so few feeds at a low rate (see {@link #MAX_FPS}); and the pack's
 * history (anti-aliasing, exposure) sees the snail's view for one frame, which can show as a brief flash in the player's own view at each capture.
 * Any error switches this off for the rest of the session (screens then go back to the relayed picture). Launch with
 * {@code -Dtranspondersnails.shadercapture=false} to switch it off from the start.
 */
@OnlyIn(Dist.CLIENT)
final class ShaderCapture {

    static final String PROPERTY = "transpondersnails.shadercapture";
    /** A capture through a pack costs a whole frame: at most this many pictures a second per feed ... */
    static final int MAX_FPS = 6;
    /** ... and one feed at full rate (more are drawn only at the slow "far screen" rate). */
    static final int MAX_FEEDS = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean SWITCHED_ON = !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    private static final int REPORT_EVERY = 60;

    private static boolean failed = false;
    /** Copy of the player's finished frame while a capture runs (same size as the main target). */
    private static RenderTarget backup;
    /** Between the window-sized view and a small feed: one step of scaling down by a factor of two or so, so the picture is not just aliased. */
    private static RenderTarget halfStep;
    private static boolean saved = false;

    private static long costNs = 0L;
    private static int costCount = 0;

    private ShaderCapture() {}

    /** Is drawing through the pack allowed (switched on and nothing has gone wrong yet)? */
    static boolean enabled() {
        return SWITCHED_ON && !failed;
    }

    /** True while a shader pack is on AND this client draws its views through it. */
    static boolean active() {
        return enabled() && ShaderCompat.isShaderPackActive();
    }

    /** Something went wrong: no more captures through the pack in this session (the relayed picture is used instead). */
    static void fail(String why, Throwable t) {
        if (!failed) {
            failed = true;
            LOGGER.error("ShaderCapture: {} - drawing views through the shader pack is switched off for this session", why, t);
        }
    }

    /** Saves the player's finished frame. Returns false if that failed (then nothing may be drawn into the main target). */
    static boolean begin(RenderTarget main) {
        try {
            backup = ensure(backup, main.width, main.height);
            blit(main.frameBufferId, 0, 0, main.width, main.height, backup.frameBufferId, 0, 0, main.width, main.height, GL11.GL_NEAREST);
            saved = true;
            return true;
        } catch (Throwable t) {
            fail("could not save the player's frame", t);
            return false;
        }
    }

    /** Scales the centre square of the view drawn into the main target down into the feed's target. */
    static void copyView(RenderTarget main, RenderTarget feed) {
        int side = Math.min(main.width, main.height);
        int x0 = (main.width - side) / 2;
        int y0 = (main.height - side) / 2;
        int size = feed.width;
        if (side >= size * 3) {
            int mid = size * 2;
            halfStep = ensure(halfStep, mid, mid);
            blit(main.frameBufferId, x0, y0, x0 + side, y0 + side, halfStep.frameBufferId, 0, 0, mid, mid, GL11.GL_LINEAR);
            blit(halfStep.frameBufferId, 0, 0, mid, mid, feed.frameBufferId, 0, 0, size, size, GL11.GL_LINEAR);
        } else {
            blit(main.frameBufferId, x0, y0, x0 + side, y0 + side, feed.frameBufferId, 0, 0, size, size, GL11.GL_LINEAR);
        }
    }

    /** Puts the player's frame back (always called after {@link #begin}, whatever happened in between). */
    static void end(RenderTarget main) {
        if (!saved) return;
        saved = false;
        try {
            blit(backup.frameBufferId, 0, 0, main.width, main.height, main.frameBufferId, 0, 0, main.width, main.height, GL11.GL_NEAREST);
        } catch (Throwable t) {
            fail("could not put the player's frame back", t);
        }
    }

    /** Remembers what a capture cost and now and then says how it goes. */
    static void noteCost(long ns, int mainWidth, int mainHeight, int feedSize) {
        costNs += ns;
        costCount++;
        if (costCount >= REPORT_EVERY) {
            LOGGER.info("ShaderCapture: {} views drawn through the shader pack, {} ms of render-thread time each on average ({}x{} frame, {} px pictures)",
                    costCount, Math.round(costNs / (double) costCount / 1_000.0) / 1000.0, mainWidth, mainHeight, feedSize);
            costNs = 0L;
            costCount = 0;
        }
    }

    /** The world is left or the game closes: free the extra targets. */
    static void release() {
        saved = false;
        if (backup != null) {
            backup.destroyBuffers();
            backup = null;
        }
        if (halfStep != null) {
            halfStep.destroyBuffers();
            halfStep = null;
        }
        costNs = 0L;
        costCount = 0;
    }

    private static RenderTarget ensure(RenderTarget target, int width, int height) {
        if (target != null && target.width == width && target.height == height) return target;
        if (target != null) target.destroyBuffers();
        return new TextureTarget(width, height, false, Minecraft.ON_OSX);
    }

    private static void blit(int readFbo, int sx0, int sy0, int sx1, int sy1, int drawFbo, int dx0, int dy0, int dx1, int dy1, int filter) {
        GlStateManager._disableScissorTest();                                    // a blit obeys the scissor box
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GL30.glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, GL11.GL_COLOR_BUFFER_BIT, filter);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
}
