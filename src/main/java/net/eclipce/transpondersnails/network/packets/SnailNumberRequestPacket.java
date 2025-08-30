package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.screen.DialingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: Requests snail number synchronization
 * Used when client needs to get the snail number for the GUI
 */
public class SnailNumberRequestPacket {
    // This packet has no data - it's just a request

    public SnailNumberRequestPacket() {
        // Empty constructor
    }

    public SnailNumberRequestPacket(FriendlyByteBuf buf) {
        // Nothing to read
    }

    public void encode(FriendlyByteBuf buf) {
        // Nothing to write
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            try {
                // Check if player has a dialing menu open
                if (player.containerMenu instanceof DialingMenu dialingMenu) {
                    int snailNumber = dialingMenu.getOwnSnailNumber();

                    if (snailNumber != -1) {
                        // Send the snail number back to client
                        ModPackets.sendToPlayer(new SnailNumberSyncPacket(snailNumber), player);
                        System.out.println("SnailNumberRequestPacket: Sent snail number #" + snailNumber +
                                " to " + player.getName().getString());
                    } else {
                        // No number available yet
                        System.out.println("SnailNumberRequestPacket: Player " + player.getName().getString() +
                                " requested snail number but none is available");

                        // Send a message to let them know the number is being assigned
                        player.sendSystemMessage(Component.literal("Assigning snail number...")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW));
                    }
                } else {
                    System.err.println("SnailNumberRequestPacket: Player " + player.getName().getString() +
                            " requested snail number but has no dialing menu open");
                }

            } catch (Exception e) {
                System.err.println("SnailNumberRequestPacket: Error handling snail number request: " + e.getMessage());
                e.printStackTrace();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}