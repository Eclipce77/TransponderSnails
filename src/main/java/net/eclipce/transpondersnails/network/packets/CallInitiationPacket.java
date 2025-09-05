package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: Request to initiate a call to a specific snail number
 */
public class CallInitiationPacket {
    private final int targetSnailNumber;
    private final int callerSnailNumber;

    public CallInitiationPacket(int targetSnailNumber, int callerSnailNumber) {
        this.targetSnailNumber = targetSnailNumber;
        this.callerSnailNumber = callerSnailNumber;
    }

    public CallInitiationPacket(FriendlyByteBuf buf) {
        this.targetSnailNumber = buf.readInt();
        this.callerSnailNumber = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(targetSnailNumber);
        buf.writeInt(callerSnailNumber);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            System.out.println("CallInitiationPacket: Player " + player.getName().getString() +
                    " (snail #" + callerSnailNumber + ") attempting to call snail #" + targetSnailNumber);

            // Validate caller's snail number matches what we expect
            if (player.containerMenu instanceof net.eclipce.transpondersnails.screen.DialingMenu dialingMenu) {
                int actualCallerNumber = dialingMenu.getOwnSnailNumber();
                if (actualCallerNumber != callerSnailNumber) {
                    player.sendSystemMessage(Component.literal("Snail number mismatch! Please reopen the menu.")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    return;
                }
            }

            // Check if trying to call own number
            if (targetSnailNumber == callerSnailNumber) {
                player.sendSystemMessage(Component.literal("You cannot call your own snail!")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
                return;
            }

            // Get the call manager and initiate the call
            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager == null) {
                player.sendSystemMessage(Component.literal("Voice chat system not available!")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            // Use the call manager to initiate the call
            boolean success = callManager.initiateCallBySnailNumber(player, callerSnailNumber, targetSnailNumber);

            if (success) {
                // Clear the dialed number after successful call initiation
                if (player.containerMenu instanceof net.eclipce.transpondersnails.screen.DialingMenu dialingMenu) {
                    if (dialingMenu.getBlockEntity() != null) {
                        dialingMenu.getBlockEntity().clearDialedNumber();
                    } else {
                        dialingMenu.clearDialedNumber();
                    }
                }

                player.sendSystemMessage(Component.literal("Calling snail #" + targetSnailNumber + "...")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
            // Error messages are handled by the call manager
        });
        context.setPacketHandled(true);
    }

    public int getTargetSnailNumber() {
        return targetSnailNumber;
    }

    public int getCallerSnailNumber() {
        return callerSnailNumber;
    }
}