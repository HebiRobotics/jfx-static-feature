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
 * Links the static JavaFX libraries into the image and tells Substrate that their JNI entry
 * points are builtin rather than loaded from a DLL.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
public class JfxStaticFeature implements Feature {

    // JNI prefixes that resolve into the static libraries
    private static final List<String> BUILTIN_PREFIXES = List.of(
            "com_sun_glass",
            "com_sun_javafx",
            "com_sun_pisces",
            "com_sun_prism",
            "com_sun_scenario_effect");

    // JavaFX libraries in the jfx-static-libs, without prefix and suffix
    private static final List<String> WINDOWS_LIBRARIES = List.of(
            "glass",
            "prism_common",
            "prism_d3d",
            "prism_sw",
            "decora_sse",
            "javafx_font",
            "javafx_iio");

    // glass is only the gtk version chooser, the backend is glassgtk3
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

    // es2 is the default, -Dprism.order=mtl selects prism_mtl
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

    private static final List<String> LINUX_SYSTEM_LIBRARIES = List.of(
            "gtk-3", "gdk-3", "gdk_pixbuf-2.0", "glib-2.0", "gobject-2.0", "gio-2.0", "cairo",
            "pangoft2-1.0", "pango-1.0", "freetype",
            "X11", "Xtst", "Xxf86vm", "GL",
            "stdc++", "m");

    // The macOS frameworks are not libraries and go through linkerOptions()
    private static final List<String> MACOS_SYSTEM_LIBRARIES = List.of("objc", "c++");

    // What buildSrc/mac.gradle links against, plus CoreText/CoreGraphics for fonts and CoreFoundation for the run loop
    private static final List<String> MACOS_FRAMEWORKS = List.of(
            "AppKit", "ApplicationServices", "Carbon", "OpenGL", "QuartzCore", "Security",
            "Network", "Metal", "CoreText", "CoreGraphics", "CoreFoundation");

    // Where jfx-static-libs keeps the archives of every platform
    private static final String NATIVES_RESOURCES = "/us/hebi/graalvm/jfx/natives/";

    // Where beforeAnalysis unpacked the archives, needed again for the macOS -force_load
    private Path staticLibraryDirectory;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (access.findClassByName("javafx.application.Application") == null) {
            return false;
        }
        // Without archives the substitutions would break a dynamic FX image, so stay off
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

    // GraalVM 25.3 renamed addBuiltinPkgNativePrefix to addBuiltinNativePrefix
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

        // Application.launch() needs the subclass by name and its no-arg constructor, which the built-in
        // JavaFXFeature does not register. Only reachable types show up here, so we don't add extra classes.
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

    // Linker syntax that has no NativeLibraries API behind it
    private List<String> linkerOptions() {
        if (Platform.includedIn(Platform.LINUX.class)) {
            // glassgtk3 references g_thread_init, which glib dropped. Absolute value because =abort only works on x86_64 by link order
            return List.of("-Wl,--defsym,g_thread_init=0");
        } else if (Platform.includedIn(Platform.MACOS.class)) {
            // The GlassWindow categories define no symbol the linker looks for and get dropped otherwise
            List<String> options = new ArrayList<>();
            options.add("-Wl,-force_load," + staticLibraryDirectory.resolve(staticLibraryFileName("glass")));
            for (String framework : MACOS_FRAMEWORKS) {
                options.add("-Wl,-framework," + framework);
            }
            return options;
        }
        return List.of();
    }

    // Unpacks the platform's archives so a consumer needs no -H:CLibraryPath
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

    // Matches NativeLibraries::getStaticLibraryName
    private static String staticLibraryFileName(String library) {
        return Platform.includedIn(Platform.WINDOWS.class) ? library + ".lib" : "lib" + library + ".a";
    }

    // Spelled like Gluon Substrate's targets, null where there are no archives
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

}
