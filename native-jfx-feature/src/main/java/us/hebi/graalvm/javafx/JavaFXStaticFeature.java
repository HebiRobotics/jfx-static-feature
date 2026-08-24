package us.hebi.graalvm.javafx;

import java.util.List;

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
            "com_sun_javafx_font",
            "com_sun_javafx_font_directwrite",
            "com_sun_javafx_font_coretext",
            "com_sun_javafx_tk_quantum",
            "com_sun_scenario_effect");

    /** Static JavaFX libraries in the static SDK's lib directory, without the .lib suffix. */
    private static final List<String> STATIC_LIBRARIES = List.of(
            "glass",
            "prism_common",
            "prism_d3d",
            "prism_sw",
            "decora_sse",
            "javafx_font",
            "javafx_iio");

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
        for (String library : STATIC_LIBRARIES) {
            nativeLibraries.addStaticJniLibrary(library);
        }

        // The built-in JavaFXFeature registers the Application subclasses themselves, but
        // Application.launch instantiates them through their no-argument constructor.
        accessImpl.findSubclasses(access.findClassByName("javafx.application.Application"))
                .forEach(applicationClass -> RuntimeReflection.register(applicationClass.getDeclaredConstructors()));
    }

}
