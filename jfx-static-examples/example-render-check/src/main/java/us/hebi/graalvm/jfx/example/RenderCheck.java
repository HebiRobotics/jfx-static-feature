package us.hebi.graalvm.jfx.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Renders a fixed set of scenes, by default on the headless glass platform, checks a few pixels of each and
 * writes the snapshots as PNGs. The {@code 2d} stage covers shapes, effects, a canvas, image decoding, a
 * control with its stylesheets, latin and arabic text and an FXML root, {@code 3d} the prism 3d path,
 * {@code titlebar} a preview extended window with a custom title bar, {@code alert} and {@code popup} the dialog and popup windows
 * with the labels the controls resource bundle carries, {@code robot} a window read back off the screen, and
 * {@code richtext} the incubator rich text controls including an RTF import. A stage whose conditional
 * feature is missing is skipped rather than failed.
 * The pipeline defaults to sw and follows a {@code -Dprism.order=d3d} (es2, mtl) argument, which a
 * native image accepts on the command line, so the same binary checks the GPU path. The glass
 * platform defaults to Headless the same way and {@code -Dglass.platform=Win} (Gtk, Mac) shows the scenes
 * in real windows for debugging.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
public class RenderCheck {

    private static final List<Scenario> scenarios = new ArrayList<>(List.of(
            new Scene2d(), new Scene3d(), new TitleBar(),
            new AlertDialog(), new PopupMenu(), new RobotCapture(), new RichText()));

    private static final Map<String, WritableImage> snapshots = new LinkedHashMap<>();
    private static final Map<String, String> skipped = new LinkedHashMap<>();
    private static Throwable failure;

    public static void main(String[] args) throws Exception {
        run(args);
    }

    /** Runs the stages above plus the ones a module built against a newer JavaFX adds */
    static void run(String[] args, Scenario... extra) throws Exception {
        scenarios.addAll(List.of(extra));

        // Defaults that a -D argument on the command line overrides, set before the toolkit starts
        setDefault("glass.platform", "Headless"); // Win, Gtk, Mac, Headless
        setDefault("prism.order", "sw"); // d3d, mtl, es2, sw
        setDefault("javafx.enablePreview", "true"); // StageStyle.EXTENDED and HeaderBar throw without it
        if (Files.isDirectory(Path.of("/sys/bus/platform/drivers/v3d"))) {
            // Mesa's V3D reports "Broadcom", which X11GLFactory's vendor list rejects
            setDefault("prism.forceGPU", "true");
        }

        String label = System.getProperty("os.name").split(" ")[0].toLowerCase() + "-" + System.getProperty("os.arch")
                + "-" + System.getProperty("prism.order").replace(' ', '_') + "-" + System.getProperty("glass.platform");
        String explicitOutput = args.length > 0 ? args[0] : null;
        long start = System.nanoTime();

        // launch() rather than Platform.startup(), the macOS main thread has to run the Cocoa loop
        Application.launch(App.class, args);

        if (failure != null) {
            failure.printStackTrace();
            System.exit(1);
        }

        List<String> problems = new ArrayList<>();
        for (Scenario stage : scenarios) {
            WritableImage image = snapshots.get(stage.name());
            if (image == null) {
                continue;
            }
            Path output = outputPath(explicitOutput, label, stage.name());
            Files.write(output, Png.encode(image));
            System.out.printf("wrote %s, %d bytes, %.0f ms, %s, %s%n", output.toAbsolutePath(), Files.size(output),
                    (System.nanoTime() - start) / 1e6, label, stage.name());
            stage.check(image, problems);
        }
        skipped.forEach((stage, reason) -> System.out.println("skipped " + stage + ": " + reason));

        problems.forEach(problem -> System.out.println("FAILED: " + problem));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    public static class App extends Application {

        @Override
        public void start(Stage primary) {
            try {
                for (Scenario stage : scenarios) {
                    String reason = stage.skipReason();
                    if (reason == null) {
                        snapshots.put(stage.name(), stage.render());
                    } else {
                        skipped.put(stage.name(), reason);
                    }
                }
            } catch (Throwable throwable) {
                failure = throwable;
            }
            Platform.exit();
        }

    }

    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    private static Path outputPath(String explicitOutput, String label, String stage) {
        if (explicitOutput == null) {
            return Path.of("render-check-" + label + "-" + stage + ".png");
        } else if (stage.equals("2d")) {
            return Path.of(explicitOutput);
        } else if (explicitOutput.endsWith(".png")) {
            return Path.of(explicitOutput.substring(0, explicitOutput.length() - ".png".length()) + "-" + stage + ".png");
        }
        return Path.of(explicitOutput + "-" + stage);
    }

}
