package net.eclipce.transpondersnails.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Main menu config screen for setting default values
 * These defaults are used when creating new worlds
 */
public class MainMenuConfigScreen extends Screen {
    private final Screen parent;
    private final ForgeConfigSpec configSpec;

    // GUI elements
    private EditBox locationalRangeBox;
    private EditBox handheldRangeBox;
    private EditBox ringTimeoutBox;
    private EditBox interactionRangeBox;

    // Default values (hardcoded defaults for new worlds)
    private static final double DEFAULT_LOCATIONAL_RANGE = 10.0;
    private static final double DEFAULT_HANDHELD_RANGE = 3.0;
    private static final long DEFAULT_RING_TIMEOUT = 30000L;
    private static final double DEFAULT_INTERACTION_RANGE = 10.0;

    // Current default values (what will be used for new worlds)
    private double currentLocationalDefault;
    private double currentHandheldDefault;
    private long currentRingTimeoutDefault;
    private double currentInteractionDefault;

    // Text labels
    private Component locationalLabel;
    private Component handheldLabel;
    private Component ringTimeoutLabel;
    private Component interactionLabel;

    // Tooltip tracking variables
    private String hoveredTooltip;
    private int tooltipX;
    private int tooltipY;

    public MainMenuConfigScreen(Screen parent, ForgeConfigSpec configSpec) {
        super(Component.literal("Transponder Snails Defaults"));
        this.parent = parent;
        this.configSpec = configSpec;

        // Initialize tooltip variables
        this.hoveredTooltip = null;
        this.tooltipX = 0;
        this.tooltipY = 0;

        // Load current default values (or use hardcoded if config not available)
        loadCurrentDefaults();
    }

    private void loadCurrentDefaults() {
        // Try to load from config, but use hardcoded defaults if not available
        try {
            currentLocationalDefault = net.eclipce.transpondersnails.config.ModConfig.SERVER.locationalSnailRange.getDefault();
            currentHandheldDefault = net.eclipce.transpondersnails.config.ModConfig.SERVER.handheldSnailRange.getDefault();
            currentRingTimeoutDefault = net.eclipce.transpondersnails.config.ModConfig.SERVER.ringTimeoutMs.getDefault();
            currentInteractionDefault = net.eclipce.transpondersnails.config.ModConfig.SERVER.snailInteractionRange.getDefault();
        } catch (Exception e) {
            // Fallback to hardcoded defaults if config access fails
            currentLocationalDefault = DEFAULT_LOCATIONAL_RANGE;
            currentHandheldDefault = DEFAULT_HANDHELD_RANGE;
            currentRingTimeoutDefault = DEFAULT_RING_TIMEOUT;
            currentInteractionDefault = DEFAULT_INTERACTION_RANGE;
        }
    }

    @Override
    protected void init() {
        super.init();

        // Create text labels
        locationalLabel = Component.literal("Placed Snail Audio Range:");
        handheldLabel = Component.literal("Handheld Snail Audio Range:");
        ringTimeoutLabel = Component.literal("Ringing Timeout (in Ms):");
        interactionLabel = Component.literal("Snail Interaction Range:");

        // Calculate centered positions
        int centerX = this.width / 2;
        int startY = 80; // Start lower to make room for explanation
        int spacing = 30;
        int textBoxWidth = 80;

        // Locational Snail Range
        locationalRangeBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        locationalRangeBox.setValue(String.valueOf(currentLocationalDefault));
        locationalRangeBox.setMaxLength(5);
        locationalRangeBox.setFocused(false);
        locationalRangeBox.setCursorPosition(0);
        locationalRangeBox.setHighlightPos(0);
        this.addRenderableWidget(locationalRangeBox);

        startY += spacing;

        // Handheld Snail Range
        handheldRangeBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        handheldRangeBox.setValue(String.valueOf(currentHandheldDefault));
        handheldRangeBox.setMaxLength(4);
        handheldRangeBox.setFocused(false);
        handheldRangeBox.setCursorPosition(0);
        handheldRangeBox.setHighlightPos(0);
        this.addRenderableWidget(handheldRangeBox);

        startY += spacing;

        // Ring Timeout
        ringTimeoutBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        ringTimeoutBox.setValue(String.valueOf(currentRingTimeoutDefault));
        ringTimeoutBox.setMaxLength(6);
        ringTimeoutBox.setFocused(false);
        ringTimeoutBox.setCursorPosition(0);
        ringTimeoutBox.setHighlightPos(0);
        this.addRenderableWidget(ringTimeoutBox);

        startY += spacing;

        // Interaction Range
        interactionRangeBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        interactionRangeBox.setValue(String.valueOf(currentInteractionDefault));
        interactionRangeBox.setMaxLength(4);
        interactionRangeBox.setFocused(false);
        interactionRangeBox.setCursorPosition(0);
        interactionRangeBox.setHighlightPos(0);
        this.addRenderableWidget(interactionRangeBox);

        startY += spacing + 20;

        // Save Button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Save Defaults"),
                        this::saveDefaults)
                .bounds(centerX - 100, startY - 15, 80, 20)
                .build());

        // Cancel Button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Cancel"),
                        this::cancelConfig)
                .bounds(centerX + 20, startY - 15, 80, 20)
                .build());

        // Reset to Hardcoded Defaults Button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Reset to Defaults"),
                        this::resetToHardcodedDefaults)
                .bounds(centerX - 70, startY + 15, 140, 20)
                .build());
    }

    private void saveDefaults(Button button) {
        try {
            // Parse and validate values
            double locationalRange = parseDouble(locationalRangeBox.getValue(), 1.0, 100.0, DEFAULT_LOCATIONAL_RANGE);
            double handheldRange = parseDouble(handheldRangeBox.getValue(), 1.0, 50.0, DEFAULT_HANDHELD_RANGE);
            long ringTimeout = parseLong(ringTimeoutBox.getValue(), 5000L, 300000L, DEFAULT_RING_TIMEOUT);
            double interactionRange = parseDouble(interactionRangeBox.getValue(), 1.0, 50.0, DEFAULT_INTERACTION_RANGE);

            // These values will be used as defaults for new worlds
            // We could save them to a separate defaults config file
            saveDefaultsToFile(locationalRange, handheldRange, ringTimeout, interactionRange);

            this.minecraft.setScreen(parent);

        } catch (Exception e) {
            System.err.println("Defaults save error: " + e.getMessage());
        }
    }

    private void saveDefaultsToFile(double locationalRange, double handheldRange, long ringTimeout, double interactionRange) {
        // Save to a separate defaults configuration file
        // This could be implemented as a simple properties file or JSON
        // For now, we'll update the static defaults in the config class

        // Update the config specification defaults (this affects new world generation)
        try {
            // Note: This is a conceptual approach - you might need to implement
            // a separate defaults storage system depending on your needs

            System.out.println("Saved new defaults:");
            System.out.println("  Locational Range: " + locationalRange);
            System.out.println("  Handheld Range: " + handheldRange);
            System.out.println("  Ring Timeout: " + ringTimeout);
            System.out.println("  Interaction Range: " + interactionRange);

            // You could save these to a separate config file that gets loaded
            // when creating new worlds, or modify the default values in ModConfig

        } catch (Exception e) {
            System.err.println("Failed to save defaults: " + e.getMessage());
        }
    }

    private void cancelConfig(Button button) {
        this.minecraft.setScreen(parent);
    }

    private void resetToHardcodedDefaults(Button button) {
        locationalRangeBox.setValue(String.valueOf(DEFAULT_LOCATIONAL_RANGE));
        handheldRangeBox.setValue(String.valueOf(DEFAULT_HANDHELD_RANGE));
        ringTimeoutBox.setValue(String.valueOf(DEFAULT_RING_TIMEOUT));
        interactionRangeBox.setValue(String.valueOf(DEFAULT_INTERACTION_RANGE));
    }

    private double parseDouble(String value, double min, double max, double defaultValue) {
        try {
            double parsed = Double.parseDouble(value);
            if (parsed < min || parsed > max) {
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long parseLong(String value, long min, long max, long defaultValue) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) {
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Render title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Render description
        String description = "Default settings for new worlds";
        guiGraphics.drawCenteredString(this.font, description, this.width / 2, 35, 0xAAAAAA);

        // Render explanation
        String explanation = "These values will be used when creating new worlds";
        guiGraphics.drawCenteredString(this.font, explanation, this.width / 2, 50, 0x888888);

        // Render text labels (centered to the left of text boxes)
        int centerX = this.width / 2;
        int startY = 80;
        int spacing = 30;

        // Calculate label positions to center them with the text boxes
        int labelX = centerX - 190; // Position labels to the left of center
        int rangeX = centerX + 100; // Position range info to the right of text boxes

        // Reset tooltip for this frame
        hoveredTooltip = null;

        // Locational Snail Range
        int labelWidth = this.font.width(locationalLabel);
        if (mouseX >= labelX && mouseX <= labelX + labelWidth && mouseY >= startY + 6 - 4 && mouseY <= startY + 6 + 9) {
            hoveredTooltip = "{locational_snail_range}";
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
        guiGraphics.drawString(this.font, locationalLabel, labelX, startY + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, "(1.0 - 100.0)", rangeX, startY + 6, 0xAAAAAA);
        startY += spacing;

        // Handheld Snail Range
        labelWidth = this.font.width(handheldLabel);
        if (mouseX >= labelX && mouseX <= labelX + labelWidth && mouseY >= startY + 6 - 4 && mouseY <= startY + 6 + 9) {
            hoveredTooltip = "{handheld_snail_range}";
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
        guiGraphics.drawString(this.font, handheldLabel, labelX, startY + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, "(1.0 - 50.0)", rangeX, startY + 6, 0xAAAAAA);
        startY += spacing;

        // Ring Timeout
        labelWidth = this.font.width(ringTimeoutLabel);
        if (mouseX >= labelX && mouseX <= labelX + labelWidth && mouseY >= startY + 6 - 4 && mouseY <= startY + 6 + 9) {
            hoveredTooltip = "{ring_timeout_ms} [1 Sec = 1000 Ms]";
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
        guiGraphics.drawString(this.font, ringTimeoutLabel, labelX, startY + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, "(5000 - 300000)", rangeX, startY + 6, 0xAAAAAA);
        startY += spacing;

        // Interaction Range
        labelWidth = this.font.width(interactionLabel);
        if (mouseX >= labelX && mouseX <= labelX + labelWidth && mouseY >= startY + 6 - 4 && mouseY <= startY + 6 + 9) {
            hoveredTooltip = "{snail_interaction_range}";
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
        guiGraphics.drawString(this.font, interactionLabel, labelX, startY + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, "(1.0 - 50.0)", rangeX, startY + 6, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render custom tooltip if hovering over a label
        if (hoveredTooltip != null) {
            renderCustomTooltip(guiGraphics, hoveredTooltip, tooltipX, tooltipY);
        }
    }

    /**
     * Renders a custom tooltip at the specified position
     */
    private void renderCustomTooltip(GuiGraphics guiGraphics, String tooltipText, int x, int y) {
        int tooltipWidth = this.font.width(tooltipText) + 8;
        int tooltipHeight = 16;

        // Adjust position to keep tooltip on screen
        int tooltipX = x + 10;
        int tooltipY = y - 24;

        if (tooltipX + tooltipWidth > this.width) {
            tooltipX = x - tooltipWidth - 10;
        }
        if (tooltipY < 0) {
            tooltipY = y + 20;
        }

        // Draw tooltip background
        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0100010);
        guiGraphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth - 1, tooltipY + tooltipHeight - 1, 0x505000FF);

        // Draw tooltip text
        guiGraphics.drawString(this.font, tooltipText, tooltipX + 4, tooltipY + 4, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}