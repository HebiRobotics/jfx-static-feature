package us.hebi.graalvm.jfx.stubs;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.graalvm.nativeimage.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Deletes the classes with JNI calls that are only implemented on Windows. Otherwise,
 * the analysis keeps them reachable and would need C stubs.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
final class DeleteWindowsNatives {

    /**
     * The Windows stubs sit on the base class, so we can't delete the whole class and need to
     * stub the individual methods instead.
     */
    @TargetClass(className = "com.sun.javafx.font.PrismFontFactory", onlyWith = {NotWindows.class, ClassPresent.class})
    static final class Target_PrismFontFactory {

        @Substitute
        private static String getFontPath() {
            throw unsupported("getFontPath");
        }

        @Substitute
        static int getLCDContrastWin32() {
            throw unsupported("getLCDContrastWin32");
        }

        @Substitute
        private static float getSystemFontSizeNative() {
            throw unsupported("getSystemFontSizeNative");
        }

        @Substitute
        private static String getSystemFontNative() {
            throw unsupported("getSystemFontNative");
        }

        @Substitute
        static short getSystemLCID() {
            throw unsupported("getSystemLCID");
        }

        @Substitute
        static void populateFontFileNameMap(HashMap<String, String> fontToFileMap,
                                            HashMap<String, String> fontToFamilyNameMap,
                                            HashMap<String, ArrayList<String>> familyToFontListMap,
                                            Locale locale) {
            throw unsupported("populateFontFileNameMap");
        }

    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.directwrite.DWFactory", onlyWith = {NotWindows.class, ClassPresent.class})
    static final class Target_DWFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.directwrite.OS", onlyWith = {NotWindows.class, ClassPresent.class})
    static final class Target_DirectWriteOS {
    }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("PrismFontFactory." + method
                                                 + " is implemented on Windows only");
    }

    static final class NotWindows implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.WINDOWS.class);
        }
    }

}
