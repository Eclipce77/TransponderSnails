package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.voice.client.BabyBlackSnailCallStateManager;
import net.eclipce.transpondersnails.voice.client.BlackSnailCallStateManager;
import net.eclipce.transpondersnails.voice.client.PortableBlackSnailCallStateManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet for syncing Black Transponder Snail call states to clients.
 *
 * This packet updates ALL THREE client-side state managers:
 * - PortableBlackSnailCallStateManager (for Portable Black Transponder Snail)
 * - BlackSnailCallStateManager (for adult Black Transponder Snail)
 * - BabyBlackSnailCallStateManager (for Baby Black Transponder Snail)
 *
 * Each manager translates the logical state to its own predicate values
 * that match their respective model JSON files.
 */
public class BlackSnailCallStateSyncPacket {

    private final UUID playerId;
    private final PortableBlackSnailCallStateManager.CallState state;

    /**
     * Create a new sync packet
     * @param playerId The UUID of the player whose state is being synced
     * @param state The call state using PortableBlackSnailCallStateManager.CallState as the canonical enum
     */
    public BlackSnailCallStateSyncPacket(UUID playerId, PortableBlackSnailCallStateManager.CallState state) {
        this.playerId = playerId;
        this.state = state;
    }

    /**
     * Decode packet from network buffer
     *
     * FIXED: Added bounds validation for the state ordinal to prevent
     * ArrayIndexOutOfBoundsException if a corrupted or malformed packet
     * arrives with an invalid ordinal value. Falls back to IDLE state
     * if the ordinal is out of range.
     */
    public BlackSnailCallStateSyncPacket(FriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        int stateOrdinal = buf.readInt();

        // Validate ordinal bounds to prevent ArrayIndexOutOfBoundsException
        PortableBlackSnailCallStateManager.CallState[] values = PortableBlackSnailCallStateManager.CallState.values();
        if (stateOrdinal >= 0 && stateOrdinal < values.length) {
            this.state = values[stateOrdinal];
        } else {
            System.err.println("[BlackSnailCallStateSyncPacket] WARNING: Received invalid state ordinal " +
                    stateOrdinal + " (max=" + (values.length - 1) + "), defaulting to IDLE");
            this.state = PortableBlackSnailCallStateManager.CallState.IDLE;
        }
    }

    /**
     * Encode packet to network buffer
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeInt(state.ordinal());
    }

    /**
     * Handle packet on the client side
     * Updates ALL THREE state managers so any type of black snail will display correctly
     */
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            // Execute on client only
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleOnClient());
        });

        context.setPacketHandled(true);
    }

    /**
     * Client-side handling - updates ALL THREE state managers
     */
    private void handleOnClient() {

        // Update ALL THREE state managers so any black snail type will work

        // 1. Update PortableBlackSnailCallStateManager (uses 0.25, 0.5, 0.75)
        PortableBlackSnailCallStateManager.getInstance().setState(playerId, state);

        // 2. Update BlackSnailCallStateManager (uses 0.1, 0.2, 0.3 to match black_transponder_snail.json)
        BlackSnailCallStateManager.getInstance().setStateFromPortable(playerId, state);

        // 3. Update BabyBlackSnailCallStateManager (uses 0.1, 0.2, 0.3 to match baby_black_transponder_snail.json)
        BabyBlackSnailCallStateManager.getInstance().setStateFromPortable(playerId, state);

    }

    // Getters for testing/debugging
    public UUID getPlayerId() {
        return playerId;
    }

    public PortableBlackSnailCallStateManager.CallState getState() {
        return state;
    }
}