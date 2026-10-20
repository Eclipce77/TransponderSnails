package net.eclipce.transpondersnails.visual.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.entity.BlackTransponderSnailBlockEntity;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.sound.ModSounds;
import net.eclipce.transpondersnails.visual.InterceptionRules;
import net.eclipce.transpondersnails.visual.InterceptionRules.Kind;
import net.eclipce.transpondersnails.visual.InterceptionRules.Signal;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualCallState;
import net.eclipce.transpondersnails.visual.network.VisualFeedControlPacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.eclipce.transpondersnails.voice.server.BlackSnailStateSyncHelper;
import net.eclipce.transpondersnails.voice.server.InterceptionHelper;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.eclipce.transpondersnails.voice.server.WhiteSnailProtectionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepting VISUAL signals with a Black Transponder Snail. It follows the phone snail's rules - the player opens the black snail (a click),
 * it searches for five seconds for a signal within its range (lightning rods extend it), the interception ends when the player closes it, the
 * signal ends, or it is out of range for five seconds - and adds what a visual snail can do:
 *
 * <ul>
 *   <li><b>Only a placed black snail that is WIRED to a Visual Transponder Snail with a Transmitter</b> (see VisualTapManager) intercepts visual
 *       signals. That snail is its display: it projects what is intercepted - a stream, or BOTH ends of a call split down the middle.</li>
 *   <li><b>White Transponder Snail protection is per end</b>, as for phone calls: a protected end's half shows looping static and its sound is
 *       not passed on (the interceptor hears the static instead).</li>
 *   <li>The sound of the signal goes to the player (as phone interception does); the picture goes to the display, where anyone near sees it.</li>
 *   <li>A nearer visual signal than any phone call takes over; otherwise the phone logic runs as before.</li>
 * </ul>
 *
 * Server thread, except routeCallAudio / routeStreamAudio / hasStreamTaps / tappedStreamCameras, which the voice chat thread calls: they only read
 * fields that are volatile or immutable snapshots.
 */
public final class VisualInterceptionManager {

    private VisualInterceptionManager() {}

    private static final long SEARCH_MS = 5000L;
    private static final long OUT_OF_RANGE_GRACE_MS = 5000L;
    private static final long AUDIO_WINDOW_MS = 500L;
    /** The player must stay this near the black snail they opened (blocks). */
    private static final double PLAYER_REACH = 16.0;
    private static final int DISPLAY_REFRESH_TICKS = 10;
    private static final int SLOW_TICKS = 20;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private enum Phase { SEARCHING, ACTIVE }

    private static final class Session {
        final UUID player;
        final GlobalPos black;
        final GlobalPos display;
        final double range;
        final Set<String> tapped = new HashSet<>();       // signals already tapped (switching offers the others first)
        final UUID displayId = UUID.randomUUID();         // identifies the display's links on the clients
        final Set<UUID> viewers = new HashSet<>();        // players near the display that were told to show it
        final Set<BlockPos> blockedWhite = new HashSet<>();

        volatile Phase phase = Phase.SEARCHING;
        volatile Kind kind;
        volatile String targetId;
        long connectAtMs;
        // what is shown (set when it connects)
        BlockPos cam1;
        @Nullable BlockPos cam2;
        volatile GlobalPos targetCamera;                  // a stream's camera
        volatile boolean static0;                         // protected ends: their half is static, their sound is not passed on
        volatile boolean static1;
        // sound
        @Nullable volatile LocationalAudioChannel channel;
        volatile long lastAudioMs;
        boolean reportedActive;
        long outOfRangeSince = -1L;

        Session(UUID player, GlobalPos black, GlobalPos display, double range) {
            this.player = player;
            this.black = black;
            this.display = display;
            this.range = range;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();   // by the player who opened the black snail
    private static volatile List<GlobalPos> streamTaps = List.of();
    private static int tickCounter = 0;

    // =====================================================================================================
    // For the voice chat thread
    // =====================================================================================================

    public static boolean hasStreamTaps() {
        return !streamTaps.isEmpty();
    }

    /** The cameras of the streams that are being tapped right now. */
    public static List<GlobalPos> tappedStreamCameras() {
        return streamTaps;
    }

    private static void rebuildStreamTaps() {
        List<GlobalPos> list = new ArrayList<>();
        for (Session s : SESSIONS.values()) {
            if (s.phase == Phase.ACTIVE && s.kind == Kind.STREAM && s.targetCamera != null) list.add(s.targetCamera);
        }
        streamTaps = list.isEmpty() ? List.of() : Collections.unmodifiableList(list);
    }

    /** One audio frame of a speaker at one end of a visual call: to whoever is tapping the call - unless that end is protected. */
    public static void routeCallAudio(VisualCallSession call, BlockPos speakerEnd, byte[] audio) {
        if (SESSIONS.isEmpty()) return;
        String id = call.callId().toString();
        boolean first = speakerEnd.equals(call.callerPos());
        for (Session s : SESSIONS.values()) {
            if (s.phase != Phase.ACTIVE || s.kind != Kind.CALL || !id.equals(s.targetId)) continue;
            if (first ? s.static0 : s.static1) continue;   // WHITE SNAIL PROTECTION: this end is protected, the tapper only hears static
            send(s, audio);
        }
    }

    /** One audio frame of a speaker near a streaming camera: to whoever is tapping the stream. */
    public static void routeStreamAudio(GlobalPos camera, byte[] audio) {
        if (SESSIONS.isEmpty()) return;
        for (Session s : SESSIONS.values()) {
            if (s.phase == Phase.ACTIVE && s.kind == Kind.STREAM && camera.equals(s.targetCamera)) send(s, audio);
        }
    }

    private static void send(Session s, byte[] audio) {
        LocationalAudioChannel channel = s.channel;
        if (channel == null) return;
        try {
            channel.send(audio);
            s.lastAudioMs = System.currentTimeMillis();
        } catch (Exception ignored) {
            // a failed frame is just lost; the interception itself is not affected
        }
    }

    /** May the relay show this player the picture of this camera? Only if it is what a display near them is showing (and not a protected end). */
    public static boolean isViewerOfCamera(UUID player, GlobalPos camera) {
        for (Session s : SESSIONS.values()) {
            if (s.phase != Phase.ACTIVE || !s.viewers.contains(player) || !s.display.dimension().equals(camera.dimension())) continue;
            if (s.kind == Kind.STREAM) {
                if (camera.equals(s.targetCamera)) return true;
            } else {
                if (!s.static0 && camera.pos().equals(s.cam1)) return true;
                if (!s.static1 && s.cam2 != null && camera.pos().equals(s.cam2)) return true;
            }
        }
        return false;
    }

    // =====================================================================================================
    // Opening and closing the black snail
    // =====================================================================================================

    private static void msg(ServerPlayer player, String text, ChatFormatting color) {
        player.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    private static double distance(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    /** The visual signals a black snail at {@code black} could pick up: the calls and streams in this dimension, with how near they are. */
    private static List<Signal> collectSignals(ServerLevel level, BlockPos black, UUID interceptor) {
        List<Signal> out = new ArrayList<>();
        VisualCallManager calls = VisualCallManager.peek();
        if (calls != null) {
            for (VisualCallSession s : calls.callsSnapshot()) {
                if (s.isStream() || s.isEnded() || s.getState() != VisualCallSession.State.CONNECTED || !s.dimension().equals(level.dimension())) continue;
                boolean isParty = false;
                for (VisualCallSession.ViewerKey key : s.viewerKeys()) if (key.player().equals(interceptor)) isParty = true;
                if (isParty) continue;   // you cannot tap a call you are part of
                out.add(new Signal(Kind.CALL, s.callId().toString(), Math.min(distance(black, s.callerPos()), distance(black, s.calleePos()))));
            }
        }
        for (VisualStreamManager.Info info : VisualStreamManager.activeStreams(level)) {
            out.add(new Signal(Kind.STREAM, info.id().toString(), distance(black, info.pos().pos())));
        }
        return out;
    }

    /**
     * The player opened a Black Transponder Snail block. If it is wired to a visual snail with a transmitter and a visual signal is in range - and
     * nearer than any phone call - it starts searching for it.
     *
     * @return true if it took over (the phone search is then not started); false: nothing for it to do, the phone logic runs as before
     */
    public static boolean onBlackOpened(ServerPlayer player, BlockPos blackPos, int lightningRodCount) {
        ServerLevel level = player.serverLevel();
        if (SESSIONS.containsKey(player.getUUID())) return false;
        if (!(level.getBlockEntity(blackPos) instanceof BlackTransponderSnailBlockEntity)) return false;
        BlockPos displayPos = VisualTapManager.findDisplay(level, blackPos);
        VisualCallManager calls = VisualCallManager.peek();
        if (displayPos == null || calls == null) return false;
        TransponderCallManager phone = TransponderSnails.getCallManager();
        if (phone != null && phone.isInCall(player.getUUID())) return false;          // the phone logic says so
        GlobalPos displayGp = GlobalPos.of(level.dimension(), displayPos);
        for (Session other : SESSIONS.values()) if (other.display.equals(displayGp)) return false;   // one tap at a time per display
        if (VisualSnailRegistry.get(displayGp) == null) return false;

        double range = InterceptionHelper.blockRange(lightningRodCount);
        Signal nearest = InterceptionRules.nearest(collectSignals(level, blackPos, player.getUUID()), range);
        if (nearest == null) return false;
        double phoneDistance = phone == null ? -1.0 : InterceptionHelper.nearestPhoneCallDistance(player, phone, range);
        if (InterceptionRules.choose(phoneDistance, nearest.distance()) != InterceptionRules.Source.VISUAL) return false;

        Session s = new Session(player.getUUID(), GlobalPos.of(level.dimension(), blackPos), displayGp, range);
        s.kind = nearest.kind();
        s.targetId = nearest.id();
        s.connectAtMs = System.currentTimeMillis() + SEARCH_MS;
        SESSIONS.put(player.getUUID(), s);
        msg(player, "⟳ Searching for call...", ChatFormatting.YELLOW);
        BlackSnailStateSyncHelper.syncSearching(player);
        return true;
    }

    /** The player closed the black snail. @return true if a visual interception (or search) was stopped */
    public static boolean onBlackClosed(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return false;
        stop(s, s.phase == Phase.ACTIVE ? "○ Disconnected from call" : "Search cancelled", ChatFormatting.GRAY);
        return true;
    }

    public static boolean hasSession(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** The black snail block was broken or removed. */
    public static void stopAtBlack(GlobalPos black) {
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (s.black.equals(black)) stop(s, "○ Disconnected from call", ChatFormatting.GRAY);
        }
    }

    public static void onPlayerLeft(UUID player) {
        Session own = SESSIONS.get(player);
        if (own != null) stop(own, null, ChatFormatting.GRAY);
        for (Session s : SESSIONS.values()) s.viewers.remove(player);
    }

    /**
     * Crouch + right click on an open black snail that is tapping: switch to the next signal in range (the nearest one not yet tapped, then
     * round again). It searches again, as for phone calls.
     *
     * @return true if the click was used for this
     */
    public static boolean switchNext(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.phase != Phase.ACTIVE) return false;
        ServerLevel level = player.serverLevel();
        Signal next = InterceptionRules.next(collectSignals(level, s.black.pos(), s.player), s.range, s.targetId, s.tapped);
        if (next == null) {
            msg(player, "No other signal in range", ChatFormatting.GRAY);
            return true;
        }
        stopShowing(s, level);
        s.kind = next.kind();
        s.targetId = next.id();
        s.phase = Phase.SEARCHING;
        s.connectAtMs = System.currentTimeMillis() + SEARCH_MS;
        setBlackIntercepting(s, level, false);
        msg(player, "⟳ Searching for call...", ChatFormatting.YELLOW);
        BlackSnailStateSyncHelper.syncSearching(player);
        rebuildStreamTaps();
        return true;
    }

    // =====================================================================================================
    // Every tick
    // =====================================================================================================

    public static void tick(@Nullable MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        boolean slow = tickCounter % SLOW_TICKS == 0;
        if (slow) {
            for (ServerLevel level : server.getAllLevels()) VisualTapManager.tickProtection(level);
        }
        if (SESSIONS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            ServerLevel level = server.getLevel(s.black.dimension());
            if (player == null || level == null || !player.level().dimension().equals(s.black.dimension())
                    || !(level.getBlockEntity(s.black.pos()) instanceof BlackTransponderSnailBlockEntity blackBE) || !blackBE.isOpen()
                    || player.distanceToSqr(s.black.pos().getX() + 0.5, s.black.pos().getY() + 0.5, s.black.pos().getZ() + 0.5) > PLAYER_REACH * PLAYER_REACH) {
                stop(s, null, ChatFormatting.GRAY);    // closed, gone, or walked away: the black snail's own handling says the rest
                continue;
            }
            if (slow && !s.display.pos().equals(VisualTapManager.findDisplay(level, s.black.pos()))) {
                stop(s, "✗ Connection lost", ChatFormatting.RED);   // the wire to the display was cut
                continue;
            }

            if (s.phase == Phase.SEARCHING) {
                if (now >= s.connectAtMs) connect(s, player, level);
                continue;
            }
            tickActive(s, player, level, blackBE, now, slow);
        }
    }

    private static void tickActive(Session s, ServerPlayer player, ServerLevel level, BlackTransponderSnailBlockEntity blackBE, long now, boolean slow) {
        // is the signal still there, and in range?
        Signal live = findSignal(level, s);
        if (live == null) {
            stop(s, s.kind == Kind.CALL ? "Call ended" : "Stream ended", ChatFormatting.GRAY);
            return;
        }
        if (live.distance() > s.range) {
            if (s.outOfRangeSince < 0L) s.outOfRangeSince = now;
            else if (now - s.outOfRangeSince > OUT_OF_RANGE_GRACE_MS) {
                stop(s, "✗ Connection lost - too far", ChatFormatting.RED);
                return;
            }
        } else {
            s.outOfRangeSince = -1L;
        }

        // the sound: the black snail's model and the player's client show whether anything is being heard
        boolean hearing = now - s.lastAudioMs < AUDIO_WINDOW_MS;
        if (hearing) blackBE.markAudioActivity();
        if (hearing != s.reportedActive) {
            s.reportedActive = hearing;
            if (hearing) BlackSnailStateSyncHelper.syncActive(player); else BlackSnailStateSyncHelper.syncIntercepting(player);
        }
        LocationalAudioChannel channel = s.channel;
        if (slow && channel != null) {
            VoicechatServerApi api = apiOrNull();
            if (api != null) channel.updateLocation(api.createPosition(player.getX(), player.getY() + 1.5, player.getZ()));
        }

        if (tickCounter % DISPLAY_REFRESH_TICKS == 0) {
            if (s.kind == Kind.CALL) refreshProtection(s, level, player);
            refreshViewers(s, level, false);
        }
    }

    @Nullable
    private static VoicechatServerApi apiOrNull() {
        VisualCallManager calls = VisualCallManager.peek();
        return calls == null ? null : calls.voiceApi();
    }

    /** The signal this session is tapping, as it is now (null if it is gone). */
    @Nullable
    private static Signal findSignal(ServerLevel level, Session s) {
        for (Signal signal : collectSignals(level, s.black.pos(), s.player)) {
            if (signal.kind() == s.kind && signal.id().equals(s.targetId)) return signal;
        }
        return null;
    }

    // =====================================================================================================
    // Connecting, showing, stopping
    // =====================================================================================================

    private static void connect(Session s, ServerPlayer player, ServerLevel level) {
        Signal signal = findSignal(level, s);
        VisualCallManager calls = VisualCallManager.peek();
        TransponderSnailBlockEntity display = VisualSnailRegistry.get(s.display);
        if (signal == null || calls == null || display == null || signal.distance() > s.range) {
            msg(player, "✗ Call ended before connection", ChatFormatting.GRAY);
            stop(s, null, ChatFormatting.GRAY);
            return;
        }
        if (s.kind == Kind.CALL) {
            VisualCallSession call = calls.sessionById(UUID.fromString(s.targetId));
            if (call == null || call.isEnded()) {
                msg(player, "✗ Call ended before connection", ChatFormatting.GRAY);
                stop(s, null, ChatFormatting.GRAY);
                return;
            }
            s.cam1 = call.callerPos();
            s.cam2 = call.calleePos();
            s.targetCamera = null;
            s.static0 = VisualTapManager.isProtected(level, call.callerPos());
            s.static1 = VisualTapManager.isProtected(level, call.calleePos());
            LOGGER.info("VisualInterceptionManager: tapping the call between {} and {}: the ends are {} / {}", call.callerPos(), call.calleePos(),
                    s.static0 ? "protected (static)" : "open", s.static1 ? "protected (static)" : "open");
        } else {
            VisualStreamManager.Info found = null;
            for (VisualStreamManager.Info info : VisualStreamManager.activeStreams(level)) if (info.id().toString().equals(s.targetId)) found = info;
            if (found == null) {
                msg(player, "✗ Call ended before connection", ChatFormatting.GRAY);
                stop(s, null, ChatFormatting.GRAY);
                return;
            }
            s.cam1 = found.pos().pos();
            s.cam2 = null;
            s.targetCamera = found.pos();
            s.static0 = false;
            s.static1 = false;
        }

        VoicechatServerApi api = calls.voiceApi();
        if (api != null) {   // the sound goes to the player, as phone interception does
            try {
                LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(level),
                        api.createPosition(player.getX(), player.getY() + 1.5, player.getZ()));
                if (channel != null) {
                    channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
                    channel.setDistance((float) s.range);
                    s.channel = channel;
                }
            } catch (Exception e) {
                // no sound, but the picture still works
            }
        }
        TransponderCallManager phone = TransponderSnails.getCallManager();
        if (phone != null && phone.getSoundManager() != null) phone.getSoundManager().startLoopingStaticForPlayer(player);

        s.phase = Phase.ACTIVE;
        s.tapped.add(s.targetId);
        s.outOfRangeSince = -1L;
        s.lastAudioMs = 0L;
        s.reportedActive = false;
        player.playSound(ModSounds.SNAIL_CONNECTED.get(), 0.3f, 1.0f);
        msg(player, s.kind == Kind.CALL ? "✓ Connected to call" : "✓ Connected to stream", ChatFormatting.GREEN);
        BlackSnailStateSyncHelper.syncIntercepting(player);
        setBlackIntercepting(s, level, true);

        display.setVisualCallState(VisualCallState.CONNECTED);      // the display turns on
        refreshViewers(s, level, true);
        if (s.kind == Kind.CALL) refreshProtection(s, level, player);
        rebuildStreamTaps();
    }

    private static void setBlackIntercepting(Session s, ServerLevel level, boolean on) {
        if (level.getBlockEntity(s.black.pos()) instanceof BlackTransponderSnailBlockEntity be) be.setVisualIntercepting(on);
    }

    /** White snail protection is per end: a protected end's half goes static, and the white snail shows that it is blocking. */
    private static void refreshProtection(Session s, ServerLevel level, ServerPlayer player) {
        VisualCallSession call = VisualCallManager.peek() == null ? null : VisualCallManager.peek().sessionById(UUID.fromString(s.targetId));
        if (call == null) return;
        BlockPos white0 = VisualTapManager.protectingWhite(level, call.callerPos());
        BlockPos white1 = VisualTapManager.protectingWhite(level, call.calleePos());
        boolean p0 = white0 != null;
        boolean p1 = white1 != null;
        WhiteSnailProtectionManager white = WhiteSnailProtectionManager.getInstance();
        for (BlockPos whitePos : new BlockPos[]{white0, white1}) {
            if (whitePos != null && s.blockedWhite.add(whitePos)) white.onInterceptionBlocked(level, whitePos, s.player);
        }
        // a white snail that no longer protects either end is not blocking any more
        for (BlockPos whitePos : new ArrayList<>(s.blockedWhite)) {
            if (!whitePos.equals(white0) && !whitePos.equals(white1)) {
                s.blockedWhite.remove(whitePos);
                white.onInterceptionEnded(level, whitePos, s.player);
            }
        }
        if (p0 != s.static0 || p1 != s.static1) {
            s.static0 = p0;
            s.static1 = p1;
            LOGGER.info("VisualInterceptionManager: the call's two ends are now {} / {} (static = protected by a White Transponder Snail)",
                    p0 ? "static" : "picture", p1 ? "static" : "picture");
            refreshViewers(s, level, true);     // the halves change in place on the clients; the picture stays
        }
    }

    private static void sendControl(ServerPlayer viewer, Session s, boolean start) {
        VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new VisualFeedControlPacket(s.displayId, s.display.pos(), s.cam1, start, true, s.cam2, s.static0, s.static1, true));
    }

    /** Players near the display are told to show it; players who walked away, to stop. {@code resend}: tell everybody again (the halves changed). */
    private static void refreshViewers(Session s, ServerLevel level, boolean resend) {
        if (s.phase != Phase.ACTIVE || s.cam1 == null) return;
        BlockPos screen = s.display.pos();
        double[] center = VisualCallManager.screenCenter(level, screen);
        double r2 = VisualCallConstants.VIDEO_TRACK_RANGE * VisualCallConstants.VIDEO_TRACK_RANGE;
        Set<UUID> near = new HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (!VisualCallManager.isNear(p, screen, center, r2)) continue;
            near.add(p.getUUID());
            if (resend || !s.viewers.contains(p.getUUID())) sendControl(p, s, true);
        }
        for (UUID old : s.viewers) {
            if (near.contains(old)) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(old);
            if (p != null) sendControl(p, s, false);
        }
        s.viewers.clear();
        s.viewers.addAll(near);
    }

    /** Turns the display off and stops what shows on it (the interception goes on - or searches again). */
    private static void stopShowing(Session s, ServerLevel level) {
        if (s.cam1 != null) {
            for (UUID id : s.viewers) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) sendControl(p, s, false);
            }
        }
        s.viewers.clear();
        s.phase = Phase.SEARCHING;   // nothing is shown now; isViewerOfCamera and the audio routing look at the phase
        TransponderSnailBlockEntity display = VisualSnailRegistry.get(s.display);
        if (display != null) {
            display.setVisualCallState(VisualCallState.IDLE);
            display.setVisualAudioActive(false);
        }
        WhiteSnailProtectionManager white = WhiteSnailProtectionManager.getInstance();
        for (BlockPos whitePos : s.blockedWhite) white.onInterceptionEnded(level, whitePos, s.player);
        s.blockedWhite.clear();
        s.channel = null;
        TransponderCallManager phone = TransponderSnails.getCallManager();
        MinecraftServer server = level.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(s.player);
        if (phone != null && phone.getSoundManager() != null && player != null) phone.getSoundManager().stopLoopingStaticForPlayer(player);
    }

    private static void stop(Session s, @Nullable String message, ChatFormatting color) {
        SESSIONS.remove(s.player);
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerLevel level = server.getLevel(s.black.dimension());
            if (level != null) {
                stopShowing(s, level);
                setBlackIntercepting(s, level, false);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            if (player != null) {
                if (message != null) msg(player, message, color);
                BlackSnailStateSyncHelper.syncIdle(player);
            }
        }
        rebuildStreamTaps();
    }

    public static void clear() {
        for (Session s : new ArrayList<>(SESSIONS.values())) stop(s, null, ChatFormatting.GRAY);
        SESSIONS.clear();
        streamTaps = List.of();
        tickCounter = 0;
        VisualTapManager.clear();
    }
}
