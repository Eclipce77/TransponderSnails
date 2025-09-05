package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: Player response to call events (accept, reject, hang up)
 */
public class CallResponsePacket {

    public enum Response {
        ACCEPT,     // Accept an incoming call
        REJECT,     // Reject an incoming call
        HANG_UP     // End an active call
    }

    private final Response response;
    private final UUID callId;

    public CallResponsePacket(Response response, UUID callId) {
        this.response = response;
        this.callId = callId;
    }

    public CallResponsePacket(FriendlyByteBuf buf) {
        this.response = buf.readEnum(Response.class);
        this.callId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(response);
        buf.writeUUID(callId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            TransponderCallManager callManager = TransponderSnails.getCallManager();
            if (callManager == null) {
                player.sendSystemMessage(Component.literal("Voice chat system not available!")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }

            try {
                switch (response) {
                    case ACCEPT:
                        handleAcceptCall(callManager, player, callId);
                        break;
                    case REJECT:
                        handleRejectCall(callManager, player, callId);
                        break;
                    case HANG_UP:
                        handleHangUp(callManager, player, callId);
                        break;
                    default:
                        System.err.println("CallResponsePacket: Unknown response type: " + response);
                        break;
                }
            } catch (Exception e) {
                System.err.println("CallResponsePacket: Error handling call response: " + e.getMessage());
                e.printStackTrace();
                player.sendSystemMessage(Component.literal("Error processing call response: " + e.getMessage())
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleAcceptCall(TransponderCallManager callManager, ServerPlayer player, UUID callId) {
        System.out.println("CallResponsePacket: Player " + player.getName().getString() + " accepting call " + callId.toString().substring(0, 8));

        boolean success = callManager.acceptCall(player, callId);
        if (success) {
            System.out.println("CallResponsePacket: Successfully accepted call");
        } else {
            System.err.println("CallResponsePacket: Failed to accept call");
        }
    }

    private void handleRejectCall(TransponderCallManager callManager, ServerPlayer player, UUID callId) {
        System.out.println("CallResponsePacket: Player " + player.getName().getString() + " rejecting call " + callId.toString().substring(0, 8));

        boolean success = callManager.rejectCall(player, callId);
        if (success) {
            System.out.println("CallResponsePacket: Successfully rejected call");
        } else {
            System.err.println("CallResponsePacket: Failed to reject call");
        }
    }

    private void handleHangUp(TransponderCallManager callManager, ServerPlayer player, UUID callId) {
        System.out.println("CallResponsePacket: Player " + player.getName().getString() + " hanging up call " + callId.toString().substring(0, 8));

        callManager.endCall(player);
        System.out.println("CallResponsePacket: Call ended");
    }

    // Getters
    public Response getResponse() {
        return response;
    }

    public UUID getCallId() {
        return callId;
    }
}