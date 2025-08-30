package net.eclipce.transpondersnails.voice;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import org.apache.logging.log4j.core.config.plugins.Plugin;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

@ForgeVoicechatPlugin
public class TransponderSnailsPlugin implements VoicechatPlugin {

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

        // Set the call manager in the main mod class
        TransponderSnails.setCallManager(callManager);

        TransponderSnails.LOGGER.info("Transponder Snails voice chat integration initialized!");
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

            System.out.println("Successfully loaded Snail Volume icon");
            return image;

        } catch (IOException e) {
            System.err.println("Error loading Snail Volume icon: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}