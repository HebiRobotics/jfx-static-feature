package us.hebi.graalvm.javafx.stubs;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;

import java.util.function.BooleanSupplier;

/**
 * Deletes the classes with JNI calls that are only implemented on iOS. Otherwise,
 * the analysis keeps them reachable and would need C stubs.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
final class DeleteIosNatives {

    @Delete
    @TargetClass(className = "com.sun.javafx.iio.ios.IosImageLoader", onlyWith = NotIos.class)
    static final class Target_IosImageLoader {
    }

    static final class NotIos implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.IOS.class);
        }
    }

}
