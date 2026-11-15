package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostics for the draw of the player into a snail's picture: wraps the buffer source the player is drawn through and records, per render type, how many
 * vertices were written and the box (in the camera's view space, the space the pose stack puts them in) they span. The box can be compared with where the
 * player was supposed to be (feet, head); a model that is the wrong size, in the wrong place or not written at all shows up at once. Also reads the GL state the
 * draw is made in (bound framebuffer, viewport, depth, blend, cull, scissor).
 */
@OnlyIn(Dist.CLIENT)
final class FeedPlayerProbe implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final Map<String, Stats> stats = new LinkedHashMap<>();

    FeedPlayerProbe(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        String name = type.toString();
        int cut = name.indexOf('[');
        if (cut > 0) name = name.substring(0, cut);
        Stats s = stats.computeIfAbsent(name, k -> new Stats());
        return new Probe(delegate.getBuffer(type), s);
    }

    /** One line: "type: n vertices box(x,y,z .. x,y,z) | ..." */
    String summary() {
        if (stats.isEmpty()) return "nothing written";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Stats> e : stats.entrySet()) {
            Stats s = e.getValue();
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append(' ').append(s.count).append("v");
            if (s.count > 0) {
                sb.append(" box(").append(r(s.minX)).append(',').append(r(s.minY)).append(',').append(r(s.minZ)).append("..")
                        .append(r(s.maxX)).append(',').append(r(s.maxY)).append(',').append(r(s.maxZ)).append(')');
            }
        }
        return sb.toString();
    }

    static String glState() {
        try {
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            return "fb=" + GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                    + " viewport=" + vp[0] + "," + vp[1] + "," + vp[2] + "x" + vp[3]
                    + " depth=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST) + "/func" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                    + " blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + " cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                    + " scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        } catch (Throwable t) {
            return "gl state unreadable: " + t.getClass().getSimpleName();
        }
    }

    private static String r(double v) {
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private static final class Stats {
        int count;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
    }

    private static final class Probe implements VertexConsumer {
        private final VertexConsumer out;
        private final Stats s;

        Probe(VertexConsumer out, Stats s) {
            this.out = out;
            this.s = s;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            s.count++;
            if (x < s.minX) s.minX = x;
            if (y < s.minY) s.minY = y;
            if (z < s.minZ) s.minZ = z;
            if (x > s.maxX) s.maxX = x;
            if (y > s.maxY) s.maxY = y;
            if (z > s.maxZ) s.maxZ = z;
            out.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            out.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            out.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            out.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            out.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            out.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            out.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
    }
}
