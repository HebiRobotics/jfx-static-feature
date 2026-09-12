package us.hebi.graalvm.jfx.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * A centered label showing the linked versions. The interesting part is the bundled
 * reflect-config.json that unconditionally registers every OS's JNI classes: the build
 * only links if the feature keeps the wrong-platform natives away from the linker.
 *
 * @author Florian Enner
 * @since 09 Sep 2026
 */
public class JniMetadataCheck extends Application {

    @Override
    public void start(Stage stage) {
        String info = String.format("JavaFX %s%nJava %s (%s)%n%s %s",
                System.getProperty("javafx.runtime.version"),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"));
        System.out.println(info);
        stage.setTitle("JNI Metadata Check");
        stage.setScene(new Scene(new StackPane(new Label(info)), 420, 220));
        stage.show();
    }

}
