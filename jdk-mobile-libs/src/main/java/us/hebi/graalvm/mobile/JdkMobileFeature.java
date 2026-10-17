package us.hebi.graalvm.mobile;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.CAnnotationProcessorCache;
import com.oracle.svm.hosted.c.NativeLibraries;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Supplies what an Android or iOS image needs beyond GraalVM: the target's CAP cache, so no query
 * program has to run on the target, and the static JDK and GraalVM C libraries for the link step,
 * native-image's own or the host app's with -H:+ExitAfterRelocatableImageWrite.
 *
 * @author Florian Enner
 * @since 10 Oct 2026
 */
public class JdkMobileFeature implements Feature {

    private static final String RESOURCES = "/us/hebi/graalvm/mobile/";
    private static final String VERSION_RESOURCE = RESOURCES + "version.properties";

    // The archives land in this directory next to the image, one subdirectory per target
    private static final String LIBRARY_DIRECTORY = "jdk-mobile-libs";

    // The -H:CLibraryPath of the jar's native-image.properties, unused when a build moves the CAP cache
    private static final String DEFAULT_LINK_DIRECTORY = "jdk-mobile-libs/lib";

    // The JDK reads it once, before any feature runs. Unset it is the builder's ABI
    private static final String FOREIGN_ABI_PROPERTY = "jdk.internal.foreign.CABI";

    // The minimum iOS version the archives are built for, without -simulator for devices
    private static final String IOS_DEFAULT_TARGET = "arm64-apple-ios15.0";

    // What the JDK and GraalVM archives call into on iOS
    private static final List<String> IOS_FRAMEWORKS = List.of("Foundation", "CoreFoundation", "Security", "SystemConfiguration");

    // What the C compiler targets, decided once by running it
    private static String compilerTriple;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (targets().isEmpty()) {
            throw UserError.abort("jdk-mobile-libs only supports Android and iOS images, build with"
                                  + " -Dsvm.platform=org.graalvm.nativeimage.Platform$ANDROID_AARCH64 or $IOS_AARCH64,"
                                  + " or remove it from the class path");
        }

        // The archives and the CAP caches belong to one GraalVM release
        String libsVersion = readProperty(VERSION_RESOURCE, "graalvm.version");
        String graalvmVersion = graalvmVersion();
        if (!libsVersion.equals(graalvmVersion)) {
            throw UserError.abort("jdk-mobile-libs " + libsVersion + " does not match GraalVM " + graalvmVersion
                                  + ", use us.hebi.graalvm:jdk-mobile-libs:" + graalvmVersion);
        }

        // FFM, on by default and used by JavaFX's Marlin, generates its stubs for the builder's ABI and
        // fails with a ClassCastException on an x86_64 host
        if (Platform.includedIn(Platform.ANDROID_AARCH64.class) && SubstrateOptions.isForeignAPIEnabled()
            && !"LINUX_AARCH_64".equals(builderForeignAbi())) {
            throw UserError.abort("Android images need the aarch64 FFM calling convention in the builder, pass"
                                  + " -J-D" + FOREIGN_ABI_PROPERTY + "=LINUX_AARCH_64, or -H:-ForeignAPISupport without FFM");
        }
        return true;
    }

    private static String builderForeignAbi() {
        String abi = System.getProperty(FOREIGN_ABI_PROPERTY);
        if (abi != null) {
            return abi;
        }
        boolean linuxAarch64 = System.getProperty("os.name").equals("Linux") && System.getProperty("os.arch").equals("aarch64");
        return linuxAarch64 ? "LINUX_AARCH_64" : null;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // NativeLibraries reads the cache right after this callback, the device and simulator caches are identical
        Path capDirectory = capDirectory();
        copyResources(targets().get(0), "cap", capDirectory);

        // When native-image links, its NativeLibraries constructor looks for the JDK archives in -H:CLibraryPath
        Path libraryDirectory = linkDirectory();
        if (relocatableOnly()) {
            createDirectories(libraryDirectory);
        } else {
            if (!cLibraryPath().contains(libraryDirectory)) {
                throw UserError.abort("jdk-mobile-libs unpacks the archives to " + libraryDirectory
                                      + ", pass -H:CLibraryPath=" + libraryDirectory + " next to -H:CAPCacheDir");
            }
            copyResources(linkTarget(), "lib", libraryDirectory);
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        // The linker runs in the temp directory and needs the archives by absolute path. The
        // default directory does not exist when a build moved the CAP cache and would abort the link
        Path libraryDirectory = linkDirectory();
        Collection<String> libraryPaths = ImageSingletons.lookup(NativeLibraries.class).getLibraryPaths();
        libraryPaths.removeIf(path -> Path.of(path).toAbsolutePath().equals(libraryDirectory)
                                      || path.equals(DEFAULT_LINK_DIRECTORY) && !Files.isDirectory(Path.of(path)));
        libraryPaths.add(libraryDirectory.toString());
    }

    // The C compiler native-image links with decides between the iOS device and simulator archives
    private static String linkTarget() {
        if (!Platform.includedIn(Platform.IOS.class)) {
            return targets().get(0);
        }
        return iosSimulator() ? "ios-arm64-simulator" : "ios-arm64";
    }

    private static boolean iosSimulator() {
        return compilerTriple().endsWith("-simulator");
    }

    // The host clang links for iOS when it gets the target and the SDK, so it needs no wrapper
    private static List<String> iosCompilerOptions() {
        List<String> options = new ArrayList<>();
        if (!compilerTriple().contains("-ios")) {
            options.add("--target=" + IOS_DEFAULT_TARGET);
        }
        options.add("-isysroot");
        options.add(run(List.of("xcrun", "--sdk", iosSimulator() ? "iphonesimulator" : "iphoneos", "--show-sdk-path")));
        return options;
    }

    private static String compilerTriple() {
        if (compilerTriple == null) {
            List<String> command = new ArrayList<>();
            String compiler = (String) optionValue(SubstrateOptions.class, "CCompilerPath");
            command.add(compiler != null ? compiler : "clang");
            command.addAll(stringValues(optionValue(SubstrateOptions.class, "CCompilerOption")));
            command.add("-dumpmachine");
            compilerTriple = run(command);
        }
        return compilerTriple;
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
            throw new IllegalStateException(ie);
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (relocatableOnly()) {
            // The host build links, ExitAfterRelocatableImageWrite exits before afterImageWrite
            Path libraryDirectory = SubstrateOptions.getImagePath().resolve(LIBRARY_DIRECTORY);
            for (String target : targets()) {
                copyResources(target, "lib", libraryDirectory.resolve(target));
            }
        } else if (Platform.includedIn(Platform.ANDROID.class)) {
            // Missing symbols would only show up at System.loadLibrary
            ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
                linkerInvocation.addNativeLinkerOption("-Wl,--no-undefined");
                return linkerInvocation;
            });
        } else if (Platform.includedIn(Platform.IOS.class)) {
            // The host clang links for iOS with the target and SDK options. The app embeds the library,
            // the default install name would be the build path. Darwin shared libraries link with
            // -undefined dynamic_lookup, which hides missing symbols until dyld
            List<String> compilerOptions = iosCompilerOptions();
            ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(linkerInvocation -> {
                compilerOptions.forEach(linkerInvocation::addNativeLinkerOption);
                linkerInvocation.addNativeLinkerOption("-Wl,-install_name,@rpath/" + linkerInvocation.getOutputFile().getFileName());
                linkerInvocation.addNativeLinkerOption("-Wl,-undefined,error");
                for (String framework : IOS_FRAMEWORKS) {
                    linkerInvocation.addNativeLinkerOption("-Wl,-framework," + framework);
                }
                return linkerInvocation;
            });
        }
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        // A JDK library registered for dynamic loading, such as java.desktop's awt, makes native-image
        // copy the builder's own copy next to the image and link libjava and libjvm shims for it.
        // Those are host binaries, the built-in features that write them run before this one
        BuildArtifacts.singleton().forEach((type, paths) -> {
            if (type == ArtifactType.JDK_LIBRARY || type == ArtifactType.JDK_LIBRARY_SHIM) {
                for (Path path : paths) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ioe) {
                        throw new UncheckedIOException("Could not delete the host library " + path, ioe);
                    }
                }
            }
        });
    }

    private static boolean relocatableOnly() {
        return (Boolean) optionValue(NativeImageOptions.class, "ExitAfterRelocatableImageWrite");
    }

    // -H:CAPCacheDir as NativeLibraries resolves it, so a build can move it into its own output directory
    private static Path capDirectory() {
        return Path.of((String) optionValue(CAnnotationProcessorCache.Options.class, "CAPCacheDir")).toAbsolutePath();
    }

    private static Path linkDirectory() {
        return capDirectory().resolveSibling("lib");
    }

    private static List<Path> cLibraryPath() {
        return values(optionValue(SubstrateOptions.class, "CLibraryPath")).stream()
                .map(path -> ((Path) path).toAbsolutePath())
                .toList();
    }

    private static List<String> stringValues(Object multiOptionValue) {
        return values(multiOptionValue).stream().map(String.class::cast).toList();
    }

    // The values of an accumulating option
    private static List<?> values(Object multiOptionValue) {
        try {
            return (List<?>) multiOptionValue.getClass().getMethod("values").invoke(multiOptionValue);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read the values of " + multiOptionValue, e);
        }
    }

    // Reflective because the option key moved to another package and module in 25.4
    private static Object optionValue(Class<?> options, String name) {
        try {
            Field option = options.getField(name);
            return option.getType().getMethod("getValue").invoke(option.get(null));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read -H:" + name, e);
        }
    }

    // iOS images are the same for devices and the simulator, only the link differs
    private static List<String> targets() {
        if (Platform.includedIn(Platform.ANDROID_AARCH64.class)) {
            return List.of("android-aarch64");
        } else if (Platform.includedIn(Platform.IOS_AARCH64.class)) {
            return List.of("ios-arm64", "ios-arm64-simulator");
        }
        return List.of();
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not create " + directory, ioe);
        }
    }

    // Copies the files that <dir>.list names
    private static void copyResources(String target, String dir, Path destination) {
        String prefix = RESOURCES + target + "/" + dir;
        try {
            Files.createDirectories(destination);
            for (String fileName : readResource(prefix + ".list").split("\\R")) {
                if (fileName.isBlank()) {
                    continue;
                }
                try (InputStream resource = JdkMobileFeature.class.getResourceAsStream(prefix + "/" + fileName)) {
                    if (resource == null) {
                        throw new IOException("Missing " + prefix + "/" + fileName + " in the jdk-mobile-libs jar");
                    }
                    Files.copy(resource, destination.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not extract " + prefix + " to " + destination, ioe);
        }
    }

    // The builder runs on the GraalVM it builds with
    private static String graalvmVersion() {
        Path release = Path.of(System.getProperty("java.home"), "release");
        try (InputStream input = Files.newInputStream(release)) {
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("GRAALVM_VERSION");
            if (version == null) {
                throw UserError.abort("No GRAALVM_VERSION in " + release + ", jdk-mobile-libs needs Oracle GraalVM");
            }
            return version.replace("\"", "");
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not read " + release, ioe);
        }
    }

    private static String readProperty(String resource, String key) {
        try (InputStream input = JdkMobileFeature.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw UserError.abort("Missing " + resource + " in the jdk-mobile-libs jar");
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty(key);
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not read " + resource, ioe);
        }
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream input = JdkMobileFeature.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing " + resource + " in the jdk-mobile-libs jar");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
