package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * The CoreText font lookup, only reachable through the macOS font factory.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Platforms({ Platform.WINDOWS.class, Platform.LINUX.class })
@Delete
@TargetClass(className = "com.sun.javafx.font.MacFontFinder")
final class Target_com_sun_javafx_font_MacFontFinder {
}
