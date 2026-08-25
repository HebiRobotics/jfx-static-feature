package us.hebi.graalvm.javafx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Six of the factory's native methods are implemented in the Windows javafx_font library only. The
 * class itself is the base of every platform factory and cannot be dropped, so the methods are
 * replaced by ones that fail loudly. The Linux and macOS factories override every caller, so
 * reaching one of these means the wrong factory was selected.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Platforms({ Platform.LINUX.class, Platform.MACOS.class })
@TargetClass(className = "com.sun.javafx.font.PrismFontFactory")
final class Target_com_sun_javafx_font_PrismFontFactory {

    @Substitute
    private static String getFontPath() {
        throw new UnsupportedOperationException(
                "PrismFontFactory.getFontPath is implemented on Windows only");
    }

    @Substitute
    static int getLCDContrastWin32() {
        throw new UnsupportedOperationException(
                "PrismFontFactory.getLCDContrastWin32 is implemented on Windows only");
    }

    @Substitute
    private static float getSystemFontSizeNative() {
        throw new UnsupportedOperationException(
                "PrismFontFactory.getSystemFontSizeNative is implemented on Windows only");
    }

    @Substitute
    private static String getSystemFontNative() {
        throw new UnsupportedOperationException(
                "PrismFontFactory.getSystemFontNative is implemented on Windows only");
    }

    @Substitute
    static short getSystemLCID() {
        throw new UnsupportedOperationException(
                "PrismFontFactory.getSystemLCID is implemented on Windows only");
    }

    @Substitute
    static void populateFontFileNameMap(HashMap<String, String> fontToFileMap,
                                        HashMap<String, String> fontToFamilyNameMap,
                                        HashMap<String, ArrayList<String>> familyToFontListMap,
                                        Locale locale) {
        throw new UnsupportedOperationException(
                "PrismFontFactory.populateFontFileNameMap is implemented on Windows only");
    }

}
