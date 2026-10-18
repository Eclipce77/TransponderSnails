package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.visual.SnailGuiLayout;
import net.eclipce.transpondersnails.visual.SnailGuiLayout.Rect;
import net.eclipce.transpondersnails.visual.StreamListMath;
import net.eclipce.transpondersnails.visual.StreamPresets;
import net.eclipce.transpondersnails.visual.TextScroll;
import net.eclipce.transpondersnails.visual.VisualStreamNames;
import net.eclipce.transpondersnails.visual.network.VisualNetwork;
import net.eclipce.transpondersnails.visual.network.VisualStreamActionPacket;
import net.eclipce.transpondersnails.visual.network.VisualStreamListPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The stream tuner beside the dialing GUI of a Visual Transponder Snail, on the same texture and layout as the projector settings:
 * <ul>
 *   <li><b>the big display</b>: the streams that are on air in this dimension - wherever they are - as a scrolling list. The snail icon
 *       says whether you are connected to it (online) or not (offline). Click once to select a stream; double-click it (or press Tune) to
 *       tune in - the stream is then deselected; double-click the stream you are tuned to to disconnect.</li>
 *   <li><b>the three boxes and round buttons</b>: saved streams (presets). While a stream is selected from the list the boxes flash: a
 *       click saves it there (replacing what was), and the stream is deselected. Otherwise a box works like a row of the list: click once to
 *       select its stream, double-click to tune in (or to disconnect, if you are tuned to it), and the stream is deselected after tuning.
 *       A right click on a box empties it. The round button beside a box has no selection: ONE click tunes in to its stream (or
 *       disconnects, if you are tuned to it); it never changes the box. Its snail icon says online (connected) or offline. A saved stream that
 *       is not on air can still be chosen; tuning says that it is not active.</li>
 *   <li><b>Tune</b>, below the panel where the settings show the depth.</li>
 * </ul>
 * A name that is too wide for its box scrolls. How many are watching a stream is NOT shown here, only at the transmitter itself.
 * The panel only shows what the server last said (VisualStreamClient) and sends what the player does (VisualStreamActionPacket).
 */
@OnlyIn(Dist.CLIENT)
public class VisualDialerPanel {

    private static final ResourceLocation TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID, "textures/gui/visual_snail_gui.png");
    /** The snail icons (16 x 16 files; the snail itself fills x 1..14, y 2..13: that part is drawn, at its own size, so no pixel is lost). */
    private static final ResourceLocation ICON_ONLINE = new ResourceLocation(TransponderSnails.MOD_ID, "textures/icons/snail_call_online.png");
    private static final ResourceLocation ICON_OFFLINE = new ResourceLocation(TransponderSnails.MOD_ID, "textures/icons/snail_call_offline.png");
    private static final int ICON_FILE = 16;
    private static final int ICON_U = 1;
    private static final int ICON_V = 2;
    private static final int ICON_W = 14;
    private static final int ICON_H = 12;

    private static final int ROW_HEIGHT = 14;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int POLL_TICKS = 20;

    /** The snail on a round button: the 14 x 12 snail scaled to fit inside the circle. */
    private static final int BUTTON_ICON_W = 10;
    private static final int BUTTON_ICON_H = 9;
    private static boolean iconFilterSet = false;

    private final BlockPos pos;
    private VisualSettingsPanel.Host host;
    private int originX;
    private int originY;

    // what the server last said
    private List<VisualStreamListPacket.Stream> streams = List.of();
    private final VisualStreamListPacket.Preset[] presets = new VisualStreamListPacket.Preset[StreamPresets.SLOTS];
    @Nullable
    private UUID tuned;
    private String tunedName = "";
    private long seenVersion = -1L;

    // what the player has done
    @Nullable
    private UUID selectedId;
    private String selectedName = "";
    private boolean selectedFromPreset = false;   // chosen from a box (it may be off air), not from the list
    private int scroll = 0;
    private boolean draggingBar = false;
    private final StreamListMath.ClickTracker clicks = new StreamListMath.ClickTracker();
    private final StreamListMath.ClickTracker boxClicks = new StreamListMath.ClickTracker();   // double clicks on a box (its slot is the "row")
    private int ticks = 0;

    public VisualDialerPanel(BlockPos pos) {
        this.pos = pos.immutable();
        Arrays.fill(presets, VisualStreamListPacket.Preset.empty());
    }

    private Rect onScreen(Rect r) {
        return new Rect(originX + r.x(), originY + r.y(), r.w(), r.h());
    }

    private int visibleRows() {
        return SnailGuiLayout.DISPLAY.h() / ROW_HEIGHT;
    }

    /** @param originX,originY screen position of the 256 x 256 texture */
    public void init(VisualSettingsPanel.Host host, int originX, int originY) {
        this.host = host;
        this.originX = originX;
        this.originY = originY;

        host.add(new StreamList(onScreen(SnailGuiLayout.DISPLAY)));
        Rect[] bars = {SnailGuiLayout.SIZE_BAR, SnailGuiLayout.SIDE_BAR, SnailGuiLayout.UP_BAR};
        Rect[] buttons = {SnailGuiLayout.SIZE_RESET, SnailGuiLayout.SIDE_RESET, SnailGuiLayout.UP_RESET};
        for (int slot = 0; slot < StreamPresets.SLOTS; slot++) {
            host.add(new PresetBar(slot, onScreen(bars[slot])));
            host.add(new PresetButton(slot, onScreen(buttons[slot])));
        }
        host.add(new TuneButton(onScreen(SnailGuiLayout.DEPTH_BAR)));
        request();
        applyData();
    }

    private void request() {
        VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.list(pos));
    }

    /** Call every tick: asks the server again now and then, and takes what it said. */
    public void tick() {
        if (++ticks % POLL_TICKS == 0) request();
        applyData();
    }

    private void applyData() {
        VisualStreamClient.ListData data = VisualStreamClient.list(pos);
        if (data == null || data.version() == seenVersion) return;
        seenVersion = data.version();

        List<VisualStreamListPacket.Stream> sorted = new ArrayList<>(data.streams());
        sorted.sort((a, b) -> {
            int c = VisualStreamNames.compare(a.name(), b.name());
            return c != 0 ? c : a.id().compareTo(b.id());
        });
        streams = sorted;
        for (int i = 0; i < StreamPresets.SLOTS; i++) {
            presets[i] = i < data.presets().size() ? data.presets().get(i) : VisualStreamListPacket.Preset.empty();
        }
        tuned = data.tuned();
        tunedName = nameOf(tuned);

        // a stream that was selected from the list and has gone off the air is no longer selected
        if (selectedId != null && !selectedFromPreset && !isOnAir(selectedId)) selectedId = null;
        scroll = StreamListMath.clampScroll(scroll, streams.size(), visibleRows());
    }

    private boolean isOnAir(UUID id) {
        for (VisualStreamListPacket.Stream s : streams) if (s.id().equals(id)) return true;
        return false;
    }

    private String nameOf(@Nullable UUID id) {
        if (id == null) return "";
        for (VisualStreamListPacket.Stream s : streams) if (s.id().equals(id)) return s.name();
        for (VisualStreamListPacket.Preset p : presets) if (p.present() && p.id().equals(id)) return p.name();
        return "a stream";
    }

    private boolean isTuned(@Nullable UUID id) {
        return id != null && id.equals(tuned);
    }

    // ---------------- what the player does ----------------

    private static void message(String text, ChatFormatting color) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(text).withStyle(color), true);
        }
    }

    private void deselect() {
        selectedId = null;
        selectedName = "";
        selectedFromPreset = false;
    }

    /** Tunes in. The stream is deselected afterwards (it is the connected one now: click it to select it again, double-click to disconnect). */
    private void tuneTo(UUID id, String name) {
        if (!isOnAir(id)) {
            message(name + " is not active.", ChatFormatting.RED);
            return;
        }
        if (isTuned(id)) {
            message("Already tuned to " + name + ". Double-click it to disconnect.", ChatFormatting.YELLOW);
            return;
        }
        VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.tune(pos, id));
        deselect();
    }

    private void tuneSelected() {
        if (selectedId == null) {
            message("Select a stream first.", ChatFormatting.YELLOW);
            return;
        }
        tuneTo(selectedId, selectedName);
    }

    /** Leaves the stream the projector is tuned to. */
    private void disconnect() {
        VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.disconnect(pos));
        deselect();
    }

    /**
     * A click on a box. With a stream selected from the list (the boxes flash) it is saved there and deselected. Otherwise the box works
     * like a row of the list: one click selects its stream, a double click tunes in - or disconnects, if it is the stream you are tuned to.
     */
    private void onPresetBar(int slot) {
        long now = System.currentTimeMillis();
        VisualStreamListPacket.Preset p = presets[slot];
        if (selectedId != null && !selectedFromPreset) {
            VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.preset(pos, slot, selectedId));
            presets[slot] = new VisualStreamListPacket.Preset(true, selectedId, selectedName, true); // at once; the server confirms
            deselect();
            boxClicks.click(now, -1);   // the click that saved is not the first half of a double click
            return;
        }
        if (!p.present()) {
            boxClicks.click(now, -1);
            message("Select a stream, then click a box to save it there.", ChatFormatting.YELLOW);
            return;
        }
        selectedId = p.id();
        selectedName = p.name();
        selectedFromPreset = true;
        if (boxClicks.click(now, slot)) {                                  // a double click
            if (isTuned(p.id())) disconnect(); else tuneSelected();
        }
    }

    /** A right click on a box: empties it. */
    private void clearPreset(int slot) {
        if (!presets[slot].present()) return;
        VisualNetwork.CHANNEL.sendToServer(VisualStreamActionPacket.clearPreset(pos, slot));
        if (selectedFromPreset && presets[slot].id().equals(selectedId)) deselect();
        presets[slot] = VisualStreamListPacket.Preset.empty(); // at once; the server confirms
    }

    /** The round button beside a box: ONE click tunes in to the stream saved in it, or disconnects if you are tuned to it. Never changes the box. */
    private void onPresetTune(int slot) {
        VisualStreamListPacket.Preset p = presets[slot];
        if (!p.present()) {
            message("Nothing is saved in this box yet.", ChatFormatting.YELLOW);
        } else if (isTuned(p.id())) {
            disconnect();
        } else {
            tuneTo(p.id(), p.name());
        }
    }

    // ---------------- drawing ----------------

    /** The texture and the title - draw BEFORE the host's widgets. */
    public void renderBackground(GuiGraphics g) {
        RenderSystem.enableBlend();
        g.blit(TEXTURE, originX, originY, 0.0F, 0.0F, SnailGuiLayout.TEXTURE_SIZE, SnailGuiLayout.TEXTURE_SIZE,
                SnailGuiLayout.TEXTURE_SIZE, SnailGuiLayout.TEXTURE_SIZE);

        Font font = host.font();
        Rect zone = onScreen(SnailGuiLayout.TITLE_ZONE);
        drawOutlined(g, font, "Stream tuner", zone.x(), zone.w(), zone.y() + 3, 0xFFFFFF);
        String line = tuned != null ? "Tuned to " + tunedName
                : streams.isEmpty() ? "No streams on air"
                : streams.size() + (streams.size() == 1 ? " stream" : " streams") + " on air";
        drawOutlined(g, font, line, zone.x(), zone.w(), zone.y() + 15, tuned != null ? 0x9BEF9B : 0xB8B8B8);
    }

    /**
     * Text in a box: centred if it fits, otherwise it scrolls (see TextScroll) inside the box, clipped to it.
     *
     * @param centered false = left aligned when it fits
     */
    private static void drawText(GuiGraphics g, Font font, String text, int x, int boxWidth, int y, int color, boolean shadow, boolean centered) {
        int width = font.width(text);
        if (width <= boxWidth) {
            g.drawString(font, text, centered ? x + (boxWidth - width) / 2 : x, y, color, shadow);
            return;
        }
        int offset = TextScroll.offset(width, boxWidth, System.currentTimeMillis());
        g.enableScissor(x, y - 2, x + boxWidth, y + 10);
        g.drawString(font, text, x - offset, y, color, shadow);
        g.disableScissor();
    }

    /** Centred text with a thin dark outline (readable over anything); scrolls if it is wider than its box. */
    private static void drawOutlined(GuiGraphics g, Font font, String text, int x, int boxWidth, int y, int color) {
        int width = font.width(text);
        boolean fits = width <= boxWidth;
        int left = fits ? x + (boxWidth - width) / 2 : x - TextScroll.offset(width, boxWidth, System.currentTimeMillis());
        if (!fits) g.enableScissor(x, y - 2, x + boxWidth, y + 10);
        g.drawString(font, text, left - 1, y, 0xFF000000, false);
        g.drawString(font, text, left + 1, y, 0xFF000000, false);
        g.drawString(font, text, left, y - 1, 0xFF000000, false);
        g.drawString(font, text, left, y + 1, 0xFF000000, false);
        g.drawString(font, text, left, y, color, false);
        if (!fits) g.disableScissor();
    }

    /** A snail icon: online (connected) or offline, at its own size. {@code alpha} dims it. */
    private static void drawSnail(GuiGraphics g, boolean online, int x, int y, float alpha) {
        RenderSystem.enableBlend();
        if (alpha < 1.0F) g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(online ? ICON_ONLINE : ICON_OFFLINE, x, y, ICON_W, ICON_H, (float) ICON_U, (float) ICON_V, ICON_W, ICON_H, ICON_FILE, ICON_FILE);
        if (alpha < 1.0F) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * A snail icon scaled down to fit a round button. Scaled with smooth filtering (a 14 x 12 pixel picture at 10 x 9 would lose pixels with the
     * nearest-pixel kind); at its own size - the list - the two look the same.
     */
    private static void drawSnailScaled(GuiGraphics g, boolean online, int x, int y, float alpha) {
        if (!iconFilterSet) {
            Minecraft.getInstance().getTextureManager().getTexture(ICON_ONLINE).setFilter(true, false);
            Minecraft.getInstance().getTextureManager().getTexture(ICON_OFFLINE).setFilter(true, false);
            iconFilterSet = true;
        }
        RenderSystem.enableBlend();
        if (alpha < 1.0F) g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(online ? ICON_ONLINE : ICON_OFFLINE, x, y, BUTTON_ICON_W, BUTTON_ICON_H, (float) ICON_U, (float) ICON_V, ICON_W, ICON_H, ICON_FILE, ICON_FILE);
        if (alpha < 1.0F) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void fillDisc(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.floor(Math.sqrt((double) radius * radius - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    // ---------------- the widgets ----------------

    /** The big display: the streams that are on air. */
    private final class StreamList extends AbstractWidget {

        StreamList(Rect r) {
            super(r.x(), r.y(), r.w(), r.h(), Component.literal("Streams on air"));
        }

        private int rows() {
            return getHeight() / ROW_HEIGHT;
        }

        private int barX() {
            return getX() + getWidth() - SCROLLBAR_WIDTH - 2;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = host.font();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int n = streams.size(), vis = rows();
            boolean bar = n > vis;
            int textRight = x + w - (bar ? SCROLLBAR_WIDTH + 4 : 3);

            g.enableScissor(x, y, x + w, y + h);
            if (n == 0) {
                drawOutlined(g, font, "No streams on air", x + 4, w - 8, y + h / 2 - 9, 0xA0A0A0);
                drawOutlined(g, font, "Right-click a Visual Transmitter", x + 4, w - 8, y + h / 2 + 3, 0x707070);
            } else {
                for (int slot = 0; slot < vis; slot++) {
                    int row = scroll + slot;
                    if (row >= n) break;
                    VisualStreamListPacket.Stream s = streams.get(row);
                    int ry = y + slot * ROW_HEIGHT;
                    boolean selected = s.id().equals(selectedId);
                    boolean hot = mouseX >= x && mouseX < textRight && mouseY >= ry && mouseY < ry + ROW_HEIGHT && isMouseOver(mouseX, mouseY);
                    boolean connected = isTuned(s.id());

                    g.fill(x + 1, ry + 1, textRight + 1, ry + ROW_HEIGHT - 1, selected ? 0x7032A0DC : hot ? 0x38FFFFFF : 0x22FFFFFF);
                    if (selected) g.fill(x + 1, ry + 1, x + 3, ry + ROW_HEIGHT - 1, 0xFF5AB4DC);

                    drawSnail(g, connected, x + 4, ry + 1, 1.0F);   // online = connected to this stream, offline = not

                    int nameX = x + 22;
                    drawText(g, font, s.name(), nameX, textRight - 2 - nameX, ry + 3,
                            selected ? 0xFFFFFF : connected ? 0xA8F0A8 : 0xE0E0E0, true, false);
                }
                if (bar) {
                    int[] thumb = StreamListMath.thumb(y + 1, h - 2, n, vis, scroll);
                    g.fill(barX(), y + 1, barX() + SCROLLBAR_WIDTH, y + h - 1, 0x60000000);
                    g.fill(barX(), thumb[0], barX() + SCROLLBAR_WIDTH, thumb[0] + thumb[1], draggingBar ? 0xFFE0E0E0 : 0xFFA0A0A0);
                }
            }
            g.disableScissor();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
            int n = streams.size(), vis = rows();
            if (n > vis && mouseX >= barX()) {                                            // the scrollbar
                draggingBar = true;
                scroll = StreamListMath.scrollForY((int) mouseY, getY() + 1, getHeight() - 2, n, vis);
                return true;
            }
            int row = StreamListMath.rowAt((int) mouseY, getY(), ROW_HEIGHT, scroll, n, vis);
            if (row < 0) {                                                                // empty space: nothing selected
                deselect();
                clicks.click(System.currentTimeMillis(), -1);
                return true;
            }
            VisualStreamListPacket.Stream s = streams.get(row);
            selectedId = s.id();
            selectedName = s.name();
            selectedFromPreset = false;
            playDownSound(Minecraft.getInstance().getSoundManager());
            if (clicks.click(System.currentTimeMillis(), row)) {                          // a double click: tune in - or leave, if it is the one you are tuned to
                if (isTuned(s.id())) disconnect(); else tuneSelected();
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!draggingBar) return false;
            scroll = StreamListMath.scrollForY((int) mouseY, getY() + 1, getHeight() - 2, streams.size(), rows());
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean was = draggingBar;
            draggingBar = false;
            return was;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!isMouseOver(mouseX, mouseY) || delta == 0.0) return false;
            scroll = StreamListMath.clampScroll(scroll + (delta > 0.0 ? -1 : 1), streams.size(), rows());
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /** One of the three boxes: a saved stream. It flashes while a stream is selected (a click saves it here); a right click empties it. */
    private final class PresetBar extends AbstractWidget {
        private final int slot;

        PresetBar(int slot, Rect r) {
            super(r.x(), r.y(), r.w(), r.h(), Component.literal("Saved stream " + (slot + 1)));
            this.slot = slot;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPresetBar(slot);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && this.active && this.visible && isMouseOver(mouseX, mouseY)) {   // right click: empty this box
                playDownSound(Minecraft.getInstance().getSoundManager());
                clearPreset(slot);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = host.font();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            VisualStreamListPacket.Preset p = presets[slot];

            if (selectedId != null && !selectedFromPreset) {      // "this box will be overwritten if you click it"
                double f = StreamListMath.flash(System.currentTimeMillis());
                int fill = (int) (0x30 + 0x50 * f) << 24 | 0x5AB4DC;
                int edge = (int) (0x60 + 0x9F * f) << 24 | 0x8FE0FF;
                g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
                g.fill(x + 1, y + 1, x + w - 1, y + 2, edge);
                g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, edge);
                g.fill(x + 1, y + 1, x + 2, y + h - 1, edge);
                g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, edge);
            } else if (selectedFromPreset && p.present() && p.id().equals(selectedId)) {   // this box's stream is the selected one
                g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x7032A0DC);
                g.fill(x + 2, y + 2, x + 4, y + h - 2, 0xFF5AB4DC);
            } else if (isHoveredOrFocused()) {
                g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x22FFFFFF);
            }

            if (!p.present()) {
                drawText(g, font, "Empty", x + 4, w - 8, y + (h - 8) / 2, 0xFF707070, true, true);
                return;
            }
            int color = !p.active() ? 0xFF8A8A8A : isTuned(p.id()) ? 0xFFA8F0A8 : 0xFFFFFFFF;
            drawText(g, font, p.active() ? p.name() : p.name() + " (off air)", x + 6, w - 12, y + (h - 8) / 2, color, true, true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /** The round button beside a box: tunes in to the stream saved in it. */
    private final class PresetButton extends AbstractWidget {
        private final int slot;

        PresetButton(int slot, Rect r) {
            super(r.x(), r.y(), r.w(), r.h(), Component.literal("Tune to saved stream " + (slot + 1)));
            this.slot = slot;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPresetTune(slot);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + getWidth() / 2;
            int cy = getY() + getHeight() / 2;
            VisualStreamListPacket.Preset p = presets[slot];
            // the snail of what is saved here: online when you are tuned to it, offline otherwise; dimmed when it is not on air
            if (p.present()) drawSnailScaled(g, isTuned(p.id()), cx - BUTTON_ICON_W / 2, cy - BUTTON_ICON_H / 2, p.active() ? 1.0F : 0.55F);
            if (isHovered()) {
                boolean pressed = Minecraft.getInstance().mouseHandler.isLeftPressed();
                fillDisc(g, cx, cy, 5, pressed ? 0x70000000 : 0x55FFFFFF);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /** The Tune button, below the panel (where the settings show the depth). */
    private final class TuneButton extends AbstractWidget {

        TuneButton(Rect r) {
            super(r.x(), r.y(), r.w(), r.h(), Component.literal("Tune"));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            tuneSelected();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = host.font();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean enabled = selectedId != null;
            // a track in the colours of the bars of the texture (there is none here), lit when it can be pressed
            g.fill(x, y, x + w, y + h, 0xFF1D1D1C);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, enabled ? 0xFF35505E : 0xFF30302F);
            g.fill(x + 2, y + 2, x + w - 2, y + h - 2, enabled ? 0xFF44687A : 0xFF3C3B3A);
            if (enabled && isHovered()) {
                boolean pressed = Minecraft.getInstance().mouseHandler.isLeftPressed();
                g.fill(x + 2, y + 2, x + w - 2, y + h - 2, pressed ? 0x50000000 : 0x30FFFFFF);
            }
            drawText(g, font, enabled ? "Tune: " + selectedName : "Select a stream", x + 6, w - 12, y + (h - 8) / 2 + 1,
                    enabled ? 0xFFFFFFFF : 0xFF808080, enabled, true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
