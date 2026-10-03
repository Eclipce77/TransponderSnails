package net.eclipce.transpondersnails.visual.server;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One call between two Visual Transponder Snails. Snail numbers are not involved: the two ends are identified by
 * their block positions (same dimension).
 *
 * State changes happen on the server thread. The audio thread (Simple Voice Chat microphone packets) only touches
 * the volatile / atomic fields (channels, audio timestamps, error counter, pending failure).
 */
public final class VisualCallSession {

    public enum State {
        /** Caller is waiting, callee snail is ringing. */
        RINGING,
        /** Answered. Audio channels exist, waiting for the video feed to come up on BOTH ends. No audio flows yet. */
        CONNECTING,
        /** Audio and video are up on both ends. */
        CONNECTED
    }

    public enum EndReason { HANG_UP, CANCELLED, REJECTED, NO_ANSWER, LOST, FAILED, SERVER_STOP }

    /** A player who was told to render the feed for one screen. */
    public record ViewerKey(UUID player, BlockPos screen) {}

    private final UUID callId;
    private final ResourceKey<Level> dimension;
    private final BlockPos callerPos;
    private final BlockPos calleePos;
    private final long createdTick;

    private volatile State state = State.RINGING;
    private volatile long connectingSinceTick;

    // channel that PLAYS AT the given snail (i.e. what the other end says comes out of this one)
    private volatile AudioChannel callerChannel;
    private volatile AudioChannel calleeChannel;

    private final Set<BlockPos> readyScreens = ConcurrentHashMap.newKeySet();
    private final Set<ViewerKey> viewers = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final AtomicInteger audioErrors = new AtomicInteger(0);
    private volatile long lastAudioAtCallerMs = 0L;
    private volatile long lastAudioAtCalleeMs = 0L;
    private volatile String pendingFailure;

    public VisualCallSession(UUID callId, ResourceKey<Level> dimension, BlockPos callerPos, BlockPos calleePos, long createdTick) {
        this.callId = callId;
        this.dimension = dimension;
        this.callerPos = callerPos.immutable();
        this.calleePos = calleePos.immutable();
        this.createdTick = createdTick;
    }

    // ---------------- identity ----------------

    public UUID callId() { return callId; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos callerPos() { return callerPos; }
    public BlockPos calleePos() { return calleePos; }
    public GlobalPos callerGp() { return GlobalPos.of(dimension, callerPos); }
    public GlobalPos calleeGp() { return GlobalPos.of(dimension, calleePos); }
    public long createdTick() { return createdTick; }

    public boolean isCaller(BlockPos pos) { return callerPos.equals(pos); }
    public boolean isCallee(BlockPos pos) { return calleePos.equals(pos); }
    public boolean involves(BlockPos pos) { return isCaller(pos) || isCallee(pos); }

    /** The other end of the call (the snail whose eyes this snail's screen shows). */
    public BlockPos other(BlockPos pos) { return isCaller(pos) ? calleePos : callerPos; }

    // ---------------- state ----------------

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public long connectingSinceTick() { return connectingSinceTick; }
    public void setConnectingSinceTick(long tick) { this.connectingSinceTick = tick; }

    /** @return true exactly once - for the caller that gets to tear the call down */
    public boolean markEnded() { return ended.compareAndSet(false, true); }
    public boolean isEnded() { return ended.get(); }

    // ---------------- audio ----------------

    public void setChannels(AudioChannel atCaller, AudioChannel atCallee) {
        this.callerChannel = atCaller;
        this.calleeChannel = atCallee;
    }

    @Nullable
    public AudioChannel channelAt(BlockPos pos) {
        return isCaller(pos) ? callerChannel : calleeChannel;
    }

    public void clearAudio() {
        callerChannel = null;
        calleeChannel = null;
    }

    /** Called from the audio thread when audio was delivered to the snail at pos. */
    public void markAudioAt(BlockPos pos) {
        long now = System.currentTimeMillis();
        if (isCaller(pos)) lastAudioAtCallerMs = now; else lastAudioAtCalleeMs = now;
        audioErrors.set(0);
    }

    public long lastAudioAt(BlockPos pos) {
        return isCaller(pos) ? lastAudioAtCallerMs : lastAudioAtCalleeMs;
    }

    /** @return consecutive send errors including this one */
    public int recordAudioError() { return audioErrors.incrementAndGet(); }

    /** Thread-safe: may be called from the audio thread; the server tick picks it up and ends the call. */
    public void flagFailure(String reason) {
        if (pendingFailure == null) pendingFailure = reason;
    }

    @Nullable
    public String takeFailure() {
        String f = pendingFailure;
        pendingFailure = null;
        return f;
    }

    // ---------------- video handshake ----------------

    public void markScreenReady(BlockPos screen) { readyScreens.add(screen); }
    public boolean allScreensReady() { return readyScreens.contains(callerPos) && readyScreens.contains(calleePos); }

    /** @return true if this (player, screen) pair was newly added, i.e. the player still has to be sent START */
    public boolean addViewer(UUID player, BlockPos screen) { return viewers.add(new ViewerKey(player, screen.immutable())); }
    public Set<ViewerKey> viewerKeys() { return new HashSet<>(viewers); }
    public void removeViewersOf(UUID player) { viewers.removeIf(k -> k.player().equals(player)); }
}
