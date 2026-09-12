package us.hebi.graalvm.jfx.example;

import javafx.application.Application;

/**
 * Separate main class so the JavaFX launcher does not take the "toolkit not on module path" path.
 *
 * @author Florian Enner
 * @since 09 Sep 2026
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(JniMetadataCheck.class, args);
    }

}
