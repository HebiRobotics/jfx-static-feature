package us.hebi.graalvm.javafx.substitutions;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Hacks for working around the GraalVM compiler
 * TODO: remove once the issues are fixed upstream
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
public final class CompilerWorkarounds {

    /**
     * The string concatenation outlining of GraalVM 25.0.4 fails on an Object-typed operand. Nothing
     * calls the method below, so a shorter text spares every image the
     * {@code -H:-OutlineIndyStringConcatenations} workaround.
     */
    @Platforms(Platform.MACOS.class)
    @TargetClass(className = "com.sun.glass.ui.mac.MacVariant")
    static final class Target_MacVariant {

        @Alias
        int type;

        @Substitute
        @Override
        public String toString() {
            return "MacVariant type: " + type;
        }

    }

    private CompilerWorkarounds() {
    }

}
