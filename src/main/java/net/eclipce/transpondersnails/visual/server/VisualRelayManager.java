package net.eclipce.transpondersnails.visual.server;

import net.eclipce.transpondersnails.visual.RelayFrames;
import net.eclipce.transpondersnails.visual.RelaySubscriptions;
import net.eclipce.transpondersnails.visual.TileCodec;
import net.eclipce.transpondersnails.visual.UploaderSelection;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.eclipce.transpondersnails.visual.network.VisualFeedRequestPacket;
import net.eclipce.transpondersnails.visual.network.VisualFrameRelayPacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Video from ANY distance. A picture is drawn by a client from the world it has loaded, so a viewer far from a camera cannot draw it. The
 * client of a player who IS near the camera (the streamer, the person at the other end of the call) can, so this relays: a viewer that cannot
 * draw a camera asks for it, the server picks a player near the camera to render it and send it up, and forwards what comes to every viewer
 * that asked. One render serves any number of viewers.
 *
 * <p>The picture is sent as TileCodec payloads: a key frame (every tile) once, then only the tiles that change. The server never looks inside
 * them - it does not decode or re-encode anything. It keeps the last key frame and what came after it, so a viewer that joins gets the whole
 * picture at once, and it asks the uploader for a new key frame when that grows too big. Each payload is forwarded to all viewers as one
 * packet object (not re-made per viewer).
 *
 * <p>Nothing is trusted: a request is only answered for a player that is really connected to that camera (VisualCallManager), only the player
 * that was chosen may upload for a camera, a payload is bounded, what a camera uploads per second has a hard cap, and the number of cameras
 * relayed at once is limited (server option relay_max_sources). Without a player near the camera there is nobody to draw it: the viewers
 * then keep the last picture.
 */
public final class VisualRelayManager {

    private VisualRelayManager() {}

    /** A player this near a camera (blocks) has its surroundings loaded and may be asked to render it. */
    private static final double UPLOAD_RANGE = 40.0;
    /** The player that is rendering keeps the job until it is this much farther than UPLOAD_RANGE. */
    private static final double STICKY_EXTRA = 16.0;
    private static final long WORK_INTERVAL_MS = 250L;
    /** What one camera may upload in a second, whatever it was asked for: a key frame of a big picture is a burst, but not more than this. */
    private static final long HARD_CAP_BYTES_PER_SECOND = 1_200_000L;

    private record Cached(int flags, byte[] data) { }

    /** Everything the server knows about one relayed camera. */
    private static final class Stream {
        @Nullable UUID uploader;
        final ArrayDeque<Cached> cache = new ArrayDeque<>();   // the last key frame and what came after it
        long cacheBytes;
        boolean keyStarted;
        boolean keyComplete;
        boolean wantKey = true;                                // ask the uploader for a key frame
        final Set<UUID> synced = new HashSet<>();              // viewers that have the whole picture and get every payload as it comes
        final Map<UUID, Integer> wishes = new HashMap<>();     // the size each viewer asked for
        long lastRequestMs;
        long windowStartMs;
        long windowBytes;

        void resetCache() {
            cache.clear();
            cacheBytes = 0;
            keyStarted = false;
            keyComplete = false;
        }
    }

    private static final RelaySubscriptions<GlobalPos, UUID> SUBSCRIPTIONS = new RelaySubscriptions<>();
    private static final Map<GlobalPos, Stream> STREAMS = new HashMap<>();
    private static long lastWorkMs = 0L;

    // =====================================================================================================
    // From the clients
    // =====================================================================================================

    /** A viewer's client says it cannot draw a camera (or no longer needs it). */
    public static void onRelayRequest(ServerPlayer player, BlockPos camera, boolean wants, int wantedResolution, boolean resync) {
        GlobalPos gp = GlobalPos.of(player.level().dimension(), camera);
        UUID id = player.getUUID();
        if (!wants) {
            SUBSCRIPTIONS.remove(gp, id);
            Stream gone = STREAMS.get(gp);
            if (gone != null) {
                gone.wishes.remove(id);
                gone.synced.remove(id);
            }
            return;
        }
        if (VisualSettings.relayMaxSources() <= 0) return;
        // only a camera that is loaded and that this player is really connected to
        VisualCallManager calls = VisualCallManager.peek();
        if (calls == null || VisualSnailRegistry.get(gp) == null || !calls.isViewerOfCamera(id, gp)) return;
        SUBSCRIPTIONS.touch(gp, id, System.currentTimeMillis() + RelayFrames.REQUEST_TTL_MS);
        Stream stream = STREAMS.computeIfAbsent(gp, k -> new Stream());
        stream.wishes.put(id, Math.max(0, Math.min(wantedResolution, TileCodec.MAX_SIZE)));
        if (resync) stream.synced.remove(id);   // it fell behind: it gets the key frame and what came after again
    }

    /** The chosen player's client sends a payload of the camera's picture. */
    public static void onFrame(ServerPlayer uploader, BlockPos camera, int flags, byte[] data) {
        GlobalPos gp = GlobalPos.of(uploader.level().dimension(), camera);
        Stream stream = STREAMS.get(gp);
        if (stream == null || stream.uploader == null || !stream.uploader.equals(uploader.getUUID())) return;   // only the one that was asked
        if (data == null || data.length < 8 || data.length > RelayFrames.MAX_FRAME_BYTES || (flags & ~(TileCodec.FLAG_KEY_START | TileCodec.FLAG_KEY_END)) != 0) return;

        long now = System.currentTimeMillis();
        if (now - stream.windowStartMs >= 1000L) {
            stream.windowStartMs = now;
            stream.windowBytes = 0L;
        }
        stream.windowBytes += data.length;
        if (stream.windowBytes > HARD_CAP_BYTES_PER_SECOND) return;

        if ((flags & TileCodec.FLAG_KEY_START) != 0) {            // a new key frame replaces everything that was kept
            stream.resetCache();
            stream.keyStarted = true;
        } else if (!stream.keyStarted) {
            return;                                               // a delta with no key frame before it: nothing to keep it for
        }
        stream.cache.add(new Cached(flags, data));
        stream.cacheBytes += data.length;
        if ((flags & TileCodec.FLAG_KEY_END) != 0) stream.keyComplete = true;

        // to every viewer that is in step, as ONE packet object sent down all their connections
        MinecraftServer server = uploader.getServer();
        if (server == null || stream.synced.isEmpty()) return;
        List<Connection> targets = new ArrayList<>(stream.synced.size());
        for (UUID viewerId : stream.synced) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer != null && viewer.level().dimension().equals(gp.dimension())) targets.add(viewer.connection.connection);
        }
        if (!targets.isEmpty()) {
            VisualNetwork.CHANNEL.send(PacketDistributor.NMLIST.with(() -> targets), new VisualFrameRelayPacket(camera, flags, data));
        }
    }

    public static void onPlayerLeft(UUID player) {
        SUBSCRIPTIONS.removeViewer(player);
        for (Stream stream : STREAMS.values()) {
            stream.wishes.remove(player);
            stream.synced.remove(player);
            if (player.equals(stream.uploader)) {
                stream.uploader = null;       // the next work pass picks another player near the camera
                stream.resetCache();
                stream.synced.clear();
                stream.wantKey = true;
            }
        }
    }

    // =====================================================================================================
    // Choosing who renders, and bringing viewers in step
    // =====================================================================================================

    /** Called every server tick; works a few times a second. */
    public static void tick(@Nullable MinecraftServer server) {
        if (server == null) return;
        long now = System.currentTimeMillis();
        if (now - lastWorkMs < WORK_INTERVAL_MS) return;
        lastWorkMs = now;

        SUBSCRIPTIONS.expire(now);
        int limit = VisualSettings.relayMaxSources();
        int sources = 0;

        for (GlobalPos camera : SUBSCRIPTIONS.cameras()) {
            ServerLevel level = server.getLevel(camera.dimension());
            if (limit <= 0 || level == null || VisualSnailRegistry.get(camera) == null) {
                for (UUID viewer : SUBSCRIPTIONS.viewersOf(camera)) SUBSCRIPTIONS.remove(camera, viewer);   // the camera is gone (unloaded / removed)
                continue;
            }
            Stream stream = STREAMS.computeIfAbsent(camera, k -> new Stream());
            UUID currentId = stream.uploader;
            if (currentId == null && sources >= limit) continue;                                          // over the limit: not relayed

            ServerPlayer current = currentId == null ? null : server.getPlayerList().getPlayer(currentId);
            if (current != null && !current.level().dimension().equals(camera.dimension())) current = null;
            BlockPos at = camera.pos();
            List<ServerPlayer> candidates = new ArrayList<>(level.players());
            ServerPlayer chosen = UploaderSelection.choose(candidates,
                    p -> p.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5), UPLOAD_RANGE, current, STICKY_EXTRA);

            if (chosen == null) {                       // nobody near enough: nobody to draw it; the viewers keep the last picture
                releaseUploader(server, camera, stream);
                continue;
            }
            sources++;
            if (!chosen.getUUID().equals(currentId)) {
                if (current != null) send(current, camera, stream, false);
                stream.uploader = chosen.getUUID();
                stream.resetCache();                    // the new uploader starts with a key frame
                stream.synced.clear();
                stream.wantKey = true;
                stream.lastRequestMs = 0L;
            }

            Set<UUID> viewers = SUBSCRIPTIONS.viewersOf(camera);
            stream.synced.retainAll(viewers);
            stream.wishes.keySet().retainAll(viewers);
            if (stream.cacheBytes > RelayFrames.MAX_CACHE_BYTES) stream.wantKey = true;                    // what a joiner would have to be sent has grown too big

            if (now - stream.lastRequestMs >= RelayFrames.REQUEST_RENEW_MS) {
                stream.lastRequestMs = now;
                send(chosen, camera, stream, true);     // renewed every second: it stops by itself if the server stops asking
                stream.wantKey = false;
            }

            // viewers that are not in step yet get the last key frame and everything after it, then every payload as it comes
            if (stream.keyComplete) {
                for (UUID viewerId : viewers) {
                    if (!stream.synced.contains(viewerId)) bringInStep(server, camera, stream, viewerId);
                }
            }
        }

        // cameras nobody wants any more
        Iterator<Map.Entry<GlobalPos, Stream>> it = STREAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlobalPos, Stream> e = it.next();
            if (SUBSCRIPTIONS.viewerCount(e.getKey()) == 0) {
                releaseUploader(server, e.getKey(), e.getValue());
                it.remove();
            }
        }
    }

    private static void bringInStep(MinecraftServer server, GlobalPos camera, Stream stream, UUID viewerId) {
        ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
        if (viewer == null || !viewer.level().dimension().equals(camera.dimension())) return;
        for (Cached cached : stream.cache) {
            VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new VisualFrameRelayPacket(camera.pos(), cached.flags(), cached.data()));
        }
        stream.synced.add(viewerId);
    }

    private static void releaseUploader(MinecraftServer server, GlobalPos camera, Stream stream) {
        UUID id = stream.uploader;
        stream.uploader = null;
        stream.resetCache();
        stream.synced.clear();
        stream.wantKey = true;
        stream.lastRequestMs = 0L;
        if (id == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) send(p, camera, stream, false);
    }

    private static void send(ServerPlayer player, GlobalPos camera, Stream stream, boolean active) {
        int resolution = RelayFrames.resolutionFor(stream.wishes.values(), VisualSettings.relayMaxResolution());
        VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new VisualFeedRequestPacket(camera.pos(), VisualSettings.relayFps(), active, resolution, VisualSettings.relayMaxKbps(), stream.wantKey));
    }

    public static void clear() {
        SUBSCRIPTIONS.expire(Long.MAX_VALUE);
        STREAMS.clear();
    }
}
