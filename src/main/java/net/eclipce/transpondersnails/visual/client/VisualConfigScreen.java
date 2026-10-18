package net.eclipce.transpondersnails.visual.client;

import net.eclipce.transpondersnails.visual.SnailGuiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The projector settings on a screen of their own: a thin host for {@link VisualSettingsPanel}, which has everything. It is opened by
 * crouch + right click on an IDLE Visual Transponder Snail (VisualProjectorConfig.open -> VisualConfigOpenPacket) and in no other way.
 */
@OnlyIn(Dist.CLIENT)
public class VisualConfigScreen extends Screen {

    private final VisualSettingsPanel panel;

    private final VisualSettingsPanel.Host host = new VisualSettingsPanel.Host() {
        @Override public int width() { return VisualConfigScreen.this.width; }
        @Override public int height() { return VisualConfigScreen.this.height; }
        @Override public Font font() { return VisualConfigScreen.this.font; }
        @Override public boolean dragging() { return VisualConfigScreen.this.isDragging(); }
        @Override public void add(AbstractWidget widget) { VisualConfigScreen.this.addRenderableWidget(widget); }
    };

    public VisualConfigScreen(BlockPos pos, int size, float side, float up, float back) {
        super(Component.literal("Projector settings"));
        this.panel = new VisualSettingsPanel(pos, size, side, up, back);
    }

    @Override
    protected void init() {
        panel.init(host, SnailGuiLayout.originX(this.width), SnailGuiLayout.originY(this.height));
    }

    @Override
    public void tick() {
        panel.tick();
    }

    @Override
    public void removed() {
        panel.removed();
        super.removed();
    }

    /** Keep the game running behind the menu (also in single player: the call timers are server ticks). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        panel.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        panel.renderOverlay(g, mouseX, mouseY);
    }
}
