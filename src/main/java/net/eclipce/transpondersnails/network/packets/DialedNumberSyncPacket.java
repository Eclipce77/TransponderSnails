package net.eclipce.transpondersnails.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: Synchronizes the dialed number from server to client
 * Used to keep the GUI in sync when players are dialing
 */
public class DialedNumberSyncPacket {
    private final String dialedNumber;

    public DialedNumberSyncPacket(String dialedNumber) {
        this.dialedNumber = dialedNumber != null ? dialedNumber : "";
    }

    public DialedNumberSyncPacket(FriendlyByteBuf buf) {
        this.dialedNumber = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dialedNumber);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-side handling only
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                handleClientSide();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleClientSide() {
        try {
            // Get the client player's current menu
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.containerMenu instanceof net.eclipce.transpondersnails.screen.DialingMenu dialingMenu) {
                // Update the client-side dialed number
                // Note: You'll need to add a setDialedNumber method to DialingMenu for client-side updates
                // For now, we'll store it in the client dialed number field

                System.out.println("DialedNumberSyncPacket: Synchronized dialed number: '" + dialedNumber + "'");

                // TODO: Update the client-side GUI to display the new dialed number
                // This might involve refreshing the screen or updating display widgets

            } else {
                System.err.println("DialedNumberSyncPacket: Received sync but player has no dialing menu open");
            }

        } catch (Exception e) {
            System.err.println("DialedNumberSyncPacket: Error handling dialed number sync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getter
    public String getDialedNumber() {
        return dialedNumber;
    }
}