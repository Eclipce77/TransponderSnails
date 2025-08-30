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
 * Client → Server: Player response to call invitation (accept/decline)
 */
public class CallResponsePacket {
    private final UUID callId;
    private final boolean accepted; // true for accept, false for decline
    private final String responseType; // "accept", "decline", "timeout"

    public CallResponsePacket(UUID callId, boolean accepted, String responseType) {
        this.callId = callId;
        this.accepted = accepted;
        this.responseType = responseType;
    }

    public CallResponsePacket(FriendlyByteBuf buf) {
        this.callId = buf.readUUID();
        this.accepted = buf.readBoolean();
        this.responseType = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(callId);
        buf.writeBoolean(accepted);
        buf.writeUtf(responseType);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            try {
                TransponderCallManager callManager = TransponderSnails.getCallManager();
                if (callManager == null) {
                    player.sendSystemMessage(Component.literal("Call system not available!")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    return;
                }

                if (accepted) {
                    // Player accepted the call
                    if (callManager.acceptCall(player)) {
                        player.sendSystemMessage(Component.literal("Call accepted!")
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                        System.out.println("CallResponsePacket: Player " + player.getName().getString() +
                                " accepted call " + callId);
                    } else {
                        player.sendSystemMessage(Component.literal("Failed to accept call - it may no longer be available")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        System.out.println("CallResponsePacket: Player " + player.getName().getString() +
                                " failed to accept call " + callId);
                    }
                } else {
                    // Player declined the call
                    // Remove the player's call request to decline it
                    // Note: The TransponderCallManager doesn't have a direct decline method,
                    // but we can simulate it by just ignoring the invitation

                    String reason = switch (responseType) {
                        case "decline" -> "declined the call";
                        case "timeout" -> "call timed out";
                        default -> "did not answer";
                    };

                    player.sendSystemMessage(Component.literal("Call " + reason)
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));

                    System.out.println("CallResponsePacket: Player " + player.getName().getString() +
                            " " + reason + " (call " + callId + ")");

                    // TODO: Notify the caller that the call was declined
                    // You might want to add a decline method to TransponderCallManager
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

    // Getters
    public UUID getCallId() { return callId; }
    public boolean isAccepted() { return accepted; }
    public String getResponseType() { return responseType; }
}