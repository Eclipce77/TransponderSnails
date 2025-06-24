package net.eclipce.transpondersnails.network.packet;

import net.eclipce.transpondersnails.client.RingingUI;
import net.eclipce.transpondersnails.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sent to both sides when ringing stops—either accepted, rejected, or timed out.
 */
public class HangUpPacket {

    private final UUID callId;

    public HangUpPacket(UUID callId) {
        this.callId = callId;
    }

    public static void encode(HangUpPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.callId);
    }

    public static HangUpPacket decode(FriendlyByteBuf buf) {
        return new HangUpPacket(buf.readUUID());
    }

    public static void handle(HangUpPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 1) Clear any “already shown” flags so future calls reuse the same ID
            IncomingCallPacket.clear(pkt.callId);
            OutgoingCallPacket.clear(pkt.callId);

            // 2) Stop the looping ringtone
            RingingUI.stopLoop(pkt.callId);

            // 3) Play the hang-up tone and show “Call ended”
            Minecraft mc = Minecraft.getInstance();  // only one mc variable
            if (mc.player != null) {
                SoundInstance hang = SimpleSoundInstance.forLocalAmbience(
                        ModSounds.DEN_DEN_MUSHI_HANG_UP.get(),
                        1.0F, 1.0F
                );
                mc.getSoundManager().play(hang);
                mc.player.displayClientMessage(
                        Component.literal("Call ended."),
                        false
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
