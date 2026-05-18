package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.screen.DialingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: Synchronizes the dialed number from server to client
 * FIXED: Uses proper side checking instead of problematic DistExecutor pattern
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
        NetworkEvent.Context context = ctx.get();

        // Verify we're on the client reception side
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                try {
                    Minecraft mc = Minecraft.getInstance();

                    if (mc.player != null && mc.player.containerMenu instanceof DialingMenu menu) {
                        menu.setClientDialedNumber(dialedNumber);
                    }
                } catch (Exception e) {
                    System.err.println("[DIALED-NUMBER-SYNC] ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });

        context.setPacketHandled(true);
    }

    public String getDialedNumber() {
        return dialedNumber;
    }
}