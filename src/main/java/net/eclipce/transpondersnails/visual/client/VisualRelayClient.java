package net.eclipce.transpondersnails.visual.client;

import com.mojang.logging.LogUtils;
import net.eclipce.transpondersnails.visual.RelayFrames;
import net.eclipce.transpondersnails.visual.TileCodec;
import net.eclipce.transpondersnails.visual.network.VisualFrameUploadPacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The client's part of the relay (see VisualRelayManager on the server for the whole idea, TileCodec for the picture).
 *
 * <ul>
 *   <li><b>Rendering for others</b>: the server asks this client - it is near a camera, so it has the area loaded - to render the camera's view
 *       and send it up. The camera is drawn like any other feed (VisualFeedManager); after each capture this class starts reading the picture
 *       back from the GPU WITHOUT waiting (GpuReadback), and on a worker thread compares it with what the viewers have, packs only the tiles
 *       that changed (TileCodec) and sends them. A camera where nothing has moved for a few frames is captured less often.</li>
 *   <li><b>Receiving</b>: payloads the server relays to this client are applied IN ORDER on a worker thread (TileCodec decoder), and the changed
 *       tiles are handed to the feed, which uploads just those to the picture on the screen.</li>
 * </ul>
 * The render thread only does what must be done there (starting the read, uploading tiles, sending). If a worker falls behind, work is dropped
 * rather than queued: the encoder simply sends what is still different next time; a viewer that falls behind asks to start again from a key frame.
 */
@OnlyIn(Dist.CLIENT)
final class VisualRelayClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** A viewer that has this many payloads waiting to be applied has fallen behind: it drops them and starts again from a key frame. */
    private static final int MAX_QUEUED = 200;

    private VisualRelayClient() {}

    private static final ExecutorService ENCODER = newWorker("transpondersnails-relay-encode");
    private static final ExecutorService DECODER = newWorker("transpondersnails-relay-decode");

    private static ExecutorService newWorker(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    // =====================================================================================================
    // Rendering for others
    // =====================================================================================================

    /** What this client is asked to do for one camera, and its encoder. */
    private static final class Exporter {
        volatile long untilMs;
        volatile int fps = 10;
        volatile int resolution = RelayFrames.MIN_RESOLUTION;
        volatile int kbps = 160;
        volatile boolean forceKey = true;                      // a new exporter starts with a key frame anyway
        volatile int quietFrames = 0;                          // frames in a row in which no tile changed
        long lastExportMs = 0L;
        final TileCodec.Encoder encoder = new TileCodec.Encoder();   // used on the encoder thread only
        final GpuReadback readback = new GpuReadback();              // used on the render thread only
        final AtomicBoolean encoding = new AtomicBoolean(false);
    }

    private static final Map<BlockPos, Exporter> EXPORTERS = new HashMap<>();   // render thread

    /** The server asks (or stops asking) this client to render the camera at {@code camera}. Renewed every second; it runs out by itself. */
    static void onFeedRequest(BlockPos camera, int fps, boolean active, int resolution, int kbps, boolean forceKey) {
        if (!active) {
            Exporter gone = EXPORTERS.remove(camera);
            if (gone != null) gone.readback.close();
            return;
        }
        Exporter e = EXPORTERS.computeIfAbsent(camera.immutable(), k -> new Exporter());
        e.untilMs = System.currentTimeMillis() + RelayFrames.REQUEST_TTL_MS;
        e.fps = Math.max(1, fps);
        e.resolution = Math.max(RelayFrames.MIN_RESOLUTION, resolution);
        e.kbps = Math.max(16, kbps);
        if (forceKey) e.forceKey = true;
        VisualFeedManager.onExportRequested(camera);
    }

    private static Exporter live(BlockPos camera, long nowMs) {
        Exporter e = EXPORTERS.get(camera);
        if (e == null) return null;
        if (e.untilMs <= nowMs) {                              // not renewed: the server has stopped asking
            EXPORTERS.remove(camera);
            e.readback.close();
            return null;
        }
        return e;
    }

    /** Is this client asked to render the camera right now? */
    static boolean isExporting(BlockPos camera, long nowMs) {
        return live(camera, nowMs) != null;
    }

    /** How often to capture the camera: the rate asked for, less while nothing moves. 0 if it is not asked for. */
    static int exportFps(BlockPos camera, long nowMs) {
        Exporter e = live(camera, nowMs);
        return e == null ? 0 : RelayFrames.captureFps(e.fps, e.quietFrames);
    }

    /** The size the far viewers want the camera drawn at, or 0. */
    static int exportResolution(BlockPos camera, long nowMs) {
        Exporter e = live(camera, nowMs);
        return e == null ? 0 : e.resolution;
    }

    /**
     * Call right after a capture of a camera that is being exported. Starts reading the picture back (no waiting: the picture of the capture
     * BEFORE this one comes back), and hands it to the encoder thread, which sends only what changed. Never more than one frame in the
     * encoder at a time; a frame that finds it busy is dropped (what it would have sent is sent next time).
     */
    static void exportFrame(VisualFeed feed) {
        long now = System.currentTimeMillis();
        Exporter e = live(feed.cameraPos, now);
        if (e == null || feed.target == null) return;
        if (now - e.lastExportMs < RelayFrames.frameInterval(RelayFrames.captureFps(e.fps, e.quietFrames)) * 7 / 10) return;
        e.lastExportMs = now;

        GpuReadback.Result picture = e.readback.submit(feed.target.getColorTextureId(), feed.resolution);
        if (picture == null || !TileCodec.validSize(picture.size())) return;
        if (!e.encoding.compareAndSet(false, true)) return;
        boolean forceKey = e.forceKey;
        e.forceKey = false;
        BlockPos camera = feed.cameraPos;

        ENCODER.execute(() -> {
            try {
                int[] abgr = picture.abgr();
                int[] argb = new int[abgr.length];
                for (int i = 0; i < abgr.length; i++) {                    // the read-back has its channels as ABGR
                    int p = abgr[i];
                    argb[i] = 0xFF000000 | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
                }
                List<TileCodec.Packet> packets = e.encoder.encode(argb, picture.size(), now, e.kbps * 1024, forceKey);
                e.quietFrames = (e.encoder.lastChangedTiles == 0) ? e.quietFrames + 1 : 0;
                if (!packets.isEmpty()) {
                    Minecraft.getInstance().execute(() -> {
                        for (TileCodec.Packet packet : packets) {
                            VisualNetwork.CHANNEL.sendToServer(new VisualFrameUploadPacket(camera, packet.flags(), packet.data()));
                        }
                    });
                }
            } catch (Throwable t) {
                LOGGER.warn("VisualRelayClient: packing a frame failed: {}", t.toString());
            } finally {
                e.encoding.set(false);
            }
        });
    }

    // =====================================================================================================
    // Receiving
    // =====================================================================================================

    private record FramePacket(int flags, byte[] data) { }

    private static final class RemoteStream {
        final ConcurrentLinkedQueue<FramePacket> queue = new ConcurrentLinkedQueue<>();
        final AtomicInteger queued = new AtomicInteger();
        final AtomicBoolean scheduled = new AtomicBoolean(false);
        final TileCodec.Decoder decoder = new TileCodec.Decoder();   // used on the decoder thread only
        volatile boolean resetRequested = false;
        volatile boolean resync = false;
    }

    private static final Map<BlockPos, RemoteStream> REMOTE = new ConcurrentHashMap<>();

    /** A relayed payload arrived (main thread): queued in order and applied on the worker. */
    static void onFrame(BlockPos camera, int flags, byte[] data) {
        RemoteStream stream = REMOTE.computeIfAbsent(camera.immutable(), k -> new RemoteStream());
        if (stream.queued.get() >= MAX_QUEUED) {                 // fallen behind: the deltas cannot be skipped, so start again from a key frame
            stream.queue.clear();
            stream.queued.set(0);
            stream.resetRequested = true;
            stream.resync = true;
            return;
        }
        stream.queue.add(new FramePacket(flags, data));
        stream.queued.incrementAndGet();
        if (stream.scheduled.compareAndSet(false, true)) DECODER.execute(() -> drain(camera.immutable(), stream));
    }

    private static void drain(BlockPos camera, RemoteStream stream) {
        try {
            if (stream.resetRequested) {
                stream.decoder.reset();
                stream.resetRequested = false;
            }
            FramePacket packet;
            while ((packet = stream.queue.poll()) != null) {
                stream.queued.decrementAndGet();
                TileCodec.Update update = stream.decoder.apply(packet.flags(), packet.data());
                if (update != null) Minecraft.getInstance().execute(() -> VisualFeedManager.onRemoteUpdate(camera, update));
            }
        } catch (Throwable t) {
            LOGGER.warn("VisualRelayClient: applying a relayed frame failed: {}", t.toString());
        } finally {
            stream.scheduled.set(false);
            if (!stream.queue.isEmpty() && stream.scheduled.compareAndSet(false, true)) DECODER.execute(() -> drain(camera, stream));
        }
    }

    /** True once (and then false) if this viewer fell behind and must ask the server to start it again from a key frame. */
    static boolean consumeResync(BlockPos camera) {
        RemoteStream stream = REMOTE.get(camera);
        if (stream == null || !stream.resync) return false;
        stream.resync = false;
        return true;
    }

    /** This viewer no longer shows the camera relayed: forget the picture state (a new relay starts from a key frame). */
    static void forget(BlockPos camera) {
        REMOTE.remove(camera);
    }

    static void clear() {
        for (Exporter e : EXPORTERS.values()) e.readback.close();
        EXPORTERS.clear();
        REMOTE.clear();
    }
}
