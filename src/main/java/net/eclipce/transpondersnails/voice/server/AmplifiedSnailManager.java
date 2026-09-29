package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.eclipce.transpondersnails.block.custom.AmplifiedTransponderSnailBlock;
import net.eclipce.transpondersnails.item.AmplifiedTransponderSnailItem;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.eclipce.transpondersnails.voice.audio.MegaphoneAudioFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server-side "megaphone" system for the Amplified Transponder Snail.
 *
 * PICKUP
 *  - HANDHELD: only the player holding right-click.
 *  - PLACED:   players within a 1 block ring around the snail (diagonals included), slightly quieter
 *              toward the edge of the ring.
 *  While a player is being picked up, their normal Simple Voice Chat stream is cancelled completely -
 *  everyone, the speaker included, hears only the megaphone version.
 *
 * BROADCAST (directional, like a real megaphone)
 *  Each speaker gets two channels carrying the same encoded audio:
 *  - FRONT: listeners inside a cone in front of the horn (handheld: where the player is looking,
 *           placed: the way the snail's head faces), full {@link VoiceChatConstants#AMPLIFIED_SNAIL_BROADCAST_RANGE}.
 *  - REAR:  everyone else, at the much shorter {@link VoiceChatConstants#AMPLIFIED_SNAIL_REAR_RANGE}, so it
 *           is quieter behind/beside the horn and inaudible further back.
 *  The channel filters are mutually exclusive, so each listener hears exactly one copy.
 *
 * AUDIO
 *  Everything goes through {@link MegaphoneAudioFilter}. Its echo/reverb keep ringing after the speaker
 *  stops, so the server tick renders that tail as extra frames ("tail pumping") instead of cutting it off.
 *
 * THREADING
 *  - {@link #onMicrophonePacket} runs on Simple Voice Chat's network thread and never touches the world.
 *  - World changes (blockstates, item NBT) and tail pumping run in {@link #tick} on the server thread.
 *  - Each {@link AudioPipeline} is synchronized: Opus codecs and the filter are not thread-safe.
 */
public final class AmplifiedSnailManager {

    private static volatile AmplifiedSnailManager instance;

    private static final int FRAME_SIZE = VoiceChatConstants.AUDIO_FRAME_SIZE; // 960 samples = 20ms
    private static final long FRAME_MS = 20;

    /** How long after the last packet the snail still shows its "active" (mouth open) state. */
    private static final long ACTIVE_WINDOW_MS = 300;
    /** Start rendering the echo/reverb tail once no packet has arrived for this long. */
    private static final long TAIL_START_AFTER_MS = 60;
    /** Maximum echo/reverb tail rendered after speech stops. */
    private static final int MAX_TAIL_FRAMES = 50; // 1 second
    /** A tail frame quieter than this (peak) ends the tail early. */
    private static final int TAIL_SILENCE_PEAK = 20;
    /** Flush a channel after this much silence so clients close the stream cleanly. */
    private static final long FLUSH_AFTER_MS = 250;
    /** Close per-speaker pipelines on placed snails that haven't been used for this long. */
    private static final long PIPELINE_IDLE_CLOSE_MS = 10_000;
    /** Listeners this close to the source always get the front channel (no odd angle effects up close). */
    private static final double ALWAYS_FRONT_DISTANCE = 2.0;

    private static final double FRONT_CONE_COS =
            Math.cos(Math.toRadians(VoiceChatConstants.AMPLIFIED_SNAIL_FRONT_CONE_HALF_ANGLE));

    private final VoicechatServerApi api;

    private final Map<UUID, HandheldSession> handheldSessions = new ConcurrentHashMap<>();
    private final Map<PlacedKey, PlacedSession> placedSessions = new ConcurrentHashMap<>();
    /** Pipelines whose speaker stopped, still playing out their echo/reverb tail before closing. */
    private final Queue<AudioPipeline> drainingPipelines = new ConcurrentLinkedQueue<>();

    private AmplifiedSnailManager(VoicechatServerApi api) {
        this.api = api;
    }

    // =================== LIFECYCLE ===================

    /** Called from TransponderSnailsPlugin when the voice chat server starts. */
    public static void initialize(VoicechatServerApi api) {
        AmplifiedSnailManager old = instance;
        if (old != null) {
            old.closeAllAudio();
        }
        instance = new AmplifiedSnailManager(api);
    }

    @Nullable
    public static AmplifiedSnailManager get() {
        return instance;
    }

    /**
     * Called on ServerStoppingEvent (server thread, levels still loaded).
     * Resets every active snail's visuals so nothing is saved "on" with no session behind it.
     */
    public static void shutdown() {
        AmplifiedSnailManager mgr = instance;
        instance = null;
        if (mgr == null) {
            return;
        }

        for (PlacedSession session : mgr.placedSessions.values()) {
            try {
                // getBlockState() loads the chunk if needed, so snails in unloaded chunks are reset too
                BlockState state = session.level.getBlockState(session.pos);
                if (state.getBlock() instanceof AmplifiedTransponderSnailBlock) {
                    session.level.setBlock(session.pos, state.setValue(
                            AmplifiedTransponderSnailBlock.AMPLIFIER_STATE, AmplifiedTransponderSnailBlock.STATE_IDLE), 3);
                }
            } catch (Exception e) {
                System.err.println("AmplifiedSnailManager: Failed to reset snail at " + session.pos + ": " + e.getMessage());
            }
        }

        for (HandheldSession session : mgr.handheldSessions.values()) {
            AmplifiedTransponderSnailItem.setState(session.stack, AmplifiedTransponderSnailBlock.STATE_IDLE);
        }

        mgr.closeAllAudio();
    }

    private void closeAllAudio() {
        for (HandheldSession session : handheldSessions.values()) {
            session.pipeline.close();
        }
        handheldSessions.clear();

        for (PlacedSession session : placedSessions.values()) {
            session.closed = true;
            for (AudioPipeline pipeline : session.pipelines.values()) {
                pipeline.close();
            }
            session.pipelines.clear();
        }
        placedSessions.clear();

        AudioPipeline pipeline;
        while ((pipeline = drainingPipelines.poll()) != null) {
            pipeline.close();
        }
    }

    // =================== HANDHELD ===================

    /**
     * Start amplifying a player's voice. Server thread only.
     *
     * @return false if the voice channels could not be created
     */
    public boolean startHandheld(ServerPlayer player, ItemStack stack) {
        UUID playerId = player.getUUID();

        HandheldSession existing = handheldSessions.get(playerId);
        if (existing != null) {
            existing.stack = stack;
            AmplifiedTransponderSnailItem.setState(stack, AmplifiedTransponderSnailBlock.STATE_CALL);
            return true;
        }

        Vec3 look = player.getLookAngle();
        AudioPipeline pipeline = createPipeline(player.serverLevel(), playerId,
                player.getX(), player.getEyeY(), player.getZ(), look.x, look.y, look.z);
        if (pipeline == null) {
            return false;
        }

        handheldSessions.put(playerId, new HandheldSession(player, stack, pipeline));
        AmplifiedTransponderSnailItem.setState(stack, AmplifiedTransponderSnailBlock.STATE_CALL);
        return true;
    }

    /** Stop amplifying a player's voice and show the "sound" (deactivated) state. Server thread only. */
    public void stopHandheld(ServerPlayer player) {
        HandheldSession session = handheldSessions.remove(player.getUUID());
        if (session != null) {
            drain(session.pipeline);
            AmplifiedTransponderSnailItem.enterSoundState(session.stack, player.level().getGameTime());
        }
    }

    /** True if this exact stack is the one currently amplifying this player's voice. */
    public boolean isActiveHandheldStack(UUID playerId, ItemStack stack) {
        HandheldSession session = handheldSessions.get(playerId);
        return session != null && session.stack == stack;
    }

    // =================== PLACED ===================

    /**
     * Turn a placed snail on. Server thread only.
     *
     * @param facing the direction the snail's head (horn) faces - the broadcast direction
     */
    public boolean activatePlaced(ServerLevel level, BlockPos pos, Direction facing) {
        PlacedKey key = new PlacedKey(level.dimension(), pos.immutable());
        if (!placedSessions.containsKey(key)) {
            placedSessions.put(key, new PlacedSession(level, pos.immutable(), facing));
        }
        return true;
    }

    /** Turn a placed snail off (does NOT touch the blockstate - the block handles its own visuals). */
    public void deactivatePlaced(ServerLevel level, BlockPos pos) {
        PlacedSession session = placedSessions.remove(new PlacedKey(level.dimension(), pos.immutable()));
        if (session != null) {
            endPlacedSession(session);
        }
    }

    private void endPlacedSession(PlacedSession session) {
        session.closed = true;
        for (AudioPipeline pipeline : session.pipelines.values()) {
            drain(pipeline);
        }
        session.pipelines.clear();
    }

    // =================== AUDIO (VOICE THREAD) ===================

    /**
     * Called from TransponderSnailsPlugin for every microphone packet (Simple Voice Chat network thread).
     */
    public void onMicrophonePacket(MicrophonePacketEvent event) {
        if (handheldSessions.isEmpty() && placedSessions.isEmpty()) {
            return; // Fast path: nothing is amplifying
        }

        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || sender.getPlayer() == null) {
            return;
        }
        if (!(sender.getPlayer().getPlayer() instanceof ServerPlayer speaker)) {
            return;
        }

        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();

        // ---------- HANDHELD: only the holder ----------
        HandheldSession handheld = handheldSessions.get(speaker.getUUID());
        if (handheld != null) {
            Vec3 look = speaker.getLookAngle();
            handheld.pipeline.setSource(speaker.getX(), speaker.getEyeY(), speaker.getZ(), look.x, look.y, look.z);
            handheld.pipeline.send(opus, 1.0f, now);
            handheld.lastAudioMs = now;

            // Their normal voice doesn't play at all - everyone (them included) hears the megaphone
            event.cancel();
            return; // Not also picked up by placed snails
        }

        // ---------- PLACED: players in the 1 block ring ----------
        if (placedSessions.isEmpty() || speaker.isSpectator()) {
            return;
        }

        boolean pickedUp = false;
        for (PlacedSession session : placedSessions.values()) {
            if (session.closed || session.level != speaker.level()) {
                continue;
            }

            float gain = session.pickupGain(speaker);
            if (gain <= 0.0f) {
                continue;
            }

            AudioPipeline pipeline = session.pipelines.computeIfAbsent(speaker.getUUID(), id ->
                    createPipeline(session.level, id, session.cx, session.cy, session.cz,
                            session.forwardX, 0.0, session.forwardZ));
            if (pipeline == null) {
                continue;
            }
            if (session.closed) {
                // Snail was switched off while we were creating this pipeline
                pipeline.close();
                continue;
            }

            pipeline.send(opus, gain, now);
            session.lastAudioMs = now;
            pickedUp = true;
        }

        if (pickedUp) {
            // Same as handheld: only the megaphone version is heard
            event.cancel();
        }
    }

    /**
     * Creates a speaker's front + rear channels. Each speaker gets their OWN pipeline, because two voices
     * interleaved into one channel's stream corrupt each other on the client.
     */
    @Nullable
    private AudioPipeline createPipeline(ServerLevel level, UUID speakerId,
                                         double x, double y, double z,
                                         double forwardX, double forwardY, double forwardZ) {
        AudioPipeline pipeline = new AudioPipeline(speakerId);
        pipeline.setSource(x, y, z, forwardX, forwardY, forwardZ);

        LocationalAudioChannel front = createChannel(level, x, y, z,
                VoiceChatConstants.AMPLIFIED_SNAIL_BROADCAST_RANGE, listener -> pipeline.isInFront(listener));
        LocationalAudioChannel rear = createChannel(level, x, y, z,
                VoiceChatConstants.AMPLIFIED_SNAIL_REAR_RANGE, listener -> !pipeline.isInFront(listener));

        if (front == null || rear == null) {
            pipeline.close();
            return null;
        }

        pipeline.attachChannels(front, rear);
        return pipeline;
    }

    @Nullable
    private LocationalAudioChannel createChannel(ServerLevel level, double x, double y, double z, float distance,
                                                 java.util.function.Predicate<de.maxhenkel.voicechat.api.ServerPlayer> filter) {
        try {
            LocationalAudioChannel channel = api.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    api.fromServerLevel(level),
                    api.createPosition(x, y, z));
            if (channel == null) {
                return null;
            }
            channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
            channel.setDistance(distance);
            channel.setFilter(filter);
            return channel;
        } catch (Exception e) {
            System.err.println("AmplifiedSnailManager: Failed to create audio channel: " + e.getMessage());
            return null;
        }
    }

    /** Let a pipeline finish its echo/reverb tail, then close it (from the tick). */
    private void drain(AudioPipeline pipeline) {
        if (pipeline.beginDrain()) {
            drainingPipelines.add(pipeline);
        }
    }

    // =================== SERVER TICK (SERVER THREAD) ===================

    /** Called every server tick (END phase) from AmplifiedSnailServerEvents. */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        tickHandheld(server, now);
        tickPlaced(now);
        tickDraining(now);
    }

    private void tickHandheld(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, HandheldSession>> it = handheldSessions.entrySet().iterator();
        while (it.hasNext()) {
            HandheldSession session = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);

            // Stop if the player left, died/respawned (new entity), or stopped using the snail for any reason
            // (slot switch, item dropped, etc. - these don't always call releaseUsing()).
            boolean stillUsing = player != null
                    && player == session.player
                    && player.isAlive()
                    && player.isUsingItem()
                    && player.getUseItem().getItem() instanceof AmplifiedTransponderSnailItem;

            if (!stillUsing) {
                it.remove();
                drain(session.pipeline);
                if (player != null) {
                    AmplifiedTransponderSnailItem.enterSoundState(session.stack, player.level().getGameTime());
                } else {
                    AmplifiedTransponderSnailItem.setState(session.stack, AmplifiedTransponderSnailBlock.STATE_IDLE);
                }
                continue;
            }

            // Follow the live stack instance if it was replaced by a sync
            session.stack = player.getUseItem();

            // Keep the broadcast position/direction current even between packets (affects the tail)
            Vec3 look = player.getLookAngle();
            session.pipeline.setSource(player.getX(), player.getEyeY(), player.getZ(), look.x, look.y, look.z);

            int desired = (now - session.lastAudioMs) < ACTIVE_WINDOW_MS
                    ? AmplifiedTransponderSnailBlock.STATE_ACTIVE
                    : AmplifiedTransponderSnailBlock.STATE_CALL;
            if (AmplifiedTransponderSnailItem.getState(session.stack) != desired) {
                AmplifiedTransponderSnailItem.setState(session.stack, desired);
            }

            session.pipeline.pumpTail(now);
            session.pipeline.flushIfIdle(now);
        }
    }

    private void tickPlaced(long now) {
        List<PlacedKey> toEnd = new ArrayList<>();

        for (Map.Entry<PlacedKey, PlacedSession> entry : placedSessions.entrySet()) {
            PlacedSession session = entry.getValue();

            // Keep sessions alive while their chunk is unloaded; only validate the block when we can see it
            if (session.level.isLoaded(session.pos)) {
                BlockState state = session.level.getBlockState(session.pos);
                if (!(state.getBlock() instanceof AmplifiedTransponderSnailBlock)) {
                    toEnd.add(entry.getKey());
                    continue;
                }

                int current = state.getValue(AmplifiedTransponderSnailBlock.AMPLIFIER_STATE);
                if (current != AmplifiedTransponderSnailBlock.STATE_CALL
                        && current != AmplifiedTransponderSnailBlock.STATE_ACTIVE) {
                    // Switched off by something other than right-click (e.g. /setblock)
                    toEnd.add(entry.getKey());
                    continue;
                }

                int desired = (now - session.lastAudioMs) < ACTIVE_WINDOW_MS
                        ? AmplifiedTransponderSnailBlock.STATE_ACTIVE
                        : AmplifiedTransponderSnailBlock.STATE_CALL;
                if (current != desired) {
                    session.level.setBlock(session.pos,
                            state.setValue(AmplifiedTransponderSnailBlock.AMPLIFIER_STATE, desired), 3);
                }
            }

            Iterator<Map.Entry<UUID, AudioPipeline>> pit = session.pipelines.entrySet().iterator();
            while (pit.hasNext()) {
                AudioPipeline pipeline = pit.next().getValue();
                if (now - pipeline.lastSendMs > PIPELINE_IDLE_CLOSE_MS) {
                    pit.remove();
                    pipeline.close();
                } else {
                    pipeline.pumpTail(now);
                    pipeline.flushIfIdle(now);
                }
            }
        }

        for (PlacedKey key : toEnd) {
            PlacedSession session = placedSessions.remove(key);
            if (session != null) {
                endPlacedSession(session);
            }
        }
    }

    private void tickDraining(long now) {
        Iterator<AudioPipeline> it = drainingPipelines.iterator();
        while (it.hasNext()) {
            AudioPipeline pipeline = it.next();
            pipeline.pumpTail(now);
            if (pipeline.isTailFinished()) {
                it.remove();
                pipeline.close();
            }
        }
    }

    // =================== DATA STRUCTURES ===================

    private record PlacedKey(ResourceKey<Level> dimension, BlockPos pos) { }

    private static final class HandheldSession {
        final UUID playerId;
        final ServerPlayer player;
        volatile ItemStack stack;
        final AudioPipeline pipeline;
        volatile long lastAudioMs;

        HandheldSession(ServerPlayer player, ItemStack stack, AudioPipeline pipeline) {
            this.playerId = player.getUUID();
            this.player = player;
            this.stack = stack;
            this.pipeline = pipeline;
        }
    }

    private static final class PlacedSession {
        final ServerLevel level;
        final BlockPos pos;
        // Broadcast point (block center) - immutable so the voice thread can read it safely
        final double cx, cy, cz;
        // Direction the snail's head (horn) faces
        final double forwardX, forwardZ;
        final Map<UUID, AudioPipeline> pipelines = new ConcurrentHashMap<>();
        volatile long lastAudioMs;
        volatile boolean closed;

        PlacedSession(ServerLevel level, BlockPos pos, Direction facing) {
            this.level = level;
            this.pos = pos;
            this.cx = pos.getX() + 0.5;
            this.cy = pos.getY() + 0.5;
            this.cz = pos.getZ() + 0.5;
            this.forwardX = facing.getStepX();
            this.forwardZ = facing.getStepZ();
        }

        /**
         * Pickup zone: a 1 block ring around the snail (diagonals included), measured horizontally from
         * the block center. Full volume next to the snail, down to
         * {@link VoiceChatConstants#AMPLIFIED_SNAIL_PICKUP_EDGE_GAIN} at the edge of the ring.
         *
         * @return 0 when outside the zone, otherwise EDGE_GAIN..1
         */
        float pickupGain(ServerPlayer speaker) {
            double dy = speaker.getEyeY() - cy;
            if (Math.abs(dy) > VoiceChatConstants.AMPLIFIED_SNAIL_PICKUP_VERTICAL) {
                return 0.0f;
            }

            double dx = speaker.getX() - cx;
            double dz = speaker.getZ() - cz;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double radius = VoiceChatConstants.AMPLIFIED_SNAIL_PICKUP_RADIUS;
            if (horizontal > radius) {
                return 0.0f;
            }

            double edge = VoiceChatConstants.AMPLIFIED_SNAIL_PICKUP_EDGE_GAIN;
            return (float) (1.0 - (1.0 - edge) * (horizontal / radius));
        }
    }

    /**
     * One speaker -> front + rear channel pair. Owns its own Opus decoder/encoder and megaphone filter, so
     * simultaneous speakers (and one speaker picked up by several snails) never share codec/filter state.
     */
    private final class AudioPipeline {
        private final UUID speakerId;
        private LocationalAudioChannel front;
        private LocationalAudioChannel rear;
        private final OpusDecoder decoder;
        private final OpusEncoder encoder;
        private final MegaphoneAudioFilter filter = new MegaphoneAudioFilter();

        // Source position + unit forward vector. Read by the channel filters on the voice thread.
        private volatile double sx, sy, sz, fx, fy, fz;

        private volatile long lastSendMs = System.currentTimeMillis();
        private long lastRealAudioMs = 0;
        private long nextTailMs = 0;
        private int tailFramesLeft = 0;
        private boolean flushed = true;
        private boolean draining = false;
        private boolean closed = false;

        AudioPipeline(UUID speakerId) {
            this.speakerId = speakerId;
            this.decoder = api.createDecoder();
            this.encoder = api.createEncoder();
        }

        void attachChannels(LocationalAudioChannel front, LocationalAudioChannel rear) {
            this.front = front;
            this.rear = rear;
        }

        void setSource(double x, double y, double z, double forwardX, double forwardY, double forwardZ) {
            double len = Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ);
            if (len < 1.0E-6) {
                forwardX = 0;
                forwardY = 0;
                forwardZ = 1;
                len = 1;
            }
            this.sx = x;
            this.sy = y;
            this.sz = z;
            this.fx = forwardX / len;
            this.fy = forwardY / len;
            this.fz = forwardZ / len;
        }

        /**
         * True if the listener should get the full-range FRONT channel.
         * The speaker always does (they hear their own megaphone), as does anyone standing right at the source.
         */
        boolean isInFront(de.maxhenkel.voicechat.api.ServerPlayer listener) {
            if (listener.getUuid().equals(speakerId)) {
                return true;
            }

            double lx, ly, lz;
            if (listener.getPlayer() instanceof ServerPlayer mcListener) {
                lx = mcListener.getX();
                ly = mcListener.getEyeY();
                lz = mcListener.getZ();
            } else {
                Position position = listener.getPosition();
                lx = position.getX();
                ly = position.getY() + 1.62;
                lz = position.getZ();
            }

            double vx = lx - sx, vy = ly - sy, vz = lz - sz;
            double distance = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (distance < ALWAYS_FRONT_DISTANCE) {
                return true;
            }
            return (vx * fx + vy * fy + vz * fz) / distance >= FRONT_CONE_COS;
        }

        /** Voice thread: process and broadcast one real microphone frame. */
        synchronized void send(byte[] opus, float gain, long now) {
            if (closed || draining) {
                return;
            }
            try {
                short[] pcm = decoder.decode(opus);
                if (pcm == null || pcm.length == 0) {
                    return;
                }

                if (gain < 0.999f) {
                    for (int i = 0; i < pcm.length; i++) {
                        pcm[i] = (short) Math.round(pcm[i] * gain);
                    }
                }

                // Megaphone filter is ALWAYS applied to amplified audio
                filter.process(pcm);

                broadcast(encoder.encode(pcm), now);

                // Speech resumed: (re)arm the tail
                lastRealAudioMs = now;
                nextTailMs = 0;
                tailFramesLeft = MAX_TAIL_FRAMES;
            } catch (Exception e) {
                System.err.println("AmplifiedSnailManager: Error processing audio: " + e.getMessage());
            }
        }

        /**
         * Server thread: once speech has paused, render the echo/reverb tail by feeding silence through the
         * filter, one 20ms frame per 20ms of real time (catching up in small bursts each tick).
         */
        synchronized void pumpTail(long now) {
            if (closed || tailFramesLeft <= 0 || now - lastRealAudioMs < TAIL_START_AFTER_MS) {
                return;
            }
            if (nextTailMs == 0) {
                nextTailMs = lastRealAudioMs + FRAME_MS;
            }
            try {
                while (tailFramesLeft > 0 && nextTailMs <= now) {
                    short[] pcm = filter.process(new short[FRAME_SIZE]);
                    tailFramesLeft--;
                    nextTailMs += FRAME_MS;

                    if (peak(pcm) < TAIL_SILENCE_PEAK) {
                        tailFramesLeft = 0; // Tail has died away
                        break;
                    }
                    broadcast(encoder.encode(pcm), now);
                }
            } catch (Exception e) {
                tailFramesLeft = 0;
                System.err.println("AmplifiedSnailManager: Error rendering audio tail: " + e.getMessage());
            }
        }

        private void broadcast(byte[] out, long now) {
            if (out == null || out.length == 0 || front == null || rear == null) {
                return;
            }
            Position position = api.createPosition(sx, sy, sz);
            front.updateLocation(position);
            rear.updateLocation(position);
            front.send(out);
            rear.send(out);
            lastSendMs = now;
            flushed = false;
        }

        synchronized void flushIfIdle(long now) {
            if (!closed && !flushed && tailFramesLeft <= 0 && now - lastSendMs > FLUSH_AFTER_MS) {
                flushChannels();
            }
        }

        /** @return true if the pipeline should be queued for draining */
        synchronized boolean beginDrain() {
            if (closed || draining) {
                return false;
            }
            draining = true;
            return true;
        }

        synchronized boolean isTailFinished() {
            return closed || tailFramesLeft <= 0;
        }

        private void flushChannels() {
            flushed = true;
            try {
                if (front != null) front.flush();
                if (rear != null) rear.flush();
            } catch (Exception ignored) {
            }
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            tailFramesLeft = 0;
            if (!flushed) {
                flushChannels();
            }
            try {
                decoder.close();
            } catch (Exception ignored) {
            }
            try {
                encoder.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static int peak(short[] pcm) {
        int max = 0;
        for (short s : pcm) {
            int abs = Math.abs(s);
            if (abs > max) {
                max = abs;
            }
        }
        return max;
    }
}