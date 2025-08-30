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
 * Bidirectional: Handles call end notifications
 * Server → Client: Notifies client that a call has ended
 * Client → Server: Player requests to end their current call
 */
public class CallEndPacket {
    private final UUID callId;
    private final String reason; // "hangup", "disconnect", "error", "timeout"
    private final String message; // Human readable reason

    public CallEndPacket(UUID callId, String reason, String message) {
        this.callId = callId;
        this.reason = reason;
        this.message = message;
    }

    public CallEndPacket(FriendlyByteBuf buf) {
        this.callId = buf.readUUID();
        this.reason = buf.readUtf();
        this.message = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(callId);
        buf.writeUtf(reason);
        buf.writeUtf(message);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isServer()) {
                // Client-side: Handle call end notification from server
                handleClientSide();
            } else {
                // Server-side: Handle player request to end call
                handleServerSide(ctx.get().getSender());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleClientSide() {
        try {
            // Determine message color based on reason
            net.minecraft.ChatFormatting color = switch (reason.toLowerCase()) {
                case "hangup" -> net.minecraft.ChatFormatting.GRAY;
                case "disconnect" -> net.minecraft.ChatFormatting.YELLOW;
                case "error" -> net.minecraft.ChatFormatting.RED;
                case "timeout" -> net.minecraft.ChatFormatting.DARK_GRAY;
                default -> net.minecraft.ChatFormatting.WHITE;
            };

            // Show call end message
            Component endMessage = Component.literal("[Call Ended] " + message).withStyle(color);
            net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(endMessage);

            // Play hang up sound
            try {
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.playNotifySound(net.minecraft.sounds.SoundEvents.DISPENSER_FAIL,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 0.8f);
                }
            } catch (Exception e) {
                System.err.println("CallEndPacket: Error playing hang up sound: " + e.getMessage());
            }

            System.out.println("CallEndPacket: Call " + callId + " ended - " + reason + ": " + message);

        } catch (Exception e) {
            System.err.println("CallEndPacket: Error handling call end notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleServerSide(ServerPlayer player) {
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

            // Check if player is actually in a call
            if (!callManager.isInCall(player.getUUID())) {
                player.sendSystemMessage(Component.literal("You are not in a call!")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
                return;
            }

            // End the call
            callManager.leaveCall(player);

            // Success message
            player.sendSystemMessage(Component.literal("Call ended")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));

            System.out.println("CallEndPacket: Player " + player.getName().getString() +
                    " requested to end call " + callId);

        } catch (Exception e) {
            System.err.println("CallEndPacket: Error handling player call end request: " + e.getMessage());
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Error ending call: " + e.getMessage())
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    // Getters
    public UUID getCallId() { return callId; }
    public String getReason() { return reason; }
    public String getMessage() { return message; }
}