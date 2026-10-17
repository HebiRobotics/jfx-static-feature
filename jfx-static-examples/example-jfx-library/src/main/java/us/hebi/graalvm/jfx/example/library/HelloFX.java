package us.hebi.graalvm.jfx.example.library;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CEntryPoint;

/**
 * Label plus button as a shared library. A host creates the isolate and calls hellofx_launch on a thread of its
 * own; on Android after handing glass its ANativeWindow, on iOS from a running UIApplication.
 *
 * @author Florian Enner
 * @since 17 Oct 2026
 */
public class HelloFX extends Application {

    @Override
    public void start(Stage stage) {
        Label label = new Label("Hello JavaFX " + System.getProperty("javafx.runtime.version") + " on " + System.getProperty("os.name"));
        Button button = new Button("Tap me");
        int[] taps = {0};
        button.setOnAction(event -> label.setText("Tapped " + (++taps[0]) + " times"));

        VBox root = new VBox(16, label, button);
        root.setAlignment(Pos.CENTER);

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setScene(new Scene(root, bounds.getWidth(), bounds.getHeight()));
        stage.show();
    }

    @CEntryPoint(name = "hellofx_create_isolate", builtin = CEntryPoint.Builtin.CREATE_ISOLATE)
    static native IsolateThread createIsolate();

    // Blocks until the application exits
    @CEntryPoint(name = "hellofx_launch")
    static void launch(IsolateThread thread) {
        if (Platform.includedIn(Platform.ANDROID.class)) {
            // Monocle renders into the host's ANativeWindow, glass picks no platform by itself when it is not Linux
            System.setProperty("javafx.platform", "android");
            System.setProperty("glass.platform", "Monocle");
            System.setProperty("monocle.platform", "Android");
            System.setProperty("use.egl", "true");
        }
        Application.launch(HelloFX.class);
    }

}
