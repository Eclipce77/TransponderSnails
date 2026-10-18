package net.eclipce.transpondersnails.visual;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Visual Transmitters (cameras) that are loaded on THIS client. A block entity registers itself with the server's VisualSnailRegistry
 * only; the client has no list of snails, and the eyes of a camera that is streaming (with nobody watching) are not part of any link. So
 * the camera block entities tell this set when they load and unload on the client, and VisualScreenRenderer lights the eyes of the ones
 * whose block says they are live. Common code (no client classes), only ever filled on a client.
 */
public final class VisualCameraPositions {

    private VisualCameraPositions() {}

    private static final Set<BlockPos> POSITIONS = ConcurrentHashMap.newKeySet();

    public static void add(BlockPos pos) {
        POSITIONS.add(pos.immutable());
    }

    public static void remove(BlockPos pos) {
        POSITIONS.remove(pos);
    }

    public static boolean isEmpty() {
        return POSITIONS.isEmpty();
    }

    public static Set<BlockPos> all() {
        return Collections.unmodifiableSet(POSITIONS);
    }

    public static void clear() {
        POSITIONS.clear();
    }
}
