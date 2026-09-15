package us.hebi.graalvm.jfx.example;

import java.io.IOException;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.EmbeddedImage;
import jfx.incubator.scene.control.richtext.model.RichTextModel;
import jfx.incubator.scene.control.richtext.model.RtfFormatHandler;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import jfx.incubator.scene.control.richtext.model.StyledInput;

/**
 * The incubator rich text controls: a styled run, an RTF paragraph, a tab stop with a wavy underline and a
 * text highlight, and an embedded image in a {@link RichTextArea}, and a {@link CodeArea} with its line
 * numbers. The RTF goes through RTFReader, whose charset tables are resources. The image goes through
 * EmbeddedImage, whose static initializer registers the accessor that EmbeddedImageHelper reads the bytes
 * through, and the skin decodes it as an inline node.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
class RichText implements Scenario {

    private static final int WIDTH = 560, HEIGHT = 380;
    private static final int AREA_HEIGHT = 220, CODE_HEIGHT = 160;
    // The first paragraph is one 24 point line, and its glyph stems are wide enough to hold the exact color
    private static final int FIRST_LINE_HEIGHT = 40;
    private static final int IMAGE_SIZE = 48;
    private static final double FONT_SIZE = 24, TAB_STOP = 200;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color RUN_COLOR = Color.web("#d81b60");
    private static final Color IMAGE_COLOR = Color.web("#1e6fd9");

    // The image is embedded at its own size, so the layout only costs it the odd row and column
    private static final int MIN_IMAGE_PIXELS = IMAGE_SIZE * IMAGE_SIZE / 2;

    private static final StyleAttributeMap BOLD = StyleAttributeMap.builder()
            .setBold(true)
            .setFontSize(FONT_SIZE)
            .build();
    private static final StyleAttributeMap COLORED = StyleAttributeMap.builder()
            .setBold(true)
            .setFontSize(FONT_SIZE)
            .setTextColor(RUN_COLOR)
            .build();

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

    private static final String RTF_TEXT = "RTF import";
    private static final String RTF = "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0 Arial;}}\\f0\\fs32 "
            + RTF_TEXT + "}";

    private static final String CODE = "int main() {\n    return 0;\n}\n";

    private String imported = "";

    @Override
    public String name() {
        return "richtext";
    }

    @Override
    public String skipReason() {
        return null;
    }

    @Override
    public WritableImage render() {
        RichTextModel model = new RichTextModel();
        RichTextArea area = new RichTextArea(model);
        area.setPrefHeight(AREA_HEIGHT);
        area.appendText("Bold", BOLD);
        area.appendText(" Colored", COLORED);
        area.appendText("\n", StyleAttributeMap.EMPTY);
        area.appendText(rtf());
        imported = plainText(model);

        TextPos tabbed = area.getDocumentEnd();
        area.appendText(TABBED_TEXT, DECORATED);
        area.applyStyle(tabbed, area.getDocumentEnd(), TAB_STOPS);
        area.appendText("\n", StyleAttributeMap.EMPTY);
        // An image rides on a one character segment, the same way a dropped image file enters the model
        area.appendText(" ", StyleAttributeMap.of(StyleAttributeMap.EMBEDDED_IMAGE, embeddedImage()));

        CodeArea code = new CodeArea();
        code.setLineNumbersEnabled(true);
        code.setText(CODE);
        code.setPrefHeight(CODE_HEIGHT);

        Scene scene = new Scene(new VBox(area, code), WIDTH, HEIGHT, BACKGROUND);
        return Scenario.snapshot(new Stage(), scene);
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        if (!Pixels.checkSize(problems, image, WIDTH, HEIGHT)) {
            return;
        }
        Pixels.checkForeground(problems, image, 0, 0, WIDTH, AREA_HEIGHT, BACKGROUND, "the rich text area");
        Pixels.checkForeground(problems, image, 0, AREA_HEIGHT, WIDTH, CODE_HEIGHT, BACKGROUND, "the code area");
        if (!imported.contains(RTF_TEXT)) {
            problems.add("the model reads \"" + imported + "\", which is missing the rtf run \"" + RTF_TEXT + "\"");
        }
        if (countColor(image, FIRST_LINE_HEIGHT, RUN_COLOR) == 0) {
            problems.add("the colored run is not " + RUN_COLOR + " anywhere in the first " + FIRST_LINE_HEIGHT
                    + " rows");
        }
        int found = countColor(image, AREA_HEIGHT, IMAGE_COLOR);
        if (found < MIN_IMAGE_PIXELS) {
            problems.add("the embedded image covers " + found + " pixels of " + IMAGE_COLOR
                    + " rather than at least " + MIN_IMAGE_PIXELS);
        }
    }

    private static int countColor(WritableImage image, int rows, Color color) {
        PixelReader pixels = image.getPixelReader();
        int found = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (Pixels.matches(pixels.getColor(x, y), color, Pixels.SOLID)) {
                    found++;
                }
            }
        }
        return found;
    }

    // The rtf import ends on a paragraph break, so the run it added is not the last paragraph
    private static String plainText(RichTextModel model) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < model.size(); i++) {
            text.append(model.getPlainText(i));
        }
        return text.toString();
    }

    private static StyledInput rtf() {
        try {
            return RtfFormatHandler.getInstance().createStyledInput(RTF, StyleAttributeMap.EMPTY);
        } catch (IOException ioe) {
            throw new IllegalStateException("Could not read the rtf paragraph", ioe);
        }
    }

    private static EmbeddedImage embeddedImage() {
        return EmbeddedImage.of(Pictures.pngBytes(IMAGE_SIZE, IMAGE_COLOR),
                IMAGE_SIZE, IMAGE_SIZE, IMAGE_SIZE, IMAGE_SIZE, true);
    }

}
