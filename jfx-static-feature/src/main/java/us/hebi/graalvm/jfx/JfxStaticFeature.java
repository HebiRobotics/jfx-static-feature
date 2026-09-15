package us.hebi.graalvm.jfx;

import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    private static final String LIBS_RESOURCES = "/us/hebi/graalvm/jfx/libs/";

    // Versions of the libs jar and of the JavaFX jars, shipped in jfx-static-libs and javafx-base
    private static final String LIBS_VERSION_RESOURCE = LIBS_RESOURCES + "version.properties";
    private static final String JAVAFX_VERSION_RESOURCE = "/javafx.properties";

    // Where beforeAnalysis unpacked the archives, needed again for the macOS -force_load
    private Path staticLibraryDirectory;

    // Opt-in/out for the Windows GUI subsystem. Unset activates if an Application is reachable
    private static final String GUI_PROPERTY = "jfx.static.gui";
    private Boolean forceGuiMode;
    private boolean imageContainsApplication = false;

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
        String javafxVersion = readProperty(JAVAFX_VERSION_RESOURCE, "javafx.version");
        String libsCoordinates = "us.hebi.graalvm:jfx-static-libs:" + (javafxVersion != null ? javafxVersion : "<javafx.version>");
        if (JfxStaticFeature.class.getResource(libsResource(staticLibraries().get(0))) == null) {
            throw UserError.abort("No jfx-static-libs jar for " + platform + " on the class path, add " + libsCoordinates);
        }
        String libsVersion = readProperty(LIBS_VERSION_RESOURCE, "version");
        if (libsVersion == null) {
            throw UserError.abort("The jfx-static-libs jar predates the version check, use " + libsCoordinates);
        }
        if (javafxVersion != null && !release(libsVersion).equals(release(javafxVersion))) {
            throw UserError.abort("jfx-static-libs " + libsVersion + " does not match the JavaFX " + javafxVersion
                                  + " on the class path, declare both with the same <javafx.version>");
        }

        // Validated here so a typo aborts early
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
            imageContainsApplication = true;
            RuntimeReflection.register(applicationClass);
            RuntimeReflection.register(applicationClass.getDeclaredConstructors());
        }, access.findClassByName("javafx.application.Application"));

        // Register web/media as shared libraries if their main entry points are reachable.
        // The required binaries are copied to the output directory from the stock platform jars.
        registerSharedLibraryModule(access, "javafx.media", "com.sun.media.jfxmediaimpl.NativeMediaManager");
        registerSharedLibraryModule(access, "javafx.web", "com.sun.webkit.WebPage");
    }

    // Media and webkit exist only as shared libraries, shipped inside the platform jars.
    // When a module's loader class is reachable, its jar's libraries get copied next to the image.
    private final Map<String, Path> sharedLibraryJars = new LinkedHashMap<>();

    private void registerSharedLibraryModule(BeforeAnalysisAccess access, String module, String loaderClassName) {
        Class<?> loader = access.findClassByName(loaderClassName);
        if (loader == null) {
            return;
        }
        access.registerReachabilityHandler(duringAnalysis -> {
            enableNativeAccessForUnnamedModule();
            CodeSource source = loader.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                System.out.println("JfxStaticFeature: " + module + " is reachable but has no code source,"
                                   + " ship its shared libraries next to the image manually");
                return;
            }
            try {
                sharedLibraryJars.put(module, Path.of(source.getLocation().toURI()));
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Could not resolve the " + module + " jar", e);
            }
        }, loader);
    }

    // The shared libraries are loaded with the restricted System.load. We can't add flags at this
    // stage, so we call the internal setter reflectively. If it doesn't work, users will just see
    // a warning.
    private static void enableNativeAccessForUnnamedModule() {
        try {
            Method addToAllUnnamed = Module.class.getDeclaredMethod("implAddEnableNativeAccessToAllUnnamed");
            addToAllUnnamed.setAccessible(true);
            addToAllUnnamed.invoke(null);
        } catch (ReflectiveOperationException roe) {
            System.out.println("javafx media/web need --enable-native-access=ALL_UNNAMED");
        }
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        Path directory = access.getImagePath().toAbsolutePath().getParent();
        sharedLibraryJars.forEach((module, jar) -> copySharedLibraries(module, jar, directory));
        if (Platform.includedIn(Platform.MACOS.class) && sharedLibraryJars.containsKey("javafx.web")) {
            writeJvmShim(directory);
        }
    }

    // libjfxwebkit.dylib links @rpath/libjvm.dylib but imports nothing. GraalVM 25.0 creates shims
    // only for Windows/Linux, and GraalVM 25.3 creates shims only for AWT or -H:+CreateJvmShim
    private static void writeJvmShim(Path directory) {
        Path shim = directory.resolve("libjvm.dylib");
        if (Files.exists(shim)) {
            return;
        }
        try {
            List<String> command = ImageSingletons.lookup(CCompilerInvoker.class).createCompilerCommand(
                    List.of("-shared", "-x", "c", "-Wl,-install_name,@rpath/libjvm.dylib"), shim, Path.of("/dev/null"));
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Could not create " + shim + ":\n" + output);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not create " + shim, ioe);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating " + shim, e);
        }
        System.out.println("JfxStaticFeature: created the empty libjvm.dylib needed by libjfxwebkit.dylib");
    }

    private static void copySharedLibraries(String module, Path jar, Path directory) {
        int copied = 0;
        try {
            if (Files.isDirectory(jar)) {
                try (var files = Files.list(jar)) {
                    for (Path file : files.filter(f -> isSharedLibrary(f.getFileName().toString())).toList()) {
                        Files.copy(file, directory.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    }
                }
            } else {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                        ZipEntry entry = entries.nextElement();
                        if (!entry.getName().contains("/") && isSharedLibrary(entry.getName())) {
                            try (InputStream stream = zip.getInputStream(entry)) {
                                Files.copy(stream, directory.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
                                copied++;
                            }
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not copy the " + module + " shared libraries", ioe);
        }
        if (copied == 0) {
            System.out.println("JfxStaticFeature: " + module + " is reachable but " + jar.getFileName()
                               + " ships no shared libraries, use the platform classifier jar or ship them manually");
        } else {
            System.out.println("JfxStaticFeature: copied " + copied + " " + module + " shared libraries next to the image");
        }
    }

    private static boolean isSharedLibrary(String name) {
        return name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib") || name.contains(".so.");
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        List<String> options = linkerOptions((BeforeImageWriteAccessImpl) access);
        if (options.isEmpty()) {
            return;
        }
        ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
            options.forEach(linkerInvocation::addNativeLinkerOption);
            return linkerInvocation;
        });
    }

    // Linker syntax that has no NativeLibraries API behind it
    private List<String> linkerOptions(BeforeImageWriteAccessImpl access) {
        if (Platform.includedIn(Platform.LINUX.class)) {
            // 1. glassgtk3 references g_thread_init, which glib dropped
            // 2. the libjfxwebkit.so's lookup requires an old style -rpath to find libjvm.so next to it
            return List.of("-Wl,--defsym,g_thread_init=0", "-Wl,--disable-new-dtags", "-Wl,-rpath,$ORIGIN");
        } else if (Platform.includedIn(Platform.MACOS.class)) {
            // The GlassWindow categories define no symbol the linker looks for and get dropped otherwise
            List<String> options = new ArrayList<>();
            options.add("-Wl,-force_load," + staticLibraryDirectory.resolve(staticLibraryFileName("glass")));
            for (String framework : MACOS_FRAMEWORKS) {
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
            for (String library : staticLibraries()) {
                String fileName = staticLibraryFileName(library);
                try (InputStream resource = JfxStaticFeature.class.getResourceAsStream(libsResource(library))) {
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

    private static String libsResource(String library) {
        return LIBS_RESOURCES + platformName() + "/" + staticLibraryFileName(library);
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
