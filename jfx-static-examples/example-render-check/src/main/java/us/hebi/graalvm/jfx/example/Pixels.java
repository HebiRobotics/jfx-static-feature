package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * The pixel assertions the stages share. Every check appends to the problem list rather than throwing, so a
 * run reports everything that went wrong rather than only the first thing.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
class Pixels {

    // sw fills solid areas exactly, this only allows for color conversion
    static final double SOLID = 2 / 255d;

    // JPEG quantisation moves a solid color by a step or two
    static final double LOSSY = 4 / 255d;

    // Snapshots are in logical pixels on every platform and scale, so the samples land where the constants say
    static boolean checkSize(List<String> problems, WritableImage image, int width, int height) {
        if (image.getWidth() != width || image.getHeight() != height) {
            problems.add("snapshot is " + (int) image.getWidth() + "x" + (int) image.getHeight()
                    + " rather than " + width + "x" + height);
            return false;
        }
        return true;
    }

    static void checkPixel(List<String> problems, WritableImage image, int x, int y, Color expected, String what) {
        checkPixel(problems, image, x, y, expected, SOLID, what);
    }

    static void checkPixel(List<String> problems, WritableImage image, int x, int y, Color expected,
                           double tolerance, String what) {
        Color found = image.getPixelReader().getColor(x, y);
        if (!matches(found, expected, tolerance)) {
            problems.add(what + " at " + x + "," + y + " is " + found + " rather than " + expected);
        }
    }

    // For the effects with no exact expected color, where all we know is that they moved the flat fill
    static void checkChanged(List<String> problems, WritableImage image, int x, int y, Color flat,
                             Color background, String what) {
        Color found = image.getPixelReader().getColor(x, y);
        if (matches(found, flat, SOLID)) {
            problems.add(what + " at " + x + "," + y + " is still the unmodified " + flat);
        } else if (matches(found, background, SOLID)) {
            problems.add(what + " at " + x + "," + y + " is the background");
        }
    }

    static void checkBrighter(List<String> problems, WritableImage image, int x, int otherX, int y, String what) {
        double brightness = image.getPixelReader().getColor(x, y).getBrightness();
        double other = image.getPixelReader().getColor(otherX, y).getBrightness();
        if (brightness <= other) {
            problems.add(what + " at " + x + "," + y + " is " + brightness + " rather than brighter than "
                    + other + " at " + otherX + "," + y);
        }
    }

    static void checkForeground(List<String> problems, WritableImage image, int x, int y,
                                int width, int height, Color background, String what) {
        if (!hasForeground(image, x, y, width, height, background)) {
            problems.add("nothing was drawn for " + what + " in " + width + "x" + height + " at " + x + "," + y);
        }
    }

    static boolean matches(Color found, Color expected, double tolerance) {
        return Math.abs(found.getRed() - expected.getRed()) <= tolerance
                && Math.abs(found.getGreen() - expected.getGreen()) <= tolerance
                && Math.abs(found.getBlue() - expected.getBlue()) <= tolerance;
    }

    private static boolean hasForeground(WritableImage image, int fromX, int fromY, int width, int height,
                                         Color background) {
        PixelReader pixels = image.getPixelReader();
        for (int y = fromY; y < fromY + height; y++) {
            for (int x = fromX; x < fromX + width; x++) {
                if (!matches(pixels.getColor(x, y), background, SOLID)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Pixels() {
    }

}
