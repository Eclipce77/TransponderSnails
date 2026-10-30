package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.client.TooltipTimer;
import net.eclipce.transpondersnails.visual.HoldRepeat;
import net.eclipce.transpondersnails.visual.ProjectorPadLayout;
import net.eclipce.transpondersnails.visual.ProjectorPlacement;
import net.eclipce.transpondersnails.visual.SnailGuiLayout;
import net.eclipce.transpondersnails.visual.SnailGuiLayout.Rect;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualMenuText;
import net.eclipce.transpondersnails.visual.VisualScreenConfig;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.eclipce.transpondersnails.visual.ZoomBarLayout;
import net.eclipce.transpondersnails.visual.network.VisualConfigUpdatePacket;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * The projector settings, drawn on the texture assets/transpondersnails/textures/gui/visual_snail_projector_settings_gui.png (where the pad is on it: {@link ProjectorPadLayout};
 * the panel, the title zone, the display and the depth zone are shared with the dialing GUI: {@link SnailGuiLayout}). It is a panel, not a screen: VisualConfigScreen hosts it. It is
 * reached ONLY by crouch + right click on an idle Visual Transponder Snail. The panel is only about the VISUAL side of the snail: it shows no snail number.
 *
 * <ul>
 *   <li><b>Title and description</b>: in the box above the panel.</li>
 *   <li><b>The direction pad</b>: four arrows slide the screen along the surface, a quarter block per click (hold to keep moving), at most 3 blocks each way; the
 *       button in the middle puts the projector back in its original position (no sliding, no extra depth) and the zoom back to the default. The buttons are part of the texture; the menu lights up the one
 *       the mouse is over with its own picture (textures/gui/buttons/), so the light has the arrow's shape - see {@link PadWidget}.</li>
 *   <li><b>Zoom (the screen size)</b>: the striped bar on the left ({@link ZoomBarLayout}) - its top half zooms in (a bigger screen), its bottom half zooms out, hold to keep
 *       going - and the mouse wheel (wheel up = zoom in). The red indicator beside the bar shows where the size is between the smallest and the biggest for the distance right now:
 *       on the top stripe all the way in, on the bottom one all the way out. The projector zooms like a real one - the further away the screen, the bigger the minimum size
 *       (+1 every 2 blocks), and the maximum is 4x the minimum; the range follows the world live.</li>
 *   <li><b>Size and distance</b> (the box below the panel): the screen's size and how far away it is, as plain text ("4 x 4 blocks, 1.5 away"). The depth is automatic when there
 *       is a surface straight behind the snail (the screen is laid on it), so there is nothing to set. Only with nothing to project onto can the screen be pushed further back
 *       with "Extra depth" (0 = original position, never towards the snail): then a slider takes the place of that text, on a track drawn like the bars of the old texture, and
 *       the size and distance are written in the display instead. Right-clicking that slider resets it, and so does the middle button of the pad.</li>
 * </ul>
 *
 * <b>The big display</b> is a 3D view (ProjectorMiniView): the projector (its own model), anything in the way of its light, and the
 * screen at its real size and place - the blocks it lies on, and the picture on it as the projector will show it - with the light drawn from
 * the lens to the screen. Drag it to turn it. It uses the same placement as the real screen (ProjectorPlacement), so it cannot disagree.
 *
 * While it is open the world stays visible and a frame shows where the screen will be; if the snail is in a call, its real
 * screen follows the pad immediately (see VisualScreenRenderer). The settings are sent to the server a moment after the last
 * change (and when the panel closes), so holding an arrow does not flood the connection.
 */
@OnlyIn(Dist.CLIENT)
public class VisualSettingsPanel {

    /** What the panel needs from the screen that shows it. */
    public interface Host {
        int width();
        int height();
        Font font();
        /** True while the mouse is dragging something in the host (no tooltip then). */
        boolean dragging();
        /** Adds a widget to the host: it is drawn, clicked and narrated with the rest of the host's widgets. */
        void add(AbstractWidget widget);
    }

    private static final Component TITLE = Component.literal("Projector settings");

    private static final ResourceLocation TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID, "textures/gui/visual_snail_projector_settings_gui.png");
    private static final ResourceLocation ZOOM_INDICATOR = new ResourceLocation(TransponderSnails.MOD_ID, "textures/gui/zoom_indicator.png");
    private static final long SEND_DELAY_MS = 150L;

    // colours: the slider fill is a faint light over the bar of the texture, the handle a light block with a dark edge
    private static final int FILL_COLOR = 0x405AB4DC;
    private static final int HANDLE_EDGE = 0xFF1D1D1C;
    private static final int HANDLE_COLOR = 0xFFC4C4C4;
    private static final int HANDLE_HOT = 0xFFF2F2F2;

    /** The panel that is open right now (read by the renderer for the live preview), or null. */
    private static VisualSettingsPanel active;

    static VisualSettingsPanel active() {
        return active;
    }

    private Host host;

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

    private int originX;
    private int originY;

    private ConfigSlider depthSlider;
    private PadWidget pad;
    private ZoomBarWidget zoomBar;
    private final TooltipTimer tooltipTimer = new TooltipTimer();

    // The 3D view in the big display, and what it is built from (the placement and the surface found for it)
    private final ProjectorMiniView miniView = new ProjectorMiniView();
    private ProjectorPlacement.Result lastPlacement;
    private double lastSurface = Double.NaN;

    public VisualSettingsPanel(BlockPos pos, int size, float side, float up, float back) {
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

    /** A rectangle of the layout, moved to where the texture is on the screen. */
    private Rect onScreen(Rect r) {
        return new Rect(originX + r.x(), originY + r.y(), r.w(), r.h());
    }

    /** Adds a widget to the host and hands it back. */
    private <T extends AbstractWidget> T addWidget(T widget) {
        host.add(widget);
        return widget;
    }

    /**
     * Builds the panel's widgets into the host.
     *
     * @param originX screen x of the left edge of the 256 x 256 texture (see SnailGuiLayout.originX for the usual place)
     * @param originY screen y of the top edge of the texture
     */
    public void init(Host host, int originX, int originY) {
        this.host = host;
        active = this;
        this.originX = originX;
        this.originY = originY;
        refreshGeometry(true); // minSize / maxSize / shownSize: the size range the mouse wheel moves in

        // the direction pad: up / down / left / right slide the screen, the middle button puts the projector and the zoom back (looks baked into the texture)
        pad = addWidget(new PadWidget(onScreen(ProjectorPadLayout.ZONE), originX, originY, this::onPad,
                () -> host.dragging()));

        // the zoom bar on the left: its top half zooms in (a bigger screen), its bottom half zooms out; the indicator beside it is drawn in renderBackground
        zoomBar = addWidget(new ZoomBarWidget(onScreen(ZoomBarLayout.ZONE), originX, originY, this::zoom, () -> host.dragging()));

        // the depth, in the box below the panel: 0 = the original position; only further back is possible (never towards the snail)
        depthSlider = addWidget(new ConfigSlider(onScreen(SnailGuiLayout.DEPTH_BAR),
                0.0, VisualCallConstants.SCREEN_BACK_MAX, VisualCallConstants.SCREEN_OFFSET_STEP, back, false,
                v -> { back = (float) v; markDirty(); },
                v -> "Extra depth: " + (v < 0.001 ? "default" : VisualMenuText.number(v) + " blocks"),
                this::resetBack));
        // With a surface behind the snail the depth is automatic: no slider, the value is shown instead (see render()).
        depthSlider.visible = !surfaceFound;
        depthSlider.active = !surfaceFound;

        // the big display: a 3D view of the projector, the space behind it and the screen. Drag to turn it.
        addWidget(new ViewDrag(onScreen(SnailGuiLayout.DISPLAY), miniView));
        miniView.markDirty();
        miniView.tick(Minecraft.getInstance().level, pos, lastPlacement, lastSurface);
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
        lastPlacement = r;   // the 3D view shows exactly this: the same placement the real screen uses
        lastSurface = surface;

        boolean changed = force || r.surfaceFound() != surfaceFound || r.minSize() != minSize || r.maxSize() != maxSize
                || r.size() != shownSize || Math.abs(r.depth() - depth) > 0.05;
        if (!changed) return;

        surfaceFound = r.surfaceFound();
        depth = r.depth();
        minSize = r.minSize();
        maxSize = r.maxSize();
        shownSize = r.size();

        if (depthSlider != null) {
            depthSlider.visible = !surfaceFound; // slider only while the screen floats; with a surface a plain value is shown
            depthSlider.active = !surfaceFound;
            depthSlider.refresh();
        }
    }

    // ---- resets: each slider on its own (the round buttons, and right click on a slider) ----

    /** A pad button was pressed (or repeated): an arrow slides the screen a step, the middle one puts the projector back where it was and the zoom back to the default. */
    private void onPad(ProjectorPadLayout.Button button) {
        ProjectorPadLayout.Offsets next = ProjectorPadLayout.apply(button, new ProjectorPadLayout.Offsets(side, up, back));
        side = next.side();
        up = next.up();
        back = next.back();
        if (button == ProjectorPadLayout.Button.CENTER) {
            if (depthSlider != null) {
                depthSlider.setTo(back); // the extra-depth slider (shown only when nothing is behind the snail) goes back with it
            }
            // the zoom goes back to the default too: the preferred size is set, and what is shown is it held inside the zoom range for the distance right now
            size = ProjectorPadLayout.defaultSize(VisualSettings.maxScreenSize());
            refreshGeometry(true); // the size shown, the indicator and the 3D view follow at once
        }
        markDirty();
    }

    /**
     * The mouse wheel zooms - resizes the screen; the zoom bar does the same: wheel up = zoom in = bigger. It moves inside the zoom range of
     * the distance right now (see minSize / maxSize). Anything under the mouse that uses the wheel itself (the extra-depth slider) gets it first; see VisualConfigScreen.
     *
     * @return true if the wheel was used
     */
    public boolean onScroll(double delta) {
        if (delta == 0.0) {
            return false;
        }
        zoom(delta > 0.0 ? ZoomBarLayout.Direction.IN : ZoomBarLayout.Direction.OUT);
        return true;
    }

    /** One step of zoom, from the wheel or the zoom bar: in = a screen one size bigger, out = one smaller, inside the range for the distance right now. */
    private void zoom(ZoomBarLayout.Direction direction) {
        int target = ZoomBarLayout.zoomed(shownSize, direction, minSize, maxSize);
        if (target != shownSize) {
            size = target;
            refreshGeometry(true); // the size shown, the indicator and the 3D view follow at once
            markDirty();
        }
    }





    private void resetBack() {
        back = 0.0F;
        depthSlider.setTo(back);
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

    public void tick() {
        refreshGeometry(false);
        miniView.tick(Minecraft.getInstance().level, pos, lastPlacement, lastSurface);
        // a moment after the LAST change, not while the slider is still being dragged
        if (dirty && System.currentTimeMillis() - lastChangeMs >= SEND_DELAY_MS) {
            sendNow();
        }
    }

    /** Call when the host closes: sends what has not been sent yet and stops the live preview. */
    public void removed() {
        if (dirty) {
            sendNow();
        }
        if (active == this) {
            active = null;
        }
    }

    /**
     * Draws the texture, the title and the depth (text or track) - call BEFORE the host draws its widgets, so the sliders land on top.
     * There is no full-screen dimming: the world has to stay visible, because the preview frame is drawn in it.
     */
    public void renderBackground(GuiGraphics g) {
        RenderSystem.enableBlend();
        g.blit(TEXTURE, originX, originY, 0.0F, 0.0F, SnailGuiLayout.TEXTURE_SIZE, SnailGuiLayout.TEXTURE_SIZE,
                SnailGuiLayout.TEXTURE_SIZE, SnailGuiLayout.TEXTURE_SIZE);

        // the zoom indicator beside the bar: on the top stripe when zoomed all the way in (the biggest screen), on the bottom one all the way out
        g.blit(ZOOM_INDICATOR, originX + ZoomBarLayout.INDICATOR_X, originY + ZoomBarLayout.indicatorY(shownSize, minSize, maxSize), 0.0F, 0.0F,
                ZoomBarLayout.INDICATOR_SIZE, ZoomBarLayout.INDICATOR_SIZE, ZoomBarLayout.INDICATOR_SIZE, ZoomBarLayout.INDICATOR_SIZE);

        // the 3D view, in the big display
        miniView.render(g, onScreen(SnailGuiLayout.DISPLAY));

        // title and description: in the box above the panel
        Rect zone = onScreen(SnailGuiLayout.TITLE_ZONE);
        drawFitted(g, host.font(), TITLE, zone.centerX(), zone.y() + 3, zone.w(), 0xFFFFFF);
        drawFitted(g, host.font(), Component.literal("As seen from the front"), zone.centerX(), zone.y() + 15, zone.w(), 0xB8B8B8);

        Rect depthZone = onScreen(SnailGuiLayout.DEPTH_ZONE);
        if (surfaceFound) {
            // With a surface behind the snail the depth is automatic and cannot be moved, so there is nothing to set here: the box shows the screen's size and how far
            // away it is, as plain text, like the title
            drawFitted(g, host.font(), Component.literal(infoText()),
                    depthZone.centerX(), depthZone.y() + (depthZone.h() - 8) / 2, depthZone.w() - 4, 0xFFFFFF);
        } else {
            // Nothing behind the snail: the extra-depth slider. The texture has no bar here, so its track is drawn like the bars of the
            // texture (a dark edge, a dark grey frame, a grey inside); the slider itself is drawn on it with the host's widgets.
            Rect t = onScreen(SnailGuiLayout.DEPTH_BAR);
            g.fill(t.x(), t.y(), t.right(), t.bottom(), 0xFF1D1D1C);
            g.fill(t.x() + 1, t.y() + 1, t.right() - 1, t.bottom() - 1, 0xFF30302F);
            g.fill(t.x() + 2, t.y() + 2, t.right() - 2, t.bottom() - 2, 0xFF3C3B3A);
        }

    }

    /** The tooltips of the round reset buttons - call AFTER the host has drawn its widgets. */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        Font font = host.font();

        Rect display = onScreen(SnailGuiLayout.DISPLAY);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 200.0F); // above the 3D view, which is drawn with a depth of its own
        // The screen's size and how far away it is are in the box below the panel. Only while that box holds the extra-depth slider (nothing behind the snail) they are written
        // here, so they can always be read.
        if (!surfaceFound) {
            drawFitted(g, font, Component.literal(infoText()),
                    display.centerX(), display.y() + 3, display.w() - 8, 0xFFFFFF);
        }
        if (mouseX >= display.x() && mouseX < display.right() && mouseY >= display.y() && mouseY < display.bottom()) {
            drawFitted(g, font, Component.literal("Drag to turn"), display.centerX(), display.bottom() - 11, display.w() - 8, 0xC8C8C8);
        }
        g.pose().popPose();

        // like a vanilla tooltip: after a short rest of the mouse, never while dragging
        ProjectorPadLayout.Button over = host.dragging() ? null : pad.buttonAt(mouseX, mouseY);
        ZoomBarLayout.Direction zoomOver = host.dragging() || over != null ? null : zoomBar.directionAt(mouseX, mouseY);
        int option = over != null ? over.ordinal() : zoomOver != null ? ProjectorPadLayout.Button.values().length + zoomOver.ordinal() : -1;
        if (tooltipTimer.update(option, System.currentTimeMillis()) && option >= 0) {
            int wrap = TooltipTimer.wrapWidth(host.width());
            String title = over != null ? padTitle(over) : zoomOver == ZoomBarLayout.Direction.IN ? "Zoom in" : "Zoom out";
            List<String> details = over != null ? padDetails(over) : zoomDetails(zoomOver);
            List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(title), wrap));
            for (String detail : details) {
                lines.addAll(font.split(Component.literal(detail).withStyle(ChatFormatting.GRAY), wrap));
            }
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** The grey lines under a zoom bar tooltip: no values, only that a click cannot go further this way at this distance, and how else to zoom. */
    private List<String> zoomDetails(ZoomBarLayout.Direction direction) {
        boolean atEnd = ZoomBarLayout.zoomed(shownSize, direction, minSize, maxSize) == shownSize;
        if (!atEnd) {
            return List.of("Scroll or use the Bar Sections");
        }
        // no value: only why a click would do nothing
        return List.of(direction == ZoomBarLayout.Direction.IN ? "Already the biggest at this distance" : "Already the smallest at this distance", "Scroll or use the Bar Sections");
    }

    private static String padTitle(ProjectorPadLayout.Button button) {
        return switch (button) {
            case UP -> "Move Up";
            case DOWN -> "Move Down";
            case LEFT -> "Move Left";
            case RIGHT -> "Move Right";
            case CENTER -> "Reset";
        };
    }

    /** The grey line under a pad tooltip: the arrows have none (no values on the buttons); the middle button says what it puts back. */
    private List<String> padDetails(ProjectorPadLayout.Button button) {
        return switch (button) {
            case UP, DOWN, LEFT, RIGHT -> List.of();   // just the title: the buttons show no values (the size and distance are in the box below the panel)
            case CENTER -> List.of("Resets position and zoom");
        };
    }

    // ------------------------------------------------------------------------------------------------

    /** The screen's size and how far away it is (the one text both places use). */
    private String infoText() {
        return VisualMenuText.sizeAndDistance(shownSize, depth);
    }

    /**
     * Draws centred text with a thin dark outline, so it stays readable over anything: the handle and the fill of a slider, the bright sky
     * behind the title. If it is wider than {@code maxWidth} it is made smaller to fit, instead of running over the edge of its box.
     */
    private static void drawFitted(GuiGraphics g, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        int width = font.width(text);
        float scale = width <= maxWidth ? 1.0F : maxWidth / (float) width;
        g.pose().pushPose();
        g.pose().translate(centerX, y + 4.0F * (1.0F - scale), 0.0F); // keep its middle where it was
        g.pose().scale(scale, scale, 1.0F);
        int x = -width / 2;
        g.drawString(font, text, x - 1, 0, 0xFF000000, false);
        g.drawString(font, text, x + 1, 0, 0xFF000000, false);
        g.drawString(font, text, x, -1, 0xFF000000, false);
        g.drawString(font, text, x, 1, 0xFF000000, false);
        g.drawString(font, text, x, 0, color, false);
        g.pose().popPose();
    }

    /** Position of a value on a slider, 0..1; a slider with no span (min == max) sits at 0. */
    private static double fraction(double value, double min, double max) {
        double span = max - min;
        return span <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, (value - min) / span));
    }



    /**
     * The direction pad: ONE widget for all five buttons, because they are not rectangles (see ProjectorPadLayout.at: the middle button is a circle, each arrow a square).
     * Their looks are part of the texture, so this draws nothing until the mouse is over one: then it draws that button's own picture (textures/gui/buttons/*.png - the very
     * picture that is baked into the texture at that place, so the light has the shape of the arrow or disc) brighter, and darker while it is pressed. Holding an arrow keeps
     * moving the screen (HoldRepeat); the middle button does its one thing per click.
     */
    private static final class PadWidget extends AbstractWidget {
        private static final float HOT = 1.7F;
        private static final float PRESSED = 0.55F;

        private final int originX;
        private final int originY;
        private final Consumer<ProjectorPadLayout.Button> action;
        private final BooleanSupplier hostDragging;
        private final HoldRepeat repeat = new HoldRepeat();
        private final ResourceLocation[] pictures = new ResourceLocation[ProjectorPadLayout.Button.values().length];
        private ProjectorPadLayout.Button pressed = null;

        PadWidget(Rect zone, int originX, int originY, Consumer<ProjectorPadLayout.Button> action, BooleanSupplier hostDragging) {
            super(zone.x(), zone.y(), zone.w(), zone.h(), Component.literal("Projector position"));
            this.originX = originX;
            this.originY = originY;
            this.action = action;
            this.hostDragging = hostDragging;
            for (ProjectorPadLayout.Button b : ProjectorPadLayout.Button.values()) {
                pictures[b.ordinal()] = new ResourceLocation(TransponderSnails.MOD_ID, "textures/gui/buttons/" + ProjectorPadLayout.pictureName(b) + ".png");
            }
        }

        /** The button under a screen position, or null. */
        ProjectorPadLayout.Button buttonAt(double mouseX, double mouseY) {
            return ProjectorPadLayout.at(mouseX - originX, mouseY - originY);
        }

        // Only a point that is on a button counts as "on this widget", so a click anywhere else in the pad's bounding box falls through to whatever is there.
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return this.active && this.visible && buttonAt(mouseX, mouseY) != null;
        }

        @Override
        protected boolean clicked(double mouseX, double mouseY) {
            return isMouseOver(mouseX, mouseY);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            ProjectorPadLayout.Button button = buttonAt(mouseX, mouseY);
            if (button == null) {
                return;
            }
            pressed = button;
            repeat.press(System.currentTimeMillis());
            action.accept(button); // the click itself is the first step
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            stop();
        }

        private void stop() {
            pressed = null;
            repeat.release();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            ProjectorPadLayout.Button hovered = buttonAt(mouseX, mouseY);
            if (pressed != null) {
                // Let go - also somewhere else, where no release reaches this widget - or slid off the button: it stops. Otherwise an arrow that is held repeats.
                if (!Minecraft.getInstance().mouseHandler.isLeftPressed() || hovered != pressed) {
                    stop();
                } else if (pressed != ProjectorPadLayout.Button.CENTER) {
                    for (int due = repeat.poll(System.currentTimeMillis()); due > 0; due--) {
                        action.accept(pressed);
                    }
                }
            }
            // no light while the mouse is dragging something else (the 3D view) across the pad
            if (hovered == null || (pressed == null && hostDragging.getAsBoolean())) {
                return;
            }
            Rect picture = ProjectorPadLayout.rect(hovered);
            float brightness = hovered == pressed ? PRESSED : HOT;
            RenderSystem.enableBlend();
            g.setColor(brightness, brightness, brightness, 1.0F);
            g.blit(pictures[hovered.ordinal()], originX + picture.x(), originY + picture.y(), 0.0F, 0.0F,
                    ProjectorPadLayout.SPRITE_SIZE, ProjectorPadLayout.SPRITE_SIZE, ProjectorPadLayout.SPRITE_SIZE, ProjectorPadLayout.SPRITE_SIZE);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /**
     * The zoom bar: the top half zooms in, the bottom half zooms out (ZoomBarLayout). Like the pad, its looks are part of the texture, so it only lights up the half the mouse is
     * over (a faint light, darker while it is pressed), and holding it keeps zooming (HoldRepeat). The indicator beside it is drawn by the panel.
     */
    private static final class ZoomBarWidget extends AbstractWidget {
        private static final int HOT = 0x40FFFFFF;
        private static final int PRESSED = 0x60000000;

        private final int originX;
        private final int originY;
        private final Consumer<ZoomBarLayout.Direction> action;
        private final BooleanSupplier hostDragging;
        private final HoldRepeat repeat = new HoldRepeat();
        private ZoomBarLayout.Direction pressed = null;

        ZoomBarWidget(Rect zone, int originX, int originY, Consumer<ZoomBarLayout.Direction> action, BooleanSupplier hostDragging) {
            super(zone.x(), zone.y(), zone.w(), zone.h(), Component.literal("Zoom"));
            this.originX = originX;
            this.originY = originY;
            this.action = action;
            this.hostDragging = hostDragging;
        }

        /** The half of the bar under a screen position, or null. */
        ZoomBarLayout.Direction directionAt(double mouseX, double mouseY) {
            return ZoomBarLayout.at(mouseX - originX, mouseY - originY);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return this.active && this.visible && directionAt(mouseX, mouseY) != null;
        }

        @Override
        protected boolean clicked(double mouseX, double mouseY) {
            return isMouseOver(mouseX, mouseY);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            ZoomBarLayout.Direction direction = directionAt(mouseX, mouseY);
            if (direction == null) {
                return;
            }
            pressed = direction;
            repeat.press(System.currentTimeMillis());
            action.accept(direction); // the click itself is the first step
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            stop();
        }

        private void stop() {
            pressed = null;
            repeat.release();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            ZoomBarLayout.Direction hovered = directionAt(mouseX, mouseY);
            if (pressed != null) {
                // Let go (also somewhere else, where no release reaches this widget) or slid off the half: it stops. Otherwise it keeps zooming.
                if (!Minecraft.getInstance().mouseHandler.isLeftPressed() || hovered != pressed) {
                    stop();
                } else {
                    for (int due = repeat.poll(System.currentTimeMillis()); due > 0; due--) {
                        action.accept(pressed);
                    }
                }
            }
            if (hovered == null || (pressed == null && hostDragging.getAsBoolean())) {
                return;
            }
            Rect half = ZoomBarLayout.half(hovered);
            g.fill(originX + half.x(), originY + half.y(), originX + half.right(), originY + half.bottom(), hovered == pressed ? PRESSED : HOT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /**
     * The surface of the 3D view: dragging on it turns the view. It draws nothing itself (the view is drawn by the panel, before the
     * widgets) and makes no click sound.
     */
    private static final class ViewDrag extends AbstractWidget {
        private final ProjectorMiniView view;

        ViewDrag(Rect r, ProjectorMiniView view) {
            super(r.x(), r.y(), r.w(), r.h(), Component.literal("Projector view"));
            this.view = view;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            // nothing: the click only starts the drag
        }

        @Override
        public void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            view.drag(dragX, dragY);
        }

        @Override
        public void playDownSound(SoundManager handler) {
            // a drag surface, not a button
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /**
     * A slider drawn on the bar of the texture: a faint fill up to the handle, a handle, and its text in the middle. Moves in fixed steps;
     * mouse drag, arrow keys (one step) and the mouse wheel (one step) all work, and the handle always sits exactly on a step. Right click
     * resets it. Its range can change while the menu is open (the size slider follows the zoom).
     */
    private static final class ConfigSlider extends AbstractSliderButton {
        private double min;
        private double max;
        private final double step;
        private final boolean centered;
        private final DoubleConsumer onChange;
        private final DoubleFunction<String> label;
        private final Runnable onReset;

        ConfigSlider(Rect r, double min, double max, double step, double initial, boolean centered,
                     DoubleConsumer onChange, DoubleFunction<String> label, Runnable onReset) {
            super(r.x(), r.y(), r.w(), r.h(), Component.empty(), fraction(initial, min, max));
            this.min = min;
            this.max = max;
            this.step = step;
            this.centered = centered;
            this.onChange = onChange;
            this.label = label;
            this.onReset = onReset;
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

        /** Sets the slider without reporting a change (used by the resets, which report once themselves). */
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
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            // the handle sits where vanilla's slider puts it, so dragging and drawing agree
            int handleX = x + 4 + (int) Math.round(fraction(current(), min, max) * (w - 8));
            int half = 3;

            if (this.active) { // a faint fill up to the handle: from the left, or from the middle for a slider with 0 in the middle
                int from = centered ? x + w / 2 : x + 2;
                g.fill(Math.min(from, handleX), y + 2, Math.max(from, handleX), y + h - 2, FILL_COLOR);
                g.fill(handleX - half, y + 1, handleX + half, y + h - 1, HANDLE_EDGE);
                g.fill(handleX - half + 1, y + 2, handleX + half - 1, y + h - 2, this.isHoveredOrFocused() ? HANDLE_HOT : HANDLE_COLOR);
            }

            // the text last, outlined: it is always fully readable, also where the handle is (at its start position that is the middle)
            Font font = Minecraft.getInstance().font;
            drawFitted(g, font, getMessage(), x + w / 2, y + (h - 8) / 2, w - 10, this.active ? 0xFFFFFF : 0x909090);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && this.active && this.visible && this.isMouseOver(mouseX, mouseY)) { // right click: reset this slider
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onReset.run();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
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
