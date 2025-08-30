// Packet to sync snail number from server to client
package net.eclipce.transpondersnails.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.eclipce.transpondersnails.screen.DialingMenu;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SnailNumberSyncPacket {
    private final int snailNumber;

    public SnailNumberSyncPacket(int snailNumber) {
        this.snailNumber = snailNumber;
    }

    public SnailNumberSyncPacket(FriendlyByteBuf buf) {
        this.snailNumber = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(snailNumber);
    }

    // Getter method for accessing the snail number
    public int getSnailNumber() {
        return snailNumber;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-side handling with improved logging and error checking
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;

            if (player == null) {
                System.out.println("SnailNumberSyncPacket: Player is null, cannot sync snail number");
                return;
            }

            AbstractContainerMenu menu = player.containerMenu;
            if (menu instanceof DialingMenu dialingMenu) {
                System.out.println("SnailNumberSyncPacket: Setting client snail number to #" + snailNumber);
                dialingMenu.setOwnSnailNumber(snailNumber);
                System.out.println("SnailNumberSyncPacket: Successfully synced snail number #" + snailNumber + " to client");
            } else {
                System.out.println("SnailNumberSyncPacket: Player menu is not DialingMenu (current menu: " +
                        (menu != null ? menu.getClass().getSimpleName() : "null") +
                        "), cannot sync snail number #" + snailNumber);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}