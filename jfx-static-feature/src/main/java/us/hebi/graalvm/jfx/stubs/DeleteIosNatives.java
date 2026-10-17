package us.hebi.graalvm.jfx.stubs;

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
    @TargetClass(className = "com.sun.javafx.iio.ios.IosImageLoader", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosImageLoader {
    }

    // jfx-mobile-libs ships the iOS glass to Android builds as well. PlatformFactory names the factory,
    // which has no natives but keeps the rest of the glass reachable

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosPlatformFactory", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosPlatformFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosApplication", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosApplication {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosCursor", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosCursor {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosGestureSupport", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosGestureSupport {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosPasteboard", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosPasteboard {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosRobot", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosRobot {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosTimer", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosTimer {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosView", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosView {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.ios.IosWindow", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IosWindow {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.IOSGLContext", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IOSGLContext {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.IOSGLDrawable", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IOSGLDrawable {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.IOSGLFactory", onlyWith = {NotIos.class, ClassPresent.class})
    static final class Target_IOSGLFactory {
    }

    static final class NotIos implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.IOS.class);
        }
    }

}
