package net.eclipce.transpondersnails.voice;

import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ForgeVoicechatPlugin that manages “phone‐call” static audio channels.
 */
@ForgeVoicechatPlugin
@Mod.EventBusSubscriber(
        modid = TransponderSnails.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD
        //value = Dist.DEDICATED_SERVER
)
public class TransponderSnailAudioPlugin implements de.maxhenkel.voicechat.api.VoicechatPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static       VoicechatServerApi api;
    private static final Map<UUID, CallChannels> activeCalls = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return TransponderSnails.MOD_ID + "_audio";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatStarted);
    }

    private void onVoicechatStarted(VoicechatServerStartedEvent evt) {
        api = evt.getVoicechat();
        LOGGER.info("[TransponderSnailAudio] API initialized: {}", api);
    }

    public static boolean isAvailable() {
        return api != null;
    }

    /**
     * Opens two one‐way static audio channels (caller→receiver and receiver→caller).
     */
    public static void openCallChannel(ServerPlayer caller, ServerPlayer receiver, UUID callId) {
        if (!isAvailable()) {
            LOGGER.warn("[TransponderSnailAudio] API not ready");
            return;
        }

        Level level = caller.level();
        VoicechatConnection callerConn   = api.getConnectionOf(caller.getUUID());
        VoicechatConnection receiverConn = api.getConnectionOf(receiver.getUUID());
        if (callerConn == null || receiverConn == null) {
            LOGGER.warn("[TransponderSnailAudio] Missing connection for one of the players");
            return;
        }

        // Wrap the Minecraft ServerLevel
        var vcLevel = api.fromServerLevel(level);

        // Create caller→receiver channel
        StaticAudioChannel out = api.createStaticAudioChannel(callId, vcLevel, receiverConn);
        // Create receiver→caller channel
        StaticAudioChannel in  = api.createStaticAudioChannel(callId, vcLevel, callerConn);

        if (out == null || in == null) {
            LOGGER.error("[TransponderSnailAudio] Failed to create static channels");
            return;
        }

        activeCalls.put(callId, new CallChannels(out, in));
        LOGGER.info("[TransponderSnailAudio] Opened call channels for {}", callId);
    }

    /**
     * Feeds raw opus bytes into both sides of the call.
     * (Called by your PhoneAudioPacket handler.)
     */
    public static void onPhoneAudio(UUID callId, byte[] opusData) {
        CallChannels ch = activeCalls.get(callId);
        if (ch == null) {
            LOGGER.warn("[TransponderSnailAudio] No active call for {}", callId);
            return;
        }
        ch.callerChannel.send(opusData);
        ch.receiverChannel.send(opusData);
    }

    /**
     * Ends a call: simply remove your references and let the API clean up.
     */
    public static void closeCall(UUID callId) {
        CallChannels ch = activeCalls.remove(callId);
        if (ch != null) {
            LOGGER.info("[TransponderSnailAudio] Closed call channels for {}", callId);
        }
    }

    private static class CallChannels {
        final StaticAudioChannel callerChannel;
        final StaticAudioChannel receiverChannel;
        CallChannels(StaticAudioChannel c, StaticAudioChannel r) {
            this.callerChannel   = c;
            this.receiverChannel = r;
        }
    }
}