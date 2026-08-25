package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * The freetype font backend is registered for reflection by the headless platform, which is
 * reachable on every platform, so its 45 native methods have to link even though the Windows and
 * macOS SDKs have no javafx_font_{freetype,pango}. Deleting the factory cuts the whole backend out
 * of the image instead. Windows picks the DirectWrite factory and macOS the CoreText factory on
 * every glass platform, headless included, so nothing ever asks for this one.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Platforms({ Platform.WINDOWS.class, Platform.MACOS.class })
@Delete
@TargetClass(className = "com.sun.javafx.font.freetype.FTFactory")
final class Target_com_sun_javafx_font_freetype_FTFactory {
}
