package net.eclipce.transpondersnails.client;

import net.eclipce.transpondersnails.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * Client configuration screen for Transponder Snails.
 * Contains the numpad toggle and the video call performance settings: True/False boxes for on/off options,
 * sliders for options with set values. Every change is saved to the client config right away.
 */
public class ClientConfigScreen extends Screen {
    /** The resolutions a video feed can be limited to (the config accepts 256 - 2048). */
    private static final int[] RESOLUTIONS = {256, 512, 1024, 2048};
    /** How many video feeds this computer may render at once (the config accepts 1 - 16). */
    private static final int[] FEED_COUNTS = IntStream.rangeClosed(1, 16).toArray();

    // Vertical layout, in pixels below the top of the option list. Everything is derived from these, so nothing can overlap.
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_NUMPAD_LABEL = 0;
    private static final int ROW_NUMPAD_BUTTON = 11;
    private static final int ROW_ADAPTIVE_LABEL = 38;
    private static final int ROW_ADAPTIVE_BUTTON = 49;
    private static final int ROW_RESOLUTION = 78;
    private static final int ROW_FEEDS = 102;
    private static final int ROW_BACK = 134;
    private static final int BLOCK_HEIGHT = ROW_BACK + ROW_HEIGHT;

    // The options that have a tooltip, in the order of TOOLTIPS.
    private static final int OPTION_NUMPAD = 0;
    private static final int OPTION_ADAPTIVE = 1;
    private static final int OPTION_RESOLUTION = 2;
    private static final int OPTION_FEEDS = 3;
    /** Kept short: vanilla tooltips wrap at about 170 pixels, so every one of these is only two or three short lines. */
    private static final String[] TOOLTIPS = {
            "Also use the numpad keys (0-9) when dialing.",
            "Lowers the quality of far or small video screens, and of all video while your frame rate is low.",
            "The highest resolution a video feed may use here. The server may set a lower limit.",
            "How many video feeds are drawn at once (the nearest ones). The rest keep their last picture."
    };

    private static final String NUMPAD_LABEL = "Enable Numpad for Dialing:";
    private static final String ADAPTIVE_LABEL = "Adaptive Video Quality:";

    private final Screen parent;
    private Button numpadButton;
    private boolean currentNumpadState;
    private Button adaptiveButton;
    private boolean currentAdaptiveState;
    private StepSlider resolutionSlider;
    private StepSlider feedsSlider;
    private final TooltipTimer tooltipTimer = new TooltipTimer();

    public ClientConfigScreen(Screen parent) {
        super(Component.literal("Transponder Snails Settings"));
        this.parent = parent;
        this.currentNumpadState = ModConfig.isNumpadEnabled();
        this.currentAdaptiveState = ModConfig.isAdaptiveVideoQuality();
    }

    /** On a short window (e.g. GUI scale 4 at 720p) the description is dropped and the title moves up, so the list still fits. */
    private boolean compact() {
        return this.height < 60 + BLOCK_HEIGHT + 8;
    }

    private int titleY() {
        return compact() ? 6 : 30;
    }

    /** Top of the option list: centred vertically, but never into the title - and never so low that the Back button leaves the screen. */
    private int top() {
        return compact()
                ? Math.max(18, this.height - BLOCK_HEIGHT - 6)
                : Math.max(60, this.height / 2 - BLOCK_HEIGHT / 2);
    }

    private static Component stateText(boolean state) {
        return Component.literal(state ? "True" : "False")
                .withStyle(state ?
                        net.minecraft.ChatFormatting.GREEN :
                        net.minecraft.ChatFormatting.RED);
    }

    @Override
    protected void init() {
        super.init();
        commitSliders(); // keep a change made just before the window was resized

        int centerX = this.width / 2;
        int top = top();

        // Numpad toggle (True/False box)
        numpadButton = Button.builder(stateText(currentNumpadState), this::toggleNumpad)
                .bounds(centerX - 40, top + ROW_NUMPAD_BUTTON, 80, ROW_HEIGHT)
                .build();
        this.addRenderableWidget(numpadButton);

        // Adaptive video quality toggle (True/False box)
        adaptiveButton = Button.builder(stateText(currentAdaptiveState), this::toggleAdaptive)
                .bounds(centerX - 40, top + ROW_ADAPTIVE_BUTTON, 80, ROW_HEIGHT)
                .build();
        this.addRenderableWidget(adaptiveButton);

        // Highest video resolution (slider through the resolutions), saved when the slider is released
        resolutionSlider = this.addRenderableWidget(new StepSlider(centerX - 100, top + ROW_RESOLUTION, 200, ROW_HEIGHT,
                RESOLUTIONS, ModConfig.getMaxVideoResolution(),
                v -> "Max Video Resolution: " + v + " px",
                ModConfig::setMaxVideoResolution));

        // How many video feeds are rendered at once (slider 1 - 16)
        feedsSlider = this.addRenderableWidget(new StepSlider(centerX - 100, top + ROW_FEEDS, 200, ROW_HEIGHT,
                FEED_COUNTS, ModConfig.getMaxActiveVideoFeeds(),
                v -> "Max Active Video Feeds: " + v,
                ModConfig::setMaxActiveVideoFeeds));

        // Add back button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(centerX - 40, top + ROW_BACK, 80, ROW_HEIGHT)
                .build());
    }

    private void toggleNumpad(Button button) {
        // Toggle the state
        currentNumpadState = !currentNumpadState;

        // Update the config immediately (auto-saves)
        ModConfig.setNumpadEnabled(currentNumpadState);

        // Update button text and color
        button.setMessage(stateText(currentNumpadState));
    }

    private void toggleAdaptive(Button button) {
        currentAdaptiveState = !currentAdaptiveState;
        ModConfig.setAdaptiveVideoQuality(currentAdaptiveState); // auto-saves
        button.setMessage(stateText(currentAdaptiveState));
    }

    /** Saves a slider change that has not been saved yet (sliders save when released, not on every step while dragging). */
    private void commitSliders() {
        if (resolutionSlider != null) resolutionSlider.commit();
        if (feedsSlider != null) feedsSlider.commit();
    }

    @Override
    public void removed() {
        commitSliders();
        super.removed();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Render title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, titleY(), 0xFFFFFF);

        // Render description (not on a short window)
        if (!compact()) {
            String description = "Client Settings";
            guiGraphics.drawCenteredString(this.font, description, this.width / 2, 45, 0xAAAAAA);
        }

        int centerX = this.width / 2;
        int top = top();

        // Labels of the True/False options (the sliders carry their name in their own text)
        drawLabel(guiGraphics, NUMPAD_LABEL, centerX, top + ROW_NUMPAD_LABEL);
        drawLabel(guiGraphics, ADAPTIVE_LABEL, centerX, top + ROW_ADAPTIVE_LABEL);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Tooltip, like a vanilla one: only over an option's NAME (the label of a True/False option - not its box - or the whole
        // slider, which carries its name), only after the mouse has rested there for a moment, and never while a slider is being dragged.
        int option = this.isDragging() ? -1 : optionUnderMouse(mouseX, mouseY, centerX, top);
        if (tooltipTimer.update(option, System.currentTimeMillis())) {
            drawTooltip(guiGraphics, TOOLTIPS[option], mouseX, mouseY);
        }
    }

    /** The option whose name the mouse is over, or -1. */
    private int optionUnderMouse(int mouseX, int mouseY, int centerX, int top) {
        if (overLabel(NUMPAD_LABEL, centerX, top + ROW_NUMPAD_LABEL, mouseX, mouseY)) return OPTION_NUMPAD;
        if (overLabel(ADAPTIVE_LABEL, centerX, top + ROW_ADAPTIVE_LABEL, mouseX, mouseY)) return OPTION_ADAPTIVE;
        if (resolutionSlider.isMouseOver(mouseX, mouseY)) return OPTION_RESOLUTION;
        if (feedsSlider.isMouseOver(mouseX, mouseY)) return OPTION_FEEDS;
        return -1;
    }

    private void drawLabel(GuiGraphics guiGraphics, String label, int centerX, int y) {
        int labelWidth = this.font.width(label);
        guiGraphics.drawString(this.font, label, centerX - labelWidth / 2, y, 0xFFFFFF);
    }

    /** Is the mouse over the label drawn by drawLabel(label, centerX, y)? */
    private boolean overLabel(String label, int centerX, int y, int mouseX, int mouseY) {
        return TooltipTimer.overLabel(mouseX, mouseY, centerX, y, this.font.width(label));
    }

    /**
     * Draws a tooltip with vanilla's own tooltip renderer, so it looks like every other tooltip (frame, colors, placement next to the
     * mouse, kept inside the window) and - unlike a hand-drawn one - is lifted above everything else on the screen, so no other text
     * can end up on top of it. The text wraps to a width that adapts to the window.
     */
    private void drawTooltip(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), TooltipTimer.wrapWidth(this.width));
        guiGraphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * A slider that picks one of a fixed list of values. The handle always sits exactly on a value. A change is saved when the slider
     * is released (or at once for a key press), not on every step while it is being dragged.
     */
    private static final class StepSlider extends AbstractSliderButton {
        private final int[] values;
        private final IntFunction<String> label;
        private final IntConsumer onCommit;
        private boolean pending = false;

        StepSlider(int x, int y, int width, int height, int[] values, int initial,
                   IntFunction<String> label, IntConsumer onCommit) {
            super(x, y, width, height, Component.empty(),
                    StepSliderMath.position(StepSliderMath.indexOf(values, initial), values.length));
            this.values = values;
            this.label = label;
            this.onCommit = onCommit;
            updateMessage();
        }

        private int index() {
            return StepSliderMath.indexAt(this.value, values.length);
        }

        @Override
        protected void updateMessage() {
            if (label == null) return; // may be called before the fields are set
            setMessage(Component.literal(label.apply(values[index()])));
        }

        @Override
        protected void applyValue() {
            this.value = StepSliderMath.position(index(), values.length); // snap the handle onto the value
            pending = true;
        }

        /** Saves the chosen value if it has not been saved yet. */
        void commit() {
            if (pending) {
                pending = false;
                onCommit.accept(values[index()]);
            }
        }

        private void nudge(int steps) {
            int idx = StepSliderMath.clampIndex(index() + steps, values.length);
            this.value = StepSliderMath.position(idx, values.length);
            pending = true;
            updateMessage();
            commit();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            commit();
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
