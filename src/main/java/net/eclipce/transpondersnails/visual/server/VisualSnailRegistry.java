package net.eclipce.transpondersnails.visual.server;

import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
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

    public static void clear() {
        SNAILS.clear();
    }
}
