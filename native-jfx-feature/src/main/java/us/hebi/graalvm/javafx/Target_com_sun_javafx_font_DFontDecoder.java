package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * The CoreText decoder for macOS .dfont files. Its five native methods live in the macOS
 * javafx_font library, so on the other platforms they would have to be stubbed out to link.
 * Nothing outside macOS ever selects this decoder, so the class is dropped instead.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Platforms({ Platform.WINDOWS.class, Platform.LINUX.class })
@Delete
@TargetClass(className = "com.sun.javafx.font.DFontDecoder")
final class Target_com_sun_javafx_font_DFontDecoder {
}
