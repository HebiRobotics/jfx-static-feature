package us.hebi.graalvm.jfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

/**
 * Links the statically built JavaFX native libraries into the image and tells Substrate that their
 * JNI entry points are compiled in rather than loaded from a DLL.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
public class JfxStaticFeature implements Feature {

    /** JNI prefixes of the packages whose natives live in the static libraries. */
    private static final List<String> BUILTIN_PREFIXES = List.of(
            "com_sun_glass",
            "com_sun_javafx",
            "com_sun_pisces",
            "com_sun_prism",
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
     * against are not libraries, so they go through {@link #MACOS_FRAMEWORKS} instead.
     *
     * @see #WINDOWS_SYSTEM_LIBRARIES
     */
    private static final List<String> MACOS_SYSTEM_LIBRARIES = List.of("objc", "c++");

    /** The frameworks buildSrc/mac.gradle links the dynamic build against, plus what the font code and the run loop need. */
    private static final List<String> MACOS_FRAMEWORKS = List.of(
            "AppKit", "ApplicationServices", "Carbon", "OpenGL", "QuartzCore", "Security",
            "Network", "Metal", "CoreText", "CoreGraphics", "CoreFoundation");

    /** Resource root jfx-static-libs keeps every platform's archives under. */
    private static final String NATIVES_RESOURCES = "/us/hebi/graalvm/jfx/natives/";

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (access.findClassByName("javafx.application.Application") == null) {
            return false;
        }
        // Without the archives the image would fall back to loading FX from a DLL, which every
        // substitution in this jar breaks, so stay out of the way instead.
        String platform = platformName();
        if (platform == null) {
            System.out.println("JfxStaticFeature: no static JavaFX libraries known for this platform, staying off");
            return false;
        }
        if (JfxStaticFeature.class.getResource(nativesResource(staticLibraries().get(0))) == null) {
            System.out.println("JfxStaticFeature: no jfx-static-libs jar for " + platform + " on the class path, staying off");
            return false;
        }
        return true;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        PlatformNativeLibrarySupport nativeLibrarySupport = PlatformNativeLibrarySupport.singleton();
        Method addBuiltinPrefix = builtinPrefixMethod();
        for (String prefix : BUILTIN_PREFIXES) {
            try {
                addBuiltinPrefix.invoke(nativeLibrarySupport, prefix);
            } catch (ReflectiveOperationException roe) {
                throw new IllegalStateException("Could not register the builtin JNI prefix " + prefix, roe);
            }
        }
    }

    /** GraalVM 25.3 renamed addBuiltinPkgNativePrefix to addBuiltinNativePrefix, and both are supported. */
    private static Method builtinPrefixMethod() {
        for (String name : List.of("addBuiltinNativePrefix", "addBuiltinPkgNativePrefix")) {
            try {
                return PlatformNativeLibrarySupport.class.getMethod(name, String.class);
            } catch (NoSuchMethodException nsme) {
                // the other spelling
            }
        }
        throw UserError.abort("This GraalVM has no PlatformNativeLibrarySupport method to register a builtin JNI prefix with");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;

        NativeLibraries nativeLibraries = accessImpl.getNativeLibraries();
        staticLibraryDirectory = extractStaticLibraries(nativeLibraries.tempDirectory);
        nativeLibraries.getLibraryPaths().add(staticLibraryDirectory.toString());

        for (String library : staticLibraries()) {
            nativeLibraries.addStaticJniLibrary(library);
        }
        for (String library : systemLibraries()) {
            nativeLibraries.addDynamicNonJniLibrary(library);
        }

        // Application.launch instantiates the concrete subclass through its no-argument
        // constructor, and the no-argument launch(String[]) overload looks the caller up by name
        // with Class.forName first. Neither is registered by the built-in JavaFXFeature. The
        // handler only fires for subclasses the analysis already reached, so an unused launcher in
        // the same jar costs nothing.
        access.registerSubtypeReachabilityHandler((duringAnalysis, applicationClass) -> {
            RuntimeReflection.register(applicationClass);
            RuntimeReflection.register(applicationClass.getDeclaredConstructors());
        }, access.findClassByName("javafx.application.Application"));
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        List<String> options = linkerOptions();
        if (options.isEmpty()) {
            return;
        }
        ((FeatureImpl.BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
            options.forEach(linkerInvocation::addNativeLinkerOption);
            return linkerInvocation;
        });
    }

    /** Platform linker syntax that has no NativeLibraries API behind it. */
    private List<String> linkerOptions() {
        if (Platform.includedIn(Platform.LINUX.class)) {
            // glassgtk3 still references g_thread_init behind a glib version check no current glib takes.
            // The value has to be absolute: ld accepts "=abort" only where abort is already defined,
            // which holds on x86_64 by accident of link order and never on aarch64.
            return List.of("-Wl,--defsym,g_thread_init=0");
        } else if (Platform.includedIn(Platform.MACOS.class)) {
            // The GlassWindow categories define no symbol the linker goes looking for and are dropped otherwise
            List<String> options = new ArrayList<>();
            options.add("-Wl,-force_load," + staticLibraryDirectory.resolve(staticLibraryFileName("glass")));
            for (String framework : MACOS_FRAMEWORKS) {
                options.add("-Wl,-framework," + framework);
            }
            return options;
        }
        return List.of();
    }

    /**
     * Unpacks the platform's archives out of the jfx-static-libs jar and returns the
     * directory to add to the library path, so that a consumer needs no -H:CLibraryPath.
     */
    private static Path extractStaticLibraries(Path tempDirectory) {
        Path directory = tempDirectory.resolve("javafx-static");
        try {
            Files.createDirectories(directory);
            for (String library : staticLibraries()) {
                String fileName = staticLibraryFileName(library);
                try (InputStream resource = JfxStaticFeature.class.getResourceAsStream(nativesResource(library))) {
                    if (resource == null) {
                        throw new IOException("Missing " + fileName + " in the jfx-static-libs jar");
                    }
                    Files.copy(resource, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not extract the static JavaFX libraries", ioe);
        }
        return directory;
    }

    private static String nativesResource(String library) {
        return NATIVES_RESOURCES + platformName() + "/" + staticLibraryFileName(library);
    }

    /** Matches NativeLibraries::getStaticLibraryName, which is what the linker looks for. */
    private static String staticLibraryFileName(String library) {
        return Platform.includedIn(Platform.WINDOWS.class) ? library + ".lib" : "lib" + library + ".a";
    }

    /** Directory name of this platform's archives, null where the feature has none. Spelled the way
     * Gluon Substrate spells its targets, so one vocabulary covers both. */
    private static String platformName() {
        if (Platform.includedIn(Platform.WINDOWS_AMD64.class)) {
            return "windows-x86_64";
        } else if (Platform.includedIn(Platform.LINUX_AMD64.class)) {
            return "linux-x86_64";
        } else if (Platform.includedIn(Platform.LINUX_AARCH64.class)) {
            return "linux-aarch64";
        } else if (Platform.includedIn(Platform.DARWIN_AMD64.class)) {
            return "darwin-x86_64";
        } else if (Platform.includedIn(Platform.DARWIN_AARCH64.class)) {
            return "darwin-aarch64";
        }
        return null;
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

    /** Where beforeAnalysis unpacked the archives, needed again for the macOS -force_load. */
    private Path staticLibraryDirectory;

}
