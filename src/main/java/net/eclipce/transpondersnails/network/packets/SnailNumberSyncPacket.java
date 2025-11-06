package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.screen.DialingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: Synchronizes snail number from server to client
 * FIXED: Uses proper side checking instead of DistExecutor
 */
public class SnailNumberSyncPacket {
    private final int snailNumber;

    public SnailNumberSyncPacket(int snailNumber) {
        this.snailNumber = snailNumber;
        System.out.println("[SNAIL-SYNC] Packet created with number: " + snailNumber);
    }

    public SnailNumberSyncPacket(FriendlyByteBuf buf) {
        this.snailNumber = buf.readInt();
        System.out.println("[SNAIL-SYNC] Packet decoded with number: " + snailNumber);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(snailNumber);
        System.out.println("[SNAIL-SYNC] Packet encoded with number: " + snailNumber);
    }

    public int getSnailNumber() {
        return snailNumber;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        // Check if we're actually on the client reception side
        if (!context.getDirection().getReceptionSide().isClient()) {
            System.err.println("[SNAIL-SYNC] ERROR: Packet received on wrong side!");
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            System.out.println("[SNAIL-SYNC] Handling packet on client side for number: " + snailNumber);

            // Use DistExecutor.safeCallWhenOn for safer client-side execution
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                try {
                    System.out.println("[SNAIL-SYNC] Executing client-side handler...");

                    // Get the Minecraft instance
                    Minecraft mc = Minecraft.getInstance();

                    if (mc.player != null && mc.player.containerMenu instanceof DialingMenu menu) {
                        System.out.println("[SNAIL-SYNC] Setting snail number to: " + snailNumber);
                        menu.setOwnSnailNumber(snailNumber);
                        System.out.println("[SNAIL-SYNC] Successfully synced snail number!");
                    } else {
                        System.err.println("[SNAIL-SYNC] WARNING: Player doesn't have DialingMenu open");
                    }
                } catch (Exception e) {
                    System.err.println("[SNAIL-SYNC] ERROR handling packet: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });

        context.setPacketHandled(true);
    }
}