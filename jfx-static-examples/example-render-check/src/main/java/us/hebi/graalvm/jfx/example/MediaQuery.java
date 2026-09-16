package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/**
 * A conditional {@code @import} and an {@code @media (-fx-platform: ...)} block, both of which the JavaFX 27
 * parser added. The import applies because the platform is not android and the media block does not, so the
 * square ends up in the imported color rather than in its base one.
 *
 * @author Florian Enner
 * @since 12 Sep 2026
 */
class MediaQuery implements Scenario {

    private static final int WIDTH = 200, HEIGHT = 120;
    private static final int SQUARE_X = 20, SQUARE_Y = 20, SQUARE_SIZE = 80;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color IMPORT_FILL = Color.web("#7b1fa2");

    private static final String BASE_STYLESHEET = ".check-media { -fx-fill: #f5a623; }";
    private static final String IMPORTED_STYLESHEET = ".check-media { -fx-fill: #7b1fa2; }";
    private static final String MEDIA_STYLESHEET = "@import url(\"" + Scenario.dataUrl(IMPORTED_STYLESHEET)
            + "\") not (-fx-platform: android); @media (-fx-platform: android) { .check-media { -fx-fill: #000000; } }";

    @Override
    public String name() {
        return "mediaquery";
    }

    @Override
    public String skipReason() {
        return null;
    }

    @Override
    public WritableImage render() {
        Rectangle square = new Rectangle(SQUARE_X, SQUARE_Y, SQUARE_SIZE, SQUARE_SIZE);
        square.getStyleClass().add("check-media");

        Scene scene = new Scene(new Group(square), WIDTH, HEIGHT, BACKGROUND);
        scene.getStylesheets().add(Scenario.dataUrl(BASE_STYLESHEET));
        scene.getStylesheets().add(Scenario.dataUrl(MEDIA_STYLESHEET));
        return Scenario.snapshot(new Stage(), scene);
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        if (!Pixels.checkSize(problems, image, WIDTH, HEIGHT)) {
            return;
        }
        Pixels.checkPixel(problems, image, SQUARE_X + SQUARE_SIZE / 2, SQUARE_Y + SQUARE_SIZE / 2, IMPORT_FILL,
                "the media query square");
    }

}
