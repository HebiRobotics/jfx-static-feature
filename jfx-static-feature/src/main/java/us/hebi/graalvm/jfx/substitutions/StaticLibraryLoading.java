package us.hebi.graalvm.jfx.substitutions;

import java.util.HashSet;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jni.functions.JNIFunctionTables;
import com.oracle.svm.core.jni.headers.JNIJavaVM;

/**
 * Substrate finds the JNI_OnLoad of a statically linked library with dlsym, which the linker
 * version script on Linux and the exported symbols list on macOS hide, and rejects glass everywhere
 * because it reports JNI 1.2/1.6/1.4 instead of the 1.8 the spec requires from a static library.
 * Each load calls the initializer directly instead, on Windows as well so all platforms share one path.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
public final class StaticLibraryLoading {

    private static final int JNI_VERSION_1_8 = 0x00010008;

    // TODO: report upstream, native-glass/win/Utils.cpp lacks the STATIC_BUILD handshake the other libraries have
    @TargetClass(className = "com.sun.glass.utils.NativeLibLoader")
    static final class Target_NativeLibLoader {

        @Alias
        private static HashSet<String> loaded;

        @Substitute
        public static synchronized void loadLibrary(String libname) {
            if (loaded.add(libname)) {
                initialize(libname);
            }
        }

        @Substitute
        public static synchronized void loadLibrary(String libname, List<String> dependencies) {
            if (loaded.add(libname)) {
                initialize(libname);
            }
        }

    }

    static void initialize(String libname) {
        JNIJavaVM vm = JNIFunctionTables.singleton().getGlobalJavaVM();
        PointerBase reserved = WordFactory.nullPointer();
        int version;
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            version = WindowsOnLoad.onLoad(libname, vm, reserved);
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            version = LinuxOnLoad.onLoad(libname, vm, reserved);
        } else {
            version = MacOnLoad.onLoad(libname, vm, reserved);
        }
        // The libraries return JNI_ERR when a class or member they look up is not registered for
        // JNI access, and leave their global ids null. Without this the failure only shows up as a
        // segfault in the first native call that uses one.
        if (version < 0) {
            throw new UnsatisfiedLinkError("JNI_OnLoad_" + libname + " failed, see the pending exception");
        }
    }

    @Platforms(Platform.WINDOWS.class)
    private static final class WindowsOnLoad {

        static int onLoad(String libname, JNIJavaVM vm, PointerBase reserved) {
            return switch (libname) {
                case "glass" -> onLoadGlass(vm, reserved);
                case "prism_d3d" -> onLoadPrismD3d(vm, reserved);
                case "prism_sw" -> onLoadPrismSw(vm, reserved);
                case "javafx_font" -> onLoadJavafxFont(vm, reserved);
                case "javafx_iio" -> onLoadJavafxIio(vm, reserved);
                case "decora_sse" -> JNI_VERSION_1_8; // no JNI_OnLoad
                default -> throw new UnsatisfiedLinkError(libname + " is not linked into this image");
            };
        }

        @CFunction("JNI_OnLoad_glass")
        private static native int onLoadGlass(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_prism_d3d")
        private static native int onLoadPrismD3d(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_prism_sw")
        private static native int onLoadPrismSw(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_font")
        private static native int onLoadJavafxFont(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_iio")
        private static native int onLoadJavafxIio(JNIJavaVM vm, PointerBase reserved);

        private WindowsOnLoad() {
        }

    }

    @Platforms(Platform.LINUX.class)
    private static final class LinuxOnLoad {

        static int onLoad(String libname, JNIJavaVM vm, PointerBase reserved) {
            return switch (libname) {
                case "glass" -> onLoadGlass(vm, reserved);
                case "glassgtk3" -> onLoadGlassGtk3(vm, reserved);
                case "prism_es2" -> onLoadPrismEs2(vm, reserved);
                case "prism_sw" -> onLoadPrismSw(vm, reserved);
                case "javafx_font" -> onLoadJavafxFont(vm, reserved);
                case "javafx_font_freetype" -> onLoadJavafxFontFreetype(vm, reserved);
                case "javafx_font_pango" -> onLoadJavafxFontPango(vm, reserved);
                case "javafx_iio" -> onLoadJavafxIio(vm, reserved);
                case "decora_sse" -> JNI_VERSION_1_8; // no JNI_OnLoad
                default -> throw new UnsatisfiedLinkError(libname + " is not linked into this image");
            };
        }

        @CFunction("JNI_OnLoad_glass")
        private static native int onLoadGlass(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_glassgtk3")
        private static native int onLoadGlassGtk3(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_prism_es2")
        private static native int onLoadPrismEs2(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_prism_sw")
        private static native int onLoadPrismSw(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_font")
        private static native int onLoadJavafxFont(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_font_freetype")
        private static native int onLoadJavafxFontFreetype(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_font_pango")
        private static native int onLoadJavafxFontPango(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_iio")
        private static native int onLoadJavafxIio(JNIJavaVM vm, PointerBase reserved);

        private LinuxOnLoad() {
        }

    }

    @Platforms(Platform.MACOS.class)
    private static final class MacOnLoad {

        static int onLoad(String libname, JNIJavaVM vm, PointerBase reserved) {
            return switch (libname) {
                case "glass" -> onLoadGlass(vm, reserved);
                case "prism_es2" -> onLoadPrismEs2(vm, reserved);
                case "prism_sw" -> onLoadPrismSw(vm, reserved);
                case "javafx_font" -> onLoadJavafxFont(vm, reserved);
                case "javafx_iio" -> onLoadJavafxIio(vm, reserved);
                case "decora_sse", "prism_mtl" -> JNI_VERSION_1_8; // no JNI_OnLoad
                default -> throw new UnsatisfiedLinkError(libname + " is not linked into this image");
            };
        }

        @CFunction("JNI_OnLoad_glass")
        private static native int onLoadGlass(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_prism_es2")
        private static native int onLoadPrismEs2(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_prism_sw")
        private static native int onLoadPrismSw(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_font")
        private static native int onLoadJavafxFont(JNIJavaVM vm, PointerBase reserved);

        @CFunction("JNI_OnLoad_javafx_iio")
        private static native int onLoadJavafxIio(JNIJavaVM vm, PointerBase reserved);

        private MacOnLoad() {
        }

    }

    private StaticLibraryLoading() {
    }

}
