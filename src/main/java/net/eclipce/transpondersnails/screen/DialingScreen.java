// Fixed Dialing Menu Container
package net.eclipce.transpondersnails.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.eclipce.transpondersnails.config.ModConfig;
import net.eclipce.transpondersnails.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DialingScreen extends AbstractContainerScreen<DialingMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("transpondersnails", "textures/gui/dial-clear.png");

    // Your GUI texture is 256x256
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 256;

    // Text box for displaying dialed number
    private EditBox numberDisplay;

    public DialingScreen(DialingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        // Center the GUI on screen
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        // Create the number display text box
        int textBoxWidth = 32;  // Width of the text box
        int textBoxHeight = 20;  // Height of the text box
        int textBoxX = this.leftPos + (this.imageWidth - textBoxWidth) / 2; // Centered horizontally
        int textBoxY = this.topPos + 117; // Position from top of GUI

        this.numberDisplay = new EditBox(
                this.font,              // Font renderer
                textBoxX,               // X position
                textBoxY,               // Y position
                textBoxWidth,           // Width
                textBoxHeight,          // Height
                Component.literal("")   // Initial text
        );

        // Configure the text box
        this.numberDisplay.setMaxLength(4);            // Max characters (adjust as needed)
        this.numberDisplay.setBordered(true);          // Show border
        this.numberDisplay.setVisible(true);           // Make visible
        this.numberDisplay.setEditable(false);         // Make read-only (buttons control input)
        this.numberDisplay.setValue("");               // Start with empty string
        this.numberDisplay.setCanLoseFocus(false);     // Prevent losing focus
        this.numberDisplay.setFocused(false);          // Start unfocused

        // Center the text in the EditBox
        this.numberDisplay.setTextColor(0xFFFFFF);     // White text color
        this.numberDisplay.setTextColorUneditable(0xFFFFFF); // White text when not editable

        // Add the text box to the screen
        this.addRenderableWidget(this.numberDisplay);

        this.addRenderableWidget(new DialButton(
                this.leftPos + 161,
                this.topPos + 65,
                32, 32, // Button size (adjust as needed)
                1, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 130,
                this.topPos + 48,
                32, 32, // Button size (adjust as needed)
                2, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 94,
                this.topPos + 48,
                32, 32, // Button size (adjust as needed)
                3, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 63,
                this.topPos + 65,
                32, 32, // Button size (adjust as needed)
                4, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 48,
                this.topPos + 95,
                32, 32, // Button size (adjust as needed)
                5, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 48,
                this.topPos + 128,
                32, 32, // Button size (adjust as needed)
                6, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 63,
                this.topPos + 159,
                32, 32, // Button size (adjust as needed)
                7, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 94,
                this.topPos + 176,
                32, 32, // Button size (adjust as needed)
                8, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 129,
                this.topPos + 176,
                32, 32, // Button size (adjust as needed)
                9, // Button number
                this::onDialButtonPressed
        ));

        this.addRenderableWidget(new DialButton(
                this.leftPos + 161,
                this.topPos + 159,
                32, 32, // Button size (adjust as needed)
                0, // Button number
                this::onDialButtonPressed
        ));

        // Add the Call Button (64x64, positioned in center)
        this.addRenderableWidget(new CallButton(
                this.leftPos + 179,
                this.topPos + 93,
                32, 32, // Button size (adjust as needed)
                10, // Button number
                this::onCallButtonPressed
        ));

        // Add the Clear Button (64x64, positioned in center)
        this.addRenderableWidget(new ClearButton(
                this.leftPos + 169,
                this.topPos + 106,
                64, 64, // Button size (adjust as needed)
                -1, // Button number
                this::onClearButtonPressed
        ));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if the click was on the number display
        if (this.numberDisplay.isMouseOver(mouseX, mouseY)) {
            // Don't allow the EditBox to gain focus - just ignore the click
            return true; // Consume the click event
        }

        // For all other clicks, use normal behavior
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle main number row keys (always enabled)
        if (keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_9) {
            onDialButtonPressed(keyCode - InputConstants.KEY_0);
            return true;
        } else if (keyCode == InputConstants.KEY_0) {
            onDialButtonPressed(0);
            return true;
        }

        // Handle numpad keys (only if enabled in config)
        if (ModConfig.isNumpadEnabled()) {
            if (keyCode >= InputConstants.KEY_NUMPAD1 && keyCode <= InputConstants.KEY_NUMPAD9) {
                onDialButtonPressed(keyCode - InputConstants.KEY_NUMPAD0);
                return true;
            } else if (keyCode == InputConstants.KEY_NUMPAD0) {
                onDialButtonPressed(0);
                return true;
            }
        }

        // Handle special keys
        switch (keyCode) {
            case InputConstants.KEY_BACKSPACE:
            case InputConstants.KEY_DELETE:
                onClearButtonPressed(-1);
                return true;

            case InputConstants.KEY_RETURN:
            case InputConstants.KEY_NUMPADENTER:
                onCallButtonPressed(10);
                return true;

            case InputConstants.KEY_ESCAPE:
                this.onClose();
                return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        // Draw the GUI background texture centered
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. First: Render the dark background
        this.renderBackground(guiGraphics);

        // 2. Second: Render the GUI background (your dial texture)
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 3. Third: Render custom labels and info
        renderSnailInfo(guiGraphics);

        // 4. Fourth: Render tooltips on top of everything
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Renders the snail's own number and status information
     */
    private void renderSnailInfo(GuiGraphics guiGraphics) {
        int ownNumber = this.menu.getOwnSnailNumber();

        if (ownNumber != -1) {
            // Show this snail's number at the top
            String numberText = "Your Number: #" + ownNumber;
            int textWidth = this.font.width(numberText);
            int centerX = this.leftPos + (this.imageWidth - textWidth) / 2;
            int topY = this.topPos + 10;

            // Draw background for better readability
            guiGraphics.fill(centerX - 2, topY - 1, centerX + textWidth + 2, topY + 9, 0x88000000);

            // Draw the text
            guiGraphics.drawString(this.font, numberText, centerX, topY, ChatFormatting.GREEN.getColor());
        } else {
            // Show loading message
            String loadingText = "Initializing...";
            int textWidth = this.font.width(loadingText);
            int centerX = this.leftPos + (this.imageWidth - textWidth) / 2;
            int topY = this.topPos + 10;

            guiGraphics.fill(centerX - 2, topY - 1, centerX + textWidth + 2, topY + 9, 0x88000000);
            guiGraphics.drawString(this.font, loadingText, centerX, topY, ChatFormatting.YELLOW.getColor());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Draw title
        //guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);

        // The dialed number is now displayed in the text box instead of here
    }

    @Override
    public void onClose() {
        // Custom cleanup when GUI closes
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // Return false so the game doesn't pause when this GUI is open
        return false;
    }

    // Helper method for dial buttons
    private void onDialButtonPressed(int digit) {
        this.menu.dialDigit(digit);

        // Update the text display
        updateNumberDisplay();

        // Optional: Play a sound
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
             ModSounds.DIAL_BUTTON.get(), 1.0F));
    }

    // Helper method for dial buttons
    private void onCallButtonPressed(int digit) {
        if (!menu.isDialedNumberValid()) {
            // This shouldn't happen if button is properly disabled, but just in case
            return;
        } else {
            this.menu.initiateCall(); // You'll need to add this method to your DialingMenu
            menu.onCallInitiated();

            // Update the text display
            updateNumberDisplay();

            // Optional: Play a sound
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.DIAL_BUTTON.get(), 1.0F));

            super.onClose();
        }

    }

    // Helper method for clear button
    private void onClearButtonPressed(int digit) {
        this.menu.clearNumber(); // You'll need to add this method to your DialingMenu
        menu.onNumberCleared();

        // Update the text display
        updateNumberDisplay();

        // Optional: Play a sound
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
             ModSounds.CLEAR_BUTTON.get(), 1.0F));
    }

    // Helper method to update the text box with current dialed number
    private void updateNumberDisplay() {
        String dialedNumber = this.menu.getDialedNumber(); // You'll need this method in DialingMenu
        if (dialedNumber == null) {
            dialedNumber = "";
        }

        this.numberDisplay.setValue(dialedNumber);
    }

    // Call this method to refresh the display when the screen opens
    @Override
    public void containerTick() {
        super.containerTick();
        updateNumberDisplay(); // Keep the display synchronized
    }

    // Deprecated method - keeping for compatibility
    private void dialDigit(int digit) {
        onDialButtonPressed(digit);
    }
}