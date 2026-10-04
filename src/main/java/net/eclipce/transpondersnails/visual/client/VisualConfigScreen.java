package net.eclipce.transpondersnails.visual.client;

import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.visual.ProjectorPlacement;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualMenuText;
import net.eclipce.transpondersnails.visual.VisualScreenConfig;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.eclipce.transpondersnails.visual.network.VisualConfigUpdatePacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Projector settings menu. Opened by crouch + right click, at any time.
 *
 * <ul>
 *   <li><b>Screen size</b>: the projector zooms like a real one - the further away the screen, the bigger the minimum size
 *       (+1 every 2 blocks), and the maximum is 4x the minimum. The slider only offers sizes inside that range for the
 *       distance right now, and the range follows the world live.</li>
 *   <li><b>Left/Right and Down/Up</b>: slide the screen along the surface, at most 3 blocks each way.</li>
 *   <li><b>Depth</b>: automatic when there is a surface straight behind the snail (the screen is laid on it). Only with nothing
 *       to project onto can the screen be pushed further back with "Extra depth" (0 = original position, never towards the snail).</li>
 * </ul>
 *
 * While it is open the world stays visible and a frame shows where the screen will be; if the snail is in a call, its real
 * screen follows the sliders immediately (see VisualScreenRenderer). The settings are sent to the server a moment after the last
 * change (and when the menu closes), so dragging a slider does not flood the connection.
 */
@OnlyIn(Dist.CLIENT)
public class VisualConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 200;
    // Vertical layout, in pixels below the title line ("top"). Everything below is derived from these, so rows, buttons and the panel
    // border cannot overlap: the buttons end at ROW_BUTTONS + ROW_HEIGHT and the panel border is PANEL_PADDING_BOTTOM below that.
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SIZE = 30;
    private static final int ROW_SIDE = 54;
    private static final int ROW_UP = 78;
    private static final int ROW_DEPTH = 102;
    private static final int ROW_BUTTONS = 132;
    private static final int PANEL_PADDING_BOTTOM = 10;
    /** Distance from the title line down to the panel's bottom border. */
    private static final int PANEL_BOTTOM = ROW_BUTTONS + ROW_HEIGHT + PANEL_PADDING_BOTTOM;
    /** Distance from the panel's top border down to the title line. */
    private static final int PANEL_PADDING_TOP = 8;
    private static final long SEND_DELAY_MS = 150L;

    /** The menu that is open right now (read by the renderer for the live preview), or null. */
    private static VisualConfigScreen active;

    static VisualConfigScreen active() {
        return active;
    }

    private final BlockPos pos;
    private int size;       // the PREFERRED size; what is shown is this held inside the zoom range
    private float side;
    private float up;
    private float back;

    private boolean dirty = false;
    private long lastChangeMs = 0L;

    // what the world around the snail allows right now (refreshed every tick)
    private boolean surfaceFound = false;
    private double depth = VisualCallConstants.SCREEN_FLOAT_DISTANCE;
    private int minSize = VisualCallConstants.SCREEN_MIN_SIZE;
    private int maxSize = VisualCallConstants.SCREEN_MAX_SIZE;
    private int shownSize = 0;

    private ConfigSlider sizeSlider;
    private ConfigSlider sideSlider;
    private ConfigSlider upSlider;
    private ConfigSlider depthSlider;

    public VisualConfigScreen(BlockPos pos, int size, float side, float up, float back) {
        super(Component.literal("Projector Settings"));
        this.pos = pos.immutable();
        this.size = VisualScreenConfig.clampSize(size, VisualSettings.maxScreenSize());
        this.side = VisualScreenConfig.snapOffset(side);
        this.up = VisualScreenConfig.snapOffset(up);
        this.back = VisualScreenConfig.snapBack(back);
    }

    BlockPos pos() { return pos; }
    int size() { return size; }
    float side() { return side; }
    float up() { return up; }
    float back() { return back; }

    private int panelLeft() {
        // on the right, so the middle of the view (where the snail and its screen usually are) stays free
        return Math.max(8, this.width - PANEL_WIDTH - 20);
    }

    private int panelTop() {
        // centred vertically, but never so low that the buttons leave the screen, nor so high that the panel does
        int centred = this.height / 2 - PANEL_BOTTOM / 2;
        int lowest = this.height - PANEL_BOTTOM - 4;
        return Math.max(PANEL_PADDING_TOP, Math.min(centred, lowest));
    }

    @Override
    protected void init() {
        active = this;
        refreshGeometry(true); // minSize / maxSize / shownSize for the size slider

        int x = panelLeft();
        int top = panelTop();
        int max = (int) VisualCallConstants.SCREEN_OFFSET_MAX;

        sizeSlider = addRenderableWidget(new ConfigSlider(x, top + ROW_SIZE, PANEL_WIDTH, ROW_HEIGHT,
                minSize, maxSize, 1.0, shownSize,
                v -> { size = (int) Math.round(v); markDirty(); },
                v -> "Screen Size: " + (int) Math.round(v) + " x " + (int) Math.round(v)));
        sizeSlider.setRange(minSize, maxSize, shownSize);

        sideSlider = addRenderableWidget(new ConfigSlider(x, top + ROW_SIDE, PANEL_WIDTH, ROW_HEIGHT,
                -max, max, VisualCallConstants.SCREEN_OFFSET_STEP, side,
                v -> { side = (float) v; markDirty(); },
                v -> VisualMenuText.offsetLabel(v, "Left/Right", "Left", "Right")));

        upSlider = addRenderableWidget(new ConfigSlider(x, top + ROW_UP, PANEL_WIDTH, ROW_HEIGHT,
                -max, max, VisualCallConstants.SCREEN_OFFSET_STEP, up,
                v -> { up = (float) v; markDirty(); },
                v -> VisualMenuText.offsetLabel(v, "Down/Up", "Down", "Up")));

        // 0 = the original position; only further back is possible (never towards the snail)
        depthSlider = addRenderableWidget(new ConfigSlider(x, top + ROW_DEPTH, PANEL_WIDTH, ROW_HEIGHT,
                0.0, VisualCallConstants.SCREEN_BACK_MAX, VisualCallConstants.SCREEN_OFFSET_STEP, back,
                v -> { back = (float) v; markDirty(); },
                v -> "Extra depth: " + (v < 0.001 ? "Default" : VisualMenuText.number(v) + " blocks")));
        // With a surface behind the snail the depth is automatic: no slider, the value is shown instead (see render()).
        depthSlider.visible = !surfaceFound;
        depthSlider.active = !surfaceFound;

        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> reset())
                .bounds(x, top + ROW_BUTTONS, 98, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x + 102, top + ROW_BUTTONS, 98, ROW_HEIGHT).build());
    }

    /**
     * Looks at the world around the snail (the same ray and the same maths as the real screen) and updates what the menu offers:
     * the size range of the zoom, and whether the depth is automatic. Cheap: the surface ray is cached for 200 ms.
     */
    private void refreshGeometry(boolean force) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TransponderSnailBlock) || !state.hasProperty(TransponderSnailBlock.FACING)) return;
        Direction facing = state.getValue(TransponderSnailBlock.FACING);

        double surface = VisualScreenRenderer.surfaceDistanceFor(level, pos, facing);
        ProjectorPlacement.Result r = ProjectorPlacement.compute(facing.getStepX(), facing.getStepZ(),
                size, side, up, back, VisualSettings.maxScreenSize(), surface);

        boolean changed = force || r.surfaceFound() != surfaceFound || r.minSize() != minSize || r.maxSize() != maxSize
                || r.size() != shownSize || Math.abs(r.depth() - depth) > 0.05;
        if (!changed) return;

        surfaceFound = r.surfaceFound();
        depth = r.depth();
        minSize = r.minSize();
        maxSize = r.maxSize();
        shownSize = r.size();

        if (sizeSlider != null) {
            sizeSlider.setRange(minSize, maxSize, shownSize);
        }
        if (depthSlider != null) {
            depthSlider.visible = !surfaceFound; // slider only while the screen floats; with a surface a plain value is shown
            depthSlider.active = !surfaceFound;
            depthSlider.refresh();
        }
    }

    private void reset() {
        size = VisualScreenConfig.clampSize(VisualCallConstants.SCREEN_DEFAULT_SIZE, VisualSettings.maxScreenSize());
        side = 0.0F;
        up = 0.0F;
        back = 0.0F;
        sideSlider.setTo(side);
        upSlider.setTo(up);
        depthSlider.setTo(back);
        refreshGeometry(true); // also moves the size slider to the (zoom-limited) default
        markDirty();
    }

    private void markDirty() {
        dirty = true;
        lastChangeMs = System.currentTimeMillis();
    }

    private void sendNow() {
        dirty = false;
        VisualNetwork.CHANNEL.sendToServer(new VisualConfigUpdatePacket(pos, size, side, up, back));
    }

    @Override
    public void tick() {
        refreshGeometry(false);
        // a moment after the LAST change, not while the slider is still being dragged
        if (dirty && System.currentTimeMillis() - lastChangeMs >= SEND_DELAY_MS) {
            sendNow();
        }
    }

    @Override
    public void removed() {
        if (dirty) {
            sendNow();
        }
        if (active == this) {
            active = null;
        }
        super.removed();
    }

    /** Keep the game running behind the menu (also in single player: the call timers are server ticks). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-screen dimming: the world has to stay visible, because the preview frame is drawn in it.
        int x = panelLeft();
        int top = panelTop();
        int left = x - 10;
        int right = x + PANEL_WIDTH + 10;
        int panelTopEdge = top - PANEL_PADDING_TOP;
        int bottom = top + PANEL_BOTTOM;
        g.fill(left, panelTopEdge, right, bottom, 0xD0101018);
        int border = 0xFF5AB4DC;
        g.fill(left, panelTopEdge, right, panelTopEdge + 1, border);
        g.fill(left, bottom - 1, right, bottom, border);
        g.fill(left, panelTopEdge, left + 1, bottom, border);
        g.fill(right - 1, panelTopEdge, right, bottom, border);

        g.drawCenteredString(this.font, this.title, x + PANEL_WIDTH / 2, top, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("Snail Projector Adjustments"), x + PANEL_WIDTH / 2, top + 14, 0xA0A0A0);

        // With a surface behind the snail the depth is automatic and cannot be moved: shown as a plain value in the depth row
        // (a box like the sliders', but with no handle), instead of a slider that does nothing.
        if (surfaceFound) {
            int ry = top + ROW_DEPTH;
            g.fill(x, ry, x + PANEL_WIDTH, ry + ROW_HEIGHT, 0xFF606060);
            g.fill(x + 1, ry + 1, x + PANEL_WIDTH - 1, ry + ROW_HEIGHT - 1, 0xFF101010);
            g.drawCenteredString(this.font, Component.literal("Depth: " + oneDecimal(depth) + " blocks (auto)"),
                    x + PANEL_WIDTH / 2, ry + (ROW_HEIGHT - 8) / 2, 0xC0C0C0);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------------------------------------

    private static String oneDecimal(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Position of a value on a slider, 0..1; a slider with no span (min == max) sits at 0. */
    private static double fraction(double value, double min, double max) {
        double span = max - min;
        return span <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, (value - min) / span));
    }

    /**
     * A slider that moves in fixed steps. Mouse drag, arrow keys (one step) and the mouse wheel (one step) all work, and the
     * handle always sits exactly on a step. Its range can change while the menu is open (the size slider follows the zoom).
     */
    private static final class ConfigSlider extends AbstractSliderButton {
        private double min;
        private double max;
        private final double step;
        private final DoubleConsumer onChange;
        private final DoubleFunction<String> label;

        ConfigSlider(int x, int y, int width, int height, double min, double max, double step, double initial,
                     DoubleConsumer onChange, DoubleFunction<String> label) {
            super(x, y, width, height, Component.empty(), fraction(initial, min, max));
            this.min = min;
            this.max = max;
            this.step = step;
            this.onChange = onChange;
            this.label = label;
            updateMessage();
        }

        private double current() {
            if (max <= min) return min;
            double v = min + this.value * (max - min);
            double snapped = min + Math.round((v - min) / step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        @Override
        protected void updateMessage() {
            if (label == null) return; // may be called before the fields are set
            setMessage(Component.literal(label.apply(current())));
        }

        @Override
        protected void applyValue() {
            double v = current();
            this.value = fraction(v, min, max); // snap the handle onto the step
            onChange.accept(v);
        }

        /** Re-reads the label (e.g. when what it says depends on something outside the slider). */
        void refresh() {
            updateMessage();
        }

        /** Sets the slider without reporting a change (used by Reset, which reports once itself). */
        void setTo(double v) {
            this.value = fraction(v, min, max);
            updateMessage();
        }

        /** New range (the zoom range follows the distance to the screen); the slider shows {@code shown}. Not a user change. */
        void setRange(double newMin, double newMax, double shown) {
            this.min = newMin;
            this.max = newMax;
            this.active = newMax > newMin; // nothing to choose when the range is a single size
            this.value = fraction(shown, min, max);
            updateMessage();
        }

        private void nudge(int steps) {
            double v = Math.max(min, Math.min(max, current() + steps * step));
            this.value = fraction(v, min, max);
            updateMessage();
            onChange.accept(v);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_LEFT) { nudge(-1); return true; }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) { nudge(1); return true; }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (this.isMouseOver(mouseX, mouseY) && delta != 0.0) {
                nudge(delta > 0.0 ? 1 : -1);
                return true;
            }
            return false;
        }
    }
}
