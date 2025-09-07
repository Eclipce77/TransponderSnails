package net.eclipce.transpondersnails.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Custom config screen for Transponder Snails
 * Provides a user-friendly interface for editing configuration values
 */
public class ModConfigScreen extends Screen {
    private final Screen parent;
    private final ForgeConfigSpec configSpec;
    private final String configFileName;

    // GUI elements
    private EditBox locationalRangeBox;
    private EditBox handheldRangeBox;
    private EditBox ringTimeoutBox;
    private EditBox interactionRangeBox;

    // Store original values for cancel functionality
    private double originalLocationalRange;
    private double originalHandheldRange;
    private long originalRingTimeout;
    private double originalInteractionRange;

    // Text labels
    private Component locationalLabel;
    private Component handheldLabel;
    private Component ringTimeoutLabel;
    private Component interactionLabel;

    // Tooltip tracking variables
    private String hoveredTooltip;
    private int tooltipX;
    private int tooltipY;

    public ModConfigScreen(Screen parent, ForgeConfigSpec configSpec, String configFileName) {
        super(Component.literal("Transponder Snails"));
        this.parent = parent;
        this.configSpec = configSpec;
        this.configFileName = configFileName;

        // Initialize tooltip variables
        this.hoveredTooltip = null;
        this.tooltipX = 0;
        this.tooltipY = 0;
    }

    @Override
    protected void init() {
        super.init();

        // Store original values
        originalLocationalRange = net.eclipce.transpondersnails.config.ModConfig.SERVER.locationalSnailRange.get();
        originalHandheldRange = net.eclipce.transpondersnails.config.ModConfig.SERVER.handheldSnailRange.get();
        originalRingTimeout = net.eclipce.transpondersnails.config.ModConfig.SERVER.ringTimeoutMs.get();
        originalInteractionRange = net.eclipce.transpondersnails.config.ModConfig.SERVER.snailInteractionRange.get();

        // Create text labels
        locationalLabel = Component.literal("Placed Snail Audio Range:");
        handheldLabel = Component.literal("Handheld Snail Audio Range:");
        ringTimeoutLabel = Component.literal("Ringing Timeout (in Ms):");
        interactionLabel = Component.literal("Snail Interaction Range:");

        // Calculate centered positions
        int centerX = this.width / 2;
        int startY = 60;
        int spacing = 30;
        int textBoxWidth = 80;

        // Locational Snail Range
        locationalRangeBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        locationalRangeBox.setValue(String.valueOf(originalLocationalRange));
        locationalRangeBox.setMaxLength(5);
        this.addRenderableWidget(locationalRangeBox);

        startY += spacing;

        // Handheld Snail Range
        handheldRangeBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        handheldRangeBox.setValue(String.valueOf(originalHandheldRange));
        handheldRangeBox.setMaxLength(4);
        this.addRenderableWidget(handheldRangeBox);

        startY += spacing;

        // Ring Timeout
        ringTimeoutBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        ringTimeoutBox.setValue(String.valueOf(originalRingTimeout));
        ringTimeoutBox.setMaxLength(6);
        this.addRenderableWidget(ringTimeoutBox);

        startY += spacing;

        // Interaction Range
        interactionRangeBox = new EditBox(this.font, centerX + 10, startY, textBoxWidth, 20, Component.literal(""));
        interactionRangeBox.setValue(String.valueOf(originalInteractionRange));
        interactionRangeBox.setMaxLength(4);
        this.addRenderableWidget(interactionRangeBox);

        startY += spacing + 20;

        // Save Button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Save"),
                        this::saveConfig)
                .bounds(centerX - 100, startY - 15, 80, 20)
                .build());

        // Cancel Button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Cancel"),
                        this::cancelConfig)
                .bounds(centerX + 20, startY - 15, 80, 20)
                .build());

        // Reset to Defaults Button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Reset to Defaults"),
                        this::resetToDefaults)
                .bounds(centerX - 70, startY + 15, 140, 20)
                .build());
    }

    private void saveConfig(Button button) {
        try {
            // Parse and validate values
            double locationalRange = parseDouble(locationalRangeBox.getValue(), 1.0, 100.0, originalLocationalRange);
            double handheldRange = parseDouble(handheldRangeBox.getValue(), 1.0, 50.0, originalHandheldRange);
            long ringTimeout = parseLong(ringTimeoutBox.getValue(), 5000L, 300000L, originalRingTimeout);
            double interactionRange = parseDouble(interactionRangeBox.getValue(), 1.0, 50.0, originalInteractionRange);

            // Set the config values
            net.eclipce.transpondersnails.config.ModConfig.SERVER.locationalSnailRange.set(locationalRange);
            net.eclipce.transpondersnails.config.ModConfig.SERVER.handheldSnailRange.set(handheldRange);
            net.eclipce.transpondersnails.config.ModConfig.SERVER.ringTimeoutMs.set(ringTimeout);
            net.eclipce.transpondersnails.config.ModConfig.SERVER.snailInteractionRange.set(interactionRange);

            // Save the config file
            configSpec.save();

            this.minecraft.setScreen(parent);

        } catch (Exception e) {
            // Show error message if parsing failed
            Component errorMsg = Component.literal("Invalid values entered! Please check your input.");
            System.err.println("Config save error: " + e.getMessage());
        }
    }

    private void cancelConfig(Button button) {
        this.minecraft.setScreen(parent);
    }

    private void resetToDefaults(Button button) {
        locationalRangeBox.setValue("10.0");
        handheldRangeBox.setValue("3.0");
        ringTimeoutBox.setValue("30000");
        interactionRangeBox.setValue("10.0");
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
        String description = "Config settings";
        guiGraphics.drawCenteredString(this.font, description, this.width / 2, 35, 0xAAAAAA);

        // Render text labels (centered to the left of text boxes)
        int centerX = this.width / 2;
        int startY = 60;
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
            hoveredTooltip = "{ring_timeout_ms} " +
                    "[1 Sec = 1000 Ms]";
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