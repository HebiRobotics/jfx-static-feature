package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * A preview extended window whose title bar is the scene's own header bar. The platform's caption buttons
 * are drawn outside the scene graph, so the snapshot shows the bar and its label, not the buttons.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
@SuppressWarnings("deprecation") // extended windows are a preview feature in 26
class TitleBar implements Scenario {

    private static final int WIDTH = 400, HEIGHT = 300, HEADER_HEIGHT = 40;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color CENTER = Color.web("#5f27cd");

    @Override
    public String name() {
        return "titlebar";
    }

    @Override
    public String skipReason() {
        if (Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW)) {
            return null;
        }
        return "ConditionalFeature.EXTENDED_WINDOW is missing on glass.platform="
                + System.getProperty("glass.platform");
    }

    @Override
    public WritableImage render() {
        HeaderBar header = new HeaderBar();
        header.setCenter(new Label("Static JavaFX"));

        Region center = new Region();
        center.setBackground(Background.fill(CENTER));
        BorderPane root = new BorderPane(center);
        root.setTop(header);

        Stage window = new Stage();
        window.initStyle(StageStyle.EXTENDED);
        // The platform draws its caption buttons at this height, and the scene fill picks their color scheme
        HeaderBar.setPrefButtonHeight(window, HEADER_HEIGHT);
        return Scenario.snapshot(window, new Scene(root, WIDTH, HEIGHT, BACKGROUND));
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        if (!Pixels.checkSize(problems, image, WIDTH, HEIGHT)) {
            return;
        }
        Pixels.checkPixel(problems, image, WIDTH / 2, (HEADER_HEIGHT + HEIGHT) / 2, CENTER,
                "the center of the extended window");
        // The band is whatever the platform paints behind the caption, so take its own corner as the background
        Color headerBackground = image.getPixelReader().getColor(5, 5);
        Pixels.checkForeground(problems, image, 0, 0, WIDTH, HEADER_HEIGHT, headerBackground, "the header band");
    }

}
