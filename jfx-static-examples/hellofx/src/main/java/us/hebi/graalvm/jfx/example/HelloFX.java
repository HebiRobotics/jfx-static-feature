package us.hebi.graalvm.jfx.example;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Label plus button, enough to exercise the toolkit, the D3D pipeline, DirectWrite text and modena.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
public class HelloFX extends Application {

    @Override
    public void start(Stage stage) {
        Label label = new Label("Hello JavaFX 26 static");
        Button button = new Button("Click me");
        int[] clicks = {0};
        button.setOnAction(event -> label.setText("Clicked " + (++clicks[0]) + " times"));

        VBox root = new VBox(16, label, button);
        root.setAlignment(Pos.CENTER);

        stage.setTitle("HelloFX Native");
        stage.setScene(new Scene(root, 420, 220));
        stage.show();
    }

}
