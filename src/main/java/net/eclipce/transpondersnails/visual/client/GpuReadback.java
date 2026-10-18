package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * Reads a texture back from the GPU WITHOUT making the CPU wait for the GPU.
 *
 * <p>The ordinary way (glGetTexImage into memory) stops the CPU until the GPU has finished drawing the picture and copied it - a stall of
 * several milliseconds every frame. Here two pixel buffers take turns: a read for this capture is STARTED into one (the GPU does it when it
 * is ready) while the result of the PREVIOUS capture is collected from the other, which has long been finished. So {@link #submit} always
 * returns the picture of the capture before - one frame (about 100 ms) late, which a relayed picture can afford - and never waits.
 *
 * <p>If anything about this fails on a machine (a GL error, a driver without the feature), it turns itself off for good and reads the
 * ordinary way instead: slower, but it works.
 *
 * Render thread only.
 */
@OnlyIn(Dist.CLIENT)
final class GpuReadback implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** A picture: {@code size} pixels each way, each pixel an int with the channels as ABGR (the order NativeImage uses). */
    record Result(int size, int[] abgr) { }

    private final int[] buffers = new int[2];
    private final int[] readSize = new int[2];   // the size of the read waiting in each buffer, 0 = nothing
    private int bufferBytes = 0;
    private int next = 0;                        // the buffer the next read goes into
    private boolean broken = false;

    /**
     * Starts reading the texture and returns the picture of the read before this one (null the first time, after a size change, or if it
     * was not ready - the caller then simply has no frame this time).
     */
    @Nullable
    Result submit(int textureId, int size) {
        if (!broken) {
            try {
                return submitAsync(textureId, size);
            } catch (Throwable t) {
                disable("exception: " + t);
            }
        }
        return readNow(textureId, size);
    }

    @Nullable
    private Result submitAsync(int textureId, int size) {
        GL11C.glGetError();                                  // forget errors from before this
        ensureBuffers(size);
        if (broken) return readNow(textureId, size);

        // 1. collect the read that was started last time (the OTHER buffer)
        Result previous = null;
        int other = next ^ 1;
        if (readSize[other] > 0) {
            previous = collect(other);
            readSize[other] = 0;
        }

        // 2. start this one; the GPU fills the buffer when it is ready, the CPU does not wait
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, buffers[next]);
        RenderSystem.bindTexture(textureId);
        GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        if (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            disable("a GL error while reading back");
            return previous != null ? previous : readNow(textureId, size);
        }
        readSize[next] = size;
        next ^= 1;
        return previous;
    }

    private void ensureBuffers(int size) {
        int bytes = size * size * 4;
        if (bytes == bufferBytes && buffers[0] != 0) return;
        deleteBuffers();                                     // a different size: anything waiting is of the old size and is dropped
        bufferBytes = bytes;
        for (int i = 0; i < 2; i++) {
            buffers[i] = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, buffers[i]);
            GL15C.glBufferData(GL21C.GL_PIXEL_PACK_BUFFER, (long) bytes, GL15C.GL_STREAM_READ);
        }
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        readSize[0] = 0;
        readSize[1] = 0;
        next = 0;
        if (GL11C.glGetError() != GL11C.GL_NO_ERROR) disable("a GL error creating the buffers");
    }

    @Nullable
    private Result collect(int index) {
        int size = readSize[index];
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, buffers[index]);
        try {
            ByteBuffer mapped = GL30C.glMapBufferRange(GL21C.GL_PIXEL_PACK_BUFFER, 0L, (long) size * size * 4L, GL30C.GL_MAP_READ_BIT);
            if (mapped == null) return null;
            int[] pixels = new int[size * size];
            mapped.asIntBuffer().get(pixels);
            return new Result(size, pixels);
        } finally {
            GL15C.glUnmapBuffer(GL21C.GL_PIXEL_PACK_BUFFER);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        }
    }

    /** The ordinary read: waits for the GPU. Used if the asynchronous way is not available. */
    @Nullable
    private static Result readNow(int textureId, int size) {
        try (NativeImage image = new NativeImage(size, size, false)) {
            RenderSystem.bindTexture(textureId);
            image.downloadTexture(0, true);
            return new Result(size, image.getPixelsRGBA());
        } catch (Throwable t) {
            LOGGER.warn("GpuReadback: could not read the picture back: {}", t.toString());
            return null;
        }
    }

    private void disable(String why) {
        if (!broken) LOGGER.warn("GpuReadback: asynchronous read-back turned off ({}); reading the ordinary way", why);
        broken = true;
        GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
        deleteBuffers();
    }

    private void deleteBuffers() {
        for (int i = 0; i < 2; i++) {
            if (buffers[i] != 0) GL15C.glDeleteBuffers(buffers[i]);
            buffers[i] = 0;
            readSize[i] = 0;
        }
        bufferBytes = 0;
    }

    @Override
    public void close() {
        deleteBuffers();
    }
}
