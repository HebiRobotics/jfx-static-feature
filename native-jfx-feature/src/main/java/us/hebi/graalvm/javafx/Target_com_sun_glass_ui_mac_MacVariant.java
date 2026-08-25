package us.hebi.graalvm.javafx;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * {@code MacVariant.toString} appends to a variable of type Object, which the string concatenation
 * outlining of GraalVM 25.0.4 cannot compile ("failed building outlined SB method ... unexpected
 * input could not be handled: class java.lang.Object"). Nothing calls it, so a shorter text spares
 * every image the {@code -H:-OutlineIndyStringConcatenations} workaround.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
@Platforms(Platform.MACOS.class)
@TargetClass(className = "com.sun.glass.ui.mac.MacVariant")
final class Target_com_sun_glass_ui_mac_MacVariant {

    @Alias //
    int type;

    @Substitute
    @Override
    public String toString() {
        return "MacVariant type: " + type;
    }

}
