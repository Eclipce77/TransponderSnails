package net.eclipce.transpondersnails.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client: Updates about call status changes
 * Used to notify clients about call state changes like connected, ringing, busy, etc.
 */
public class CallStatusPacket {
    private final UUID callId;
    private final String status; // "ringing", "connected", "busy", "failed", "ended"
    private final String message; // Human readable message
    private final boolean playSound; // Whether to play a sound effect

    public CallStatusPacket(UUID callId, String status, String message, boolean playSound) {
        this.callId = callId;
        this.status = status;
        this.message = message;
        this.playSound = playSound;
    }

    public CallStatusPacket(FriendlyByteBuf buf) {
        this.callId = buf.readUUID();
        this.status = buf.readUtf();
        this.message = buf.readUtf();
        this.playSound = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(callId);
        buf.writeUtf(status);
        buf.writeUtf(message);
        buf.writeBoolean(playSound);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-side handling
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                handleClientSide();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleClientSide() {
        try {
            // Determine message color based on status
            net.minecraft.ChatFormatting color = switch (status.toLowerCase()) {
                case "connected" -> net.minecraft.ChatFormatting.GREEN;
                case "ringing" -> net.minecraft.ChatFormatting.YELLOW;
                case "busy", "failed" -> net.minecraft.ChatFormatting.RED;
                case "ended" -> net.minecraft.ChatFormatting.GRAY;
                default -> net.minecraft.ChatFormatting.WHITE;
            };

            // Show status message
            Component statusMessage = Component.literal("[Call] " + message).withStyle(color);
            net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(statusMessage);

            // Play sound effect if requested
            if (playSound) {
                playStatusSound(status);
            }

            System.out.println("CallStatusPacket: Call " + callId + " status changed to " + status + ": " + message);

        } catch (Exception e) {
            System.err.println("CallStatusPacket: Error handling call status update: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void playStatusSound(String status) {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.sounds.SoundEvent soundEvent = null;

            // Map status to appropriate sound
            switch (status.toLowerCase()) {
                case "ringing":
                    soundEvent = net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get();
                    break;
                case "connected":
                    soundEvent = net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP;
                    break;
                case "busy":
                    soundEvent = net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.get();
                    break;
                case "failed":
                    soundEvent = net.minecraft.sounds.SoundEvents.ITEM_BREAK;
                    break;
                case "ended":
                    soundEvent = net.minecraft.sounds.SoundEvents.DISPENSER_FAIL;
                    break;
                default:
                    soundEvent = net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get();
                    break;
            }

            if (soundEvent != null && minecraft.player != null) {
                minecraft.player.playNotifySound(soundEvent,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.0f);
            }

        } catch (Exception e) {
            System.err.println("CallStatusPacket: Error playing status sound: " + e.getMessage());
        }
    }

    // Getters
    public UUID getCallId() { return callId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public boolean shouldPlaySound() { return playSound; }
}