package net.eclipce.transpondersnails.visual;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Packs a square picture into a JPEG small enough for one network packet (a client may send at most 32 KiB at once), and unpacks it. Used by
 * the relay: the client of a player near a camera renders the camera's view and sends it up; the server relays it to the viewers.
 * Pure Java (no Minecraft classes), unit tested.
 */
public final class FrameCodec {

    private FrameCodec() {}

    static {
        ImageIO.setUseCache(false); // no temporary files
    }

    /** From good to poor: a frame is encoded at the first quality that fits. */
    public static final float[] QUALITIES = {0.70f, 0.55f, 0.42f, 0.30f, 0.20f, 0.10f};

    /** A decoded picture: ARGB, row by row from the first row of the file. */
    public record Decoded(int width, int height, int[] argb) { }

    /**
     * @param argb    size * size pixels (alpha is ignored)
     * @param maxBytes the most the result may be
     * @return the JPEG, or null if even the poorest quality does not fit (the caller then sends a smaller picture)
     */
    public static byte[] encode(int[] argb, int size, int maxBytes) {
        return encode(argb, size, size, maxBytes, QUALITIES);
    }

    /** The same for a picture of any size, trying the given qualities from first to last. */
    public static byte[] encode(int[] argb, int width, int height, int maxBytes, float[] qualities) {
        if (width <= 0 || height <= 0 || argb.length < width * height) return null;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        for (float quality : qualities) {
            byte[] bytes = write(image, quality);
            if (bytes != null && bytes.length <= maxBytes) return bytes;
        }
        return null;
    }

    private static byte[] write(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    /** The picture in a JPEG, or null if it is not a usable picture (damaged, or not between 16 and 1024 pixels each way). */
    public static Decoded decode(byte[] data) {
        if (data == null || data.length < 16) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) return null;
            int w = image.getWidth();
            int h = image.getHeight();
            if (w < 16 || h < 16 || w > 1024 || h > 1024) return null;
            return new Decoded(w, h, image.getRGB(0, 0, w, h, null, 0, w));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
