package net.eclipce.transpondersnails.visual;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Video for a FIXED camera, over a network, at the resolution of a local feed.
 *
 * <p>A camera does not move, so most of every frame is the same as the one before. The picture is cut into 32 x 32 tiles; the encoder keeps
 * what it last SENT for every tile and sends only the tiles that have changed since (a moving player, water, a torch flame - not the walls).
 * The changed tiles are packed side by side into ONE small picture and compressed as ONE JPEG (a JPEG per tile would be mostly header), with
 * the list of which tiles they are in front of it. A still scene costs almost nothing; a busy one costs what changes. The decoder keeps the
 * whole picture and replaces tiles as they arrive - nothing is added up, so no error can build up over time.
 *
 * <p>Every KEY_INTERVAL_MS (and when the size changes, or on request) the encoder sends every tile: a KEY frame. A viewer that joins late
 * is given the last key frame and everything since it. A byte budget per second (a token bucket) keeps a busy scene from using more than
 * the server allows: tiles that do not fit wait for the next frame (the most changed tiles go first), they are never dropped.
 *
 * Pure (no Minecraft classes), unit tested.
 */
public final class TileCodec {

    private TileCodec() {}

    public static final int TILE = 32;
    /** Set on the first packet of a key frame. */
    public static final int FLAG_KEY_START = 1;
    /** Set on the last packet of a key frame: from here on the picture is whole. */
    public static final int FLAG_KEY_END = 2;

    public static final int MIN_SIZE = 64;
    public static final int MAX_SIZE = 2048;
    /**
     * A safety net: every tile is sent again this often. Normally a key frame is only sent when the stream starts, the size changes, or the server
     * asks (it keeps the last key frame and everything after it for viewers that join, and asks for a new one when that grows too big).
     */
    public static final long KEY_INTERVAL_MS = 60000L;

    /** The most a packet's payload may be (header + JPEG): the packet limit of a client with room for the rest of the packet. */
    public static final int MAX_PAYLOAD = RelayFrames.MAX_FRAME_BYTES;

    /** One network payload and its flags. */
    public record Packet(int flags, byte[] data) { }

    /** What the decoder hands back: tiles to draw. {@code full}: the whole picture ({@code size} squared pixels in {@code pixels}, {@code tiles} empty). */
    public record Update(int size, boolean full, int[] tiles, int[] pixels) { }

    public static boolean validSize(int size) {
        return size >= MIN_SIZE && size <= MAX_SIZE && size % TILE == 0;
    }

    /** The largest valid size not above {@code size} (a multiple of 32, at least 64). */
    public static int alignSize(int size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size - size % TILE));
    }

    // ---------------------------------------------------------------------------------------------------
    // The wire format of one payload: varint size | varint k | k tile indices (increasing, each as the gap to the one before, varints) | JPEG
    // The JPEG is the k tiles in a grid, cols = ceil(sqrt(k)) across, row by row in the order of the indices; unused cells are grey.
    // ---------------------------------------------------------------------------------------------------

    private static void putVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int gridCols(int k) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(k)));
    }

    // =====================================================================================================
    // Encoder
    // =====================================================================================================

    public static final class Encoder {

        private static final float[] TILE_QUALITIES = {0.85f, 0.70f, 0.55f, 0.40f, 0.25f};
        /** A tile is "changed" when its sampled mean difference (0..255) reaches this, or several single pixels differ a lot (a spark, a particle). */
        private static final double CHANGE_MEAN = 1.5;
        private static final int BIG_PIXEL = 48;
        private static final int BIG_PIXELS_NEEDED = 3;
        /** The bucket never holds more than this many seconds of budget (so a long quiet spell does not allow a huge burst). */
        private static final double BURST_SECONDS = 0.5;

        private int size = -1;
        private int[] reference;               // what the viewers have: the last SENT pixels of every tile
        private long lastKeyMs = Long.MIN_VALUE / 2;
        private double tokens = 0.0;
        private long lastTokenMs = -1L;
        private double bytesPerTile = 250.0;   // a running estimate, to know how many tiles fit

        /** Tiles that differed in the last call (before the budget), tiles sent, bytes sent, and whether it was a key frame: for statistics and pacing. */
        public int lastChangedTiles;
        public int lastSentTiles;
        public int lastBytes;
        public boolean lastWasKey;

        /** Forget what the viewers have: the next call sends a key frame. */
        public void reset() {
            reference = null;
            size = -1;
        }

        /**
         * @param argb           the picture, size * size pixels (alpha ignored)
         * @param bytesPerSecond the most to send per second, on average (a key frame is not held to it, but what follows waits for the bucket)
         * @param forceKey       send every tile now
         * @return the packets to send, in order (empty if nothing changed, or nothing fits yet)
         */
        public List<Packet> encode(int[] argb, int size, long nowMs, int bytesPerSecond, boolean forceKey) {
            lastChangedTiles = 0;
            lastSentTiles = 0;
            lastBytes = 0;
            lastWasKey = false;
            if (!validSize(size) || argb.length < size * size) return List.of();

            boolean key = forceKey || reference == null || this.size != size || nowMs - lastKeyMs >= KEY_INTERVAL_MS;
            if (this.size != size || reference == null) {
                this.size = size;
                this.reference = new int[size * size];
                tokens = 0.0;
                lastTokenMs = -1L;
            }
            double rate = Math.max(1, bytesPerSecond);
            if (lastTokenMs >= 0L) tokens += Math.max(0L, nowMs - lastTokenMs) / 1000.0 * rate;
            lastTokenMs = nowMs;
            tokens = Math.min(tokens, rate * BURST_SECONDS);

            int tilesPerRow = size / TILE;
            int totalTiles = tilesPerRow * tilesPerRow;
            int[] selected;
            int selectedCount;
            if (key) {
                selected = new int[totalTiles];
                for (int i = 0; i < totalTiles; i++) selected[i] = i;
                selectedCount = totalTiles;
                lastChangedTiles = totalTiles;
            } else {
                long[] changed = new long[totalTiles];
                int changedCount = 0;
                for (int ty = 0; ty < tilesPerRow; ty++) {
                    for (int tx = 0; tx < tilesPerRow; tx++) {
                        int score = changeScore(argb, size, tx, ty);   // 0 = unchanged, else 16 * mean difference (>= 1)
                        if (score > 0) changed[changedCount++] = ((long) score << 32) | (ty * tilesPerRow + tx);
                    }
                }
                lastChangedTiles = changedCount;
                if (changedCount == 0) return List.of();
                int canSend = (int) (tokens / Math.max(64.0, bytesPerTile));
                if (canSend <= 0) return List.of();                 // over budget: wait, the tiles stay changed
                int take = Math.min(canSend, changedCount);
                Arrays.sort(changed, 0, changedCount);              // by score, the most changed last
                selected = new int[take];
                for (int i = 0; i < take; i++) selected[i] = (int) (changed[changedCount - 1 - i] & 0xFFFFFFFFL);
                Arrays.sort(selected);                              // increasing, for the index list
                selectedCount = take;
            }

            List<byte[]> payloads = new ArrayList<>();
            int perMosaic = (int) Math.max(1, Math.min(256, MAX_PAYLOAD * 0.7 / Math.max(64.0, bytesPerTile)));
            for (int from = 0; from < selectedCount; from += perMosaic) {
                encodeRange(selected, from, Math.min(selectedCount, from + perMosaic), argb, size, payloads);
            }
            int bytes = 0;
            List<Packet> packets = new ArrayList<>(payloads.size());
            for (int i = 0; i < payloads.size(); i++) {
                int flags = 0;
                if (key && i == 0) flags |= FLAG_KEY_START;
                if (key && i == payloads.size() - 1) flags |= FLAG_KEY_END;
                packets.add(new Packet(flags, payloads.get(i)));
                bytes += payloads.get(i).length;
            }
            // the viewers have these tiles now
            for (int i = 0; i < selectedCount; i++) copyTile(argb, reference, size, selected[i] % tilesPerRow, selected[i] / tilesPerRow);

            tokens -= bytes;
            if (selectedCount > 0) bytesPerTile = 0.8 * bytesPerTile + 0.2 * ((double) bytes / selectedCount);
            if (key) lastKeyMs = nowMs;
            lastSentTiles = selectedCount;
            lastBytes = bytes;
            lastWasKey = key;
            return packets;
        }

        /** 0 if the tile is as the viewers have it, otherwise 16 * its mean difference (at least 1). Looks at every other pixel each way. */
        private int changeScore(int[] argb, int size, int tx, int ty) {
            int x0 = tx * TILE;
            int y0 = ty * TILE;
            long sum = 0;
            int big = 0;
            int samples = 0;
            for (int y = y0; y < y0 + TILE; y += 2) {
                int row = y * size;
                for (int x = x0; x < x0 + TILE; x += 2) {
                    int a = argb[row + x];
                    int b = reference[row + x];
                    int d = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) + Math.abs((a & 255) - (b & 255));
                    sum += d;
                    if (d > BIG_PIXEL) big++;
                    samples++;
                }
            }
            double mean = sum / (3.0 * samples);
            if (mean >= CHANGE_MEAN || big >= BIG_PIXELS_NEEDED) return Math.max(1, (int) Math.round(mean * 16.0));
            return 0;
        }

        private static void copyTile(int[] from, int[] to, int size, int tx, int ty) {
            for (int y = ty * TILE; y < ty * TILE + TILE; y++) {
                System.arraycopy(from, y * size + tx * TILE, to, y * size + tx * TILE, TILE);
            }
        }

        /** Packs tiles[from..to) into one payload; if it is too big, into two halves (and so on). */
        private void encodeRange(int[] tiles, int from, int to, int[] argb, int size, List<byte[]> out) {
            int k = to - from;
            int tilesPerRow = size / TILE;
            int cols = gridCols(k);
            int rows = (k + cols - 1) / cols;
            int width = cols * TILE;
            int height = rows * TILE;
            int[] mosaic = new int[width * height];
            Arrays.fill(mosaic, 0xFF808080);
            for (int i = 0; i < k; i++) {
                int tx = tiles[from + i] % tilesPerRow;
                int ty = tiles[from + i] / tilesPerRow;
                int gx = (i % cols) * TILE;
                int gy = (i / cols) * TILE;
                for (int y = 0; y < TILE; y++) {
                    System.arraycopy(argb, (ty * TILE + y) * size + tx * TILE, mosaic, (gy + y) * width + gx, TILE);
                }
            }
            ByteArrayOutputStream header = new ByteArrayOutputStream(8 + k * 2);
            putVarInt(header, size);
            putVarInt(header, k);
            int previous = 0;
            for (int i = 0; i < k; i++) {
                putVarInt(header, tiles[from + i] - previous);
                previous = tiles[from + i];
            }
            byte[] jpeg = FrameCodec.encode(mosaic, width, height, MAX_PAYLOAD - header.size(), TILE_QUALITIES);
            if (jpeg == null) {
                if (k == 1) return;                              // cannot happen: one tile is a few KB at the poorest quality
                int mid = from + k / 2;
                encodeRange(tiles, from, mid, argb, size, out);
                encodeRange(tiles, mid, to, argb, size, out);
                return;
            }
            byte[] payload = new byte[header.size() + jpeg.length];
            System.arraycopy(header.toByteArray(), 0, payload, 0, header.size());
            System.arraycopy(jpeg, 0, payload, header.size(), jpeg.length);
            out.add(payload);
        }
    }

    // =====================================================================================================
    // Decoder
    // =====================================================================================================

    public static final class Decoder {

        private int size = -1;
        private int[] image;
        private boolean ready = false;   // a whole key frame has arrived: the picture is whole
        private boolean inKey = false;

        public boolean isReady() {
            return ready;
        }

        public int size() {
            return size;
        }

        public void reset() {
            size = -1;
            image = null;
            ready = false;
            inKey = false;
        }

        /**
         * Takes one payload. @return what to draw now, or null if there is nothing to draw (the picture is not whole yet, the payload was
         * a delta with no key frame before it, or the payload is not valid - it is then ignored)
         */
        public Update apply(int flags, byte[] data) {
            if (data == null || data.length < 8) return null;
            int[] pos = {0};
            int n = readVarInt(data, pos);
            int k = readVarInt(data, pos);
            if (!validSize(n) || k < 1) return null;
            int tilesPerRow = n / TILE;
            if (k > tilesPerRow * tilesPerRow || k > 1024) return null;
            int[] tiles = new int[k];
            int previous = 0;
            for (int i = 0; i < k; i++) {
                int gap = readVarInt(data, pos);
                if (gap < 0 || (i > 0 && gap == 0)) return null;
                previous += gap;
                if (previous < 0 || previous >= tilesPerRow * tilesPerRow) return null;
                tiles[i] = previous;
            }
            if (pos[0] < 0 || pos[0] >= data.length) return null;
            FrameCodec.Decoded mosaic = FrameCodec.decode(Arrays.copyOfRange(data, pos[0], data.length));
            int cols = gridCols(k);
            int rows = (k + cols - 1) / cols;
            if (mosaic == null || mosaic.width() != cols * TILE || mosaic.height() != rows * TILE) return null;

            boolean keyStart = (flags & FLAG_KEY_START) != 0;
            if (keyStart) {
                inKey = true;
                if (n != size) {                                   // a new size: start again (the old picture stays on the screen until this one is whole)
                    size = n;
                    image = new int[n * n];
                    ready = false;
                }
            } else if (!ready && !inKey) {
                return null;                                       // a delta with nothing to apply it to
            }
            if (n != size) return null;

            int[] pixels = new int[k * TILE * TILE];
            for (int i = 0; i < k; i++) {
                int tx = tiles[i] % tilesPerRow;
                int ty = tiles[i] / tilesPerRow;
                int gx = (i % cols) * TILE;
                int gy = (i / cols) * TILE;
                for (int y = 0; y < TILE; y++) {
                    System.arraycopy(mosaic.argb(), (gy + y) * mosaic.width() + gx, image, (ty * TILE + y) * n + tx * TILE, TILE);
                    System.arraycopy(mosaic.argb(), (gy + y) * mosaic.width() + gx, pixels, (i * TILE + y) * TILE, TILE);
                }
            }
            if ((flags & FLAG_KEY_END) != 0) {
                inKey = false;
                if (!ready) {
                    ready = true;
                    return new Update(n, true, new int[0], Arrays.copyOf(image, image.length));   // the first whole picture
                }
            }
            return ready ? new Update(n, false, tiles, pixels) : null;
        }

        private static int readVarInt(byte[] b, int[] pos) {
            int value = 0;
            int shift = 0;
            while (pos[0] < b.length && shift < 35) {
                int c = b[pos[0]++] & 0xFF;
                value |= (c & 0x7F) << shift;
                if ((c & 0x80) == 0) return value;
                shift += 7;
            }
            pos[0] = -1;                                           // ran off the end
            return -1;
        }
    }
}
