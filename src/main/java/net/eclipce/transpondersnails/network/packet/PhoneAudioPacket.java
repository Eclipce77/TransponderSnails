package net.eclipce.transpondersnails.network.packet;

import net.eclipce.transpondersnails.voice.TransponderSnailAudioPlugin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PhoneAudioPacket {

    private final UUID callId;
    private final byte[] opusData;

    public PhoneAudioPacket(UUID callId, byte[] opusData) {
        this.callId = callId;
        this.opusData = opusData;
    }

    public static void encode(PhoneAudioPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.callId);
        buf.writeInt(pkt.opusData.length);
        buf.writeBytes(pkt.opusData);
    }

    public static PhoneAudioPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int len = buf.readInt();
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PhoneAudioPacket(id, data);
    }

    public static void handle(PhoneAudioPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // Run on the server thread
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return; // should not happen, but guard anyway
            }
            // Dispatch into your audio‐plugin
            TransponderSnailAudioPlugin.onPhoneAudio(pkt.callId, pkt.opusData);
        });
        context.setPacketHandled(true);
    }
}
