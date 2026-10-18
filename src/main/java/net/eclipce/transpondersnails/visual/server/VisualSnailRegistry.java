package net.eclipce.transpondersnails.visual.server;

import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every loaded, placed Visual Transponder Snail (server side).
 *
 * Deliberately independent of TransponderCallManager: snails load with their chunks, which can happen before the
 * Simple Voice Chat plugin has created the call manager, so registration must never depend on it.
 */
public final class VisualSnailRegistry {

    private static final Map<GlobalPos, TransponderSnailBlockEntity> SNAILS = new ConcurrentHashMap<>();

    private VisualSnailRegistry() {}

    public static void register(TransponderSnailBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        SNAILS.put(GlobalPos.of(level.dimension(), be.getBlockPos()), be);
    }

    public static void unregister(TransponderSnailBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) return;
        // only remove if it is still THIS block entity (a replaced block entity may already have re-registered)
        SNAILS.remove(GlobalPos.of(level.dimension(), be.getBlockPos()), be);
    }

    @Nullable
    public static TransponderSnailBlockEntity get(GlobalPos pos) {
        return SNAILS.get(pos);
    }

    public static Collection<TransponderSnailBlockEntity> all() {
        return new ArrayList<>(SNAILS.values());
    }

    /**
     * The loaded, placed Visual Transponder Snail that holds this snail number, or null (not loaded, not placed, or not a visual
     * snail's number). Server thread.
     */
    @Nullable
    public static TransponderSnailBlockEntity findByNumber(int snailNumber) {
        if (snailNumber == -1) return null;
        for (TransponderSnailBlockEntity be : SNAILS.values()) {
            if (!be.isRemoved() && be.getLevel() != null && be.hasAssignedNumber() && be.getSnailNumber() == snailNumber) {
                return be;
            }
        }
        return null;
    }

    /** Where the placed visual snail with this number is, or null. (A snail carried as an item is found by TransponderCallManager.locateSnail.) */
    @Nullable
    public static TransponderCallManager.SnailLocation locate(int snailNumber) {
        TransponderSnailBlockEntity be = findByNumber(snailNumber);
        if (be == null) return null;
        BlockPos pos = be.getBlockPos();
        return new TransponderCallManager.SnailLocation(true, be.getLevel().dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(), null, null);
    }

    /**
     * Applies an admin removal of numbers (SnailNumberRegistry.revokeSnail / revokeAllSnails) to the loaded placed visual snails: each
     * one that held one of these numbers forgets its identity and registers a new number the next time it is used. A video call it is in
     * is not number based and carries on. Call AFTER the registry has revoked the numbers. Server thread.
     *
     * @return one description per snail that was reset, e.g. "#1234 at minecraft:overworld 10 64 -3"
     */
    public static List<String> applyRevocation(Collection<Integer> numbers) {
        List<String> reset = new ArrayList<>();
        for (int number : new HashSet<>(numbers)) {
            TransponderSnailBlockEntity be = findByNumber(number);
            if (be == null) continue;
            BlockPos pos = be.getBlockPos();
            reset.add("#" + number + " at " + be.getLevel().dimension().location() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            be.revokeSnailIdentity();
        }
        return reset;
    }

    public static void clear() {
        SNAILS.clear();
    }
}
