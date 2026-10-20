package net.eclipce.transpondersnails.visual.server;

import com.mojang.logging.LogUtils;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.custom.WhiteTransponderSnailBlock;
import net.eclipce.transpondersnails.block.custom.WireBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.WireTrace;
import net.eclipce.transpondersnails.voice.server.WhiteSnailProtectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The wire side of intercepting with visual snails. A Visual Transponder Snail WITH a Transmitter that is wired to a Black Transponder Snail is
 * "tapped": it becomes that black snail's display (it shows what the black snail intercepts) and can no longer call, be called or tune to a
 * stream. A visual snail with a transmitter wired to a White Transponder Snail is "protected": an interceptor sees static instead of it.
 *
 * "Wired" is the mod's existing wire network (WireBlock): the same walk the lightning-rod and white-snail traces use (see {@link WireTrace}).
 * Answers are remembered for a second (and forgotten the moment a wire changes) because they are asked in interactions and every tick of an
 * interception, and a trace can look at thousands of blocks.
 *
 * Server thread only.
 */
public final class VisualTapManager {

    private VisualTapManager() {}

    /** Like the mod's other wire traces: a trace looks at no more than this many blocks. */
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_VISITED = WireBlock.MAX_WIRE_RANGE * WireBlock.MAX_WIRE_RANGE;
    private static final long CACHE_MS = 1000L;

    private record Cached(long version, long atMs, boolean tapped) { }

    private record CachedWhite(long version, long atMs, @Nullable BlockPos white) { }

    private static final Map<GlobalPos, Cached> TAPPED_CACHE = new HashMap<>();
    private static final Map<GlobalPos, CachedWhite> WHITE_CACHE = new HashMap<>();
    private static long wireVersion = 0L;

    /** Ends of connected visual calls that have been reported to the white snail as protected, and by which white snail. */
    private static final Map<GlobalPos, BlockPos> REPORTED_PROTECTED = new HashMap<>();

    // =====================================================================================================
    // What is what
    // =====================================================================================================

    private static boolean isBlock(Level level, BlockPos pos, Block block) {
        return level.getBlockState(pos).getBlock() == block;
    }

    /** Is this block a Visual Transponder Snail with a Transmitter? */
    public static boolean isTapCapable(Level level, BlockPos pos) {
        return isBlock(level, pos, ModBlocks.VISUAL_TRANSPONDER_SNAIL_TRANSMITTER.get());
    }

    private static WireTrace.Pos pos(BlockPos p) {
        return new WireTrace.Pos(p.getX(), p.getY(), p.getZ());
    }

    private static BlockPos blockPos(WireTrace.Pos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }

    private static boolean isWire(Level level, WireTrace.Pos p) {
        return level.getBlockState(blockPos(p)).getBlock() instanceof WireBlock;
    }

    /** Every position of a block of the wanted kind that is wired to {@code start}. */
    private static List<BlockPos> wiredTo(Level level, BlockPos start, Block wanted) {
        if (!level.isLoaded(start)) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (WireTrace.Pos p : WireTrace.findTargets(pos(start), q -> level.isLoaded(blockPos(q)) && isWire(level, q),
                q -> level.isLoaded(blockPos(q)) && level.getBlockState(blockPos(q)).getBlock() == wanted, MAX_VISITED)) {
            out.add(blockPos(p));
        }
        return out;
    }

    /**
     * The visual snail a Black Transponder Snail block is wired to - its display - or null. If it is wired to several, the nearest one.
     */
    @Nullable
    public static BlockPos findDisplay(Level level, BlockPos blackSnail) {
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos candidate : wiredTo(level, blackSnail, ModBlocks.VISUAL_TRANSPONDER_SNAIL_TRANSMITTER.get())) {
            double d = candidate.distSqr(blackSnail);
            if (d < bestSq || (d == bestSq && best != null && candidate.asLong() < best.asLong())) {
                bestSq = d;
                best = candidate;
            }
        }
        return best;
    }

    /** Is this visual snail (with a transmitter) wired to a Black Transponder Snail? Then it is that snail's display and nothing else. */
    public static boolean isTapped(@Nullable Level level, @Nullable BlockPos snail) {
        if (level == null || snail == null || level.isClientSide || !isTapCapable(level, snail)) return false;
        GlobalPos key = GlobalPos.of(level.dimension(), snail);
        long now = System.currentTimeMillis();
        Cached cached = TAPPED_CACHE.get(key);
        if (cached != null && cached.version() == wireVersion && now - cached.atMs() < CACHE_MS) return cached.tapped();
        boolean tapped = !wiredTo(level, snail, ModBlocks.BLACK_TRANSPONDER_SNAIL_BLOCK.get()).isEmpty();
        TAPPED_CACHE.put(key, new Cached(wireVersion, now, tapped));
        return tapped;
    }

    // =====================================================================================================
    // When wires or snails change
    // =====================================================================================================

    /**
     * A wire (or a snail on the wire network) was placed or broken. What is tapped may have changed: a visual snail that is now wired to a
     * black snail leaves its call or stream, and the protection of every connected visual call is looked at again.
     */
    public static void onWireNetworkChanged(Level level, BlockPos changed) {
        if (level == null || level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        wireVersion++;
        VisualCallManager calls = VisualCallManager.peek();
        if (calls != null) {
            for (TransponderSnailBlockEntity be : new ArrayList<>(VisualSnailRegistry.all())) {
                if (be.isRemoved() || be.getLevel() != level || !isTapCapable(level, be.getBlockPos())) continue;
                GlobalPos gp = GlobalPos.of(level.dimension(), be.getBlockPos());
                if (calls.isInCall(gp) && isTapped(level, be.getBlockPos())) {
                    calls.endCallsOf(gp, "connected to a Black Transponder Snail");
                }
            }
            syncAllProtection(serverLevel, calls);
        }
    }

    // =====================================================================================================
    // Protection: telling the white snail about visual calls (it lights up like it does for a phone call)
    // =====================================================================================================

    /**
     * The White Transponder Snail a visual snail (with a transmitter) is wired to, or null. Found with the SAME wire trace the black snail's
     * tap uses (not the white snail manager's own trace and cache), so the two always agree about what is wired to what. If several, the nearest.
     */
    @Nullable
    public static BlockPos protectingWhite(@Nullable Level level, @Nullable BlockPos snail) {
        if (level == null || snail == null || level.isClientSide || !isTapCapable(level, snail)) return null;
        GlobalPos key = GlobalPos.of(level.dimension(), snail);
        long now = System.currentTimeMillis();
        CachedWhite cached = WHITE_CACHE.get(key);
        if (cached != null && cached.version() == wireVersion && now - cached.atMs() < CACHE_MS) return cached.white();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (WireTrace.Pos p : WireTrace.findTargets(pos(snail), q -> level.isLoaded(blockPos(q)) && isWire(level, q),
                q -> level.isLoaded(blockPos(q)) && level.getBlockState(blockPos(q)).getBlock() instanceof WhiteTransponderSnailBlock, MAX_VISITED)) {
            BlockPos candidate = blockPos(p);
            double d = candidate.distSqr(snail);
            if (d < bestSq || (d == bestSq && best != null && candidate.asLong() < best.asLong())) {
                bestSq = d;
                best = candidate;
            }
        }
        WHITE_CACHE.put(key, new CachedWhite(wireVersion, now, best));
        return best;
    }

    /** Is this visual snail wired to a White Transponder Snail? (Only a snail with a transmitter can be.) */
    public static boolean isProtected(Level level, BlockPos snail) {
        return protectingWhite(level, snail) != null;
    }

    /** The white snail is told whether this end of a connected visual call is protected by it (and the state of the white snail follows). */
    public static void syncProtection(ServerLevel level, GlobalPos end, boolean inConnectedCall) {
        BlockPos whiteNow = inConnectedCall ? protectingWhite(level, end.pos()) : null;
        BlockPos whiteBefore = REPORTED_PROTECTED.get(end);
        if (java.util.Objects.equals(whiteNow, whiteBefore)) return;
        WhiteSnailProtectionManager white = WhiteSnailProtectionManager.getInstance();
        if (whiteBefore != null) {
            REPORTED_PROTECTED.remove(end);
            white.onVisualProtectionEnded(level, end.pos(), whiteBefore);
            LOGGER.info("VisualTapManager: the visual snail at {} is no longer protected by the White Transponder Snail at {}", end.pos(), whiteBefore);
        }
        if (whiteNow != null) {
            REPORTED_PROTECTED.put(end, whiteNow);
            white.onVisualProtectionStarted(level, end.pos(), whiteNow);
            LOGGER.info("VisualTapManager: the visual snail at {} is protected by the White Transponder Snail at {}", end.pos(), whiteNow);
        }
    }

    private static void syncAllProtection(ServerLevel level, VisualCallManager calls) {
        Set<GlobalPos> connectedEnds = new HashSet<>();
        for (VisualCallSession s : calls.callsSnapshot()) {
            if (s.isStream() || s.isEnded() || !s.dimension().equals(level.dimension())) continue;
            if (s.getState() != VisualCallSession.State.CONNECTED) continue;
            connectedEnds.add(s.callerGp());
            connectedEnds.add(s.calleeGp());
        }
        for (GlobalPos end : connectedEnds) syncProtection(level, end, true);
        for (GlobalPos end : new ArrayList<>(REPORTED_PROTECTED.keySet())) {
            if (end.dimension().equals(level.dimension()) && !connectedEnds.contains(end)) syncProtection(level, end, false);
        }
    }

    /** Called now and then (the interception manager's tick): keeps the white snails' states right as calls start and end. */
    public static void tickProtection(ServerLevel level) {
        VisualCallManager calls = VisualCallManager.peek();
        if (calls != null) syncAllProtection(level, calls);
    }

    public static void clear() {
        TAPPED_CACHE.clear();
        WHITE_CACHE.clear();
        REPORTED_PROTECTED.clear();
        wireVersion = 0L;
    }
}
