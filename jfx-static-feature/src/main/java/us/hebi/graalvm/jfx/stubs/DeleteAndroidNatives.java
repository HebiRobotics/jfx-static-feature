package us.hebi.graalvm.jfx.stubs;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;

import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;

/**
 * Deletes the classes with JNI calls that are only implemented on Android. Otherwise,
 * the analysis keeps them reachable and would need C stubs.
 *
 * @author Florian Enner
 * @since 09 Oct 2026
 */
final class DeleteAndroidNatives {

    // jfx-mobile-libs ships the Android glass and Monocle to iOS builds as well. PlatformFactory names
    // the factory, which has no natives but keeps the rest of Monocle reachable

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.MonoclePlatformFactory", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_MonoclePlatformFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.android.Activity", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_Activity {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.android.SoftwareKeyboard", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_SoftwareKeyboard {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.AndroidScreen", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_AndroidScreen {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.C", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_C {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.DispmanAcceleratedScreen", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_DispmanAcceleratedScreen {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.DispmanCursor", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_DispmanCursor {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.DispmanScreen", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_DispmanScreen {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.EGL", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_EGL {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.EPDSystem$FbVarScreenInfo", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_EPDSystem_FbVarScreenInfo {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.EPDSystem", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_EPDSystem {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.LinuxSystem$FbVarScreenInfo", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_LinuxSystem_FbVarScreenInfo {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.LinuxSystem", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_LinuxSystem {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.MX6AcceleratedScreen", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_MX6AcceleratedScreen {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.Udev", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_Udev {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X$XButtonEvent", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X_XButtonEvent {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X$XClientMessageEvent", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X_XClientMessageEvent {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X$XColor", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X_XColor {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X$XDisplay", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X_XDisplay {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X$XEvent", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X_XEvent {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X$XMotionEvent", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X_XMotionEvent {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X$XSetWindowAttributes", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X_XSetWindowAttributes {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.X", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_X {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.MonocleGLFactory", onlyWith = {NotAndroid.class, ClassPresent.class})
    static final class Target_MonocleGLFactory {
    }

    /**
     * The Linux system calls behind LinuxSystem, which Android leaves out except for dlopen,
     * which nativeBridge.c provides
     */
    @TargetClass(className = "com.sun.glass.ui.monocle.LinuxSystem", onlyWith = {IsAndroid.class, ClassPresent.class})
    static final class Target_LinuxSystem_Android {

        @Delete
        native long open(String path, int flags);

        @Delete
        native int close(long fd);

        @Delete
        native long read(long fd, ByteBuffer buf, int position, int limit);

        @Delete
        native long sysconf(int name);

        @Delete
        native int EVIOCGABS(int type);

        @Delete
        native int ioctl(long fd, int request, long data);

        @Delete
        native int IOW(int type, int number, int size);

        @Delete
        native int errno();

        @Delete
        native String strerror(int errnum);

    }

    // Android ships no prism_sw, so the pipeline that the Linux jar brings has no natives behind it

    @Delete
    @TargetClass(className = "com.sun.prism.sw.SWPipeline", onlyWith = {IsAndroid.class, ClassPresent.class})
    static final class Target_SWPipeline {
    }

    static final class NotAndroid implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.ANDROID.class);
        }
    }

    static final class IsAndroid implements BooleanSupplier {
        public boolean getAsBoolean() {
            return Platform.includedIn(Platform.ANDROID.class);
        }
    }

}
