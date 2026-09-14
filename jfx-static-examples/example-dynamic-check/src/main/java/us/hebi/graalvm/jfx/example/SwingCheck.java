package us.hebi.graalvm.jfx.example;

import java.awt.Color;
import java.awt.Dimension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import us.hebi.graalvm.reachability.annotations.Reachable;

/**
 * Embeds a Swing panel in a JavaFX scene via SwingNode and verifies the Swing content reaches the
 * JavaFX snapshot, exercising the javafx.swing metadata (SwingNodeHelper forceInit, the
 * LightweightFrameWrapper interop) and the java2d -> prism int[] buffer bridge.
 *
 * @author Florian Enner
 * @since 14 Sep 2026
 */

/**
 * Probably a pretty general list that every Swing application will need (at minimum). Determined
 * by running the agent on all platforms and extracting the awt/swing classes.
 */
// AWT/java2d JNI lookups shared by all platforms, traced with the native-image agent
// AWT/java2d lookups shared across platforms, traced with the native-image agent
@Reachable(jniAccessible = true, classNames = {
        "java.awt.AWTEvent",
        "java.awt.AlphaComposite",
        "java.awt.Color",
        "java.awt.Component",
        "java.awt.Container",
        "java.awt.Cursor",
        "java.awt.Dialog",
        "java.awt.Dimension",
        "java.awt.DisplayMode",
        "java.awt.Font",
        "java.awt.Frame",
        "java.awt.GraphicsEnvironment",
        "java.awt.Insets",
        "java.awt.Point",
        "java.awt.Rectangle",
        "java.awt.SequencedEvent",
        "java.awt.Toolkit",
        "java.awt.Window",
        "java.awt.Window$Type",
        "java.awt.desktop.UserSessionEvent$Reason",
        "java.awt.event.ComponentEvent",
        "java.awt.event.InputEvent",
        "java.awt.event.KeyEvent",
        "java.awt.geom.AffineTransform",
        "java.awt.geom.GeneralPath",
        "java.awt.geom.Path2D",
        "java.awt.geom.Path2D$Float",
        "java.awt.geom.Point2D$Double",
        "java.awt.geom.Point2D$Float",
        "java.awt.geom.Rectangle2D$Double",
        "java.awt.geom.Rectangle2D$Float",
        "java.awt.image.BufferedImage",
        "java.awt.image.ColorModel",
        "java.awt.image.DirectColorModel",
        "java.awt.image.IndexColorModel",
        "java.awt.image.Raster",
        "java.awt.image.SampleModel",
        "java.awt.image.SinglePixelPackedSampleModel",
        "java.lang.Boolean",
        "java.lang.Class",
        "java.lang.Enum",
        "java.lang.Integer",
        "java.lang.Iterable",
        "java.lang.Long",
        "java.lang.Object",
        "java.lang.Runnable",
        "java.lang.String",
        "java.lang.System",
        "java.lang.Thread",
        "java.util.ArrayList",
        "java.util.Collections",
        "java.util.HashMap",
        "java.util.HashSet",
        "java.util.Iterator",
        "java.util.List",
        "java.util.Locale",
        "java.util.Map",
        "java.util.Set",
        "jdk.swing.interop.LightweightFrameWrapper",
        "sun.awt.AWTAutoShutdown",
        "sun.awt.EmbeddedFrame",
        "sun.awt.ExtendedKeyCodes",
        "sun.awt.FontDescriptor",
        "sun.awt.LightweightFrame",
        "sun.awt.PlatformFont",
        "sun.awt.SunHints",
        "sun.awt.SunToolkit",
        "sun.awt.TimedWindowEvent",
        "sun.awt.image.BufImgSurfaceData$ICMColorData",
        "sun.awt.image.IntegerComponentRaster",
        "sun.awt.image.SunVolatileImage",
        "sun.awt.image.VolatileSurfaceManager",
        "sun.font.CharToGlyphMapper",
        "sun.font.Font2D",
        "sun.font.FontStrike",
        "sun.font.GlyphList",
        "sun.font.PhysicalStrike",
        "sun.font.StrikeMetrics",
        "sun.font.TrueTypeFont",
        "sun.font.Type1Font",
        "sun.java2d.Disposer",
        "sun.java2d.InvalidPipeException",
        "sun.java2d.NullSurfaceData",
        "sun.java2d.SunGraphics2D",
        "sun.java2d.SurfaceData",
        "sun.java2d.loops.Blit",
        "sun.java2d.loops.BlitBg",
        "sun.java2d.loops.CompositeType",
        "sun.java2d.loops.DrawGlyphList",
        "sun.java2d.loops.DrawGlyphListAA",
        "sun.java2d.loops.DrawGlyphListLCD",
        "sun.java2d.loops.DrawLine",
        "sun.java2d.loops.DrawParallelogram",
        "sun.java2d.loops.DrawPath",
        "sun.java2d.loops.DrawPolygons",
        "sun.java2d.loops.DrawRect",
        "sun.java2d.loops.FillParallelogram",
        "sun.java2d.loops.FillPath",
        "sun.java2d.loops.FillRect",
        "sun.java2d.loops.FillSpans",
        "sun.java2d.loops.GraphicsPrimitive",
        "sun.java2d.loops.GraphicsPrimitiveMgr",
        "sun.java2d.loops.MaskBlit",
        "sun.java2d.loops.MaskFill",
        "sun.java2d.loops.ScaledBlit",
        "sun.java2d.loops.SurfaceType",
        "sun.java2d.loops.TransformHelper",
        "sun.java2d.loops.XORComposite",
        "sun.java2d.pipe.Region",
        "sun.java2d.pipe.RegionIterator",
})
// Windows toolkit and pipelines
@Reachable(jniAccessible = true, classNames = {
        "sun.awt.Win32GraphicsConfig",
        "sun.awt.Win32GraphicsDevice",
        "sun.awt.Win32GraphicsEnvironment",
        "sun.awt.windows.TranslucentWindowPainter$VIOptWindowPainter$1",
        "sun.awt.windows.WComponentPeer",
        "sun.awt.windows.WDesktopPeer",
        "sun.awt.windows.WDesktopProperties",
        "sun.awt.windows.WFontPeer",
        "sun.awt.windows.WFramePeer",
        "sun.awt.windows.WObjectPeer",
        "sun.awt.windows.WPanelPeer",
        "sun.awt.windows.WToolkit",
        "sun.awt.windows.WWindowPeer",
        "sun.java2d.d3d.D3DGraphicsDevice$1",
        "sun.java2d.d3d.D3DRenderQueue$1",
        "sun.java2d.d3d.D3DSurfaceData",
        "sun.java2d.d3d.D3DSurfaceData$1",
        "sun.java2d.windows.WindowsFlags",
})
// Linux X11 toolkit and XRender
@Reachable(jniAccessible = true, classNames = {
        "sun.awt.X11.XBaseWindow",
        "sun.awt.X11.XErrorHandlerUtil",
        "sun.awt.X11.XToolkit",
        "sun.awt.X11.XWindow",
        "sun.awt.X11GraphicsConfig",
        "sun.awt.X11GraphicsDevice",
        "sun.awt.X11InputMethodBase",
        "sun.java2d.xr.XRBackendNative",
        "sun.java2d.xr.XRSurfaceData",
})
// macOS OpenGL java2d pipeline
@Reachable(jniAccessible = true, classNames = {
        "sun.java2d.opengl.CGLGraphicsConfig",
        "sun.java2d.opengl.OGLRenderQueue",
        "sun.java2d.opengl.OGLSurfaceData",
})
// Reflective (non-JNI), shared
@Reachable(classNames = {
        "java.awt.SystemColor",
        "javax.swing.plaf.basic.BasicPanelUI",
        "javax.swing.plaf.metal.MetalRootPaneUI",
        "sun.java2d.marlin.DMarlinRenderingEngine",
})
// Windows
@Reachable(classNames = {
        "sun.awt.Symbol",
        "sun.awt.windows.WLightweightFramePeer",
        "sun.awt.windows.WingDings",
})
// Linux
@Reachable(classNames = {
        "sun.awt.X11.XContentWindow",
        "sun.awt.X11.XLightweightFramePeer",
})
// macOS
@Reachable(classNames = {
        "com.apple.laf.AquaKeyBindings",
        "com.apple.laf.AquaLookAndFeel",
        "com.apple.laf.AquaPanelUI",
        "com.apple.laf.AquaRootPaneUI",
        "sun.lwawt.macosx.LWCToolkit",
})
public class SwingCheck {

    private static final int WIDTH = 200, HEIGHT = 120;
    private static final java.awt.Color SWING_FILL = new java.awt.Color(0xF5, 0xA6, 0x23);
    private static final javafx.scene.paint.Color EXPECT = javafx.scene.paint.Color.web("#f5a623");

    static void run() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<WritableImage> shot = new AtomicReference<>();

        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            JPanel panel = new JPanel();
            panel.setBackground(SWING_FILL);
            panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
            swingNode.setContent(panel);
        });

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new Group(swingNode), WIDTH, HEIGHT));
            stage.show();
            // The Swing paint crosses the EDT and is uploaded on a later pulse, so settle first
            PauseTransition settle = new PauseTransition(Duration.millis(800));
            settle.setOnFinished(e -> {
                try {
                    shot.set(swingNode.getParent().getScene().getRoot().snapshot(null, null));
                } catch (Throwable t) {
                    failure.set("snapshot threw: " + t);
                } finally {
                    stage.close();
                    done.countDown();
                }
            });
            settle.play();
        });

        if (!done.await(20, TimeUnit.SECONDS)) {
            fail("swing round-trip timed out");
        }
        if (failure.get() != null) {
            fail(failure.get());
        }

        WritableImage image = shot.get();
        PixelReader px = image.getPixelReader();
        javafx.scene.paint.Color center = px.getColor(WIDTH / 2, HEIGHT / 2);
        if (!close(center, EXPECT)) {
            fail("center pixel " + fmt(center) + " is not the Swing fill " + fmt(EXPECT));
        }
        System.out.println("SwingNode content rendered, center=" + fmt(center));
        System.out.println("swing check passed");
        System.exit(0);
    }

    private static boolean close(javafx.scene.paint.Color a, javafx.scene.paint.Color b) {
        return Math.abs(a.getRed() - b.getRed()) < 0.08
                && Math.abs(a.getGreen() - b.getGreen()) < 0.08
                && Math.abs(a.getBlue() - b.getBlue()) < 0.08;
    }

    private static String fmt(javafx.scene.paint.Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    private static void fail(String message) {
        System.err.println("swing check failed: " + message);
        System.exit(1);
    }

}
