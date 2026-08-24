package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jni.functions.JNIFunctionTables;
import com.oracle.svm.core.jni.headers.JNIJavaVM;

/**
 * {@code JNI_OnLoad_glass} reports JNI_VERSION_1_2, while the JNI specification requires 1.8 or
 * later from a statically linked library; every other JavaFX library gets this right. Substrate
 * enforces the rule, so {@code System.loadLibrary("glass")} fails. glass is linked into the image
 * anyway, so the load is replaced by a direct call to its initializer, dropping the version check
 * along with it.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@TargetClass(className = "com.sun.glass.ui.Application")
final class Target_com_sun_glass_ui_Application {

    @Alias //
    static boolean loaded;

    @Substitute
    protected static synchronized void loadNativeLibrary(String libname) {
        if (!loaded) {
            GlassLibrary.onLoad(JNIFunctionTables.singleton().getGlobalJavaVM(), WordFactory.nullPointer());
            loaded = true;
        }
    }

}

final class GlassLibrary {

    @CFunction("JNI_OnLoad_glass")
    static native int onLoad(JNIJavaVM vm, PointerBase reserved);

    private GlassLibrary() {
    }

}
