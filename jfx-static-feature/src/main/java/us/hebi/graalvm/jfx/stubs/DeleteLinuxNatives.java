package us.hebi.graalvm.jfx.stubs;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;

import java.util.function.BooleanSupplier;

/**
 * Deletes the classes with JNI calls that are only implemented on Linux. Otherwise,
 * the analysis keeps them reachable and would need C stubs.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
final class DeleteLinuxNatives {

    @Delete
    @TargetClass(className = "com.sun.javafx.font.freetype.FTFactory", onlyWith = NotLinux.class)
    static final class Target_FTFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.FontConfigManager", onlyWith = NotLinux.class)
    static final class Target_FontConfigManager {
    }

    static final class NotLinux implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.LINUX.class);
        }
    }

}
