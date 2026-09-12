package us.hebi.graalvm.jfx.example;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * One scene of the render check, rendered on the fx thread, written out as a PNG and then verified on the
 * pixels alone. A stage the platform cannot run names its reason instead of failing the check.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
interface Scenario {

    String name();

    /** null when the stage runs on this platform */
    String skipReason();

    WritableImage render();

    void check(WritableImage image, List<String> problems);

    /** A stylesheet inlined as a URL, which is the path a class path resource does not cover */
    static String dataUrl(String stylesheet) {
        return "data:text/css;base64,"
                + Base64.getEncoder().encodeToString(stylesheet.getBytes(StandardCharsets.UTF_8));
    }

    // Every stage shows its window so a -Dglass.platform=Win run goes through a real peer, and closes it again
    static WritableImage snapshot(Stage window, Scene scene) {
        window.setScene(scene);
        window.show();
        WritableImage image = scene.snapshot(null);
        window.close();
        return image;
    }

}
