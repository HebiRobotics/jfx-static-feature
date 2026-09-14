package us.hebi.graalvm.jfx.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * One native image for all of the shared-library checks, so the analysis runs once instead of once
 * per module. The first argument picks the check; each runs in its own process and exits with its
 * own status, so a failure still names the module that broke.
 *
 * @author Florian Enner
 * @since 15 Sep 2026
 */
public class Launcher extends Application {

    public static void main(String[] args) {
        // launch() rather than Platform.startup(), the macOS main thread has to run the Cocoa loop
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);
        String check = getParameters().getRaw().stream().findFirst().orElse("");
        // The check bodies block on latches, so they run off the FX thread
        Thread worker = new Thread(() -> {
            try {
                switch (check) {
                    case "media" -> MediaCheck.run();
                    case "web" -> WebCheck.run();
                    case "swing" -> SwingCheck.run();
                    default -> {
                        System.err.println("usage: check-dynamic <media|web|swing>");
                        System.exit(2);
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        }, check + "-check");
        worker.setDaemon(true);
        worker.start();
    }

}
