package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Packet to clear the dialed number
public class ClearDialedNumberPacket {
    private final BlockPos blockPos;

    public ClearDialedNumberPacket(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public ClearDialedNumberPacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                BlockEntity blockEntity = level.getBlockEntity(blockPos);

                if (blockEntity instanceof TransponderSnailBlockEntity snailEntity) {
                    // Verify player is close enough to the block entity
                    if (player.distanceToSqr(blockPos.getX(), blockPos.getY(), blockPos.getZ()) <= 64.0) {
                        snailEntity.clearDialedNumber();
                    }
                }
            }
        });
        return true;
    }
}