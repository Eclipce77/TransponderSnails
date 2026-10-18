package net.eclipce.transpondersnails.visual;

import java.util.Collection;

/**
 * The numbers and the small arithmetic of relayed video (see TileCodec for the picture itself and VisualRelayManager for the whole idea).
 * Pure (no Minecraft classes), unit tested.
 */
public final class RelayFrames {

    private RelayFrames() {}

    /** The most one network payload may be: a client may send at most 32767 bytes at once, and the packet has a little around it. */
    public static final int MAX_FRAME_BYTES = 30000;
    /** How long a request for a relay (or to render for one) is good for; the sender renews it more often than this. */
    public static final long REQUEST_TTL_MS = 3000L;
    public static final long REQUEST_RENEW_MS = 1000L;
    /** The server keeps the last key frame and what came after it, for viewers that join; when that is more than this it asks for a new key frame. */
    public static final int MAX_CACHE_BYTES = 1_500_000;
    /** The smallest relayed picture. */
    public static final int MIN_RESOLUTION = 256;

    /** After this many frames in a row without a changed tile the camera is "quiet" and is captured less often. */
    public static final int QUIET_FRAMES_FOR_IDLE = 4;
    public static final int IDLE_FPS_DIVISOR = 4;
    public static final int MIN_IDLE_FPS = 2;

    /** The minimum time between two frames of a stream at {@code fps} (ms). */
    public static long frameInterval(int fps) {
        return 1000L / Math.max(1, fps);
    }

    /**
     * How often to capture a camera that is being relayed: the rate asked for, but a view where nothing has changed for a few frames is
     * looked at a quarter as often (at least {@value #MIN_IDLE_FPS} a second), and at the full rate again the moment something moves. A still
     * scene then costs a quarter of the capturing, reading back and comparing; the price is that movement is noticed up to half a second late.
     */
    public static int captureFps(int configuredFps, int quietFrames) {
        int fps = Math.max(1, configuredFps);
        if (quietFrames < QUIET_FRAMES_FOR_IDLE) return fps;
        return Math.min(fps, Math.max(MIN_IDLE_FPS, fps / IDLE_FPS_DIVISOR));
    }

    /**
     * The size of the picture to relay: what the most demanding viewer wants (each viewer asks for what its screen can show, as for a local
     * feed), but no more than the server allows, and a size the tiles fit.
     *
     * @param wishes what each viewer asked for (pixels, square)
     * @param cap    the server's limit
     */
    public static int resolutionFor(Collection<Integer> wishes, int cap) {
        int wanted = MIN_RESOLUTION;
        for (int w : wishes) wanted = Math.max(wanted, w);
        return TileCodec.alignSize(Math.max(MIN_RESOLUTION, Math.min(wanted, cap)));
    }

    /** A camera is drawn here only if the chunks this many chunks around it (each way) are loaded; it goes back to relay when the narrow ring is not. */
    public static final int LOADED_MARGIN_WIDE = 4;
    public static final int LOADED_MARGIN_NARROW = 2;

    /**
     * Draw a camera's view here, or have it relayed? A picture drawn here is only whole if the area around the camera is loaded on this
     * client, so a camera at the edge of what is loaded is relayed too. To keep the picture from flipping between the two at that edge, the
     * switch has a margin: a relayed camera is drawn here again only once the WIDE ring around it is loaded; a camera drawn here is relayed
     * only once the NARROW ring is not.
     *
     * @return true = relayed
     */
    public static boolean chooseRemote(boolean currentlyRemote, boolean wideRingLoaded, boolean narrowRingLoaded) {
        return currentlyRemote ? !wideRingLoaded : !narrowRingLoaded;
    }
}
