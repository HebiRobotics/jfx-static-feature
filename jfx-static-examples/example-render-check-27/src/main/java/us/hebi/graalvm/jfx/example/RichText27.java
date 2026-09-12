package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.EmbeddedImage;
import jfx.incubator.scene.control.richtext.model.RichTextModel;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

/**
 * The rich text attributes JavaFX 27 added: an embedded image, a tab stop, a wavy underline and a text
 * highlight. The image goes through EmbeddedImage, whose static initializer registers the accessor that
 * EmbeddedImageHelper reads the bytes through, and the skin decodes it as an inline node.
 *
 * @author Florian Enner
 * @since 12 Sep 2026
 */
class RichText27 implements Scenario {

    private static final int WIDTH = 560, HEIGHT = 200;
    private static final int IMAGE_SIZE = 48;
    private static final double FONT_SIZE = 24, TAB_STOP = 200;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color IMAGE_COLOR = Color.web("#1e6fd9");

    // The image is embedded at its own size, so the layout only costs it the odd row and column
    private static final int MIN_IMAGE_PIXELS = IMAGE_SIZE * IMAGE_SIZE / 2;

    // The wavy underline and the highlight take their colors from the wavy-underline-1 and text-highlight-1 styles
    private static final StyleAttributeMap DECORATED = StyleAttributeMap.builder()
            .setFontSize(FONT_SIZE)
            .set(StyleAttributeMap.WAVY_UNDERLINE_1, true)
            .set(StyleAttributeMap.TEXT_HIGHLIGHT_1, true)
            .build();

    // A paragraph attribute does not ride along with an appended run, it is applied over the range afterwards
    private static final StyleAttributeMap TAB_STOPS = StyleAttributeMap.builder()
            .setTabStops(TAB_STOP)
            .build();

    private static final String TABBED_TEXT = "Tab\tstop";

    @Override
    public String name() {
        return "richtext27";
    }

    @Override
    public String skipReason() {
        return null;
    }

    @Override
    public WritableImage render() {
        RichTextArea area = new RichTextArea(new RichTextModel());
        area.appendText(TABBED_TEXT, DECORATED);
        area.applyStyle(TextPos.ZERO, TextPos.ofLeading(0, TABBED_TEXT.length()), TAB_STOPS);
        area.appendText("\n", StyleAttributeMap.EMPTY);
        // An image rides on a one character segment, the same way a dropped image file enters the model
        area.appendText(" ", StyleAttributeMap.of(StyleAttributeMap.EMBEDDED_IMAGE, embeddedImage()));

        Scene scene = new Scene(area, WIDTH, HEIGHT, BACKGROUND);
        return Scenario.snapshot(new Stage(), scene);
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        if (!Pixels.checkSize(problems, image, WIDTH, HEIGHT)) {
            return;
        }
        Pixels.checkForeground(problems, image, 0, 0, WIDTH, HEIGHT, BACKGROUND, "the rich text area");
        int found = countColor(image, IMAGE_COLOR);
        if (found < MIN_IMAGE_PIXELS) {
            problems.add("the embedded image covers " + found + " pixels of " + IMAGE_COLOR
                    + " rather than at least " + MIN_IMAGE_PIXELS);
        }
    }

    private static EmbeddedImage embeddedImage() {
        return EmbeddedImage.of(Pictures.pngBytes(IMAGE_SIZE, IMAGE_COLOR),
                IMAGE_SIZE, IMAGE_SIZE, IMAGE_SIZE, IMAGE_SIZE, true);
    }

    private static int countColor(WritableImage image, Color color) {
        PixelReader pixels = image.getPixelReader();
        int found = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (Pixels.matches(pixels.getColor(x, y), color, Pixels.SOLID)) {
                    found++;
                }
            }
        }
        return found;
    }

}
