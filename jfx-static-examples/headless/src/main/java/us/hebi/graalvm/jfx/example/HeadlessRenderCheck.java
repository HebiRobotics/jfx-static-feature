package us.hebi.graalvm.jfx.example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * Renders a fixed scene on the headless glass platform, checks a handful of pixels and writes the
 * snapshot as a PNG. This is what CI runs on every platform: the headless target loads no glass
 * native at all, but the render still goes through prism_sw, javafx_font and, for the decoded
 * picture, javafx_iio, which is enough to prove the static link and the loader substitutions.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
public class HeadlessRenderCheck {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color SHAPE = Color.web("#1e6fd9");
    private static final Color PICTURE = Color.web("#3fb950");

    /** Where each of the three nodes ends up, which is also where the check samples. */
    private static final int SHAPE_X = 20, SHAPE_Y = 20, SHAPE_WIDTH = 120, SHAPE_HEIGHT = 80;
    private static final int PICTURE_X = 240, PICTURE_Y = 20, PICTURE_SIZE = 80;
    private static final int TEXT_X = 20, TEXT_BASELINE = 200, TEXT_SIZE = 36;

    public static void main(String[] args) throws Exception {
        // Both have to be set before the toolkit starts, so that no -D is needed on the command line
        System.setProperty("glass.platform", "Headless");
        System.setProperty("prism.order", "sw");

        Path output = Path.of(args.length > 0 ? args[0] : "render-check.png");
        long start = System.nanoTime();

        WritableImage[] snapshot = new WritableImage[1];
        Throwable[] failure = new Throwable[1];
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                snapshot[0] = render();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();

        if (failure[0] != null) {
            failure[0].printStackTrace();
            System.exit(1);
        }
        Files.write(output, encodePng(snapshot[0]));
        System.out.printf("wrote %s, %d bytes, %.0f ms%n", output.toAbsolutePath(), Files.size(output),
                (System.nanoTime() - start) / 1e6);

        List<String> problems = check(snapshot[0]);
        problems.forEach(problem -> System.out.println("FAILED: " + problem));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    private static WritableImage render() {
        Rectangle shape = new Rectangle(SHAPE_X, SHAPE_Y, SHAPE_WIDTH, SHAPE_HEIGHT);
        shape.setFill(SHAPE);

        ImageView picture = new ImageView(decodePicture());
        picture.setX(PICTURE_X);
        picture.setY(PICTURE_Y);

        Text text = new Text(TEXT_X, TEXT_BASELINE, "Static JavaFX");
        text.setFont(Font.font(TEXT_SIZE));
        text.setFill(Color.BLACK);

        Scene scene = new Scene(new Pane(shape, picture, text), WIDTH, HEIGHT, BACKGROUND);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        return scene.snapshot(null);
    }

    /**
     * A solid square encoded as a PNG and decoded again, so that the check covers javafx_iio rather
     * than only the pixels this process wrote itself.
     */
    private static Image decodePicture() {
        WritableImage square = new WritableImage(PICTURE_SIZE, PICTURE_SIZE);
        for (int y = 0; y < PICTURE_SIZE; y++) {
            for (int x = 0; x < PICTURE_SIZE; x++) {
                square.getPixelWriter().setColor(x, y, PICTURE);
            }
        }
        try {
            return new Image(new ByteArrayInputStream(encodePng(square)));
        } catch (IOException ioe) {
            throw new IllegalStateException("Could not encode the test picture", ioe);
        }
    }

    private static List<String> check(WritableImage image) {
        List<String> problems = new ArrayList<>();
        checkPixel(problems, image, 5, HEIGHT - 5, BACKGROUND, "the background");
        checkPixel(problems, image, SHAPE_X + SHAPE_WIDTH / 2, SHAPE_Y + SHAPE_HEIGHT / 2, SHAPE, "the rectangle");
        checkPixel(problems, image, PICTURE_X + PICTURE_SIZE / 2, PICTURE_Y + PICTURE_SIZE / 2, PICTURE, "the picture");
        if (!hasForeground(image, TEXT_BASELINE - TEXT_SIZE, TEXT_BASELINE)) {
            problems.add("nothing was drawn in the text band, rows " + (TEXT_BASELINE - TEXT_SIZE)
                    + " to " + TEXT_BASELINE);
        }
        return problems;
    }

    private static void checkPixel(List<String> problems, WritableImage image, int x, int y, Color expected, String what) {
        Color found = image.getPixelReader().getColor(x, y);
        // The software pipeline fills solid areas exactly, so this leaves room for color conversion only
        double tolerance = 2 / 255d;
        if (Math.abs(found.getRed() - expected.getRed()) > tolerance
                || Math.abs(found.getGreen() - expected.getGreen()) > tolerance
                || Math.abs(found.getBlue() - expected.getBlue()) > tolerance) {
            problems.add(what + " at " + x + "," + y + " is " + found + " rather than " + expected);
        }
    }

    private static boolean hasForeground(WritableImage image, int fromRow, int toRow) {
        PixelReader pixels = image.getPixelReader();
        for (int y = fromRow; y <= toRow; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (!pixels.getColor(x, y).equals(BACKGROUND)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A PNG encoder rather than javax.imageio, whose BufferedImage clinit loads libawt and fails in
     * an image without AWT metadata.
     */
    private static byte[] encodePng(WritableImage image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();

        // One filter byte (0 = None) plus RGBA per row, which is what deflate below compresses
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

}
