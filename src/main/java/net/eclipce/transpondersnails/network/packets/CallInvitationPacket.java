package net.eclipce.transpondersnails.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client: Notifies client of incoming call invitation
 * This packet tells the client GUI to show call invitation UI
 */
public class CallInvitationPacket {
    private final UUID callId;
    private final String callerName;
    private final int callerNumber;
    private final String callType;
    private final long timeoutMs;

    public CallInvitationPacket(UUID callId, String callerName, int callerNumber, String callType, long timeoutMs) {
        this.callId = callId;
        this.callerName = callerName;
        this.callerNumber = callerNumber;
        this.callType = callType;
        this.timeoutMs = timeoutMs;
    }

    public CallInvitationPacket(FriendlyByteBuf buf) {
        this.callId = buf.readUUID();
        this.callerName = buf.readUtf();
        this.callerNumber = buf.readInt();
        this.callType = buf.readUtf();
        this.timeoutMs = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(callId);
        buf.writeUtf(callerName);
        buf.writeInt(callerNumber);
        buf.writeUtf(callType);
        buf.writeLong(timeoutMs);
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
            // Show call invitation in chat
            Component inviteMessage = Component.literal("📞 Incoming call from " + callerName + " (Snail #" + callerNumber + ")")
                    .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD);

            Component actionMessage = Component.literal("Right-click your Transponder Snail or use /call accept to answer")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW);

            // Get Minecraft instance and show messages
            net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(inviteMessage);
            net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(actionMessage);

            // TODO: You could also show a custom GUI overlay here
            // TODO: Play ringing sound effect

            System.out.println("CallInvitationPacket: Received call invitation from " + callerName + " (call " + callId + ")");

        } catch (Exception e) {
            System.err.println("CallInvitationPacket: Error handling call invitation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getters
    public UUID getCallId() { return callId; }
    public String getCallerName() { return callerName; }
    public int getCallerNumber() { return callerNumber; }
    public String getCallType() { return callType; }
    public long getTimeoutMs() { return timeoutMs; }
}