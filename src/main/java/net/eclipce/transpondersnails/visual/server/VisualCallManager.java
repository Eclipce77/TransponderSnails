package net.eclipce.transpondersnails.visual.server;

import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.ProjectorPlacement;
import net.eclipce.transpondersnails.visual.ProjectorSurface;
import net.eclipce.transpondersnails.visual.ScreenLayout;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualRoles;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.eclipce.transpondersnails.visual.VisualSnailRole;
import net.eclipce.transpondersnails.visual.VisualCallState;
import net.eclipce.transpondersnails.visual.network.VisualFeedControlPacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.visual.server.VisualCallSession.EndReason;
import net.eclipce.transpondersnails.visual.server.VisualCallSession.State;
import net.eclipce.transpondersnails.visual.server.VisualCallSession.ViewerKey;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.eclipce.transpondersnails.voice.server.CallSoundManager;
import net.eclipce.transpondersnails.voice.server.SnailAudioRelay;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Video calls between Visual Transponder Snails.
 *
 * - No snail numbers: when a call is started the nearest idle Visual Snail within
 *   {@link VisualCallConstants#CALL_RANGE} blocks (same dimension) is called automatically.
 * - Audio uses the mod's existing pipeline: locational Simple Voice Chat channels at the snail blocks (same category,
 *   same range as a placed snail), the shared phone filter / distance falloff in {@link SnailAudioRelay}, and the same
 *   CallSoundManager sounds (ring, pick up, connected, disconnected, busy, hang up).
 * - Video is rendered on the viewing clients (see the client package). The server coordinates the handshake.
 * - Either half failing (audio channel creation / sending, video start / render error) FAILS the call. There is no
 *   audio-only fallback.
 *
 * Everything except {@link #handleMicrophonePacket} runs on the server thread.
 */
public final class VisualCallManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile VisualCallManager instance;

    /** Lazily bound to the current TransponderCallManager (which owns the Simple Voice Chat API). Null until it exists. */
    @Nullable
    public static VisualCallManager get() {
        TransponderCallManager cm = TransponderSnails.getCallManager();
        if (cm == null) return null;
        VisualCallManager current = instance;
        if (current != null && current.callManager == cm) return current;
        synchronized (VisualCallManager.class) {
            current = instance;
            if (current == null || current.callManager != cm) {
                if (current != null) current.shutdown();
                current = new VisualCallManager(cm);
                instance = current;
            }
            return current;
        }
    }

    /** Existing manager or null - never creates one. */
    @Nullable
    public static VisualCallManager peek() {
        VisualCallManager current = instance;
        if (current == null) return null;
        return current.callManager == TransponderSnails.getCallManager() ? current : null;
    }

    public static void discard() {
        synchronized (VisualCallManager.class) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    // =====================================================================================================

    private final TransponderCallManager callManager;
    private final VoicechatServerApi api;
    private final CallSoundManager soundManager;

    private final Map<UUID, VisualCallSession> sessions = new ConcurrentHashMap<>();
    private final Map<GlobalPos, UUID> snailToCall = new ConcurrentHashMap<>();
    private long tickCounter = 0;
    /** Last tick a player's click was accepted (rate limit, see INTERACTION_COOLDOWN_TICKS). */
    private final Map<UUID, Long> lastInteractionTick = new ConcurrentHashMap<>();

    private VisualCallManager(TransponderCallManager callManager) {
        this.callManager = callManager;
        this.api = callManager.getVoiceChatApi();
        this.soundManager = callManager.getSoundManager();
        LOGGER.info("VisualCallManager: initialised (voice chat API {})", api != null ? "available" : "MISSING");
    }

    public boolean isInCall(GlobalPos snail) {
        return snailToCall.containsKey(snail);
    }

    // =====================================================================================================
    // Interaction
    // =====================================================================================================

    /**
     * Right click on a Visual Snail that is in a call or is a camera. A Visual Transponder Snail (projector) that is NOT in a call never
     * gets here: a click on it opens its dialing GUI and crouch + click its settings (TransponderSnailBlockEntity#handleVisualInteraction);
     * the call is placed by number (see {@link #dial}). There is no reject - a call that is not wanted is simply not answered.
     * <ul>
     *   <li>idle (only a camera gets here): start a call to the nearest idle Visual Snail in range</li>
     *   <li>ringing (callee): answer; ringing (caller): cancel</li>
     *   <li>connecting / connected: hang up</li>
     * </ul>
     */
    public InteractionResult onInteract(ServerPlayer player, TransponderSnailBlockEntity snail) {
        if (!(snail.getLevel() instanceof ServerLevel level)) return InteractionResult.FAIL;

        // A player hammering the button would start and end calls (with sounds and packets) every tick: ignore clicks that
        // come too soon after the last accepted one. Ignored clicks do not extend the cooldown.
        Long lastClick = lastInteractionTick.get(player.getUUID());
        if (lastClick != null && tickCounter - lastClick < VisualCallConstants.INTERACTION_COOLDOWN_TICKS) {
            return InteractionResult.SUCCESS;
        }
        lastInteractionTick.put(player.getUUID(), tickCounter);

        BlockPos pos = snail.getBlockPos();
        GlobalPos gp = GlobalPos.of(level.dimension(), pos);
        UUID callId = snailToCall.get(gp);
        VisualCallSession session = callId == null ? null : sessions.get(callId);

        if (session == null) {
            return initiate(player, level, snail) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }

        switch (session.getState()) {
            case RINGING:
                if (session.isCallee(pos)) {
                    return accept(player, level, session) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
                }
                end(session, EndReason.CANCELLED, null, player, pos);
                return InteractionResult.SUCCESS;
            case CONNECTING:
            case CONNECTED:
                end(session, EndReason.HANG_UP, null, player, pos);
                return InteractionResult.SUCCESS;
            default:
                return InteractionResult.FAIL;
        }
    }

    private boolean initiate(ServerPlayer player, ServerLevel level, TransponderSnailBlockEntity caller) {
        if (api == null) {
            msg(player, "Voice chat is not available - cannot start a video call!", ChatFormatting.RED);
            return false;
        }

        // server-wide limit (server config: visual_snails.max_concurrent_calls)
        if (sessions.size() >= VisualSettings.maxConcurrentCalls()) {
            msg(player, "This server's limit of " + VisualSettings.maxConcurrentCalls()
                    + " simultaneous video calls has been reached - try again later.", ChatFormatting.RED);
            return false;
        }

        BlockPos callerPos = caller.getBlockPos();
        VisualSnailRole callerRole = VisualRoles.ofEntity(caller);
        if (callerRole == null) callerRole = VisualSnailRole.DUPLEX;

        List<TransponderSnailBlockEntity> others = new ArrayList<>();
        List<VisualPartnerFinder.Candidate> candidates = new ArrayList<>();
        for (TransponderSnailBlockEntity be : VisualSnailRegistry.all()) {
            if (be == caller || be.isRemoved() || be.getLevel() != level) continue;
            // only snails that can show a picture are called: a camera (transmit only) is never the one being called
            VisualSnailRole role = VisualRoles.ofEntity(be);
            if (role == null || !VisualSnailRole.canCall(callerRole, role)) continue;
            BlockPos p = be.getBlockPos();
            others.add(be);
            candidates.add(new VisualPartnerFinder.Candidate(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                    !snailToCall.containsKey(GlobalPos.of(level.dimension(), p))));
        }

        VisualPartnerFinder.Result result = VisualPartnerFinder.find(
                callerPos.getX() + 0.5, callerPos.getY() + 0.5, callerPos.getZ() + 0.5,
                candidates, VisualCallConstants.CALL_RANGE);

        switch (result.outcome()) {
            case NONE:
                msg(player, callerRole == VisualSnailRole.CAMERA
                                ? "No Visual Transponder Snail within " + (int) VisualCallConstants.CALL_RANGE + " blocks to transmit to!"
                                : "No other Visual Transponder Snail within " + (int) VisualCallConstants.CALL_RANGE + " blocks!",
                        ChatFormatting.RED);
                return false;
            case BUSY:
                soundManager.playBusySoundAtSnail(player, callerPos);
                msg(player, "Every Visual Transponder Snail in range is busy!", ChatFormatting.RED);
                return false;
            default:
                break;
        }

        return startSession(player, level, caller, callerRole, others.get(result.index()));
    }

    /**
     * Dialing by number, from the dialing GUI of a Visual Transponder Snail (the same GUI as a phone): rings the visual snail that holds
     * {@code targetNumber}. Only visual snails are dialed here - the caller (TransponderCallManager.initiateCallBySnailNumber) has
     * already refused a number of the other kind. Tells the player why if the call cannot be made.
     *
     * @return true if the call is ringing
     */
    public boolean dial(ServerPlayer player, int callerNumber, int targetNumber) {
        if (api == null) {
            msg(player, "Voice chat is not available - cannot start a video call!", ChatFormatting.RED);
            return false;
        }

        // the snail the player is dialing from: loaded, in the player's level, and within reach of the player
        TransponderSnailBlockEntity caller = VisualSnailRegistry.findByNumber(callerNumber);
        if (caller == null || !(caller.getLevel() instanceof ServerLevel level) || level != player.level()
                || player.distanceToSqr(Vec3.atCenterOf(caller.getBlockPos())) > 64.0) {
            msg(player, "Could not dial from this snail!", ChatFormatting.RED);
            return false;
        }

        GlobalPos callerGp = GlobalPos.of(level.dimension(), caller.getBlockPos());
        if (snailToCall.containsKey(callerGp)) {
            msg(player, "This snail is already in a call!", ChatFormatting.RED);
            return false;
        }

        TransponderSnailBlockEntity callee = VisualSnailRegistry.findByNumber(targetNumber);
        if (callee == null) {
            msg(player, "Snail #" + targetNumber + " is not available!", ChatFormatting.RED);
            return false;
        }
        if (callee.getLevel() != level) {
            msg(player, "Snail #" + targetNumber + " is in another dimension!", ChatFormatting.RED);
            return false;
        }

        VisualSnailRole callerRole = VisualRoles.ofEntity(caller);
        VisualSnailRole calleeRole = VisualRoles.ofEntity(callee);
        if (callerRole == null || calleeRole == null || !VisualSnailRole.canCall(callerRole, calleeRole)) {
            msg(player, "Snail #" + targetNumber + " cannot take this call!", ChatFormatting.RED);
            return false;
        }

        // No range limit: like a phone snail, a visual snail can be dialed anywhere in its dimension (the dimension was checked above).
        BlockPos a = caller.getBlockPos();
        BlockPos b = callee.getBlockPos();

        if (snailToCall.containsKey(GlobalPos.of(level.dimension(), b))) {
            soundManager.playBusySoundAtSnail(player, a);
            msg(player, "Snail #" + targetNumber + " is busy!", ChatFormatting.RED);
            return false;
        }

        return startSession(player, level, caller, callerRole, callee);
    }

    /** Rings {@code callee} for a call from {@code caller}: the session, the snail states, the ring tone. Shared by pairing and dialing. */
    private boolean startSession(ServerPlayer player, ServerLevel level, TransponderSnailBlockEntity caller,
                                 VisualSnailRole callerRole, TransponderSnailBlockEntity callee) {
        // server-wide limit (server config: visual_snails.max_concurrent_calls)
        if (sessions.size() >= VisualSettings.maxConcurrentCalls()) {
            msg(player, "This server's limit of " + VisualSettings.maxConcurrentCalls()
                    + " simultaneous video calls has been reached - try again later.", ChatFormatting.RED);
            return false;
        }

        BlockPos callerPos = caller.getBlockPos();
        BlockPos calleePos = callee.getBlockPos();

        VisualSnailRole calleeRole = VisualRoles.ofEntity(callee);
        VisualCallSession session = new VisualCallSession(UUID.randomUUID(), level.dimension(), callerPos, calleePos, tickCounter,
                callerRole, calleeRole == null ? VisualSnailRole.DUPLEX : calleeRole);
        sessions.put(session.callId(), session);
        snailToCall.put(session.callerGp(), session.callId());
        snailToCall.put(session.calleeGp(), session.callId());

        caller.setVisualCallState(VisualCallState.CALLING_OUT);
        callee.setVisualCallState(VisualCallState.RINGING_IN);

        soundManager.playLocationalRingToneAtPosition(level, calleePos);

        for (ServerPlayer near : playersNear(level, calleePos, VoiceChatConstants.getSnailInteractionRange())) {
            msg(near, "Visual Transponder Snail is ringing!", ChatFormatting.YELLOW);
        }
        msg(player, "Calling...", ChatFormatting.YELLOW);
        return true;
    }

    private boolean accept(ServerPlayer player, ServerLevel level, VisualCallSession session) {
        if (session.getState() != State.RINGING) return false;

        // Audio first: if it cannot be set up the call never connects.
        soundManager.stopSnailPositionSounds(session.calleePos(), CallSoundManager.SoundType.RING_TONE);

        // a channel (speaker) is only needed at an end that plays audio; a transmit-only camera has none
        boolean callerPlays = session.receivesFromOther(session.callerPos());
        boolean calleePlays = session.receivesFromOther(session.calleePos());
        LocationalAudioChannel atCaller = callerPlays ? createChannel(level, session.callerPos()) : null;
        LocationalAudioChannel atCallee = calleePlays ? createChannel(level, session.calleePos()) : null;
        if ((callerPlays && atCaller == null) || (calleePlays && atCallee == null)) {
            fail(session, "audio channel could not be created", player);
            return false;
        }
        session.setChannels(atCaller, atCallee);

        soundManager.playPickUpSoundAtSnail(player, session.calleePos());

        session.setState(State.CONNECTING);
        session.setConnectingSinceTick(tickCounter);

        setSnailState(session.callerGp(), VisualCallState.CONNECTING);
        setSnailState(session.calleeGp(), VisualCallState.CONNECTING);

        msg(player, "Connecting video...", ChatFormatting.YELLOW);

        refreshViewers(level, session);
        return true;
    }

    /** Same setup as TransponderCallManager#createBlockAudioChannelAtPosition: a locational channel at the block. */
    @Nullable
    private LocationalAudioChannel createChannel(ServerLevel level, BlockPos pos) {
        try {
            LocationalAudioChannel channel = api.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    api.fromServerLevel(level),
                    api.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (channel == null) return null;
            channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
            channel.setDistance((float) VoiceChatConstants.getLocationalSnailRange());
            return channel;
        } catch (Exception e) {
            LOGGER.error("VisualCallManager: failed to create audio channel at {}", pos, e);
            return null;
        }
    }

    // =====================================================================================================
    // Video handshake
    // =====================================================================================================

    /** Client says the feed for {@code screenPos} is rendering (ok) or broke (!ok). */
    public void onVideoStatus(ServerPlayer player, UUID callId, BlockPos screenPos, boolean ok, String reason) {
        VisualCallSession session = sessions.get(callId);
        if (session == null || session.getState() == State.RINGING) return;
        if (!session.involves(screenPos) || !session.receivesFromOther(screenPos)) return; // only ends that have a screen report
        if (session.isStream()) return; // a viewer that cannot get the picture (too far: its area is not loaded there) shows "no signal"; the stream goes on
        if (!player.level().dimension().equals(session.dimension())) return;
        double maxDist = VisualCallConstants.VIDEO_TRACK_RANGE + 16.0;
        if (player.distanceToSqr(screenPos.getX() + 0.5, screenPos.getY() + 0.5, screenPos.getZ() + 0.5) > maxDist * maxDist) return;

        if (ok) {
            session.markScreenReady(screenPos);
        } else if (VisualCallConstants.FAIL_CALL_ON_ANY_VIEWER_ERROR) {
            fail(session, "video feed error (" + (reason == null || reason.isEmpty() ? "unknown" : reason) + ")", null);
        }
    }

    /** Tell every player near a screen to render its feed. Idempotent per (player, screen). One pass over the players, no allocation. */
    private void refreshViewers(ServerLevel level, VisualCallSession session) {
        BlockPos a = session.callerPos();
        BlockPos b = session.calleePos();
        boolean aShows = session.receivesFromOther(a); // a transmit-only camera has no screen to feed
        boolean bShows = session.receivesFromOther(b);
        if (!aShows && !bShows) return;

        // A projector's screen can be up to PROJECTOR_MAX_THROW blocks from the snail (on the surface behind it), so a player
        // counts as a viewer when they are near the snail OR near the screen itself.
        double r2 = VisualCallConstants.VIDEO_TRACK_RANGE * VisualCallConstants.VIDEO_TRACK_RANGE;
        double[] aScreen = aShows ? screenCenter(level, a) : null;
        double[] bScreen = bShows ? screenCenter(level, b) : null;
        for (ServerPlayer p : level.players()) {
            if (aShows && isNear(p, a, aScreen, r2) && session.addViewer(p.getUUID(), a)) {
                sendControl(p, session.callId(), a, b, true, session.isStream());
            }
            if (bShows && isNear(p, b, bScreen, r2) && session.addViewer(p.getUUID(), b)) {
                sendControl(p, session.callId(), b, a, true, session.isStream());
            }
        }
    }

    private static boolean isNear(ServerPlayer p, BlockPos snail, double[] screen, double rangeSq) {
        return p.distanceToSqr(snail.getX() + 0.5, snail.getY() + 0.5, snail.getZ() + 0.5) <= rangeSq
                || p.distanceToSqr(screen[0], screen[1], screen[2]) <= rangeSq;
    }

    /**
     * World position of the middle of a projector snail's screen (the snail itself if that cannot be worked out). Uses the same
     * placement as the client draws (ProjectorPlacement), so server and clients agree about where the screen is.
     */
    private static double[] screenCenter(ServerLevel level, BlockPos pos) {
        double[] snailCenter = {pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5};
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TransponderSnailBlock) || !state.hasProperty(TransponderSnailBlock.FACING)) return snailCenter;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TransponderSnailBlockEntity snail)) return snailCenter;

        Direction facing = state.getValue(TransponderSnailBlock.FACING);
        Direction screenDir = VisualCallConstants.SCREEN_BEHIND_SNAIL ? facing.getOpposite() : facing;
        double surface = ProjectorSurface.find(level, pos, screenDir);
        ProjectorPlacement.Result r = ProjectorPlacement.compute(facing.getStepX(), facing.getStepZ(), snail.getScreenSize(),
                snail.getScreenOffsetSide(), snail.getScreenOffsetUp(), snail.getScreenOffsetBack(),
                VisualSettings.maxScreenSize(), surface);
        double[] c = r.center();
        return new double[]{pos.getX() + c[0], pos.getY() + c[1], pos.getZ() + c[2]};
    }

    private static void sendControl(ServerPlayer player, UUID callId, BlockPos screen, BlockPos camera, boolean start, boolean stream) {
        VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new VisualFeedControlPacket(callId, screen, camera, start, stream));
    }

    public void onPlayerLeft(UUID playerId) {
        lastInteractionTick.remove(playerId);
        for (VisualCallSession s : sessions.values()) {
            s.removeViewersOf(playerId);
        }
    }

    // =====================================================================================================
    // Tick
    // =====================================================================================================

    public void tick() {
        tickCounter++;
        if (sessions.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // sessions is a ConcurrentHashMap: iterating it directly is safe even though end() removes entries, and costs no copy per tick
        for (VisualCallSession session : sessions.values()) {
            try {
                tickSession(server, session);
            } catch (Exception e) {
                LOGGER.error("VisualCallManager: error while ticking call {}", session.callId(), e);
                end(session, EndReason.FAILED, "internal error", null, null);
            }
        }
    }

    private void tickSession(MinecraftServer server, VisualCallSession session) {
        ServerLevel level = server.getLevel(session.dimension());
        if (level == null) {
            end(session, EndReason.FAILED, "dimension unavailable", null, null);
            return;
        }

        if (session.isStream()) {
            tickStream(level, session);
            return;
        }

        TransponderSnailBlockEntity caller = VisualSnailRegistry.get(session.callerGp());
        TransponderSnailBlockEntity callee = VisualSnailRegistry.get(session.calleeGp());
        if (caller == null || caller.isRemoved() || callee == null || callee.isRemoved()) {
            end(session, EndReason.LOST, null, null, null);
            return;
        }

        String failure = session.takeFailure();
        if (failure != null) {
            fail(session, failure, null);
            return;
        }

        switch (session.getState()) {
            case RINGING:
                long ringTicks = Math.max(1L, VoiceChatConstants.getRingTimeoutMs() / 50L);
                if (tickCounter - session.createdTick() > ringTicks) {
                    end(session, EndReason.NO_ANSWER, null, null, null);
                }
                break;

            case CONNECTING:
                if (tickCounter % 10 == 0) refreshViewers(level, session);
                if (session.allScreensReady()) {
                    connect(level, session);
                } else if (tickCounter - session.connectingSinceTick() > VisualCallConstants.VIDEO_START_TIMEOUT_TICKS) {
                    fail(session, "video feed did not start on both ends", null);
                }
                break;

            case CONNECTED:
                if (tickCounter % VisualCallConstants.VIEWER_REFRESH_TICKS == 0) refreshViewers(level, session);
                long now = System.currentTimeMillis();
                caller.setVisualAudioActive(audioActive(session, session.callerPos(), now));
                callee.setVisualAudioActive(audioActive(session, session.calleePos(), now));
                break;
        }
    }

    /** "Active" model: an end that plays audio shows it while audio arrives; a transmit-only end (camera) while it picks sound up. */
    private static boolean audioActive(VisualCallSession session, BlockPos end, long nowMs) {
        long last = session.receivesFromOther(end) ? session.lastAudioAt(end) : session.lastAudioFrom(end);
        return nowMs - last < VisualCallConstants.AUDIO_ACTIVITY_WINDOW_MS;
    }

    /** Audio AND video are up on every end that has a screen / speaker. */
    private void connect(ServerLevel level, VisualCallSession session) {
        session.setState(State.CONNECTED);
        setSnailState(session.callerGp(), VisualCallState.CONNECTED);
        setSnailState(session.calleeGp(), VisualCallState.CONNECTED);

        ServerPlayer anyone = anyPlayer(level, null);
        if (anyone != null) {
            soundManager.playCallConnectedSoundAtSnail(anyone, session.callerPos());
            soundManager.playCallConnectedSoundAtSnail(anyone, session.calleePos());
        }
        broadcastNear(level, session, "Call connected!", ChatFormatting.GREEN);
    }

    // =====================================================================================================
    // Streams: a Visual Transmitter broadcasts, any number of projectors tune in
    // =====================================================================================================

    /**
     * A projector tunes in to the stream of a camera that is broadcasting: a one-way connection, up at once - no ringing, no handshake.
     * The camera is not marked busy and shows nothing (a camera does not announce who is watching); only the projector changes. A
     * projector that is tuned to another stream is retuned; one that is in a call is busy.
     *
     * @return true if it is tuned in
     */
    public boolean tune(ServerPlayer player, TransponderSnailBlockEntity projector, TransponderSnailBlockEntity camera, String streamName) {
        if (api == null) {
            msg(player, "Voice chat is not available - cannot receive a stream!", ChatFormatting.RED);
            return false;
        }
        if (!(projector.getLevel() instanceof ServerLevel level) || level != player.level()
                || player.distanceToSqr(Vec3.atCenterOf(projector.getBlockPos())) > 100.0) {
            msg(player, "Could not tune in from here!", ChatFormatting.RED);
            return false;
        }
        if (camera.getLevel() != level) {
            msg(player, "That stream is in another dimension!", ChatFormatting.RED);   // the only limit on the range
            return false;
        }
        if (!camera.isStreaming()) {
            msg(player, streamName + " is not active.", ChatFormatting.RED);
            return false;
        }

        GlobalPos projectorGp = GlobalPos.of(level.dimension(), projector.getBlockPos());
        UUID existing = snailToCall.get(projectorGp);
        if (existing != null) {
            VisualCallSession old = sessions.get(existing);
            if (old != null && old.isStream()) {
                if (old.callerPos().equals(camera.getBlockPos())) {
                    msg(player, "Already tuned to " + streamName + ".", ChatFormatting.YELLOW);
                    return true;
                }
                endStream(old, null); // retune: quietly leave the old stream
            } else {
                msg(player, "This snail is in a call!", ChatFormatting.RED);
                return false;
            }
        }
        if (sessions.size() >= VisualSettings.maxConcurrentCalls()) {
            msg(player, "This server's limit of " + VisualSettings.maxConcurrentCalls()
                    + " simultaneous video calls has been reached - try again later.", ChatFormatting.RED);
            return false;
        }

        LocationalAudioChannel atProjector = createChannel(level, projector.getBlockPos());
        if (atProjector == null) {
            msg(player, "Could not tune in: the audio channel could not be created.", ChatFormatting.RED);
            return false;
        }

        VisualCallSession session = new VisualCallSession(UUID.randomUUID(), level.dimension(), camera.getBlockPos(), projector.getBlockPos(),
                tickCounter, VisualSnailRole.CAMERA, VisualSnailRole.DUPLEX, true);
        session.setChannels(null, atProjector);   // only the projector has a speaker
        session.setState(State.CONNECTED);
        sessions.put(session.callId(), session);
        snailToCall.put(projectorGp, session.callId());   // the projector is busy; the camera is NOT (it can have any number of viewers)

        projector.setVisualCallState(VisualCallState.CONNECTED);
        soundManager.playCallConnectedSoundAtSnail(player, projector.getBlockPos());
        refreshViewers(level, session);
        msg(player, "Tuned to " + streamName + ".", ChatFormatting.GREEN);
        return true;
    }

    private void tickStream(ServerLevel level, VisualCallSession session) {
        TransponderSnailBlockEntity camera = VisualSnailRegistry.get(session.callerGp());
        TransponderSnailBlockEntity projector = VisualSnailRegistry.get(session.calleeGp());
        if (camera == null || camera.isRemoved() || projector == null || projector.isRemoved() || !camera.isStreaming()) {
            end(session, EndReason.LOST, null, null, null);   // the stream was switched off, or one of the two snails is gone
            return;
        }
        String failure = session.takeFailure();
        if (failure != null) {
            fail(session, failure, null);
            return;
        }
        if (tickCounter % VisualCallConstants.VIEWER_REFRESH_TICKS == 0) refreshViewers(level, session);
        long now = System.currentTimeMillis();
        projector.setVisualAudioActive(audioActive(session, session.calleePos(), now));
        camera.setVisualAudioActive(now - session.lastAudioFrom(session.callerPos()) < VisualCallConstants.AUDIO_ACTIVITY_WINDOW_MS);
    }

    /** One microphone packet, to the speaker of EVERY projector that is tuned to the camera at {@code cameraPos}. */
    private void sendToStreamViewers(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos cameraPos, byte[] audio) {
        for (VisualCallSession s : sessions.values()) {
            if (!s.isStream() || s.getState() != State.CONNECTED || !s.dimension().equals(dimension) || !s.callerPos().equals(cameraPos)) continue;
            AudioChannel channel = s.channelAt(s.calleePos());
            if (channel == null) continue;
            try {
                channel.send(audio);
                s.markAudioAt(s.calleePos());
                s.markAudioFrom(cameraPos);
            } catch (Exception e) {
                int errors = s.recordAudioError();
                if (errors == 1) LOGGER.warn("VisualCallManager: audio send to a stream viewer failed: {}", e.toString());
                if (errors >= VisualCallConstants.MAX_AUDIO_SEND_ERRORS) s.flagFailure("audio transmission error"); // that viewer only
            }
        }
    }

    /** Ends every stream session of a camera (the stream was switched off, or the camera is gone). @param message what the viewers are told, or null */
    public void endStreamsOf(GlobalPos camera, @Nullable String message) {
        for (VisualCallSession s : sessions.values()) {
            if (s.isStream() && s.callerGp().equals(camera)) endStream(s, message);
        }
    }

    /** A projector leaves the stream it is tuned to (the player double-clicked it in the tuner). */
    public void disconnectStream(GlobalPos projector, @Nullable ServerPlayer actor) {
        VisualCallSession s = streamSessionOf(projector);
        if (s != null) end(s, EndReason.HANG_UP, null, actor, s.calleePos());
    }

    /**
     * Whether this player is a viewer of a screen that shows the picture of the snail at {@code camera} (a call or a stream). The relay
     * only sends a camera's picture to such a player: nobody can watch a camera they are not connected to.
     */
    public boolean isViewerOfCamera(UUID player, GlobalPos camera) {
        for (VisualCallSession s : sessions.values()) {
            if (s.isEnded() || !s.dimension().equals(camera.dimension())) continue;
            for (ViewerKey key : s.viewerKeys()) {
                if (key.player().equals(player) && s.other(key.screen()).equals(camera.pos())) return true;
            }
        }
        return false;
    }

    /** How many projectors are tuned to the camera right now. */
    public int viewerCount(GlobalPos camera) {
        int n = 0;
        for (VisualCallSession s : sessions.values()) {
            if (s.isStream() && !s.isEnded() && s.callerGp().equals(camera)) n++;
        }
        return n;
    }

    /** The stream session a projector is tuned in with, or null. */
    @Nullable
    public VisualCallSession streamSessionOf(GlobalPos projector) {
        UUID id = snailToCall.get(projector);
        VisualCallSession s = id == null ? null : sessions.get(id);
        return s != null && s.isStream() ? s : null;
    }

    private static String streamEndMessage(EndReason reason, @Nullable String detail, @Nullable ServerPlayer actor) {
        if (reason == EndReason.HANG_UP && actor != null) return "Disconnected from the stream.";
        if (reason == EndReason.FAILED) return "The stream failed: " + (detail == null ? "unknown error" : detail);
        return "The stream ended.";
    }

    /**
     * Ends one viewer's connection to a stream. Only the projector is touched: the camera was never in a call, shows nothing, and goes on
     * for its other viewers. {@code message} is told to the players near the projector (null = quietly, as when retuning).
     */
    private void endStream(VisualCallSession session, @Nullable String message) {
        if (!session.markEnded()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        sessions.remove(session.callId());
        snailToCall.remove(session.calleeGp(), session.callId());

        runStep("reset projector", () -> resetSnail(session.calleeGp()));
        runStep("stop video", () -> {
            if (server == null) return;
            for (ViewerKey key : session.viewerKeys()) {
                ServerPlayer p = server.getPlayerList().getPlayer(key.player());
                if (p != null) sendControl(p, session.callId(), key.screen(), session.other(key.screen()), false, true);
            }
        });
        session.clearAudio();

        if (message == null || server == null) return;
        ServerLevel level = server.getLevel(session.dimension());
        if (level == null) return;
        runStep("messages", () -> {
            ServerPlayer anyone = anyPlayer(level, null);
            if (anyone != null && VisualSnailRegistry.get(session.calleeGp()) != null) {
                soundManager.playCallDisconnectedSoundAtSnail(anyone, session.calleePos());
            }
            for (ServerPlayer near : playersNear(level, session.calleePos(), VoiceChatConstants.getSnailInteractionRange())) {
                msg(near, message, ChatFormatting.GRAY);
            }
        });
    }

    // =====================================================================================================
    // Audio (called from the Simple Voice Chat thread through SnailAudioRelay)
    // =====================================================================================================

    /**
     * Routes a microphone packet into a visual call if the speaker stands near one of its snails. Whoever is near a
     * snail is heard at the OTHER snail, with the same linear distance falloff a placed numbered snail uses. A speaker
     * near both snails is attributed to the nearer one only (no echo).
     *
     * @return true if the packet was consumed (the normal call pipeline must not process it)
     */
    public boolean handleMicrophonePacket(SnailAudioRelay relay, ServerPlayer speaker, MicrophonePacketEvent event) {
        if (sessions.isEmpty()) return false;
        if (callManager.isInCall(speaker.getUUID())) return false; // a numbered call takes priority

        double range = VoiceChatConstants.getLocationalSnailRange();
        if (range <= 0.0) return false;

        // This runs for EVERY microphone packet of EVERY player (about 50 per second each), so it must stay cheap: squared
        // distances only (one sqrt at the end), one dimension lookup, no allocation.
        VisualCallSession bestSession = null;
        BlockPos bestPos = null;
        double bestDistSq = range * range;
        var speakerDimension = speaker.level().dimension();

        for (VisualCallSession s : sessions.values()) {
            if (!s.dimension().equals(speakerDimension)) continue;
            for (int end = 0; end < 2; end++) {
                BlockPos p = end == 0 ? s.callerPos() : s.calleePos();
                if (!s.transmitsToOther(p)) continue; // nobody listens at the other end (e.g. a projector that is watching a camera)
                double dSq = speaker.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestSession = s;
                    bestPos = p;
                }
            }
        }

        if (bestSession == null) return false;
        double bestDist = Math.sqrt(bestDistSq);
        if (bestSession.getState() != State.CONNECTED) return true; // in range, but audio only flows once video is up too

        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) return true;

        float gain = (float) (1.0 - bestDist / range);
        byte[] audio = relay.processForVisualCall(opus, speaker.getUUID(), gain);
        if (audio == null) return true;

        if (bestSession.isStream()) {
            sendToStreamViewers(bestSession.dimension(), bestPos, audio);
            return true;
        }

        BlockPos target = bestSession.other(bestPos);
        AudioChannel channel = bestSession.channelAt(target);
        if (channel == null) return true;

        try {
            channel.send(audio);
            bestSession.markAudioAt(target);
            bestSession.markAudioFrom(bestPos);
        } catch (Exception e) {
            int errors = bestSession.recordAudioError();
            if (errors == 1) LOGGER.warn("VisualCallManager: audio send failed: {}", e.toString());
            if (errors >= VisualCallConstants.MAX_AUDIO_SEND_ERRORS) {
                bestSession.flagFailure("audio transmission error");
            }
        }
        return true;
    }

    // =====================================================================================================
    // Ending
    // =====================================================================================================

    private void fail(VisualCallSession session, String reason, @Nullable ServerPlayer hint) {
        end(session, EndReason.FAILED, reason, hint, null);
    }

    /** A snail block was removed / unloaded. The removed snail is already out of the registry. */
    public void onSnailRemoved(TransponderSnailBlockEntity be) {
        if (be.getLevel() == null) return;
        endStreamsOf(GlobalPos.of(be.getLevel().dimension(), be.getBlockPos()), "The stream ended.");   // a camera that was streaming
        UUID id = snailToCall.get(GlobalPos.of(be.getLevel().dimension(), be.getBlockPos()));
        VisualCallSession session = id == null ? null : sessions.get(id);
        if (session != null) {
            end(session, EndReason.LOST, null, null, null);
        }
    }

    /**
     * Tears a call down exactly once: stops ringing, resets both snails (idle / "sound" model for the disconnect
     * sound), stops the video on the clients, drops the audio channels and plays the usual hang up / disconnected sounds.
     *
     * @param actor    the player that hung up / rejected / cancelled (null for automatic endings)
     * @param actorPos the snail that player used
     */
    private void end(VisualCallSession session, EndReason reason, @Nullable String detail,
                     @Nullable ServerPlayer actor, @Nullable BlockPos actorPos) {
        if (session.isStream()) {
            endStream(session, streamEndMessage(reason, detail, actor));
            return;
        }
        if (!session.markEnded()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        sessions.remove(session.callId());
        snailToCall.remove(session.callerGp(), session.callId());
        snailToCall.remove(session.calleeGp(), session.callId());

        runStep("stop ringing", () ->
                soundManager.stopSnailPositionSounds(session.calleePos(), CallSoundManager.SoundType.RING_TONE));

        boolean callerAlive = VisualSnailRegistry.get(session.callerGp()) != null;
        boolean calleeAlive = VisualSnailRegistry.get(session.calleeGp()) != null;

        runStep("reset snails", () -> {
            resetSnail(session.callerGp());
            resetSnail(session.calleeGp());
        });

        runStep("stop video", () -> {
            if (server == null) return;
            for (ViewerKey key : session.viewerKeys()) {
                ServerPlayer p = server.getPlayerList().getPlayer(key.player());
                if (p != null) sendControl(p, session.callId(), key.screen(), session.other(key.screen()), false, false);
            }
        });

        session.clearAudio();

        ServerLevel level = server == null ? null : server.getLevel(session.dimension());
        if (level == null) return;

        runStep("sounds", () -> {
            ServerPlayer anyone = anyPlayer(level, actor);
            if (anyone == null) return;
            if (reason == EndReason.HANG_UP && actor != null && actorPos != null) {
                soundManager.playHangUpSoundAtSnail(actor, actorPos);
            }
            if (callerAlive) soundManager.playCallDisconnectedSoundAtSnail(anyone, session.callerPos());
            if (calleeAlive) soundManager.playCallDisconnectedSoundAtSnail(anyone, session.calleePos());
        });

        runStep("messages", () -> {
            switch (reason) {
                case FAILED:
                    broadcastNear(level, session, "Call failed: " + (detail == null ? "unknown error" : detail), ChatFormatting.RED);
                    break;
                case NO_ANSWER:
                    broadcastNear(level, session, "No answer.", ChatFormatting.GRAY);
                    break;
                case CANCELLED:
                    broadcastNear(level, session, "Call cancelled.", ChatFormatting.GRAY);
                    break;
                default:
                    broadcastNear(level, session, "Call ended.", ChatFormatting.GRAY);
                    break;
            }
        });

        LOGGER.debug("VisualCallManager: call {} ended ({}{})", session.callId(), reason, detail == null ? "" : ": " + detail);
    }

    private void resetSnail(GlobalPos gp) {
        TransponderSnailBlockEntity be = VisualSnailRegistry.get(gp);
        if (be != null && !be.isRemoved()) {
            be.setVisualAudioActive(false);
            be.setVisualCallState(VisualCallState.IDLE);
        }
    }

    private void setSnailState(GlobalPos gp, VisualCallState state) {
        TransponderSnailBlockEntity be = VisualSnailRegistry.get(gp);
        if (be != null && !be.isRemoved()) {
            be.setVisualCallState(state);
        }
    }

    private static void runStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            LOGGER.error("VisualCallManager: error ending call ({})", name, e);
        }
    }

    public void shutdown() {
        for (VisualCallSession session : new ArrayList<>(sessions.values())) {
            end(session, EndReason.SERVER_STOP, null, null, null);
        }
        sessions.clear();
        snailToCall.clear();
    }

    // =====================================================================================================
    // Helpers
    // =====================================================================================================

    private static void msg(ServerPlayer player, String text, ChatFormatting color) {
        player.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    private static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double range) {
        double r2 = range * range;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    private static void broadcastNear(ServerLevel level, VisualCallSession session, String text, ChatFormatting color) {
        double range = VoiceChatConstants.getSnailInteractionRange();
        for (ServerPlayer p : level.players()) {
            boolean near = p.distanceToSqr(session.callerPos().getX() + 0.5, session.callerPos().getY() + 0.5, session.callerPos().getZ() + 0.5) <= range * range
                    || p.distanceToSqr(session.calleePos().getX() + 0.5, session.calleePos().getY() + 0.5, session.calleePos().getZ() + 0.5) <= range * range;
            if (near) msg(p, text, color);
        }
    }

    /** CallSoundManager only needs a player to find the level the sound is played in. */
    @Nullable
    private static ServerPlayer anyPlayer(ServerLevel level, @Nullable ServerPlayer preferred) {
        if (preferred != null && preferred.level() == level) return preferred;
        List<ServerPlayer> players = level.players();
        return players.isEmpty() ? null : players.get(0);
    }
}
