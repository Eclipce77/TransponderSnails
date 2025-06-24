package net.eclipce.transpondersnails.network.packet;

import net.eclipce.transpondersnails.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class OutgoingCallPacket {

    private final UUID callId;
    private final int  targetNumber;

    // Tracks which callIds have already shown the chat line
    private static final Set<UUID> notified = new HashSet<>();

    public OutgoingCallPacket(UUID callId, int targetNumber) {
        this.callId       = callId;
        this.targetNumber = targetNumber;
    }

    public static void encode(OutgoingCallPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.callId);
        buf.writeInt(pkt.targetNumber);
    }

    public static OutgoingCallPacket decode(FriendlyByteBuf buf) {
        return new OutgoingCallPacket(buf.readUUID(), buf.readInt());
    }

    public static void handle(OutgoingCallPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Only send chat the first time we see this callId
            if (notified.add(pkt.callId)) {
                mc.player.displayClientMessage(
                        Component.literal("Calling Snail #" +
                                String.format("%04d", pkt.targetNumber) + "..."),
                        false
                );
            }

            // Always play the ring-back tone on every packet
            SoundInstance tone = SimpleSoundInstance.forLocalAmbience(
                    ModSounds.DEN_DEN_MUSHI_RINGING.get(),
                    1.0F,
                    1.0F
            );
            mc.getSoundManager().play(tone);

        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Call this when the call ends (e.g. in your HangUpPacket handler)
     * so that if you ever reuse the same UUID you’ll get the chat again.
     */
    public static void clear(UUID callId) {
        notified.remove(callId);
    }
}
