package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a snail's camera really captured, for the person who has to tell what is wrong with a picture: one log line (tagged [feed-diag]) for each of the first captures
 * of a feed and then once a minute, and the picture itself (PNG, read back straight from the feed's own render target - before the screen draws it) for a few captures,
 * in {game folder}/transpondersnails_feed_debug. A picture that is wrong in the PNG is wrong in the capture; one that is right there is wrong on the screen.
 * Costs nothing while no feed runs and next to nothing while one does. Off with -Dtranspondersnails.feeddiag=false.
 */
@OnlyIn(Dist.CLIENT)
final class FeedDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("transpondersnails.feeddiag"));
    /** Capture numbers (per feed) whose picture is saved: the first one, one a few seconds in, one well into the call. */
    private static final int[] DUMP_AT = {1, 45, 300};
    private static final int MAX_SAMPLES = 10;
    private static final long LOG_EVERY_MS = 60_000L;
    private static final double CLOSE_PLAYER_BLOCKS = 3.0;

    private static final class PerFeed {
        int captures = 0;
        int dumps = 0;
        long lastLogMs = 0L;
        int closeDumps = 0;
        long lastCloseMs = 0L;
    }

    private static final Map<BlockPos, PerFeed> FEEDS = new HashMap<>();

    // what the capture in progress did (reset by begin)
    private static int entitiesSeen;
    private static int entitiesPassed;
    private static int entitiesRejected;
    private static int headsHidden;
    private static int glowGuardTripped;
    private static int playerDraws;
    private static boolean playerShouldRender;
    private static double playerDistance;
    private static String playerOffset = "-";
    private static String playerExtra = "-";
    private static double poseDelta = -1.0;
    private static boolean poseWasClear = true;
    /** Where the player's feet / middle / head land in the picture (0..1 from the top left), NaN if behind the camera or not drawn: painted into the saved PNGs. */
    private static final double[] MARKS = new double[6];
    private static final List<String> SAMPLES = new ArrayList<>();

    private FeedDiagnostics() {}

    static void begin() {
        entitiesSeen = 0;
        entitiesPassed = 0;
        entitiesRejected = 0;
        headsHidden = 0;
        glowGuardTripped = 0;
        playerDraws = 0;
        playerShouldRender = false;
        playerDistance = -1.0;
        playerOffset = "-";
        playerExtra = "-";
        poseDelta = -1.0;
        poseWasClear = true;
        java.util.Arrays.fill(MARKS, Double.NaN);
        SAMPLES.clear();
    }

    /** One entity the level renderer asked about during the capture; {@code eye} is where the camera is. */
    static void entity(Entity entity, boolean passed, Vec3 eye) {
        entitiesSeen++;
        if (passed) entitiesPassed++; else entitiesRejected++;
        if (passed && SAMPLES.size() < MAX_SAMPLES && eye != null) {
            SAMPLES.add(EntityType.getKey(entity.getType()) + "@" + Math.round(entity.getX() - eye.x) + "," + Math.round(entity.getY() - eye.y) + ","
                    + Math.round(entity.getZ() - eye.z));
        }
    }

    static void headHidden() {
        headsHidden++;
    }

    static void glowGuard() {
        glowGuardTripped++;
    }

    /** Where the player stood relative to the lens when it was drawn: dx,dy,dz in blocks and how far off the view axis (degrees, 0 = dead centre, 45 = frame edge). */
    static void playerOffset(double dx, double dy, double dz, double yawDegrees) {
        playerOffset = round(dx) + "," + round(dy) + "," + round(dz) + " yawOffAxis=" + round(yawDegrees);
    }

    /** How far the event's matrix stack was from the one built from the camera (0 = identical), and whether it was back at its base entry. */
    static void poseDelta(double delta, boolean wasClear) {
        poseDelta = delta;
        poseWasClear = wasClear;
    }

    /** Projects the player's feet, middle and head through the camera (90 degrees, square) to where they must appear in the picture. */
    static void playerMarks(Vec3 cam, float xRotDeg, float yRotDeg, double px, double py, double pz) {
        double yaw = Math.toRadians(yRotDeg), pitch = Math.toRadians(xRotDeg);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0.0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        double[] heights = {0.0, 0.9, 1.7};
        for (int i = 0; i < 3; i++) {
            double vx = px - cam.x, vy = py + heights[i] - cam.y, vz = pz - cam.z;
            double depth = vx * fx + vy * fy + vz * fz;
            if (depth <= 0.05) {
                MARKS[i * 2] = Double.NaN;
                MARKS[i * 2 + 1] = Double.NaN;
                continue;
            }
            MARKS[i * 2] = 0.5 + 0.5 * ((vx * rx + vy * ry + vz * rz) / depth);
            MARKS[i * 2 + 1] = 0.5 - 0.5 * ((vx * ux + vy * uy + vz * uz) / depth);
        }
    }

    private static String marksText() {
        return "feet=" + markText(0) + " middle=" + markText(1) + " head=" + markText(2);
    }

    private static String markText(int i) {
        return Double.isNaN(MARKS[i * 2]) ? "behind/none" : "(" + round(MARKS[i * 2]) + "," + round(MARKS[i * 2 + 1]) + ")";
    }

    private static void paintMarks(NativeImage image) {
        int[] colours = {0xFFFF00FF, 0xFF00FFFF, 0xFFFFFF00}; // ABGR: magenta feet, yellow middle, cyan head
        for (int i = 0; i < 3; i++) {
            if (Double.isNaN(MARKS[i * 2])) continue;
            int cx = (int) Math.round(MARKS[i * 2] * image.getWidth()), cy = (int) Math.round(MARKS[i * 2 + 1] * image.getHeight());
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    int x = cx + dx, y = cy + dy;
                    if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && (Math.abs(dx) <= 1 || Math.abs(dy) <= 1)) image.setPixelRGBA(x, y, colours[i]);
                }
            }
        }
    }

    /** The state the player was drawn in and the matrices at that moment (see VisualScreenRenderer#drawDiagnosticBoxes). */
    static void playerExtra(String text) {
        playerExtra = text;
    }

    static String round2(double v) {
        return String.valueOf(round(v));
    }

    static void player(boolean shouldRender, double distance, boolean drawn) {
        playerShouldRender = shouldRender;
        playerDistance = distance;
        if (drawn) playerDraws++;
    }

    static void finish(Minecraft mc, VisualFeed feed, Vec3 eyePos, Direction facing, boolean sodium, String links) {
        if (!ENABLED) return;
        try {
            PerFeed state = FEEDS.computeIfAbsent(feed.cameraPos.immutable(), k -> new PerFeed());
            state.captures++;
            long now = System.currentTimeMillis();
            boolean log = state.captures <= 3 || now - state.lastLogMs >= LOG_EVERY_MS;
            boolean dump = false;
            for (int n : DUMP_AT) {
                if (state.captures == n) dump = true;
            }
            // the player standing close to the lens is what is being investigated: log it and save its picture (at most one every 4 s, 8 per feed)
            if (playerDraws > 0 && playerDistance < CLOSE_PLAYER_BLOCKS && state.closeDumps < 8 && now - state.lastCloseMs >= 4_000L) {
                state.closeDumps++;
                state.lastCloseMs = now;
                log = true;
                dump = true;
            }
            if (log) {
                state.lastLogMs = now;
                LOGGER.info("[feed-diag] camera={} facing={} eye=({}, {}, {}) capture#{} {}px sodiumLike={} shaderPack={} sections={} | entities seen/passed/rejected={}/{}/{} "
                                + "| projector heads hidden={} glowGuard={} | player: shouldRender={} distance={} draws={} offset={} poseDelta={} poseWasClear={} marks[{}] | state: {} | links: {} | passed entities (type@dx,dy,dz from the camera): {}",
                        feed.cameraPos, facing, round(eyePos.x), round(eyePos.y), round(eyePos.z), state.captures, feed.resolution, sodium, ShaderCompat.isShaderPackActive(),
                        feed.sections.size(), entitiesSeen, entitiesPassed, entitiesRejected, headsHidden, glowGuardTripped, playerShouldRender, round(playerDistance), playerDraws, playerOffset, round(poseDelta), poseWasClear, marksText(), playerExtra,
                        links, SAMPLES);
            }
            if (dump && feed.target != null) {
                File dir = new File(mc.gameDirectory, "transpondersnails_feed_debug");
                if (dir.isDirectory() || dir.mkdirs()) {
                    File file = new File(dir, "feed_" + feed.cameraPos.getX() + "_" + feed.cameraPos.getY() + "_" + feed.cameraPos.getZ() + "_capture" + state.captures + ".png");
                    try (NativeImage image = Screenshot.takeScreenshot(feed.target)) {
                        if (playerDraws > 0) paintMarks(image);
                        image.writeToFile(file);
                        state.dumps++;
                        LOGGER.info("[feed-diag] saved the picture of capture #{} of the snail at {} as {}", state.captures, feed.cameraPos, file.getAbsolutePath());
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[feed-diag] could not write the diagnostics of a capture (further problems are not logged): {}", t.toString());
            // never let a diagnostic break a capture
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static void forget() {
        FEEDS.clear();
    }
}
