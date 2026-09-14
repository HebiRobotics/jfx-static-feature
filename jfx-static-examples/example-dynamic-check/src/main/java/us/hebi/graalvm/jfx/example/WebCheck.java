package us.hebi.graalvm.jfx.example;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Exercises the metadata paths of javafx.web in a static image: jfxwebkit links and loads, a page
 * renders through the prism graphics bridge, the DOM/JS callback path returns a value, and an
 * HTMLEditor builds its skin. Each check prints a line and any failure exits non-zero.
 *
 * @author Florian Enner
 * @since 14 Sep 2026
 */
public class WebCheck {

    private static final int WIDTH = 200, HEIGHT = 120;
    // A solid page: the body fills the view, a div carries a known color the snapshot samples.
    private static final Color FILL = Color.web("#f5a623");
    private static final String PAGE =
            "<html><body style='margin:0'>"
            + "<div id='box' style='width:200px;height:120px;background:#f5a623'></div>"
            + "</body></html>";

    static void run() throws Exception {
        renderAndDom();
        htmlEditor();

        System.out.println("web check passed");
        System.exit(0);
    }

    // WebView render (prism graphics bridge) + DOM/JS callback (twkGetDocument -> NodeImpl, JSObject)
    private static void renderAndDom() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<WritableImage> shot = new AtomicReference<>();
        AtomicReference<Object> domResult = new AtomicReference<>();

        Platform.runLater(() -> {
            WebView view = new WebView();
            view.setPrefSize(WIDTH, HEIGHT);
            WebEngine engine = view.getEngine();
            engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == Worker.State.FAILED) {
                    failure.set("page load failed");
                    done.countDown();
                } else if (state == Worker.State.SUCCEEDED) {
                    try {
                        // DOM + JS bridge: reaches through twkGetDocument -> NodeImpl and back as a value
                        domResult.set(engine.executeScript("document.getElementById('box').clientWidth"));
                        Stage stage = new Stage();
                        stage.setScene(new Scene(new Group(view), WIDTH, HEIGHT));
                        stage.show();
                        // WebView paints on a later pulse, so settle before snapshotting
                        PauseTransition settle = new PauseTransition(Duration.millis(500));
                        settle.setOnFinished(e -> {
                            try {
                                shot.set(view.snapshot(null, null));
                            } catch (Throwable t) {
                                failure.set("snapshot threw: " + t);
                            } finally {
                                stage.close();
                                done.countDown();
                            }
                        });
                        settle.play();
                    } catch (Throwable t) {
                        failure.set("dom/render threw: " + t);
                        done.countDown();
                    }
                }
            });
            engine.loadContent(PAGE);
        });

        if (!done.await(20, TimeUnit.SECONDS)) {
            fail("web render/dom timed out");
        }
        if (failure.get() != null) {
            fail(failure.get());
        }
        if (!"200".equals(String.valueOf(domResult.get()))) {
            fail("DOM query returned " + domResult.get() + ", expected 200");
        }
        System.out.println("render + DOM ok, box clientWidth=" + domResult.get());

        WritableImage image = shot.get();
        PixelReader px = image.getPixelReader();
        Color center = px.getColor(WIDTH / 2, HEIGHT / 2);
        if (!close(center, FILL)) {
            fail("rendered center pixel " + center + " is not the page fill " + FILL);
        }
        System.out.println("snapshot pixel ok, center=" + fmt(center));
    }

    // HTMLEditor builds HTMLEditorSkin (string -fx-skin) and the ScrollBarWidget forceInit path
    private static void htmlEditor() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                HTMLEditor editor = new HTMLEditor();
                editor.setHtmlText("<p>hello static web</p>");
                Stage stage = new Stage();
                stage.setScene(new Scene(editor, 400, 260));
                stage.show();
                if (editor.getSkin() == null) {
                    failure.set("HTMLEditor has no skin");
                }
                stage.close();
            } catch (Throwable t) {
                failure.set("HTMLEditor threw: " + t);
            }
            done.countDown();
        });
        if (!done.await(15, TimeUnit.SECONDS)) {
            fail("HTMLEditor timed out");
        }
        if (failure.get() != null) {
            fail(failure.get());
        }
        System.out.println("HTMLEditor skin ok");
    }

    private static boolean close(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) < 0.08
                && Math.abs(a.getGreen() - b.getGreen()) < 0.08
                && Math.abs(a.getBlue() - b.getBlue()) < 0.08;
    }

    private static String fmt(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    private static void fail(String message) {
        System.err.println("web check failed: " + message);
        System.exit(1);
    }

}
