package us.hebi.graalvm.jfx;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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

    // es2 only, buildSrc/ios.gradle has no decora_sse and no mtl
    private static final List<String> IOS_LIBRARIES = List.of(
            "glass",
            "prism_common",
            "prism_es2",
            "prism_sw",
            "javafx_font",
            "javafx_iio");

    // Monocle glass and es2. javafx_font is an empty archive on Android, javafx_font_freetype does the fonts
    private static final List<String> ANDROID_LIBRARIES = List.of(
            "glass_monocle",
            "prism_common",
            "prism_es2_monocle",
            "decora_sse",
            "javafx_font_freetype",
            "javafx_iio");

    // Android has no public FreeType, so the libs jar ships it next to the JavaFX archives
    private static final List<String> ANDROID_BUNDLED_LIBRARIES = List.of("freetype");

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

    private static final List<String> ANDROID_SYSTEM_LIBRARIES = List.of("EGL", "GLESv2", "android", "log", "m");

    // The macOS frameworks are not libraries and go through linkerOptions()
    private static final List<String> MACOS_SYSTEM_LIBRARIES = List.of("objc", "c++");

    // What buildSrc/mac.gradle links against, plus CoreText/CoreGraphics for fonts and CoreFoundation for the run loop
    private static final List<String> MACOS_FRAMEWORKS = List.of(
            "AppKit", "ApplicationServices", "Carbon", "OpenGL", "QuartzCore", "Security",
            "Network", "Metal", "CoreText", "CoreGraphics", "CoreFoundation");

    // What the iOS glass, prism_es2, font and iio sources import
    private static final List<String> IOS_FRAMEWORKS = List.of(
            "UIKit", "Foundation", "CoreFoundation", "CoreGraphics", "CoreText", "QuartzCore",
            "OpenGLES", "ImageIO", "MobileCoreServices");

    // Where jfx-static-libs and jfx-mobile-libs keep the archives of every platform
    private static final String LIBS_RESOURCES = "/us/hebi/graalvm/jfx/libs/";

    // Versions of the libs jar and of the JavaFX jars, shipped in jfx-static-libs and javafx-base
    private static final String LIBS_VERSION_RESOURCE = LIBS_RESOURCES + "version.properties";
    private static final String JAVAFX_VERSION_RESOURCE = "/javafx.properties";

    // The iOS archives for devices or the simulator, decided once by the C compiler
    private static Boolean iosSimulator;

    // Where beforeAnalysis unpacked the archives, needed again for the macOS -force_load
    private Path staticLibraryDirectory;

    // Opt-in/out for the Windows GUI subsystem. Unset activates if an Application is reachable
    private static final String GUI_PROPERTY = "jfx.static.gui";
    private Boolean forceGuiMode;
    private boolean imageContainsApplication = false;

    // Media and webkit have no static archives and ship as shared libraries next to the image
    private final SharedLibraryModules sharedLibraryModules = new SharedLibraryModules();

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (access.findClassByName("javafx.application.Application") == null) {
            return false;
        }

        // The classes in jfx-mobile-libs complete the OpenJFX jars of one build host
        if (isMobile()) {
            String target = Platform.includedIn(Platform.ANDROID.class) ? "Android" : "iOS";
            String host = Platform.includedIn(Platform.ANDROID.class) ? "Linux" : "Mac";
            if (!System.getProperty("os.name").startsWith(host)) {
                throw UserError.abort(target + " images need the OpenJFX jars of " + host + ", build them on " + host);
            }
        }

        // The substitutions apply independently of the feature and break the link without
        // archives, so an unsupported platform fails here instead of at the linker
        String platform = platformName();
        if (platform == null) {
            throw UserError.abort("No static JavaFX libraries exist for this platform,"
                                  + " remove jfx-static-feature from the class path");
        }

        // Check that we have static archives on the classpath and they are compatible with the runtime artifacts
        String libsArtifact = isMobile() ? "jfx-mobile-libs" : "jfx-static-libs";
        String javafxVersion = readProperty(JAVAFX_VERSION_RESOURCE, "javafx.version");
        String libsCoordinates = "us.hebi.graalvm:" + libsArtifact + ":" + (javafxVersion != null ? javafxVersion : "<javafx.version>");
        if (JfxStaticFeature.class.getResource(libsResource(staticLibraries().get(0))) == null) {
            throw UserError.abort("No " + libsArtifact + " jar for " + platform + " on the class path, add " + libsCoordinates);
        }
        String libsVersion = readProperty(LIBS_VERSION_RESOURCE, "version");
        if (libsVersion == null) {
            throw UserError.abort("The " + libsArtifact + " jar predates the version check, use " + libsCoordinates);
        }
        if (javafxVersion != null && !release(libsVersion).equals(release(javafxVersion))) {
            throw UserError.abort(libsArtifact + " " + libsVersion + " does not match the JavaFX " + javafxVersion
                                  + " on the class path, declare both with the same <javafx.version>");
        }

        // Check the supported gui property. Done here so typos abort early.
        String gui = System.getProperty(GUI_PROPERTY, "auto");
        forceGuiMode = switch (gui) {
            case "auto" -> null;
            case "true" -> true;
            case "false" -> false;
            default -> throw UserError.abort("-D" + GUI_PROPERTY + " must be true, false, or auto, not: " + gui);
        };

        return true;
    }

    // 26.0.2-1 matches 26.0.2, and 26-ea+3 reads as 26
    private static String release(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    private static String readProperty(String resource, String key) {
        try (InputStream input = JfxStaticFeature.class.getResourceAsStream(resource)) {
            if (input == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty(key);
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not read " + resource, ioe);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        PlatformNativeLibrarySupport nativeLibrarySupport = PlatformNativeLibrarySupport.singleton();
        Method addBuiltinPrefix = builtinPrefixMethod();
        for (String prefix : BUILTIN_PREFIXES) {
            // iOS has no decora_sse, the SSE peers stay regular JNI and PSWRenderer falls back to Java
            if (prefix.equals("com_sun_scenario_effect") && Platform.includedIn(Platform.IOS.class)) {
                continue;
            }
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
        for (String library : bundledLibraries()) {
            nativeLibraries.addStaticNonJniLibrary(library);
        }
        for (String library : systemLibraries()) {
            nativeLibraries.addDynamicNonJniLibrary(library);
        }

        // Application.launch() needs the subclass by name and its no-arg constructor, which the built-in
        // JavaFXFeature does not register. Only reachable types show up here, so we don't add extra classes.
        access.registerSubtypeReachabilityHandler((duringAnalysis, applicationClass) -> {
            imageContainsApplication = true;
            RuntimeReflection.register(applicationClass);
            RuntimeReflection.register(applicationClass.getDeclaredConstructors());
        }, access.findClassByName("javafx.application.Application"));

        // Register web/media as shared libraries if their main entry points are reachable.
        // The required binaries are copied to the output directory from the stock platform jars.
        sharedLibraryModules.register(access, "javafx.media", "com.sun.media.jfxmediaimpl.NativeMediaManager");
        sharedLibraryModules.register(access, "javafx.web", "com.sun.webkit.WebPage");
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        sharedLibraryModules.copyNextToImage(access);
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        BeforeImageWriteAccessImpl accessImpl = (BeforeImageWriteAccessImpl) access;
        List<String> options = linkerOptions(accessImpl);
        if (options.isEmpty()) {
            return;
        }
        accessImpl.registerLinkerInvocationTransformer(linkerInvocation -> {
            options.forEach(linkerInvocation::addNativeLinkerOption);
            return linkerInvocation;
        });
    }

    // Linker syntax that has no NativeLibraries API behind it
    private List<String> linkerOptions(BeforeImageWriteAccessImpl access) {
        if (Platform.includedIn(Platform.ANDROID.class)) {
            return List.of();
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            // 1. glassgtk3 references g_thread_init, which glib dropped
            // 2. the libjfxwebkit.so's lookup requires an old style -rpath to find libjvm.so next to it
            return List.of("-Wl,--defsym,g_thread_init=0", "-Wl,--disable-new-dtags", "-Wl,-rpath,$ORIGIN");
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            // The GlassWindow categories define no symbol the linker looks for and get dropped otherwise
            List<String> options = new ArrayList<>();
            options.add("-Wl,-force_load," + staticLibraryDirectory.resolve(staticLibraryFileName("glass")));
            for (String framework : Platform.includedIn(Platform.IOS.class) ? IOS_FRAMEWORKS : MACOS_FRAMEWORKS) {
                options.add("-Wl,-framework," + framework);
            }
            return options;
        } else if (Platform.includedIn(Platform.WINDOWS.class)) {
            // Remove the extra console window on Windows. The GUI-subsystem
            // CRT expects WinMain, /ENTRY keeps the startup that calls main
            boolean removeConsole = forceGuiMode != null ? forceGuiMode : imageContainsApplication;
            if (removeConsole && access.getImage().getImageKind().isExecutable) {
                return List.of("/SUBSYSTEM:WINDOWS", "/ENTRY:mainCRTStartup");
            }
        }
        return List.of();
    }

    // Unpacks the platform's archives so a consumer needs no -H:CLibraryPath
    private static Path extractStaticLibraries(Path tempDirectory) {
        Path directory = tempDirectory.resolve("javafx-static");
        try {
            Files.createDirectories(directory);
            for (String library : concat(staticLibraries(), bundledLibraries())) {
                String fileName = staticLibraryFileName(library);
                try (InputStream resource = JfxStaticFeature.class.getResourceAsStream(libsResource(library))) {
                    if (resource == null) {
                        throw new IOException("Missing " + fileName + " in the " + (isMobile() ? "jfx-mobile-libs" : "jfx-static-libs") + " jar");
                    }
                    Files.copy(resource, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not extract the static JavaFX libraries", ioe);
        }
        return directory;
    }

    private static String libsResource(String library) {
        return LIBS_RESOURCES + platformName() + "/" + staticLibraryFileName(library);
    }

    // Matches NativeLibraries::getStaticLibraryName
    private static String staticLibraryFileName(String library) {
        return Platform.includedIn(Platform.WINDOWS.class) ? library + ".lib" : "lib" + library + ".a";
    }

    // Spelled like Gluon Substrate's targets, null where there are no archives. Mobile
    // first, IOS_AARCH64 also counts as DARWIN_AARCH64
    private static String platformName() {
        if (Platform.includedIn(Platform.IOS_AARCH64.class)) {
            return iosSimulator() ? "ios-arm64-simulator" : "ios-arm64";
        } else if (Platform.includedIn(Platform.ANDROID_AARCH64.class)) {
            return "android-aarch64";
        } else if (Platform.includedIn(Platform.WINDOWS_AMD64.class)) {
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

    private static boolean isMobile() {
        return Platform.includedIn(Platform.ANDROID.class) || Platform.includedIn(Platform.IOS.class);
    }

    private static boolean iosSimulator() {
        if (iosSimulator == null) {
            List<String> command = new ArrayList<>();
            String compiler = (String) optionValue(SubstrateOptions.class, "CCompilerPath");
            command.add(compiler != null ? compiler : "clang");
            for (Object option : optionValues(optionValue(SubstrateOptions.class, "CCompilerOption"))) {
                command.add((String) option);
            }
            command.add("-dumpmachine");
            iosSimulator = run(command).endsWith("-simulator");
        }
        return iosSimulator;
    }

    // Reflective because the option key moved to another package and module after 25.0
    private static Object optionValue(Class<?> options, String name) {
        try {
            Field option = options.getField(name);
            return option.getType().getMethod("getValue").invoke(option.get(null));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read -H:" + name, e);
        }
    }

    private static List<?> optionValues(Object multiOptionValue) {
        try {
            return (List<?>) multiOptionValue.getClass().getMethod("values").invoke(multiOptionValue);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read the values of " + multiOptionValue, e);
        }
    }

    private static String run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor() != 0) {
                throw UserError.abort("Could not run " + String.join(" ", command) + "\n" + output);
            }
            return output;
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not run " + command.get(0), ioe);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + command.get(0), ie);
        }
    }

    private static List<String> staticLibraries() {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return WINDOWS_LIBRARIES;
        } else if (Platform.includedIn(Platform.ANDROID.class)) {
            return ANDROID_LIBRARIES;
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return LINUX_LIBRARIES;
        } else if (Platform.includedIn(Platform.MACOS.class)) {
            return MACOS_LIBRARIES;
        } else if (Platform.includedIn(Platform.IOS.class)) {
            return IOS_LIBRARIES;
        }
        throw new UnsupportedOperationException("No static JavaFX libraries known for this platform");
    }

    // Non-JNI archives the libs jar ships because the platform has no system copy
    private static List<String> bundledLibraries() {
        return Platform.includedIn(Platform.ANDROID.class) ? ANDROID_BUNDLED_LIBRARIES : List.of();
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<String> systemLibraries() {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return WINDOWS_SYSTEM_LIBRARIES;
        } else if (Platform.includedIn(Platform.ANDROID.class)) {
            return ANDROID_SYSTEM_LIBRARIES;
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return LINUX_SYSTEM_LIBRARIES;
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            return MACOS_SYSTEM_LIBRARIES;
        }
        throw new UnsupportedOperationException("No system libraries known for this platform");
    }

}
