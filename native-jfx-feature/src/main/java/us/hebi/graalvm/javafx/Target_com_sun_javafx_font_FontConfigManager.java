package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * The fontconfig bridge, only reachable through the freetype font factory that
 * {@link Target_com_sun_javafx_font_freetype_FTFactory} already drops off Linux.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Platforms({ Platform.WINDOWS.class, Platform.MACOS.class })
@Delete
@TargetClass(className = "com.sun.javafx.font.FontConfigManager")
final class Target_com_sun_javafx_font_FontConfigManager {
}
