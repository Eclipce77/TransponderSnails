package net.eclipce.transpondersnails.visual.server;

import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.VisualRoles;
import net.eclipce.transpondersnails.visual.VisualSnailRole;
import net.eclipce.transpondersnails.visual.network.VisualConfigOpenPacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of the projector settings menu (screen size and offsets of a Visual Transponder Snail). Independent of the
 * voice chat and of any running call: the settings can be changed at any time.
 */
public final class VisualProjectorConfig {

    /** The player must be within this many blocks of the snail to open the menu or change its settings. */
    private static final double MAX_DISTANCE = 8.0;

    private VisualProjectorConfig() {}

    /** Tells the player's client to open the settings menu of this snail. */
    public static void open(ServerPlayer player, TransponderSnailBlockEntity snail) {
        VisualNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new VisualConfigOpenPacket(snail.getBlockPos(), snail.getScreenSize(),
                        snail.getScreenOffsetSide(), snail.getScreenOffsetUp(), snail.getScreenOffsetBack()));
    }

    /** A client asks to change the settings. Validates everything; silently ignores anything that does not check out. */
    public static void handleUpdate(ServerPlayer player, BlockPos pos, int size, float side, float up, float back) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE * MAX_DISTANCE) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TransponderSnailBlockEntity snail)) return;
        if (VisualRoles.ofEntity(snail) != VisualSnailRole.DUPLEX) return; // only the projector has a screen

        snail.setScreenConfig(size, side, up, back); // clamps + snaps, stores, syncs to every client; no-op if unchanged
    }
}
