package net.eclipce.transpondersnails.voice.server;

import com.mojang.logging.LogUtils;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.HornedDenDenMushiBlock;
import net.eclipce.transpondersnails.block.entity.HornedDenDenMushiBlockEntity;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.config.ModConfig;
import net.eclipce.transpondersnails.entity.custom.HornedDenDenMushiEntity;
import net.eclipce.transpondersnails.jam.JamMath;
import net.eclipce.transpondersnails.visual.server.VisualCallManager;
import net.eclipce.transpondersnails.visual.server.VisualCallSession;
import net.eclipce.transpondersnails.visual.server.VisualStreamManager;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Jamming. A jammer does not cut a call off, it fills it with STATIC - more the nearer you are to it - and the static fades in and out instead
 * of switching on and off:
 *
 * <ul>
 *   <li><b>Sources</b>: a placed Horned Transponder Snail that is switched on (range 20 blocks, more with a lightning rod tower wired to it, up to a
 *       maximum - see {@link JamMath#rangeForRods}), and every Horned Den Den Mushi creature (passively, a few blocks around it, wherever it walks).</li>
 *   <li><b>Strength</b> at a position: 100% within a block of a source, falling in a straight line to 0% at the edge of its range; the strongest
 *       source wins ({@link JamMath#factor}).</li>
 *   <li><b>A call, or a stream, has ONE level</b>: the strongest at any of its ends (a stream: at its camera), so both sides of a call hear the same
 *       thing, whoever taps it hears it too, and a video call's picture dissolves into static by the same amount. The level eases towards that
 *       target over {@code jamming_fade_seconds}; it never jumps.</li>
 * </ul>
 * The server thread updates the levels once a tick; the voice chat threads that process the audio only read them (ConcurrentHashMap).
 * Streams are covered too; so are calls a Black Transponder Snail is tapping, since they share the call's audio.
 */
public class HornedDDMJammerManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final HornedDDMJammerManager INSTANCE = new HornedDDMJammerManager();

    public static HornedDDMJammerManager getInstance() {
        return INSTANCE;
    }

    private HornedDDMJammerManager() {}

    /** A place that jams, and how far. */
    private record Source(ResourceKey<Level> dimension, double x, double y, double z, double range) { }

    /** Placed Horned Transponder Snails (whether they are switched on is read from the block each tick). */
    private final Map<BlockPos, ResourceKey<Level>> blockJammers = new ConcurrentHashMap<>();
    /** Horned Den Den Mushi creatures in the world. */
    private final Map<UUID, HornedDenDenMushiEntity> entities = new ConcurrentHashMap<>();
    /** What jams right now (a fresh snapshot each tick). */
    private volatile Source[] sources = new Source[0];
    /** The smoothed jam level of every call (by call id) and stream (by its camera's position) that has any. */
    private final Map<Object, Double> levels = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    /** What was last reported to the log for each jammed call or stream: 1 = partly jammed, 2 = (nearly) all static. */
    private final Map<Object, Integer> reported = new ConcurrentHashMap<>();

    // =================== REGISTRATION ===================

    /** Called by HornedDenDenMushiBlockEntity.onLoad(). */
    public void registerJammer(BlockPos pos, ServerLevel level) {
        blockJammers.put(pos.immutable(), level.dimension());
    }

    /** Called by HornedDenDenMushiBlockEntity.setRemoved(). */
    public void unregisterJammer(BlockPos pos) {
        blockJammers.remove(pos.immutable());
    }

    public void registerEntity(HornedDenDenMushiEntity entity) {
        entities.put(entity.getUUID(), entity);
    }

    public void unregisterEntity(UUID id) {
        entities.remove(id);
    }

    // =================== EVERY TICK ===================

    public void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        rebuildSources(server);
        updateLevels(server);
    }

    private void rebuildSources(MinecraftServer server) {
        if (blockJammers.isEmpty() && entities.isEmpty()) {
            if (sources.length > 0) sources = new Source[0];
            return;
        }
        double base = ModConfig.getHornedDDMJammingRadius();
        double perRod = ModConfig.getHornedJammingRangePerRod();
        double max = ModConfig.getHornedJammingMaxRange();
        boolean recount = tickCounter % 40 == 0;      // the tower is looked at again now and then (a rod or a wire may have changed)

        List<Source> list = new ArrayList<>();
        for (Map.Entry<BlockPos, ResourceKey<Level>> entry : blockJammers.entrySet()) {
            BlockPos pos = entry.getKey();
            ServerLevel level = server.getLevel(entry.getValue());
            if (level == null || !level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HornedDenDenMushiBlock) || !state.getValue(HornedDenDenMushiBlock.ACTIVE)) continue;   // switched off
            int rods = 0;
            if (level.getBlockEntity(pos) instanceof HornedDenDenMushiBlockEntity be) {
                if (recount) be.updateAntennaConnection();
                rods = be.getLightningRodCount();
            }
            list.add(new Source(entry.getValue(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, JamMath.rangeForRods(rods, base, perRod, max)));
        }
        double entityRadius = ModConfig.getHornedEntityJammingRadius();
        for (HornedDenDenMushiEntity entity : new ArrayList<>(entities.values())) {
            if (entity.isRemoved() || !entity.isAlive()) {
                entities.remove(entity.getUUID());
                continue;
            }
            list.add(new Source(entity.level().dimension(), entity.getX(), entity.getY() + 0.5, entity.getZ(), entityRadius));
        }
        sources = list.toArray(new Source[0]);
    }

    private void updateLevels(MinecraftServer server) {
        Source[] src = sources;
        if (src.length == 0 && levels.isEmpty()) return;
        double step = JamMath.stepPerTick(ModConfig.getHornedJammingFadeSeconds());
        Set<Object> alive = new HashSet<>();

        TransponderCallManager phone = TransponderSnails.getCallManager();
        if (phone != null) {
            for (CallSession call : phone.getActiveCalls()) {
                if (call.getState() != CallSession.CallState.CONNECTED) continue;
                settle("phone call", call.getCallId(), phoneCallTarget(phone, call, src), step, alive);
            }
        }
        VisualCallManager visual = VisualCallManager.peek();
        if (visual != null) {
            for (VisualCallSession s : visual.callsSnapshot()) {
                if (s.isStream() || s.isEnded() || s.getState() != VisualCallSession.State.CONNECTED) continue;
                double target = Math.max(levelAt(src, s.dimension(), s.callerPos()), levelAt(src, s.dimension(), s.calleePos()));
                if (target < 1.0 && src.length > 0) target = Math.max(target, peopleAtEnds(server, s, src));   // the people speaking into / listening at the two snails count too
                settle("video call", s.callId(), target, step, alive);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (VisualStreamManager.Info info : VisualStreamManager.activeStreams(level)) {
                settle("stream", info.pos(), levelAt(src, level.dimension(), info.pos().pos()), step, alive);
            }
        }
        for (Object key : new ArrayList<>(reported.keySet())) {
            if (!alive.contains(key)) {
                reported.remove(key);
                LOGGER.info("Jamming: {} is no longer jammed", describe(key));
            }
        }
        levels.keySet().retainAll(alive);   // calls and streams that have ended, or have no jam left, are forgotten
    }

    private void settle(String kind, Object key, double target, double step, Set<Object> alive) {
        double current = JamMath.approach(levels.getOrDefault(key, 0.0), target, step);
        if (current > 0.0) {
            levels.put(key, current);
            alive.add(key);
            int bucket = current >= 0.98 ? 2 : 1;
            Integer before = reported.put(key, bucket);
            if (before == null || before != bucket) {   // a line when a call starts being jammed, and when it is all static - not every tick
                LOGGER.info("Jamming: {} is {} ({}% static, aiming for {}%)", kind + " " + describe(key), bucket == 2 ? "now all static" : "being jammed",
                        Math.round(current * 100.0), Math.round(target * 100.0));
            }
        }
    }

    private static String describe(Object key) {
        if (key instanceof UUID id) return id.toString().substring(0, 8);
        if (key instanceof GlobalPos pos) return "at " + pos.pos().toShortString();
        return String.valueOf(key);
    }

    /** The players within hearing of either end of a video call, jammed by where THEY are: a person at the jammer is on the call even if their snail is across the room. */
    private double peopleAtEnds(MinecraftServer server, VisualCallSession s, Source[] src) {
        double range = VoiceChatConstants.getLocationalSnailRange();
        ServerLevel level = server.getLevel(s.dimension());
        if (level == null || range <= 0.0) return 0.0;
        double rangeSq = range * range;
        double best = 0.0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(s.callerPos().getX() + 0.5, s.callerPos().getY() + 0.5, s.callerPos().getZ() + 0.5) <= rangeSq
                    || player.distanceToSqr(s.calleePos().getX() + 0.5, s.calleePos().getY() + 0.5, s.calleePos().getZ() + 0.5) <= rangeSq) {
                best = Math.max(best, levelAt(src, level.dimension(), player.getX(), player.getY(), player.getZ()));
            }
        }
        return best;
    }

    /** A phone call is jammed as much as its most jammed end: a placed snail where it stands, a player where they are (also the one at a placed snail). */
    private double phoneCallTarget(TransponderCallManager phone, CallSession call, Source[] src) {
        double target = 0.0;
        if (src.length == 0) return 0.0;
        for (CallSession.CallParticipant p : call.getAllParticipants()) {
            BlockPos blockPos = p.getBlockPosition();
            if (blockPos != null) {
                TransponderSnailBlockEntity snail = phone.getRegisteredSnailBlock(p.getSnailNumber());
                if (snail != null && snail.getLevel() != null) target = Math.max(target, levelAt(src, snail.getLevel().dimension(), blockPos));
            }
            if (p.getPlayerId() != null) {
                ServerPlayer player = phone.getPlayerById(p.getPlayerId());
                if (player != null) target = Math.max(target, levelAt(src, player.level().dimension(), player.getX(), player.getY(), player.getZ()));
            }
        }
        // ...and everyone else on the call: the players who hear it at a placed snail count by where they stand, like the two ends
        for (UUID listener : call.getAllAudioParticipants()) {
            ServerPlayer player = phone.getPlayerById(listener);
            if (player != null) target = Math.max(target, levelAt(src, player.level().dimension(), player.getX(), player.getY(), player.getZ()));
        }
        return target;
    }

    // =================== STRENGTH AT A PLACE ===================

    private static double levelAt(Source[] src, ResourceKey<Level> dimension, BlockPos pos) {
        return levelAt(src, dimension, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static double levelAt(Source[] src, ResourceKey<Level> dimension, double x, double y, double z) {
        double best = 0.0;
        for (Source s : src) {
            if (!s.dimension().equals(dimension)) continue;
            double dx = x - s.x(), dy = y - s.y(), dz = z - s.z();
            best = Math.max(best, JamMath.factor(Math.sqrt(dx * dx + dy * dy + dz * dz), s.range()));
        }
        return best;
    }

    // =================== WHAT THE AUDIO AND VIDEO ASK ===================

    /** How jammed a call is right now, 0..1 (smoothed). A phone call or a visual call, by its call id. Safe to call from any thread. */
    public double levelForCall(UUID callId) {
        return callId == null ? 0.0 : levels.getOrDefault(callId, 0.0);
    }

    /** How jammed a stream is right now, 0..1 (smoothed), by the position of its camera. Safe to call from any thread. */
    public double levelForStream(GlobalPos camera) {
        return camera == null ? 0.0 : levels.getOrDefault(camera, 0.0);
    }

    // =================== UTILITY ===================

    public int getActiveJammerCount() {
        return sources.length;
    }

    /** Called on server stop to avoid stale entries across restarts. */
    public void clear() {
        blockJammers.clear();
        entities.clear();
        levels.clear();
        reported.clear();
        sources = new Source[0];
        tickCounter = 0;
    }
}
