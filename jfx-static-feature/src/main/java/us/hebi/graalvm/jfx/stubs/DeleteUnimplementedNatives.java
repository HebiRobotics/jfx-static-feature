package us.hebi.graalvm.jfx.stubs;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Deletes the classes with JNI calls that no native archive implements on any OS. Monocle
 * classes only arrive through jfx-mobile-libs, because buildSrc/android.gradle compiles only
 * monocle/android and monocle/util, and the desktop jars have no Monocle.
 *
 * @author Florian Enner
 * @since 17 Oct 2026
 */
final class DeleteUnimplementedNatives {

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.EGLPlatform", onlyWith = {ClassPresent.class})
    static final class Target_EGLPlatform {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.EGLScreen", onlyWith = {ClassPresent.class})
    static final class Target_EGLScreen {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.EGLAcceleratedScreen", onlyWith = {ClassPresent.class})
    static final class Target_EGLAcceleratedScreen {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.EGLCursor", onlyWith = {ClassPresent.class})
    static final class Target_EGLCursor {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.monocle.LinuxSystem$InputAbsInfo", onlyWith = {ClassPresent.class})
    static final class Target_LinuxSystem_InputAbsInfo {
    }

}
