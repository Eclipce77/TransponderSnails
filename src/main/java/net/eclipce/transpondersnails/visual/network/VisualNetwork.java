package net.eclipce.transpondersnails.visual.network;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Own SimpleChannel for the video call system, so the existing ModPackets registration does not need to change.
 * Registered from {@link VisualNetworkSetup} during FMLCommonSetupEvent.
 */
public final class VisualNetwork {

    private static final String PROTOCOL = "4"; // 2: streams; 3: relayed video (any distance); 4: tile video (key frames + changed tiles)

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TransponderSnails.MOD_ID, "visual_video"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered = false;

    private VisualNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        int id = 0;
        CHANNEL.registerMessage(id++, VisualFeedControlPacket.class,
                VisualFeedControlPacket::encode, VisualFeedControlPacket::decode, VisualFeedControlPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, VisualFeedStatusPacket.class,
                VisualFeedStatusPacket::encode, VisualFeedStatusPacket::decode, VisualFeedStatusPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // projector settings menu
        CHANNEL.registerMessage(id++, VisualConfigOpenPacket.class,
                VisualConfigOpenPacket::encode, VisualConfigOpenPacket::decode, VisualConfigOpenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, VisualConfigUpdatePacket.class,
                VisualConfigUpdatePacket::encode, VisualConfigUpdatePacket::decode, VisualConfigUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // streams: the tuner at a projector, the status menu of a transmitter
        CHANNEL.registerMessage(id++, VisualStreamActionPacket.class,
                VisualStreamActionPacket::encode, VisualStreamActionPacket::decode, VisualStreamActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, VisualStreamListPacket.class,
                VisualStreamListPacket::encode, VisualStreamListPacket::decode, VisualStreamListPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, VisualTransmitterStatusPacket.class,
                VisualTransmitterStatusPacket::encode, VisualTransmitterStatusPacket::decode, VisualTransmitterStatusPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // relayed video: a far camera is drawn by the client of a player near it and sent to the viewers through the server
        CHANNEL.registerMessage(id++, VisualRelayRequestPacket.class,
                VisualRelayRequestPacket::encode, VisualRelayRequestPacket::decode, VisualRelayRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, VisualFeedRequestPacket.class,
                VisualFeedRequestPacket::encode, VisualFeedRequestPacket::decode, VisualFeedRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, VisualFrameUploadPacket.class,
                VisualFrameUploadPacket::encode, VisualFrameUploadPacket::decode, VisualFrameUploadPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, VisualFrameRelayPacket.class,
                VisualFrameRelayPacket::encode, VisualFrameRelayPacket::decode, VisualFrameRelayPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
