package us.hebi.graalvm.jfx;

import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature.AfterImageWriteAccess;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Media and webkit exist only as shared libraries, shipped inside the platform jars.
 * When a module's loader class is reachable, its jar's libraries get copied next to the image.
 *
 * @author Florian Enner
 * @since 16 Sep 2026
 */
class SharedLibraryModules {

    private final Map<String, Path> sharedLibraryJars = new LinkedHashMap<>();

    void register(BeforeAnalysisAccess access, String module, String loaderClassName) {
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

    void copyNextToImage(AfterImageWriteAccess access) {
        Path directory = access.getImagePath().toAbsolutePath().getParent();
        sharedLibraryJars.forEach((module, jar) -> copySharedLibraries(module, jar, directory));
        if (Platform.includedIn(Platform.MACOS.class) && sharedLibraryJars.containsKey("javafx.web")) {
            writeJvmShim(directory);
        }
    }

    /**
     * libjfxwebkit.dylib links @rpath/libjvm.dylib but imports no symbols from it, so an empty file
     * satisfies the load. GraalVM 25.0 creates jvm shims only on Windows/Linux, and 25.3 on any OS
     * but only for AWT or -H:+CreateJvmShim, so for javafx.web on macOS we create it ourselves.
     */
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

}
