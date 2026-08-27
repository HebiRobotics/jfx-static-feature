package us.hebi.graalvm.jfx.example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * Renders a fixed scene, by default on the headless glass platform, checks a few pixels and writes the snapshot
 * as a PNG. Headless loads no glass native, but the scene exercises a solid shape, a decora effect,
 * a gradient paint, a PNG and a JPEG decode through javafx_iio, a control with the modena stylesheet,
 * its skin and a user stylesheet from a data URL, text through javafx_font, and a root loaded from FXML.
 * The pipeline defaults to sw and follows a {@code -Dprism.order=d3d} (es2, mtl) argument, which a
 * native image accepts on the command line, so the same binary checks the GPU path. The glass
 * platform defaults to Headless the same way and {@code -Dglass.platform=Win} (Gtk, Mac) shows the scene
 * in a real window for debugging.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
public class RenderCheck {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color SHAPE = Color.web("#1e6fd9");
    private static final Color PICTURE = Color.web("#3fb950");
    private static final Color GRADIENT_LEFT = Color.web("#c0392b");
    private static final Color GRADIENT_RIGHT = Color.web("#8e44ad");
    private static final Color JPEG_PICTURE_COLOR = Color.rgb(217, 160, 29);
    private static final Color BUTTON = Color.web("#2ecc71");

    // Node positions, which is also where the check samples
    private static final int SHAPE_X = 20, SHAPE_Y = 20, SHAPE_WIDTH = 120, SHAPE_HEIGHT = 80;
    private static final int SHADOW_RADIUS = 10, SHADOW_SAMPLE = 4;
    private static final int GRADIENT_X = 160, GRADIENT_Y = 20, GRADIENT_WIDTH = 120, GRADIENT_HEIGHT = 80;
    private static final int PICTURE_X = 300, PICTURE_Y = 20, PICTURE_SIZE = 80;
    private static final int JPEG_X = 20, JPEG_Y = 130, JPEG_SIZE = 80;
    private static final int BUTTON_X = 140, BUTTON_Y = 130, BUTTON_SAMPLE = 4;
    private static final int TEXT_X = 20, TEXT_BASELINE = 270, TEXT_SIZE = 36;

    // A second user stylesheet on top of modena and check.css, loaded through a data URL
    private static final String STYLESHEET = ".check-button { -fx-background-color: #2ecc71; -fx-background-insets: 0; -fx-background-radius: 0; }";

    // fx:root takes the instance from the loader, so the parser runs without reflecting on the type
    private static final String FXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<fx:root type=\"javafx.scene.layout.Pane\" xmlns:fx=\"http://javafx.com/fxml/1\"/>";

    // An 80x80 solid rgb(217,160,30) square, encoded once at quality 100, decodes to rgb(217,160,29)
    private static final String JPEG_PICTURE = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEB"
            + "AQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEB"
            + "AQEBAQEBAQH/wAARCABQAFADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUF"
            + "BAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVW"
            + "V1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi"
            + "4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAEC"
            + "AxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVm"
            + "Z2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq"
            + "8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD9EKKKK/5pz/TgKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACii"
            + "igAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooA/9k=";

    public static void main(String[] args) throws Exception {
        // Defaults that a -D argument on the command line overrides, set before the toolkit starts
        setDefault("glass.platform", "Headless"); // Win, Gtk, Mac, Headless
        setDefault("prism.order", "sw"); // d3d, mtl, es2, sw (add -Dprism.forceGPU=true to run es2 on RPi)

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
        System.out.printf("wrote %s, %d bytes, %.0f ms, %s%n", output.toAbsolutePath(), Files.size(output),
                (System.nanoTime() - start) / 1e6, System.getProperty("prism.order").replace(' ', '_'));

        List<String> problems = check(snapshot[0]);
        problems.forEach(problem -> System.out.println("FAILED: " + problem));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    private static WritableImage render() {
        Rectangle shape = new Rectangle(SHAPE_X, SHAPE_Y, SHAPE_WIDTH, SHAPE_HEIGHT);
        shape.setFill(SHAPE);
        // The decora peers differ between the sw and the GPU pipelines
        shape.setEffect(new DropShadow(SHADOW_RADIUS, Color.BLACK));

        // The fill is a hard-stop gradient from check.css, so the CSS paint parser and the shader loaders run
        Rectangle gradient = new Rectangle(GRADIENT_X, GRADIENT_Y, GRADIENT_WIDTH, GRADIENT_HEIGHT);
        gradient.getStyleClass().add("check-gradient");

        ImageView picture = new ImageView(decodePicture());
        picture.setX(PICTURE_X);
        picture.setY(PICTURE_Y);

        ImageView jpeg = new ImageView(new Image(new ByteArrayInputStream(Base64.getDecoder().decode(JPEG_PICTURE))));
        jpeg.setX(JPEG_X);
        jpeg.setY(JPEG_Y);

        // Pulls in the CSS parser, the modena stylesheet, the skin and the controls resource bundle
        Button button = new Button("OK");
        button.getStyleClass().add("check-button");
        button.setLayoutX(BUTTON_X);
        button.setLayoutY(BUTTON_Y);

        Text text = new Text(TEXT_X, TEXT_BASELINE, "Static JavaFX");
        text.setFont(Font.font(TEXT_SIZE));
        text.setFill(Color.BLACK);

        Pane root = loadRoot();
        root.getChildren().addAll(shape, gradient, picture, jpeg, button, text);
        // The button loads modena, whose .root rule would paint -fx-base over the scene fill
        root.setBackground(Background.fill(BACKGROUND));

        Scene scene = new Scene(root, WIDTH, HEIGHT, BACKGROUND);
        // One stylesheet from the class path, one from a data URL
        scene.getStylesheets().add(RenderCheck.class.getResource("check.css").toExternalForm());
        scene.getStylesheets().add("data:text/css;base64,"
                + Base64.getEncoder().encodeToString(STYLESHEET.getBytes(StandardCharsets.UTF_8)));
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        return scene.snapshot(null);
    }

    // The root comes through FXMLLoader, which has its own metadata in javafx.fxml
    private static Pane loadRoot() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setRoot(new Pane());
            return loader.load(new ByteArrayInputStream(FXML.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ioe) {
            throw new IllegalStateException("Could not load the fxml root", ioe);
        }
    }

    // Round trip through PNG so the check covers javafx_iio
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

        // Snapshots are in logical pixels on every platform and scale, so the samples land where the constants say
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            problems.add("snapshot is " + (int) image.getWidth() + "x" + (int) image.getHeight()
                    + " rather than " + WIDTH + "x" + HEIGHT);
            return problems;
        }

        checkPixel(problems, image, 5, HEIGHT - 5, BACKGROUND, "the background");
        checkPixel(problems, image, SHAPE_X + SHAPE_WIDTH / 2, SHAPE_Y + SHAPE_HEIGHT / 2, SHAPE, "the rectangle");
        // The shadow darkens the pixels just outside the rectangle
        checkForeground(problems, image, SHAPE_X + SHAPE_WIDTH + SHADOW_SAMPLE, SHAPE_Y + SHAPE_HEIGHT / 2, 1, 1,
                "the shadow");
        checkPixel(problems, image, GRADIENT_X + GRADIENT_WIDTH / 4, GRADIENT_Y + GRADIENT_HEIGHT / 2,
                GRADIENT_LEFT, "the left half of the gradient");
        checkPixel(problems, image, GRADIENT_X + 3 * GRADIENT_WIDTH / 4, GRADIENT_Y + GRADIENT_HEIGHT / 2,
                GRADIENT_RIGHT, "the right half of the gradient");
        checkPixel(problems, image, PICTURE_X + PICTURE_SIZE / 2, PICTURE_Y + PICTURE_SIZE / 2, PICTURE, "the picture");
        // JPEG quantisation moves the solid color by a step or two
        checkPixel(problems, image, JPEG_X + JPEG_SIZE / 2, JPEG_Y + JPEG_SIZE / 2, JPEG_PICTURE_COLOR,
                4 / 255d, "the jpeg picture");

        // The stylesheet removes the insets and the radius, so the corner is a solid fill
        checkPixel(problems, image, BUTTON_X + BUTTON_SAMPLE, BUTTON_Y + BUTTON_SAMPLE, BUTTON, "the button");
        checkForeground(problems, image, 0, TEXT_BASELINE - TEXT_SIZE, WIDTH, TEXT_SIZE, "the text band");
        return problems;
    }

    private static void checkPixel(List<String> problems, WritableImage image, int x, int y, Color expected, String what) {
        // sw fills solid areas exactly, this only allows for color conversion
        checkPixel(problems, image, x, y, expected, 2 / 255d, what);
    }

    private static void checkPixel(List<String> problems, WritableImage image, int x, int y, Color expected,
                                   double tolerance, String what) {
        Color found = image.getPixelReader().getColor(x, y);
        if (Math.abs(found.getRed() - expected.getRed()) > tolerance
                || Math.abs(found.getGreen() - expected.getGreen()) > tolerance
                || Math.abs(found.getBlue() - expected.getBlue()) > tolerance) {
            problems.add(what + " at " + x + "," + y + " is " + found + " rather than " + expected);
        }
    }

    private static void checkForeground(List<String> problems, WritableImage image, int x, int y,
                                        int width, int height, String what) {
        if (!hasForeground(image, x, y, width, height)) {
            problems.add("nothing was drawn for " + what + " in " + width + "x" + height + " at " + x + "," + y);
        }
    }

    private static boolean hasForeground(WritableImage image, int fromX, int fromY, int width, int height) {
        PixelReader pixels = image.getPixelReader();
        for (int y = fromY; y < fromY + height; y++) {
            for (int x = fromX; x < fromX + width; x++) {
                if (!pixels.getColor(x, y).equals(BACKGROUND)) {
                    return true;
                }
            }
        }
        return false;
    }

    // javax.imageio would pull in AWT
    private static byte[] encodePng(WritableImage image) throws IOException {
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

}
