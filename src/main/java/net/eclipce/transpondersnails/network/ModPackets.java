package net.eclipce.transpondersnails.network;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModPackets {

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel CHANNEL;
    private static int nextId = 0;

    /** Call this in FMLCommonSetupEvent (inside enqueueWork). */
    public static void init() {
        // Create the channel during proper initialization phase
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(TransponderSnails.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // Reset packet ID counter
        nextId = 0;

        // 1) Client → Server: CallInitiationPacket (updated with call manager integration)
        CHANNEL.messageBuilder(CallInitiationPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(CallInitiationPacket::new)
                .encoder(CallInitiationPacket::encode)
                .consumerMainThread(CallInitiationPacket::handle)
                .add();

        // 2) Server → Client: SnailNumberSyncPacket (existing)
        CHANNEL.messageBuilder(SnailNumberSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SnailNumberSyncPacket::new)
                .encoder(SnailNumberSyncPacket::encode)
                .consumerMainThread(SnailNumberSyncPacket::handle)
                .add();

        // 3) Client → Server: Snail number request
        CHANNEL.messageBuilder(SnailNumberRequestPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SnailNumberRequestPacket::new)
                .encoder(SnailNumberRequestPacket::encode)
                .consumerMainThread(SnailNumberRequestPacket::handle)
                .add();

        // 4) Client → Server: Dial digit input
        CHANNEL.messageBuilder(DialDigitPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(DialDigitPacket::new)
                .encoder(DialDigitPacket::encode)
                .consumerMainThread(DialDigitPacket::handle)
                .add();

        // 5) Server → Client: Dialed number sync
        CHANNEL.messageBuilder(DialedNumberSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(DialedNumberSyncPacket::new)
                .encoder(DialedNumberSyncPacket::encode)
                .consumerMainThread(DialedNumberSyncPacket::handle)
                .add();

        // 6) Server → Client: Call state synchronization
        CHANNEL.messageBuilder(CallStateSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CallStateSyncPacket::new)
                .encoder(CallStateSyncPacket::encode)
                .consumerMainThread(CallStateSyncPacket::handle)
                .add();

        // 7) Client → Server: Call response (accept/reject/hang up)
        CHANNEL.messageBuilder(CallResponsePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(CallResponsePacket::new)
                .encoder(CallResponsePacket::encode)
                .consumerMainThread(CallResponsePacket::handle)
                .add();

        // 8) Server → Client: Black Snail call state sync
        CHANNEL.messageBuilder(BlackSnailCallStateSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(BlackSnailCallStateSyncPacket::new)
                .encoder(BlackSnailCallStateSyncPacket::encode)
                .consumerMainThread(BlackSnailCallStateSyncPacket::handle)
                .add();

        System.out.println("ModPackets: Registered " + nextId + " packet types");
    }

    /** Client-side: send a packet to the server. */
    public static <MSG> void sendToServer(MSG msg) {
        if (CHANNEL != null) {
            CHANNEL.sendToServer(msg);
        }
    }

    /** Server-side: send a packet to a specific player. */
    public static <MSG> void sendToPlayer(MSG msg, ServerPlayer player) {
        if (CHANNEL != null) {
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    msg
            );
        }
    }

    /** Server-side: send a packet to all players. */
    public static <MSG> void sendToAllPlayers(MSG msg) {
        if (CHANNEL != null) {
            CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
        }
    }

    /** Server-side: send a packet to all players near a position. */
    public static <MSG> void sendToPlayersNear(MSG msg, ServerPlayer player, double range) {
        if (CHANNEL != null) {
            CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            player.getX(), player.getY(), player.getZ(), range, player.level().dimension()
                    )),
                    msg
            );
        }
    }

    /** Server-side: send a packet to multiple specific players. */
    public static <MSG> void sendToPlayers(MSG msg, Iterable<ServerPlayer> players) {
        if (CHANNEL != null) {
            for (ServerPlayer player : players) {
                sendToPlayer(msg, player);
            }
        }
    }
}