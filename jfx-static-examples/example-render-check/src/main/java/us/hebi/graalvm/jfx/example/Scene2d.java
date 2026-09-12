package us.hebi.graalvm.jfx.example;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.ColorInput;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Light;
import javafx.scene.effect.Lighting;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * Solid shapes, four decora effects, a canvas, a gradient paint, a png and a jpeg decode, a control with the
 * modena stylesheet and a user stylesheet from a data URL, latin and arabic text, and a root loaded from FXML.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
class Scene2d implements Scenario {

    private static final int WIDTH = 560;
    private static final int HEIGHT = 420;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color SHAPE = Color.web("#1e6fd9");
    private static final Color PICTURE = Color.web("#3fb950");
    private static final Color GRADIENT_LEFT = Color.web("#c0392b");
    private static final Color GRADIENT_RIGHT = Color.web("#8e44ad");
    private static final Color JPEG_PICTURE_COLOR = Color.rgb(217, 160, 29);
    private static final Color BUTTON = Color.web("#2ecc71");
    private static final Color CANVAS_FILL = Color.web("#0b7285");
    private static final Color ADJUST_FILL = Color.web("#2288cc");
    private static final Color LIGHTING_FILL = Color.web("#d95f1e");
    private static final Color BLEND_BOTTOM = Color.web("#80ff80");
    private static final Color BLEND_TOP = Color.web("#ff8040");
    // MULTIPLY is a component wise product of the two non premultiplied sRGB colors
    private static final Color BLEND_RESULT = Color.color(BLEND_TOP.getRed() * BLEND_BOTTOM.getRed(),
            BLEND_TOP.getGreen() * BLEND_BOTTOM.getGreen(), BLEND_TOP.getBlue() * BLEND_BOTTOM.getBlue());

    // The grid: row one at y 20..100 holds the shape, the gradient, the picture and the canvas, row two at
    // y 130..210 the jpeg, the button and the three 60x60 effect squares, and the two text baselines follow
    private static final int SHAPE_X = 20, SHAPE_Y = 20, SHAPE_WIDTH = 120, SHAPE_HEIGHT = 80;
    private static final int SHADOW_RADIUS = 10, SHADOW_SAMPLE = 4;
    private static final int GRADIENT_X = 160, GRADIENT_Y = 20, GRADIENT_WIDTH = 120, GRADIENT_HEIGHT = 80;
    private static final int PICTURE_X = 300, PICTURE_Y = 20, PICTURE_SIZE = 80;
    private static final int CANVAS_X = 400, CANVAS_Y = 20, CANVAS_WIDTH = 120, CANVAS_HEIGHT = 80, CANVAS_INSET = 10;
    private static final int JPEG_X = 20, JPEG_Y = 130, JPEG_SIZE = 80;
    private static final int BUTTON_X = 140, BUTTON_Y = 130, BUTTON_SAMPLE = 4;
    private static final int EFFECT_Y = 130, EFFECT_SIZE = 60;
    private static final int ADJUST_X = 260, BLEND_X = 340, LIGHTING_X = 420;
    private static final int TEXT_X = 20, TEXT_BASELINE = 270, TEXT_SIZE = 36;
    private static final int ARABIC_BASELINE = 340, ARABIC_SIZE = 24;

    // Arabic "marhaba" as escapes so the source stays ascii, the shaping runs in javafx_font either way
    private static final String ARABIC_TEXT = "\u0645\u0631\u062d\u0628\u0627";

    private static final String STYLESHEET = ".check-button { -fx-background-color: #2ecc71; -fx-background-insets: 0; -fx-background-radius: 0; }";

    // fx:root takes the instance from the loader, so the parser runs without reflecting on the type
    private static final String FXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<fx:root type=\"javafx.scene.layout.Pane\" xmlns:fx=\"http://javafx.com/fxml/1\"/>";

    @Override
    public String name() {
        return "2d";
    }

    @Override
    public String skipReason() {
        return null;
    }

    @Override
    public WritableImage render() {
        Rectangle shape = new Rectangle(SHAPE_X, SHAPE_Y, SHAPE_WIDTH, SHAPE_HEIGHT);
        shape.setFill(SHAPE);
        shape.setEffect(new DropShadow(SHADOW_RADIUS, Color.BLACK));

        Rectangle gradient = new Rectangle(GRADIENT_X, GRADIENT_Y, GRADIENT_WIDTH, GRADIENT_HEIGHT);
        gradient.getStyleClass().add("check-gradient");

        ImageView picture = new ImageView(Pictures.png(PICTURE_SIZE, PICTURE));
        picture.setX(PICTURE_X);
        picture.setY(PICTURE_Y);

        ImageView jpeg = new ImageView(Pictures.jpeg());
        jpeg.setX(JPEG_X);
        jpeg.setY(JPEG_Y);

        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        canvas.setLayoutX(CANVAS_X);
        canvas.setLayoutY(CANVAS_Y);
        canvas.getGraphicsContext2D().setFill(CANVAS_FILL);
        canvas.getGraphicsContext2D().fillRect(CANVAS_INSET, CANVAS_INSET,
                CANVAS_WIDTH - 2 * CANVAS_INSET, CANVAS_HEIGHT - 2 * CANVAS_INSET);

        Rectangle adjust = effectSquare(ADJUST_X, ADJUST_FILL);
        adjust.setEffect(new ColorAdjust(0.5, 0, 0, 0));

        // The top input covers the square exactly, so every pixel of it is the product of the two colors
        Rectangle blend = effectSquare(BLEND_X, BLEND_BOTTOM);
        Blend multiply = new Blend(BlendMode.MULTIPLY);
        multiply.setTopInput(new ColorInput(BLEND_X, EFFECT_Y, EFFECT_SIZE, EFFECT_SIZE, BLEND_TOP));
        blend.setEffect(multiply);

        Rectangle lighting = effectSquare(LIGHTING_X, LIGHTING_FILL);
        lighting.setEffect(new Lighting(new Light.Distant(45, 60, Color.WHITE)));

        Button button = new Button("OK");
        button.getStyleClass().add("check-button");
        button.setLayoutX(BUTTON_X);
        button.setLayoutY(BUTTON_Y);

        Text text = new Text(TEXT_X, TEXT_BASELINE, "Static JavaFX");
        text.setFont(Font.font(TEXT_SIZE));
        text.setFill(Color.BLACK);

        Text arabic = new Text(TEXT_X, ARABIC_BASELINE, ARABIC_TEXT);
        arabic.setFont(Font.font("System", FontWeight.BOLD, ARABIC_SIZE));
        arabic.setFill(Color.BLACK);

        Pane root = loadRoot();
        root.getChildren().addAll(shape, gradient, picture, jpeg, canvas, adjust, blend, lighting, button, text, arabic);
        // The button loads modena, whose .root rule would paint -fx-base over the scene fill
        root.setBackground(Background.fill(BACKGROUND));

        Scene scene = new Scene(root, WIDTH, HEIGHT, BACKGROUND);
        scene.getStylesheets().add(Scene2d.class.getResource("check.css").toExternalForm());
        scene.getStylesheets().add(Scenario.dataUrl(STYLESHEET));
        return Scenario.snapshot(new Stage(), scene);
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        if (!Pixels.checkSize(problems, image, WIDTH, HEIGHT)) {
            return;
        }
        Pixels.checkPixel(problems, image, 5, HEIGHT - 5, BACKGROUND, "the background");
        Pixels.checkPixel(problems, image, SHAPE_X + SHAPE_WIDTH / 2, SHAPE_Y + SHAPE_HEIGHT / 2, SHAPE, "the rectangle");
        // The shadow darkens the pixels just outside the rectangle
        Pixels.checkForeground(problems, image, SHAPE_X + SHAPE_WIDTH + SHADOW_SAMPLE, SHAPE_Y + SHAPE_HEIGHT / 2, 1, 1,
                BACKGROUND, "the shadow");
        Pixels.checkPixel(problems, image, GRADIENT_X + GRADIENT_WIDTH / 4, GRADIENT_Y + GRADIENT_HEIGHT / 2,
                GRADIENT_LEFT, "the left half of the gradient");
        Pixels.checkPixel(problems, image, GRADIENT_X + 3 * GRADIENT_WIDTH / 4, GRADIENT_Y + GRADIENT_HEIGHT / 2,
                GRADIENT_RIGHT, "the right half of the gradient");
        Pixels.checkPixel(problems, image, PICTURE_X + PICTURE_SIZE / 2, PICTURE_Y + PICTURE_SIZE / 2, PICTURE,
                "the picture");
        Pixels.checkPixel(problems, image, JPEG_X + JPEG_SIZE / 2, JPEG_Y + JPEG_SIZE / 2, JPEG_PICTURE_COLOR,
                Pixels.LOSSY, "the jpeg picture");
        Pixels.checkPixel(problems, image, CANVAS_X + CANVAS_WIDTH / 2, CANVAS_Y + CANVAS_HEIGHT / 2, CANVAS_FILL,
                "the canvas");
        Pixels.checkChanged(problems, image, ADJUST_X + EFFECT_SIZE / 2, EFFECT_Y + EFFECT_SIZE / 2, ADJUST_FILL,
                BACKGROUND, "the hue adjusted square");
        Pixels.checkPixel(problems, image, BLEND_X + EFFECT_SIZE / 2, EFFECT_Y + EFFECT_SIZE / 2, BLEND_RESULT,
                "the multiplied square");
        Pixels.checkChanged(problems, image, LIGHTING_X + EFFECT_SIZE / 2, EFFECT_Y + EFFECT_SIZE / 2, LIGHTING_FILL,
                BACKGROUND, "the lit square");

        // The stylesheet removes the insets and the radius, so the corner is a solid fill
        Pixels.checkPixel(problems, image, BUTTON_X + BUTTON_SAMPLE, BUTTON_Y + BUTTON_SAMPLE, BUTTON, "the button");
        Pixels.checkForeground(problems, image, 0, TEXT_BASELINE - TEXT_SIZE, WIDTH, TEXT_SIZE, BACKGROUND,
                "the text band");
        Pixels.checkForeground(problems, image, 0, ARABIC_BASELINE - ARABIC_SIZE, WIDTH, ARABIC_SIZE, BACKGROUND,
                "the arabic text band");
    }

    private static Rectangle effectSquare(int x, Color fill) {
        Rectangle square = new Rectangle(x, EFFECT_Y, EFFECT_SIZE, EFFECT_SIZE);
        square.setFill(fill);
        return square;
    }

    private static Pane loadRoot() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setRoot(new Pane());
            return loader.load(new ByteArrayInputStream(FXML.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ioe) {
            throw new IllegalStateException("Could not load the fxml root", ioe);
        }
    }

}
