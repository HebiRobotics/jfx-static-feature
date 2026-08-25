package us.hebi.graalvm.javafx.substitutions;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalEnvironment;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

/**
 * Substrate resolves the JNI entry point of a builtin (statically linked) native method by its
 * short name only, so an overloaded native method can never be linked: the C side of an overload
 * carries the long, signature-mangled name. The overloads below are therefore routed past the JNI
 * linkage and straight to their mangled symbols.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
public final class OverloadedNatives {

    // The C side uses neither env nor jclass
    @Platforms(Platform.WINDOWS.class)
    @TargetClass(className = "com.sun.javafx.font.directwrite.OS")
    static final class Target_directwrite_OS {

        @Substitute
        static long CreateFontFace(long font) {
            return DirectWrite.createFontFace(WordFactory.nullPointer(), WordFactory.nullPointer(), font);
        }

        @Substitute
        static long CreateFontFace(long fontFile, int faceType, long fontFiles, int faceIndex, int simulations) {
            return DirectWrite.createFontFace(WordFactory.nullPointer(), WordFactory.nullPointer(),
                    fontFile, faceType, fontFiles, faceIndex, simulations);
        }

        @Substitute
        static long GetFontFamily(long font) {
            return DirectWrite.getFontFamily(WordFactory.nullPointer(), WordFactory.nullPointer(), font);
        }

        @Substitute
        static long GetFontFamily(long collection, int index) {
            return DirectWrite.getFontFamily(WordFactory.nullPointer(), WordFactory.nullPointer(), collection, index);
        }

    }

    // The C side reads through the JNIEnv, so pass the real one and a local handle
    @Platforms(Platform.MACOS.class)
    @TargetClass(className = "com.sun.javafx.font.coretext.OS")
    static final class Target_coretext_OS {

        @Substitute
        static long CFStringCreateWithCharacters(long alloc, char[] chars, long numChars) {
            return CoreText.createStringWithCharacters(alloc, chars, numChars);
        }

        @Substitute
        static long CFStringCreateWithCharacters(long alloc, char[] chars, long start, long numChars) {
            return CoreText.createStringWithCharacters(alloc, chars, start, numChars);
        }

    }

    // The C side reads through the JNIEnv, so pass the real one and a local handle
    @Platforms(Platform.MACOS.class)
    @TargetClass(className = "com.sun.glass.ui.mac.MacTimer")
    static final class Target_MacTimer {

        @Substitute
        protected long _start(Runnable runnable) {
            return MacTimer.start(runnable);
        }

        @Substitute
        protected long _start(Runnable runnable, int period) {
            return MacTimer.start(runnable, period);
        }

    }

    @Platforms(Platform.WINDOWS.class)
    private static final class DirectWrite {

        @CFunction("Java_com_sun_javafx_font_directwrite_OS_CreateFontFace__J")
        static native long createFontFace(PointerBase env, PointerBase clazz, long font);

        @CFunction("Java_com_sun_javafx_font_directwrite_OS_CreateFontFace__JIJII")
        static native long createFontFace(PointerBase env, PointerBase clazz,
                long fontFile, int faceType, long fontFiles, int faceIndex, int simulations);

        @CFunction("Java_com_sun_javafx_font_directwrite_OS_GetFontFamily__J")
        static native long getFontFamily(PointerBase env, PointerBase clazz, long font);

        @CFunction("Java_com_sun_javafx_font_directwrite_OS_GetFontFamily__JI")
        static native long getFontFamily(PointerBase env, PointerBase clazz, long collection, int index);

        private DirectWrite() {
        }

    }

    @Platforms(Platform.MACOS.class)
    private static final class CoreText {

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

        private CoreText() {
        }

    }

    @Platforms(Platform.MACOS.class)
    private static final class MacTimer {

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

        private MacTimer() {
        }

    }

    private OverloadedNatives() {
    }

}
