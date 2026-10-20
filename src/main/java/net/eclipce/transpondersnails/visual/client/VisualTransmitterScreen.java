package net.eclipce.transpondersnails.visual.client;

import net.eclipce.transpondersnails.visual.StreamAccess;
import net.eclipce.transpondersnails.visual.StreamListMath;
import net.eclipce.transpondersnails.visual.VisualStreamNames;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.visual.network.VisualStreamActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The status menu of a Visual Transmission Transponder Snail (crouch + right click, at any time - also while it is streaming): whether the
 * stream is Active or Idle, how many are watching, a box to name the stream (empty = "Steve's Stream", after its owner), and who may watch it:
 *
 * <ul>
 *   <li><b>Visibility</b>: PUBLIC (any Visual Transponder Snail in the dimension can tune in) or PRIVATE (invite only).</li>
 *   <li><b>Invited snails</b>: the snail numbers that may see a private stream - type a number and press Invite; the red x removes one. A
 *       snail that is watching when it is removed (or when the stream is made private without it) is disconnected. Only the owner can change
 *       any of this; anyone who opens the menu can read it.</li>
 * </ul>
 * Plain on purpose: it is a status page, not the snail's own GUI. The server answers every ten ticks while it is open, so what is shown is live.
 */
@OnlyIn(Dist.CLIENT)
public class VisualTransmitterScreen extends Screen {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 228;
    private static final int POLL_TICKS = 10;
    private static final int ROW_H = 12;
    private static final int ROWS = 4;
    private static final int LIST_W = 150;
    private static final int BAR_W = 4;
    private static final int PRIVATE_COLOR = 0xFFA030;

    private final BlockPos pos;
    private EditBox nameBox;
    private EditBox inviteBox;
    private Button visibilityButton;
    private Button inviteButton;

    private boolean active = false;
    private int viewers = 0;
    private boolean canRename = true;
    private boolean privateStream = false;
    private List<Integer> invites = new ArrayList<>();
    private String defaultName = "Visual Stream";
    private long seenVersion = -1L;
    private boolean valueSet = false;
    private String lastSent = null;
    private int ticks = 0;
    private int scroll = 0;
    private boolean draggingBar = false;

    public VisualTransmitterScreen(BlockPos pos) {
        super(Component.literal("Visual Transmission"));
        this.pos = pos.immutable();
    }

    public BlockPos pos() {
        return pos;
    }

    private int left() { return (this.width - WIDTH) / 2; }
    private int top() { return (this.height - HEIGHT) / 2; }
    private int listX() { return left() + 13; }
    private int listY() { return top() + 150; }
    private boolean hasBar() { return invites.size() > ROWS; }

    @Override
    protected void init() {
        int l = left();
        int t = top();
        nameBox = new EditBox(this.font, l + 13, t + 70, WIDTH - 26, 16, Component.literal("Stream name"));
        nameBox.setMaxLength(VisualStreamNames.MAX_LENGTH);
        addRenderableWidget(nameBox);

        visibilityButton = Button.builder(Component.literal("Public"), b -> toggleVisibility()).bounds(l + 70, t + 90, WIDTH - 83, 18).build();
        addRenderableWidget(visibilityButton);

        inviteBox = new EditBox(this.font, l + 13, t + 128, 62, 16, Component.literal("Snail number"));
        inviteBox.setMaxLength(5);                                               // four digits, and a # in front is allowed
        inviteBox.setFilter(s -> s.matches("[0-9#]*"));
        inviteBox.setHint(Component.literal("1234").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(inviteBox);
        inviteButton = Button.builder(Component.literal("Invite"), b -> invite()).bounds(l + 80, t + 127, WIDTH - 93, 18).build();
        addRenderableWidget(inviteButton);

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
        privateStream = st.isPrivate();
        invites = new ArrayList<>(st.invites());
        scroll = StreamListMath.clampScroll(scroll, invites.size(), ROWS);
        defaultName = st.defaultName().isEmpty() ? "Visual Stream" : st.defaultName();
        nameBox.setEditable(canRename);
        nameBox.setHint(Component.literal(defaultName));
        inviteBox.setEditable(canRename);
        inviteButton.active = canRename;
        visibilityButton.active = canRename;
        visibilityButton.setMessage(Component.literal(privateStream ? "Private (invite only)" : "Public").withStyle(privateStream ? ChatFormatting.GOLD : ChatFormatting.GREEN));
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

    private static void message(String text, ChatFormatting color) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(text).withStyle(color), true);
        }
    }

    private void toggleVisibility() {
        if (canRename) VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.setPrivate(pos, !privateStream));
    }

    /** Sends the number in the box (the server checks that it is a Visual Transponder Snail's number and says what happened). */
    private void invite() {
        if (!canRename) return;
        int number = StreamAccess.parseNumber(inviteBox.getValue());
        if (number < 0) {
            message("Enter a four-digit snail number.", ChatFormatting.RED);
            return;
        }
        VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.invite(pos, number));
        inviteBox.setValue("");
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
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && nameBox != null && nameBox.isFocused()) {
            commit();
            nameBox.setFocused(false);
            return true;
        }
        if (enter && inviteBox != null && inviteBox.isFocused()) {
            invite();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- the invite list ----

    private boolean overList(double mx, double my) {
        return mx >= listX() && mx < listX() + LIST_W && my >= listY() && my < listY() + ROWS * ROW_H;
    }

    private int removeX() {
        return listX() + LIST_W - 14 - (hasBar() ? BAR_W + 2 : 0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overList(mouseX, mouseY)) {
            int total = invites.size();
            if (hasBar() && mouseX >= listX() + LIST_W - BAR_W - 2) {                       // the scrollbar
                draggingBar = true;
                scroll = StreamListMath.scrollForY((int) mouseY, listY(), ROWS * ROW_H, total, ROWS);
                return true;
            }
            int row = StreamListMath.rowAt((int) mouseY, listY(), ROW_H, scroll, total, ROWS);
            if (row >= 0 && canRename && mouseX >= removeX() && mouseX < removeX() + 12) {   // the red x
                VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.uninvite(pos, invites.get(row)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar) {
            scroll = StreamListMath.scrollForY((int) mouseY, listY(), ROWS * ROW_H, invites.size(), ROWS);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (overList(mouseX, mouseY) && delta != 0.0) {
            scroll = StreamListMath.clampScroll(scroll + (delta > 0.0 ? -1 : 1), invites.size(), ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
        g.drawString(this.font, Component.literal("Visibility"), l + 13, t + 95, 0xC0C0C0, false);
        if (!canRename) {
            g.drawString(this.font, Component.literal("Only the owner can change this"), l + 13, t + 109, 0xCC6666, false);
        }
        g.drawString(this.font, Component.literal("Invited snails (" + invites.size() + "/" + StreamAccess.MAX_INVITES + ")"), l + 13, t + 119, 0xC0C0C0, false);

        // the invite list: the numbers, each with a red x
        int lx = listX();
        int ly = listY();
        g.fill(lx - 1, ly - 1, lx + LIST_W + 1, ly + ROWS * ROW_H + 1, 0xFF000000);
        g.fill(lx, ly, lx + LIST_W, ly + ROWS * ROW_H, 0xFF1E1E1E);
        int total = invites.size();
        if (total == 0) {
            g.drawCenteredString(this.font, Component.literal(privateStream ? "Nobody is invited yet" : "Public: any snail can tune in"), lx + LIST_W / 2, ly + 5,
                    privateStream ? PRIVATE_COLOR : 0x808080);
            g.drawCenteredString(this.font, Component.literal(privateStream ? "Type a number and press Invite" : "Invites apply when it is private"), lx + LIST_W / 2, ly + 17, 0x707070);
        } else {
            for (int slot = 0; slot < ROWS; slot++) {
                int row = scroll + slot;
                if (row >= total) break;
                int ry = ly + slot * ROW_H;
                boolean hot = mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;
                g.fill(lx + 1, ry + 1, lx + LIST_W - 1 - (hasBar() ? BAR_W + 1 : 0), ry + ROW_H - 1, hot ? 0x40FFFFFF : 0x22FFFFFF);
                g.drawString(this.font, Component.literal("#" + invites.get(row)), lx + 5, ry + 2, privateStream ? PRIVATE_COLOR : 0xB0B0B0, false);
                if (canRename) {
                    boolean overX = mouseX >= removeX() && mouseX < removeX() + 12 && mouseY >= ry && mouseY < ry + ROW_H;
                    g.drawString(this.font, Component.literal("x"), removeX() + 3, ry + 2, overX ? 0xFFFF5555 : 0xFFAA3333, false);
                }
            }
            if (hasBar()) {
                int[] thumb = StreamListMath.thumb(ly, ROWS * ROW_H, total, ROWS, scroll);
                int bx = lx + LIST_W - BAR_W - 1;
                g.fill(bx, ly + 1, bx + BAR_W, ly + ROWS * ROW_H - 1, 0x60000000);
                g.fill(bx, thumb[0], bx + BAR_W, thumb[0] + thumb[1], draggingBar ? 0xFFE0E0E0 : 0xFFA0A0A0);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }
}
