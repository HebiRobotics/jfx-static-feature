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
    @TargetClass(className = "com.sun.javafx.font.freetype.FTFactory", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_FTFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.FontConfigManager", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_FontConfigManager {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.freetype.OSFreetype", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_OSFreetype {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.freetype.OSPango", onlyWith = {NotLinux.class, ClassPresent.class})
    static final class Target_OSPango {
    }

    /**
     * Declared in OSFreetype but implemented in no native archive on any OS
     */
    @TargetClass(className = "com.sun.javafx.font.freetype.OSFreetype", onlyWith = {IsLinux.class, ClassPresent.class})
    static final class Target_OSFreetype_Linux {

        @Delete
        static native int FT_Get_Char_Index(long face, long charcode);

    }

    static final class NotLinux implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.LINUX.class);
        }
    }

    static final class IsLinux implements BooleanSupplier {
        public boolean getAsBoolean() {
            return Platform.includedIn(Platform.LINUX.class);
        }
    }

}
