package net.eclipce.transpondersnails.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class CallButton extends Button {
    private final ResourceLocation buttonTexture;
    private final int buttonNumber;

    // Constructor
    public CallButton(int x, int y, int width, int height, int buttonNumber, Consumer<Integer> onPress) {
        super(x, y, width, height, Component.literal(String.valueOf(buttonNumber)),
                button -> onPress.accept(buttonNumber), DEFAULT_NARRATION);

        this.buttonNumber = buttonNumber;
        // Set the texture path for this button
        this.buttonTexture = new ResourceLocation("transpondersnails",
                "textures/gui/buttons/dial-call-button.png");
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        // Ensure the button is visible
        if (!this.visible) {
            return;
        }

        // Enable blending for proper alpha rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Set up rendering
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
        //RenderSystem.setShaderTexture(0, this.buttonTexture);

        // SIMPLIFIED: Just render the full 32x32 texture
        // Comment out all the state-based texture selection for now
        // int textureY = 0; // Normal state
        // if (!this.active) {
        //     textureY = 64; // Disabled state (if your texture has multiple states)
        // } else if (this.isHoveredOrFocused()) {
        //     textureY = 32; // Hovered state (if your texture has multiple states)
        // }

        // Draw the button texture - render the full 32x32 texture
        // Using textureY = 0 to always use the top of the texture

        // Calculate button state for different textures (if your texture has multiple states)
        int textureY = 0; // Default state
        int textureHeight = this.height;

        if (!this.active) {
            textureY = this.height; // Hovered state (second section of texture)
        }

        // Draw the button texture
        guiGraphics.blit(this.buttonTexture,
                this.getX(), this.getY(),           // Screen position
                0, textureY,                        // Texture UV start
                this.width, this.height,            // Screen size
                this.width, this.height);       // Texture size (assuming 3 states stacked vertically)

        // Optional: Add text overlay if you want numbers on top of the texture
        // int textColor = this.active ? 0xFFFFFF : 0xA0A0A0;
        // guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
        //     this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);

        // Disable blending
        RenderSystem.disableBlend();
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        // Override this method to prevent default button click sound
        // Leave empty to disable the default sound
    }

    public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.isHoveredOrFocused()) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font,
                    Component.literal("Call"), mouseX, mouseY);
        }
    }

    // Getters
    public int getButtonNumber() {
        return buttonNumber;
    }
}