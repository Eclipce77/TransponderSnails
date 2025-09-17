package net.eclipce.transpondersnails.client;

import net.eclipce.transpondersnails.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple client configuration screen for Transponder Snails
 * Only contains the numpad toggle option
 */
public class ClientConfigScreen extends Screen {
    private final Screen parent;
    private Button numpadButton;
    private boolean currentNumpadState;

    public ClientConfigScreen(Screen parent) {
        super(Component.literal("Transponder Snails Settings"));
        this.parent = parent;
        this.currentNumpadState = ModConfig.isNumpadEnabled();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Create the numpad toggle button
        numpadButton = Button.builder(
                        Component.literal(currentNumpadState ? "True" : "False")
                                .withStyle(currentNumpadState ?
                                        net.minecraft.ChatFormatting.GREEN :
                                        net.minecraft.ChatFormatting.RED),
                        this::toggleNumpad)
                .bounds(centerX - 40, centerY - 10, 80, 20)
                .build();
        this.addRenderableWidget(numpadButton);

        // Add back button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(centerX - 40, centerY + 30, 80, 20)
                .build());
    }

    private void toggleNumpad(Button button) {
        // Toggle the state
        currentNumpadState = !currentNumpadState;

        // Update the config immediately (auto-saves)
        ModConfig.setNumpadEnabled(currentNumpadState);

        // Update button text and color
        button.setMessage(Component.literal(currentNumpadState ? "True" : "False")
                .withStyle(currentNumpadState ?
                        net.minecraft.ChatFormatting.GREEN :
                        net.minecraft.ChatFormatting.RED));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Render title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);

        // Render description
        String description = "Client Settings";
        guiGraphics.drawCenteredString(this.font, description, this.width / 2, 45, 0xAAAAAA);

        // Render label for the numpad option
        String label = "Enable Numpad for Dialing:";
        int labelWidth = this.font.width(label);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        guiGraphics.drawString(this.font, label, centerX - labelWidth / 2, centerY - 25, 0xFFFFFF);

        // Render tooltip if hovering over the label
        if (mouseX >= centerX - labelWidth / 2 && mouseX <= centerX + labelWidth / 2 &&
                mouseY >= centerY - 29 && mouseY <= centerY - 16) {
            renderTooltip(guiGraphics, "Allows using numpad keys (0-9) for dialing in addition to number row keys", mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTooltip(GuiGraphics guiGraphics, String text, int x, int y) {
        int tooltipWidth = this.font.width(text) + 8;
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
        guiGraphics.drawString(this.font, text, tooltipX + 4, tooltipY + 4, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}