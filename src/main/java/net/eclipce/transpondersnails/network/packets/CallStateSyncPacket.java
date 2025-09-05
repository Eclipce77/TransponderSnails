package net.eclipce.transpondersnails.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client: Synchronizes call state information to the client
 * Used to update the GUI with current call status
 */
public class CallStateSyncPacket {

    public enum CallState {
        IDLE,           // No active call
        DIALING,        // Initiating a call
        RINGING_OUT,    // Waiting for answer (caller side)
        RINGING_IN,     // Incoming call (recipient side)
        CONNECTED,      // Call is active
        BUSY,           // Target is busy
        DISCONNECTED    // Call ended/failed
    }

    private final CallState callState;
    private final UUID callId;        // null if no active call
    private final int otherSnailNumber;  // -1 if not applicable
    private final String statusMessage;

    public CallStateSyncPacket(CallState callState, @Nullable UUID callId, int otherSnailNumber, String statusMessage) {
        this.callState = callState;
        this.callId = callId;
        this.otherSnailNumber = otherSnailNumber;
        this.statusMessage = statusMessage != null ? statusMessage : "";
    }

    public CallStateSyncPacket(FriendlyByteBuf buf) {
        this.callState = buf.readEnum(CallState.class);

        boolean hasCallId = buf.readBoolean();
        this.callId = hasCallId ? buf.readUUID() : null;

        this.otherSnailNumber = buf.readInt();
        this.statusMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(callState);

        buf.writeBoolean(callId != null);
        if (callId != null) {
            buf.writeUUID(callId);
        }

        buf.writeInt(otherSnailNumber);
        buf.writeUtf(statusMessage);
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
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;

            if (player == null) {
                System.err.println("CallStateSyncPacket: Player is null on client side");
                return;
            }

            // Update the dialing menu if it's open
            if (player.containerMenu instanceof net.eclipce.transpondersnails.screen.DialingMenu dialingMenu) {
                dialingMenu.updateCallState(callState, callId, otherSnailNumber, statusMessage);

                System.out.println("CallStateSyncPacket: Updated client call state to " + callState +
                        (callId != null ? " (call " + callId.toString().substring(0, 8) + ")" : "") +
                        (otherSnailNumber != -1 ? " with snail #" + otherSnailNumber : "") +
                        (!statusMessage.isEmpty() ? " - " + statusMessage : ""));

            } else {
                System.out.println("CallStateSyncPacket: Received call state " + callState + " but no dialing menu is open");
            }

        } catch (Exception e) {
            System.err.println("CallStateSyncPacket: Error handling call state sync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getters
    public CallState getCallState() {
        return callState;
    }

    @Nullable
    public UUID getCallId() {
        return callId;
    }

    public int getOtherSnailNumber() {
        return otherSnailNumber;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}