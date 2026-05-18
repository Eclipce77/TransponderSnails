package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.screen.DialingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client: Synchronizes call state information to the client
 * FIXED: Uses proper side checking instead of problematic DistExecutor pattern
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
    private final UUID callId;
    private final int otherSnailNumber;
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
                        menu.updateCallState(callState, callId, otherSnailNumber, statusMessage);
                    }
                } catch (Exception e) {
                    System.err.println("[CALL-STATE-SYNC] ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });

        context.setPacketHandled(true);
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