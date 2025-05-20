package net.eclipce.transpondersnails.voice;

import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ForgeVoicechatPlugin
@Mod.EventBusSubscriber(
        modid = TransponderSnails.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.DEDICATED_SERVER
)
public class SnailNumberGroups implements VoicechatPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static VoicechatServerApi api;
    private static MinecraftServer mcServer;

    @Override
    public String getPluginId() {
        // Must return a unique ID for your plugin :contentReference[oaicite:1]{index=1}
        return "transpondersnails";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Listen for the voice chat server starting so we can grab the API
        registration.registerEvent(
                VoicechatServerStartedEvent.class,
                this::onVoicechatStarted
        );
    }

    private void onVoicechatStarted(VoicechatServerStartedEvent event) {
        LOGGER.info("[SnailGroups] Voicechat started, api={} ", event.getVoicechat());
        api = event.getVoicechat();
        if (mcServer != null) {
            syncVoiceGroups();
        }
    }

    @SubscribeEvent
    public static void onForgeServerStarting(ServerStartingEvent event) {
        mcServer = event.getServer();
        LOGGER.info("[SnailGroups] Forge server starting, mcServer={}", mcServer);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        LOGGER.debug("[SnailGroups] Tick (side={}, phase={})", event.side, event.phase);
        if (event.side == LogicalSide.SERVER
                && event.phase == ServerTickEvent.Phase.END
                && api != null
                && mcServer != null) {

            LOGGER.info("[SnailGroups] onServerTick firing, syncing…");
            syncVoiceGroups();
        }
    }

    public static void syncVoiceGroups() {
        LOGGER.info("[TransponderSnails] Running syncVoiceGroups…");

        // 1) Gather all current snail numbers from your registry
        ServerLevel level = mcServer.overworld();
        var registry = SnailNumberRegistry.get(level);
        var numbers = registry.getAllNumbers();
        LOGGER.info("[TransponderSnails] Registry snail numbers = {}", numbers);

        // Convert to the UUID set we want
        Set<UUID> desired = numbers.stream()
                .map(num -> String.format("%04d", num))            // e.g. "0042"
                .map(idStr -> {
                    UUID uuid = UUID.nameUUIDFromBytes(idStr.getBytes());
                    LOGGER.debug("[TransponderSnails] Desired group ID for {} → {}", idStr, uuid);
                    return uuid;
                })
                .collect(Collectors.toSet());

        // 2) See which groups already exist
        Collection<Group> groups = api.getGroups();
        LOGGER.info("[TransponderSnails] Existing groups count = {}", groups.size());
        Set<UUID> existing = groups.stream()
                .map(Group::getId)
                .peek(id -> LOGGER.debug("[SnailGroups] Found existing group ID = {}", id))
                .collect(Collectors.toSet());

        // 3) Create any missing groups
        for (UUID id : desired) {
            if (!existing.contains(id)) {
                String numberString = String.format("%04d", registry.getNumberByUuid(id).orElse(-1));
                LOGGER.info("[TransponderSnails] Creating group for snail #{}", numberString);
                api.groupBuilder()
                        .setId(id)
                        .setName(numberString)
                        .setType(Group.Type.OPEN)
                        .setPersistent(true)
                        .setHidden(false)
                        .build();
            }
        }

        // 4) Remove stale groups
        for (UUID id : existing) {
            if (!desired.contains(id)) {
                LOGGER.info("[SnailGroups] Removing stale group ID = {}", id);
                api.removeGroup(id);
            }
        }

        LOGGER.info("[SnailGroups] syncVoiceGroups complete.");
    }

}
