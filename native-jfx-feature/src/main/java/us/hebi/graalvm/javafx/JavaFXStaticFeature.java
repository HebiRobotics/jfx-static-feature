package us.hebi.graalvm.javafx;

import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

/**
 * Links the statically built JavaFX native libraries into the image and tells Substrate that their
 * JNI entry points are compiled in rather than loaded from a DLL.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
public class JavaFXStaticFeature implements Feature {

    /** JNI prefixes of the packages whose natives live in the static libraries. */
    private static final List<String> BUILTIN_PREFIXES = List.of(
            "com_sun_glass",
            "com_sun_javafx_embed",
            "com_sun_javafx_iio",
            "com_sun_javafx_iio_jpeg",
            "com_sun_prism_d3d",
            "com_sun_prism_es2",
            "com_sun_prism_mtl",
            "com_sun_prism_j2d",
            "com_sun_pisces",
            "com_sun_javafx_font",
            "com_sun_javafx_font_directwrite",
            "com_sun_javafx_font_coretext",
            "com_sun_javafx_tk_quantum",
            "com_sun_scenario_effect");

    /** Static JavaFX libraries in the static SDK's lib directory, without prefix and suffix. */
    private static final List<String> WINDOWS_LIBRARIES = List.of(
            "glass",
            "prism_common",
            "prism_d3d",
            "prism_sw",
            "decora_sse",
            "javafx_font",
            "javafx_iio");

    /** glass is only the gtk version chooser, the backend itself is glassgtk3. */
    private static final List<String> LINUX_LIBRARIES = List.of(
            "glass",
            "glassgtk3",
            "prism_common",
            "prism_es2",
            "prism_sw",
            "decora_sse",
            "javafx_font",
            "javafx_font_freetype",
            "javafx_font_pango",
            "javafx_iio");

    /** es2 is the default, prism_mtl is what -Dprism.order=mtl selects */
    private static final List<String> MACOS_LIBRARIES = List.of(
            "glass",
            "prism_common",
            "prism_es2",
            "prism_mtl",
            "prism_sw",
            "decora_sse",
            "javafx_font",
            "javafx_iio");

    /**
     * System libraries the static glass, prism and font archives reference. STATIC_BUILD only
     * drops the link flags from the archives, it does not vendor anything, so every one of these
     * stays a dynamic dependency of the image and the linker has to be told about it.
     */
    private static final List<String> WINDOWS_SYSTEM_LIBRARIES = List.of(
            "comdlg32", "comctl32", "imm32", "shell32", "ole32", "oleaut32", "gdi32", "user32",
            "urlmon", "winmm", "d3d9", "uiautomationcore", "dwrite", "d2d1", "windowscodecs",
            "usp10", "advapi32", "shlwapi", "dwmapi", "uuid", "msimg32", "propsys");

    /** @see #WINDOWS_SYSTEM_LIBRARIES */
    private static final List<String> LINUX_SYSTEM_LIBRARIES = List.of(
            "gtk-3", "gdk-3", "gdk_pixbuf-2.0", "glib-2.0", "gobject-2.0", "gio-2.0", "cairo",
            "pangoft2-1.0", "pango-1.0", "freetype",
            "X11", "Xtst", "Xxf86vm", "GL",
            "stdc++", "m");

    /**
     * The Objective-C runtime and the C++ standard library. The frameworks glass and prism link
     * against are not libraries, so they stay linker options in the SDK's native-image.properties.
     *
     * @see #WINDOWS_SYSTEM_LIBRARIES
     */
    private static final List<String> MACOS_SYSTEM_LIBRARIES = List.of("objc", "c++");

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("javafx.application.Application") != null;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        PlatformNativeLibrarySupport nativeLibrarySupport = PlatformNativeLibrarySupport.singleton();
        for (String prefix : BUILTIN_PREFIXES) {
            nativeLibrarySupport.addBuiltinPkgNativePrefix(prefix);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;

        NativeLibraries nativeLibraries = accessImpl.getNativeLibraries();
        for (String library : staticLibraries()) {
            nativeLibraries.addStaticJniLibrary(library);
        }
        for (String library : systemLibraries()) {
            nativeLibraries.addDynamicNonJniLibrary(library);
        }

        // Application.launch instantiates the concrete subclass through its no-argument
        // constructor, which the built-in JavaFXFeature does not register. The handler only
        // fires for subclasses the analysis already reached, so an unused launcher in the
        // same jar costs nothing.
        access.registerSubtypeReachabilityHandler((duringAnalysis, applicationClass) ->
                RuntimeReflection.register(applicationClass.getDeclaredConstructors()),
                access.findClassByName("javafx.application.Application"));
    }

    private static List<String> staticLibraries() {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return WINDOWS_LIBRARIES;
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return LINUX_LIBRARIES;
        } else if (Platform.includedIn(Platform.MACOS.class)) {
            return MACOS_LIBRARIES;
        }
        throw new UnsupportedOperationException("No static JavaFX libraries known for this platform");
    }

    private static List<String> systemLibraries() {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return WINDOWS_SYSTEM_LIBRARIES;
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return LINUX_SYSTEM_LIBRARIES;
        } else if (Platform.includedIn(Platform.MACOS.class)) {
            return MACOS_SYSTEM_LIBRARIES;
        }
        throw new UnsupportedOperationException("No system libraries known for this platform");
    }

}
