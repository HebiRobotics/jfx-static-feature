package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substrate resolves the JNI entry point of a builtin (statically linked) native method by its
 * short name only, so an overloaded native method can never be linked: the C side of an overload
 * carries the long, signature-mangled name. All four overloads below are therefore routed past the
 * JNI linkage and straight to their mangled symbols.
 * <p>
 * The C implementations in directwrite.cpp use neither the JNIEnv nor the jclass argument, so
 * passing null for both is safe.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Platforms(Platform.WINDOWS.class)
@TargetClass(className = "com.sun.javafx.font.directwrite.OS")
final class Target_com_sun_javafx_font_directwrite_OS {

    @Substitute
    static long CreateFontFace(long font) {
        return DirectWriteOverloads.createFontFace(WordFactory.nullPointer(), WordFactory.nullPointer(), font);
    }

    @Substitute
    static long CreateFontFace(long fontFile, int faceType, long fontFiles, int faceIndex, int simulations) {
        return DirectWriteOverloads.createFontFace(WordFactory.nullPointer(), WordFactory.nullPointer(),
                fontFile, faceType, fontFiles, faceIndex, simulations);
    }

    @Substitute
    static long GetFontFamily(long font) {
        return DirectWriteOverloads.getFontFamily(WordFactory.nullPointer(), WordFactory.nullPointer(), font);
    }

    @Substitute
    static long GetFontFamily(long collection, int index) {
        return DirectWriteOverloads.getFontFamily(WordFactory.nullPointer(), WordFactory.nullPointer(), collection, index);
    }

}

@Platforms(Platform.WINDOWS.class)
final class DirectWriteOverloads {

    @CFunction("Java_com_sun_javafx_font_directwrite_OS_CreateFontFace__J")
    static native long createFontFace(PointerBase env, PointerBase clazz, long font);

    @CFunction("Java_com_sun_javafx_font_directwrite_OS_CreateFontFace__JIJII")
    static native long createFontFace(PointerBase env, PointerBase clazz,
            long fontFile, int faceType, long fontFiles, int faceIndex, int simulations);

    @CFunction("Java_com_sun_javafx_font_directwrite_OS_GetFontFamily__J")
    static native long getFontFamily(PointerBase env, PointerBase clazz, long font);

    @CFunction("Java_com_sun_javafx_font_directwrite_OS_GetFontFamily__JI")
    static native long getFontFamily(PointerBase env, PointerBase clazz, long collection, int index);

    private DirectWriteOverloads() {
    }

}
