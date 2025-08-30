package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Packet to initiate a call from the GUI
public class InitiateCallPacket {
    private final BlockPos blockPos;

    public InitiateCallPacket(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public InitiateCallPacket(FriendlyByteBuf buf) {
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
                        boolean success = snailEntity.initiateCall();
                        if (!success) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Call failed! Invalid number or target not found."));
                        }
                    }
                }
            }
        });
        return true;
    }
}