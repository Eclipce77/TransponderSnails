package net.eclipce.transpondersnails.network.packet;

import net.eclipce.transpondersnails.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class IncomingCallPacket {

    private final UUID callId;
    private final int   callerNumber;

    public IncomingCallPacket(UUID callId, int callerNumber) {
        this.callId       = callId;
        this.callerNumber = callerNumber;
    }

    public static void encode(IncomingCallPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.callId);
        buf.writeInt(pkt.callerNumber);
    }

    public static IncomingCallPacket decode(FriendlyByteBuf buf) {
        return new IncomingCallPacket(buf.readUUID(), buf.readInt());
    }

    // Track which callIds have already shown the UI
    private static final Set<UUID> notified = ConcurrentHashMap.newKeySet();

    public static void handle(IncomingCallPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // **always** play the ring tone
            SoundInstance ring = SimpleSoundInstance.forLocalAmbience(
                    ModSounds.DEN_DEN_MUSHI_RINGING.get(), 1.0F, 1.0F
            );
            mc.getSoundManager().play(ring);

            // Only send the chat/buttons once per callId
            if (notified.add(pkt.callId)) {
                // Build the base notification
                MutableComponent message = Component.literal(
                        "Incoming call from Snail #" +
                                String.format("%04d", pkt.callerNumber) + " "
                );

                // [Accept] button
                MutableComponent accept = Component.literal("[Accept]")
                        .setStyle(Style.EMPTY
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/snailnumber accept " + pkt.callId
                                ))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click to accept the call")
                                ))
                        );

                // [Decline] button
                MutableComponent decline = Component.literal("[Decline]")
                        .setStyle(Style.EMPTY
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/snailnumber decline " + pkt.callId
                                ))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click to decline the call")
                                ))
                        );

                message.append(accept)
                        .append(Component.literal(" "))
                        .append(decline);

                mc.player.displayClientMessage(message, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Call this when the call ends (e.g. in HangUpPacket.handle)
     * so that the next time you use the same callId you’ll get the UI again.
     */
    public static void clear(UUID callId) {
        notified.remove(callId);
    }
}
