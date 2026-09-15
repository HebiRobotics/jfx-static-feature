package us.hebi.graalvm.jfx.substitutions;

import java.net.URI;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Hacks for working around JavaFX media issues in native images
 * TODO: remove once the issues are fixed upstream
 *
 * @author Florian Enner
 * @since 15 Sep 2026
 */
public final class MacMedia {

    /**
     * OSXMediaPlayer.mm only handles 'jar' and 'jrt' URIs through the Locator. All unknown schemes are
     * sent to AVFoundation, which fails on native-image's 'resource' URIs. The native side only checks
     * the name of the scheme, so we replace resource->jar to enter the stream path.
     */
    @Platforms(Platform.MACOS.class)
    @TargetClass(className = LocatorPresent.LOCATOR, onlyWith = LocatorPresent.class)
    static final class Target_Locator {

        @Alias
        protected URI uri;

        @Substitute
        public String getStringLocation() {
            return "resource".equals(uri.getScheme()) ? "jar:" + uri : uri.toString();
        }

    }

    static final class LocatorPresent implements BooleanSupplier {

        static final String LOCATOR = "com.sun.media.jfxmedia.locator.Locator";

        @Override
        public boolean getAsBoolean() {
            try {
                Class<?> locator = Class.forName(LOCATOR, false, LocatorPresent.class.getClassLoader());
                return locator.getDeclaredField("uri").getType() == URI.class
                        && locator.getDeclaredMethod("getStringLocation").getReturnType() == String.class;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

    }

    private MacMedia() {
    }

}
