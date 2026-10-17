package us.hebi.graalvm.jfx.stubs;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;

import java.util.function.BooleanSupplier;

/**
 * Deletes the classes with JNI calls that are only implemented on Linux, or on Linux and Android. Otherwise,
 * the analysis keeps them reachable and would need C stubs.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
final class DeleteLinuxNatives {

    @Delete
    @TargetClass(className = "com.sun.javafx.font.freetype.FTFactory", onlyWith = {NotLinuxNorAndroid.class, ClassPresent.class})
    static final class Target_FTFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.FontConfigManager", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_FontConfigManager {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.freetype.OSFreetype", onlyWith = {NotLinuxNorAndroid.class, ClassPresent.class})
    static final class Target_OSFreetype {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.freetype.OSPango", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_OSPango {
    }

    /**
     * Declared in OSFreetype but implemented in no native archive on any OS
     */
    @TargetClass(className = "com.sun.javafx.font.freetype.OSFreetype", onlyWith = {IsLinux.class, ClassPresent.class})
    static final class Target_OSFreetype_Linux {

        @Delete
        static native int FT_Get_Char_Index(long face, long charcode);

    }

    // The Linux OpenJFX jar that Android builds use ships the GTK glass and the X11 es2 classes
    // without Android natives, and android.gradle's includeGTK follows the build host the same way

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkApplication", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkApplication {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkWindow", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkWindow {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkView", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkView {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkSystemClipboard", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkSystemClipboard {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkDnDClipboard", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkDnDClipboard {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkTimer", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkTimer {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkPixels", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkPixels {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkCursor", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkCursor {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.gtk.GtkCommonDialogs", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_GtkCommonDialogs {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.X11GLFactory", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_X11GLFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.X11GLContext", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_X11GLContext {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.X11GLDrawable", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_X11GLDrawable {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.X11GLPixelFormat", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_X11GLPixelFormat {
    }

    // Android is Linux to GraalVM and needs FreeType as well
    static final class NotLinuxNorAndroid implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.LINUX.class);
        }
    }

    // Android is Linux to GraalVM, but its archives have no fontconfig, pango, GTK or X11
    static final class NotLinux implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.ANDROID.class);
        }
    }

    static final class IsLinux implements BooleanSupplier {
        public boolean getAsBoolean() {
            return Platform.includedIn(Platform.LINUX.class);
        }
    }

}
