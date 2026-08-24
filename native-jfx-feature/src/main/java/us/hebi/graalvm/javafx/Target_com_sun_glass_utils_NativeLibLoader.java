package us.hebi.graalvm.javafx;

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
 * Substrate looks up the {@code JNI_OnLoad_<lib>} of a statically linked library with dlsym, but
 * hides every symbol the image does not export itself behind a linker version script, so on Linux
 * no JavaFX library can be loaded that way. Each load is therefore replaced by a direct call to the
 * library's initializer, which the linker resolves. This also drops the JNI version handshake,
 * which glass and glassgtk3 fail because they report JNI_VERSION_1_6 instead of the 1.8 the
 * specification requires from a statically linked library.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Platforms(Platform.LINUX.class)
@TargetClass(className = "com.sun.glass.utils.NativeLibLoader")
final class Target_com_sun_glass_utils_NativeLibLoader {

    @Alias //
    private static HashSet<String> loaded;

    @Substitute
    public static synchronized void loadLibrary(String libname) {
        if (loaded.add(libname)) {
            StaticJavaFXLibraries.initialize(libname);
        }
    }

    @Substitute
    public static synchronized void loadLibrary(String libname, List<String> dependencies) {
        if (loaded.add(libname)) {
            StaticJavaFXLibraries.initialize(libname);
        }
    }

}

@Platforms(Platform.LINUX.class)
final class StaticJavaFXLibraries {

    private static final int JNI_VERSION_1_8 = 0x00010008;

    static void initialize(String libname) {
        JNIJavaVM vm = JNIFunctionTables.singleton().getGlobalJavaVM();
        PointerBase reserved = WordFactory.nullPointer();
        int version = switch (libname) {
            case "glass" -> onLoadGlass(vm, reserved);
            case "glassgtk3" -> onLoadGlassGtk3(vm, reserved);
            case "prism_es2" -> onLoadPrismEs2(vm, reserved);
            case "prism_sw" -> onLoadPrismSw(vm, reserved);
            case "javafx_font" -> onLoadJavafxFont(vm, reserved);
            case "javafx_font_freetype" -> onLoadJavafxFontFreetype(vm, reserved);
            case "javafx_font_pango" -> onLoadJavafxFontPango(vm, reserved);
            case "javafx_iio" -> onLoadJavafxIio(vm, reserved);
            case "decora_sse" -> JNI_VERSION_1_8; // the only FX library without a JNI_OnLoad
            default -> throw new UnsatisfiedLinkError(libname + " is not linked into this image");
        };
        // The libraries return JNI_ERR when a class or member they look up is not registered for
        // JNI access, and leave their global ids null. Without this the failure only shows up as a
        // segfault in the first native call that uses one.
        if (version < 0) {
            throw new UnsatisfiedLinkError("JNI_OnLoad_" + libname + " failed, see the pending exception");
        }
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

    private StaticJavaFXLibraries() {
    }

}
