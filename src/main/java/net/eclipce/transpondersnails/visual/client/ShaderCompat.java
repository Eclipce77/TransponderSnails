package net.eclipce.transpondersnails.visual.client;

import com.mojang.logging.LogUtils;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.visual.network.VisualShaderStatePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Shader packs (Iris, Oculus) and the video capture do not mix. A capture draws the world a second time in the middle of a frame into
 * another render target; a shader pack owns the whole frame (shadow pass, g-buffers, composite passes, all sized for the main window), so a
 * second world render breaks it: the player's own view goes black or dark and hard to see for as long as a feed is drawn.
 *
 * So while a shader pack is on, this client never draws a feed, neither for its own screens nor for others. Its screens show the picture the
 * relay brings (drawn by another player's client), and it tells the server not to pick it as the one that draws. Nothing here depends on Iris:
 * it is looked up by name, and a missing or different version just means "no shader pack".
 */
@OnlyIn(Dist.CLIENT)
public final class ShaderCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The Iris API class: Iris (and recent Oculus) use the first name, older Oculus the second. */
    private static final String[] API_CLASSES = {
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi"
    };
    private static final long CHECK_INTERVAL_MS = 1000L;
    /** While a shader pack is on the server is told again this often (it forgets after {@code VisualRelayManager.SHADER_CLIENT_TTL_MS}). */
    private static final long REPORT_INTERVAL_MS = 5000L;

    private static boolean looked = false;
    private static Object api = null;
    private static Method inUse = null;

    private static boolean active = false;
    private static long lastCheckMs = 0L;
    private static boolean reportedState = false;
    private static long lastReportMs = 0L;

    private ShaderCompat() {}

    /** True while a shader pack is running (checked at most once a second). */
    public static boolean isShaderPackActive() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_INTERVAL_MS) return active;
        lastCheckMs = now;
        boolean now2 = query();
        if (now2 != active) {
            active = now2;
            LOGGER.info("ShaderCompat: shader pack {} - video feeds are {} on this client", active ? "on" : "off",
                    active ? "not drawn (the relay is used)" : "drawn here again");
        }
        return active;
    }

    private static boolean query() {
        if (!looked) {
            looked = true;
            for (String name : API_CLASSES) {
                try {
                    Class<?> cls = Class.forName(name);
                    api = cls.getMethod("getInstance").invoke(null);
                    inUse = cls.getMethod("isShaderPackInUse");
                    break;
                } catch (Throwable ignored) {
                    // not this one
                }
            }
        }
        if (api == null || inUse == null) return false;
        try {
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Keeps the server informed (called every frame; sends a packet only when the state changes, and now and then while a shader pack is on). */
    public static void report() {
        boolean state = isShaderPackActive();
        long now = System.currentTimeMillis();
        if (state != reportedState || (state && now - lastReportMs >= REPORT_INTERVAL_MS)) {
            reportedState = state;
            lastReportMs = now;
            try {
                VisualNetwork.CHANNEL.sendToServer(new VisualShaderStatePacket(state));
            } catch (Throwable ignored) {
                // not connected (yet): the next change or report sends it
            }
        }
    }

    /** A new world: tell the server again. */
    static void reset() {
        reportedState = false;
        lastReportMs = 0L;
    }
}
