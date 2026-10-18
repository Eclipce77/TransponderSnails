package net.eclipce.transpondersnails.visual.client;

import net.eclipce.transpondersnails.screen.DialingMenu;
import net.eclipce.transpondersnails.screen.DialingScreen;
import net.eclipce.transpondersnails.visual.SnailGuiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The dialing GUI of a Visual Transponder Snail: the same dialer as a phone's (dimmed background and all), with a
 * {@link VisualDialerPanel} (the stream tuner) beside it. It is its own class so that things that must treat only the visual snail's GUI differently - JEI
 * is not shown on it, see TransponderSnailsJeiPlugin - can tell it from the phone's.
 */
@OnlyIn(Dist.CLIENT)
public class VisualDialingScreen extends DialingScreen {

    private static final int GAP = 4;

    private final VisualDialerPanel panel;

    private final VisualSettingsPanel.Host host = new VisualSettingsPanel.Host() {
        @Override public int width() { return VisualDialingScreen.this.width; }
        @Override public int height() { return VisualDialingScreen.this.height; }
        @Override public Font font() { return VisualDialingScreen.this.font; }
        @Override public boolean dragging() { return VisualDialingScreen.this.isDragging(); }
        @Override public void add(AbstractWidget widget) { VisualDialingScreen.this.addRenderableWidget(widget); }
    };

    public VisualDialingScreen(DialingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.panel = new VisualDialerPanel(menu.getVisualPos()); // the projector this dialer belongs to
    }

    /** Whether the window is wide enough for the dialer and the panel side by side. */
    private boolean fitsBeside() {
        return this.width >= this.imageWidth + GAP + SnailGuiLayout.PANEL.w() + 8;
    }

    /** Side by side when there is room: the pair is centered. In a narrow window the dialer stays centered and the panel overlaps its margin. */
    @Override
    protected int dialerLeftPos() {
        if (!fitsBeside()) return super.dialerLeftPos();
        int total = this.imageWidth + GAP + SnailGuiLayout.PANEL.w();
        return (this.width - total) / 2;
    }

    @Override
    protected void initSidePanel() {
        int originX = fitsBeside()
                ? this.leftPos + this.imageWidth + GAP - SnailGuiLayout.PANEL.x()
                : SnailGuiLayout.originX(this.width);
        panel.init(host, originX, SnailGuiLayout.originY(this.height));
    }

    @Override
    protected void renderSidePanelBackground(GuiGraphics guiGraphics) {
        panel.renderBackground(guiGraphics);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        panel.tick(); // asks the server for the streams now and then
    }
}
