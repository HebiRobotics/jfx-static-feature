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
 * {@code CFStringCreateWithCharacters} is the one overloaded native in coretext.c, same problem as
 * {@link Target_com_sun_javafx_font_directwrite_OS}. The C side reads the char array through the
 * JNIEnv, so both pass the real environment and a handle for the array. The jclass is unused.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
@Platforms(Platform.MACOS.class)
@TargetClass(className = "com.sun.javafx.font.coretext.OS")
final class Target_com_sun_javafx_font_coretext_OS {

    @Substitute
    static long CFStringCreateWithCharacters(long alloc, char[] chars, long numChars) {
        return CoreTextOverloads.createStringWithCharacters(alloc, chars, numChars);
    }

    @Substitute
    static long CFStringCreateWithCharacters(long alloc, char[] chars, long start, long numChars) {
        return CoreTextOverloads.createStringWithCharacters(alloc, chars, start, numChars);
    }

}

@Platforms(Platform.MACOS.class)
final class CoreTextOverloads {

    // Called once per laid out string, so the array handle lives in a local frame
    static long createStringWithCharacters(long alloc, char[] chars, long numChars) {
        JNIObjectHandles.pushLocalFrame(1);
        try {
            return createStringWithCharacters(JNIThreadLocalEnvironment.getAddress(), JNIObjectHandles.nullHandle(),
                    alloc, JNIObjectHandles.createLocal(chars), numChars);
        } finally {
            JNIObjectHandles.popLocalFrame();
        }
    }

    static long createStringWithCharacters(long alloc, char[] chars, long start, long numChars) {
        JNIObjectHandles.pushLocalFrame(1);
        try {
            return createStringWithCharacters(JNIThreadLocalEnvironment.getAddress(), JNIObjectHandles.nullHandle(),
                    alloc, JNIObjectHandles.createLocal(chars), start, numChars);
        } finally {
            JNIObjectHandles.popLocalFrame();
        }
    }

    @CFunction("Java_com_sun_javafx_font_coretext_OS_CFStringCreateWithCharacters__J_3CJ")
    private static native long createStringWithCharacters(JNIEnvironment env, JNIObjectHandle clazz,
            long alloc, JNIObjectHandle chars, long numChars);

    @CFunction("Java_com_sun_javafx_font_coretext_OS_CFStringCreateWithCharacters__J_3CJJ")
    private static native long createStringWithCharacters(JNIEnvironment env, JNIObjectHandle clazz,
            long alloc, JNIObjectHandle chars, long start, long numChars);

    private CoreTextOverloads() {
    }

}
