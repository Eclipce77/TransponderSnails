package net.eclipce.transpondersnails.network;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.network.packet.HangUpPacket;
import net.eclipce.transpondersnails.network.packet.IncomingCallPacket;
import net.eclipce.transpondersnails.network.packet.OutgoingCallPacket;
import net.eclipce.transpondersnails.network.packet.PhoneAudioPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TransponderSnails.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    /** Call this in FMLCommonSetupEvent (inside enqueueWork). */
    public static void init() {

        // 1) Client → Server
        CHANNEL.messageBuilder(PhoneAudioPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PhoneAudioPacket::encode)
                .decoder(PhoneAudioPacket::decode)
                .consumerMainThread(PhoneAudioPacket::handle)
                .add();

        // 2) Server → Client: IncomingCallPacket
        CHANNEL.messageBuilder(IncomingCallPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IncomingCallPacket::encode)
                .decoder(IncomingCallPacket::decode)
                .consumerMainThread(IncomingCallPacket::handle)
                .add();

        // 3) Server → Client: OutgoingCallPacket
        CHANNEL.messageBuilder(OutgoingCallPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OutgoingCallPacket::encode)
                .decoder(OutgoingCallPacket::decode)
                .consumerMainThread(OutgoingCallPacket::handle)
                .add();

        // 4) Server → Client: HangUpPacket
        CHANNEL.messageBuilder(HangUpPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HangUpPacket::encode)
                .decoder(HangUpPacket::decode)
                .consumerMainThread(HangUpPacket::handle)
                .add();

    }

    /** Client‐side: send a packet to the server. */
    public static <MSG> void sendToServer(MSG msg) {
        CHANNEL.sendToServer(msg);
    }

    /** Server‐side: send a packet to a specific player. */
    public static <MSG> void sendToPlayer(MSG msg, ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                msg
        );
    }
}
