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

    private static final String PROTOCOL = "1";

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
    }
}
