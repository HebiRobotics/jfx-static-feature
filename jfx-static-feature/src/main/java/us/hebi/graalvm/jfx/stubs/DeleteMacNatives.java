package us.hebi.graalvm.jfx.stubs;

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Deletes the classes with JNI calls that are only implemented on macOS. Otherwise,
 * the analysis keeps them reachable and would need C stubs.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
final class DeleteMacNatives {

    @Delete
    @TargetClass(className = "com.sun.javafx.font.DFontDecoder", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_DFontDecoder {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.MacFontFinder", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacFontFinder {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.coretext.CTFactory", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_CTFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.coretext.OS", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_CoreTextOS {
    }

    static final class NotMacOs implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.MACOS.class);
        }
    }

}
