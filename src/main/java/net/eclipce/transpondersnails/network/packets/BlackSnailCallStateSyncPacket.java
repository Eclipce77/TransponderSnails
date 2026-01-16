package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.voice.client.PortableBlackSnailCallStateManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client: Syncs Portable Black Snail call state
 * Sent whenever state changes (searching, intercepting, audio activity, idle)
 */
public class BlackSnailCallStateSyncPacket {

    private final UUID playerId;
    private final int stateOrdinal; // CallState enum ordinal

    public BlackSnailCallStateSyncPacket(UUID playerId, PortableBlackSnailCallStateManager.CallState state) {
        this.playerId = playerId;
        this.stateOrdinal = state.ordinal();
    }

    public BlackSnailCallStateSyncPacket(FriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        this.stateOrdinal = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeInt(stateOrdinal);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        // Verify we're on the client reception side
        if (!context.getDirection().getReceptionSide().isClient()) {
            System.err.println("[BLACK-SNAIL-STATE] ERROR: Packet received on wrong side!");
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                try {
                    // Convert ordinal back to enum
                    PortableBlackSnailCallStateManager.CallState state =
                            PortableBlackSnailCallStateManager.CallState.values()[stateOrdinal];

                    // Update client-side state
                    PortableBlackSnailCallStateManager.getInstance().setState(playerId, state);

                    // ✅ VERBOSE LOGGING
                    System.out.println("[BLACK-SNAIL-STATE] ✅ Packet received on CLIENT");
                    System.out.println("[BLACK-SNAIL-STATE]    Player: " + playerId.toString().substring(0, 8));
                    System.out.println("[BLACK-SNAIL-STATE]    State: " + state + " (predicate value=" + state.getPredicateValue() + ")");
                    System.out.println("[BLACK-SNAIL-STATE]    State manager updated");

                } catch (Exception e) {
                    System.err.println("[BLACK-SNAIL-STATE] ❌ ERROR in packet handler: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });

        context.setPacketHandled(true);
    }
}