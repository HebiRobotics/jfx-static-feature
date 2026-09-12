package us.hebi.graalvm.jfx.example;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * The two pictures the stages decode, one lossless and one lossy, so a check covers both javafx_iio readers.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
class Pictures {

    // 80x80 solid rgb(217,160,30) at quality 100, decodes to rgb(217,160,29)
    private static final String JPEG = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEB"
            + "AQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEB"
            + "AQEBAQEBAQH/wAARCABQAFADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUF"
            + "BAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVW"
            + "V1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi"
            + "4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAEC"
            + "AxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVm"
            + "Z2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq"
            + "8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD9EKKKK/5pz/TgKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACii"
            + "igAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooA/9k=";

    static Image jpeg() {
        return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(JPEG)));
    }

    static Image png(int size, Color color) {
        return new Image(new ByteArrayInputStream(pngBytes(size, color)));
    }

    static byte[] pngBytes(int size, Color color) {
        WritableImage square = new WritableImage(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                square.getPixelWriter().setColor(x, y, color);
            }
        }
        try {
            return Png.encode(square);
        } catch (IOException ioe) {
            throw new IllegalStateException("Could not encode the test picture", ioe);
        }
    }

    private Pictures() {
    }

}
