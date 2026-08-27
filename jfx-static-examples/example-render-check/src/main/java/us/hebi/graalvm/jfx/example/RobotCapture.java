package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Reads a shown window back off the screen with the robot rather than out of the scene graph. The headless
 * robot serves the same calls from its own framebuffer, so this runs without a display as well.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
class RobotCapture implements Scenario {

    private static final int WINDOW_X = 120, WINDOW_Y = 90, WIDTH = 300, HEIGHT = 200;
    // Green in the capture means the screen shows the window, anything else is what was there instead
    private static final Color FILL = Color.web("#27ae60");
    // The window reaches the screen a pulse or two after show(), and the robot reads the screen, not the scene
    private static final int PAINT_FRAMES = 10;

    private Robot robot;
    private Color mousePixel;

    @Override
    public String name() {
        return "robot";
    }

    @Override
    public String skipReason() {
        // Only the sw pipeline uploads into the headless framebuffer, a GPU pipeline leaves it empty
        String platform = System.getProperty("glass.platform");
        String order = System.getProperty("prism.order");
        if (platform.equals("Headless") && !order.equals("sw")) {
            return "the headless framebuffer is empty on prism.order=" + order;
        }
        try {
            robot = new Robot();
            return null;
        } catch (Throwable unavailable) {
            return "javafx.scene.robot.Robot: " + unavailable;
        }
    }

    @Override
    public WritableImage render() {
        Stage window = new Stage();
        // Undecorated so the scene starts at the window origin and screen coordinates match the constants
        window.initStyle(StageStyle.UNDECORATED);
        window.setX(WINDOW_X);
        window.setY(WINDOW_Y);
        window.setAlwaysOnTop(true);
        Region root = new Region();
        root.setBackground(Background.fill(FILL));
        window.setScene(new Scene(root, WIDTH, HEIGHT, FILL));
        window.show();
        waitForFrames(PAINT_FRAMES);

        // A window manager may place the window elsewhere, so read back where it ended up
        int x = (int) window.getX();
        int y = (int) window.getY();
        int centerX = x + WIDTH / 2;
        int centerY = y + HEIGHT / 2;
        robot.mouseMove(centerX, centerY);
        mousePixel = robot.getPixelColor(centerX, centerY);
        // The four argument overload scales to fit, so the capture is in logical pixels like every snapshot
        WritableImage image = robot.getScreenCapture(null, x, y, WIDTH, HEIGHT);
        window.close();
        return image;
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        if (!Pixels.checkSize(problems, image, WIDTH, HEIGHT)) {
            return;
        }
        if (!Pixels.matches(mousePixel, FILL, Pixels.SOLID)) {
            problems.add("the screen under the mouse is " + mousePixel + " rather than " + FILL);
        }
        Pixels.checkPixel(problems, image, WIDTH / 2, HEIGHT / 2, FILL, "the center of the screen capture");
    }

    // A nested event loop keeps the pulses running while the fx thread waits for the window to be painted
    private static void waitForFrames(int frames) {
        Object key = new Object();
        new AnimationTimer() {
            int remaining = frames;

            @Override
            public void handle(long now) {
                if (--remaining <= 0) {
                    stop();
                    Platform.exitNestedEventLoop(key, null);
                }
            }
        }.start();
        Platform.enterNestedEventLoop(key);
    }

}
