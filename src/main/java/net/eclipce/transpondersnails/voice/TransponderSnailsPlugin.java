package net.eclipce.transpondersnails.voice;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.eclipce.transpondersnails.voice.server.SnailAudioRelay;
import org.apache.logging.log4j.core.config.plugins.Plugin;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ForgeVoicechatPlugin
public class TransponderSnailsPlugin implements VoicechatPlugin {

    private SnailAudioRelay audioRelay;
    private ScheduledExecutorService scheduler;

    @Override
    public String getPluginId() {
        return VoiceChatConstants.PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);

        // Register microphone packet event for audio forwarding
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        VoicechatServerApi api = event.getVoicechat();
        VolumeCategory snailVolume = api.volumeCategoryBuilder()
                .setId(VoiceChatConstants.SNAIL_VOLUME_CATEGORY)
                .setName("Snails")
                .setDescription("Volume of all Snails")
                .setIcon(getIcon())
                .build();

        api.registerVolumeCategory(snailVolume);

        // Initialize the call manager with the API
        TransponderCallManager callManager = new TransponderCallManager(api);

        // Initialize the audio relay system
        audioRelay = new SnailAudioRelay(api, callManager);

        // Connect them together
        callManager.setAudioRelay(audioRelay);

        // Set the call manager in the main mod class
        TransponderSnails.setCallManager(callManager);

        // Start cleanup scheduler for audio relay
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
        }, 5, 5, TimeUnit.SECONDS); // Clean up every 30 seconds

        TransponderSnails.LOGGER.info("Transponder Snails voice chat integration initialized with audio relay!");
    }

    /**
     * Handle microphone packets for audio forwarding
     */
    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (audioRelay != null) {
            try {
                audioRelay.onMicrophonePacket(event);
            } catch (Exception e) {
                System.err.println("TransponderSnailsPlugin: Error in microphone packet handler: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Nullable
    private int[][] getIcon() {
        try {
            // Load icon from resources - note the corrected path
            InputStream iconStream = getClass().getResourceAsStream("/assets/transpondersnails/textures/icons/snail_volume_icon.png");

            if (iconStream == null) {
                System.err.println("Could not find snail_call_icon.png at /assets/transpondersnails/textures/icons/");
                return null;
            }

            BufferedImage bufferedImage = ImageIO.read(iconStream);
            iconStream.close();

            // Validate image dimensions
            if (bufferedImage.getWidth() != 16 || bufferedImage.getHeight() != 16) {
                System.err.println("Icon must be 16x16 pixels! Found: " + bufferedImage.getWidth() + "x" + bufferedImage.getHeight());
                return null;
            }

            // Convert BufferedImage to int[][] format expected by Simple Voice Chat
            int[][] image = new int[16][16];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    image[x][y] = bufferedImage.getRGB(x, y);
                }
            }

            return image;

        } catch (IOException e) {
            System.err.println("Error loading Snail Volume icon: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Cleanup method for when the plugin shuts down
     */
    public void cleanup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}