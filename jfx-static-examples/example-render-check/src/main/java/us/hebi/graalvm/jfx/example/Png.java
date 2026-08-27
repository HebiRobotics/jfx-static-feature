package us.hebi.graalvm.jfx.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

/**
 * A minimal RGBA png encoder, javax.imageio would pull in AWT.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
class Png {

    static byte[] encode(WritableImage image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();

        // filter byte 0 plus RGBA per row
        byte[] raw = new byte[height * (1 + width * 4)];
        int i = 0;
        for (int y = 0; y < height; y++) {
            raw[i++] = 0;
            for (int x = 0; x < width; x++) {
                int argb = pixels.getArgb(x, y);
                raw[i++] = (byte) (argb >> 16);
                raw[i++] = (byte) (argb >> 8);
                raw[i++] = (byte) argb;
                raw[i++] = (byte) (argb >>> 24);
            }
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a });
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeInt(header, width);
        writeInt(header, height);
        header.write(new byte[] { 8, 6, 0, 0, 0 }); // 8 bits per sample, RGBA, no interlace
        writeChunk(png, "IHDR", header.toByteArray());
        writeChunk(png, "IDAT", deflate(raw));
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4);
        byte[] block = new byte[16 * 1024];
        while (!deflater.finished()) {
            out.write(block, 0, deflater.deflate(block));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        writeInt(out, data.length);
        out.write(name);
        out.write(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private Png() {
    }

}
