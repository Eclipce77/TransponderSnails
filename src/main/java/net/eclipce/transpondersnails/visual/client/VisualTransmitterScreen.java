package net.eclipce.transpondersnails.visual.client;

import net.eclipce.transpondersnails.visual.VisualStreamNames;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.visual.network.VisualStreamActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * The status menu of a Visual Transmitter (crouch + right click, at any time - also while it is streaming): whether the stream is Active or
 * Idle, how many are watching, and a box to name the stream (empty = "Steve's Stream", after its owner). Plain on purpose: it is a status
 * page, not the snail's own GUI. The server answers every ten ticks while it is open, so the viewers count is live.
 */
@OnlyIn(Dist.CLIENT)
public class VisualTransmitterScreen extends Screen {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 126;
    private static final int POLL_TICKS = 10;

    private final BlockPos pos;
    private EditBox nameBox;

    private boolean active = false;
    private int viewers = 0;
    private boolean canRename = true;
    private String defaultName = "Visual Stream";
    private long seenVersion = -1L;
    private boolean valueSet = false;
    private String lastSent = null;
    private int ticks = 0;

    public VisualTransmitterScreen(BlockPos pos) {
        super(Component.literal("Visual Transmitter"));
        this.pos = pos.immutable();
    }

    public BlockPos pos() {
        return pos;
    }

    private int left() { return (this.width - WIDTH) / 2; }
    private int top() { return (this.height - HEIGHT) / 2; }

    @Override
    protected void init() {
        int l = left();
        int t = top();
        nameBox = new EditBox(this.font, l + 13, t + 70, WIDTH - 26, 16, Component.literal("Stream name"));
        nameBox.setMaxLength(VisualStreamNames.MAX_LENGTH);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(l + WIDTH / 2 - 40, t + HEIGHT - 24, 80, 20).build());
        VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.status(pos));
        applyStatus();
    }

    /** Takes what the server last said. The name box is only overwritten while the player is not typing in it. */
    private void applyStatus() {
        VisualStreamClient.Status st = VisualStreamClient.status(pos);
        if (st == null || st.version() == seenVersion) return;
        seenVersion = st.version();
        active = st.active();
        viewers = st.viewers();
        canRename = st.canRename();
        defaultName = st.defaultName().isEmpty() ? "Visual Stream" : st.defaultName();
        nameBox.setEditable(canRename);
        nameBox.setHint(Component.literal(defaultName));
        if (!valueSet || !nameBox.isFocused()) {
            nameBox.setValue(st.customName());
            valueSet = true;
            lastSent = st.customName();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % POLL_TICKS == 0) {
            VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.status(pos));
        }
        applyStatus();
    }

    /** Sends the name if it changed (and the player may rename). */
    private void commit() {
        if (!canRename || nameBox == null) return;
        String name = VisualStreamNames.sanitize(nameBox.getValue());
        if (lastSent == null || !name.equals(lastSent)) {
            lastSent = name;
            VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.rename(pos, name));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && nameBox != null && nameBox.isFocused()) {
            commit();
            nameBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        commit();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int l = left();
        int t = top();
        g.fill(l - 2, t - 2, l + WIDTH + 2, t + HEIGHT + 2, 0xFF000000);
        g.fill(l - 1, t - 1, l + WIDTH + 1, t + HEIGHT + 1, 0xFF5A5A5A);
        g.fill(l, t, l + WIDTH, t + HEIGHT, 0xFF2B2B2B);
        g.fill(l, t, l + WIDTH, t + 18, 0xFF383838);

        g.drawCenteredString(this.font, this.title, l + WIDTH / 2, t + 5, 0xFFFFFF);

        // the status: a dot and the word, then how many are watching
        int dot = active ? 0xFF55FF55 : 0xFF808080;
        g.fill(l + 13, t + 28, l + 21, t + 36, 0xFF000000);
        g.fill(l + 14, t + 29, l + 20, t + 35, dot);
        g.drawString(this.font, Component.literal(active ? "Active" : "Idle"), l + 27, t + 28, active ? 0x55FF55 : 0xA0A0A0);
        g.drawString(this.font, Component.literal(active ? "Watching: " + viewers : "Right click the snail to stream"), l + 13, t + 42,
                active ? 0xE0E0E0 : 0x909090, false);

        g.drawString(this.font, Component.literal("Stream name"), l + 13, t + 58, 0xC0C0C0, false);
        if (!canRename) {
            g.drawString(this.font, Component.literal("Only the owner can rename"), l + 13, t + 89, 0xCC6666, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }
}
