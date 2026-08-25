package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

/**
 * Same overload problem as {@link Target_com_sun_javafx_font_directwrite_OS}: GlassTimer.m only has
 * the two signature-mangled {@code _start} symbols, the short name Substrate emits resolves to
 * neither. GlassTimer.m keeps a global reference to the runnable through the JNIEnv, so unlike
 * directwrite these pass the real environment and a real handle.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
@Platforms(Platform.MACOS.class)
@TargetClass(className = "com.sun.glass.ui.mac.MacTimer")
final class Target_com_sun_glass_ui_mac_MacTimer {

    @Substitute
    protected long _start(Runnable runnable) {
        return MacTimerOverloads.start(runnable);
    }

    @Substitute
    protected long _start(Runnable runnable, int period) {
        return MacTimerOverloads.start(runnable, period);
    }

}

@Platforms(Platform.MACOS.class)
final class MacTimerOverloads {

    // jThis is unused on the C side, the local frame keeps the handle from outliving the call
    static long start(Runnable runnable) {
        JNIObjectHandles.pushLocalFrame(1);
        try {
            return start(JNIThreadLocalEnvironment.getAddress(), JNIObjectHandles.nullHandle(),
                    JNIObjectHandles.createLocal(runnable));
        } finally {
            JNIObjectHandles.popLocalFrame();
        }
    }

    static long start(Runnable runnable, int period) {
        JNIObjectHandles.pushLocalFrame(1);
        try {
            return start(JNIThreadLocalEnvironment.getAddress(), JNIObjectHandles.nullHandle(),
                    JNIObjectHandles.createLocal(runnable), period);
        } finally {
            JNIObjectHandles.popLocalFrame();
        }
    }

    @CFunction("Java_com_sun_glass_ui_mac_MacTimer__1start__Ljava_lang_Runnable_2")
    private static native long start(JNIEnvironment env, JNIObjectHandle thiz, JNIObjectHandle runnable);

    @CFunction("Java_com_sun_glass_ui_mac_MacTimer__1start__Ljava_lang_Runnable_2I")
    private static native long start(JNIEnvironment env, JNIObjectHandle thiz, JNIObjectHandle runnable, int period);

    private MacTimerOverloads() {
    }

}
