package net.eclipce.transpondersnails.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RingingUI {
    private static final Map<UUID, SoundInstance> LOOPS = new ConcurrentHashMap<>();

    public static void registerLoop(UUID callId, SoundInstance loop) {
        LOOPS.put(callId, loop);
    }

    public static void stopLoop(UUID callId) {
        SoundInstance loop = LOOPS.remove(callId);
        if (loop != null) {
            Minecraft.getInstance().getSoundManager().stop(loop);
        }
    }
}

