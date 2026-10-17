package us.hebi.graalvm.jfx.stubs;

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Deletes the classes with JNI calls that are only implemented on macOS, or on macOS and iOS. Otherwise,
 * the analysis keeps them reachable and would need C stubs.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
final class DeleteMacNatives {

    @Delete
    @TargetClass(className = "com.sun.javafx.font.DFontDecoder", onlyWith = {NotMacNorIos.class, ClassPresent.class})
    static final class Target_DFontDecoder {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.MacFontFinder", onlyWith = {NotMacNorIos.class, ClassPresent.class})
    static final class Target_MacFontFinder {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.coretext.CTFactory", onlyWith = {NotMacNorIos.class, ClassPresent.class})
    static final class Target_CTFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.javafx.font.coretext.OS", onlyWith = {NotMacNorIos.class, ClassPresent.class})
    static final class Target_CoreTextOS {
    }

    // iOS is Darwin, but its archives implement none of the macOS glass, es2 and Metal natives.
    // PlatformFactory names the factory, which has no natives but keeps the rest of the glass reachable

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacPlatformFactory", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacPlatformFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacAccessible", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacAccessible {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacApplication", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacApplication {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacCommonDialogs", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacCommonDialogs {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacCursor", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacCursor {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacFileNSURL", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacFileNSURL {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacGestureSupport", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacGestureSupport {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacMenuBarDelegate", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacMenuBarDelegate {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacMenuDelegate", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacMenuDelegate {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacPasteboard", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacPasteboard {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacPixels", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacPixels {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacRobot", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacRobot {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacSystemClipboard", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacSystemClipboard {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacSystemClipboard$FormatEncoder", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacSystemClipboard_FormatEncoder {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacTimer", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacTimer {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacView", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacView {
    }

    @Delete
    @TargetClass(className = "com.sun.glass.ui.mac.MacWindow", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacWindow {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.MacGLContext", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacGLContext {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.MacGLDrawable", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacGLDrawable {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.MacGLFactory", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacGLFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.es2.MacGLPixelFormat", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MacGLPixelFormat {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.mtl.MTLContext", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MTLContext {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.mtl.MTLGraphics", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MTLGraphics {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.mtl.MTLPipeline", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MTLPipeline {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.mtl.MTLRTTexture", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MTLRTTexture {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.mtl.MTLResourceFactory", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MTLResourceFactory {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.mtl.MTLShader", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MTLShader {
    }

    @Delete
    @TargetClass(className = "com.sun.prism.mtl.MTLTexture", onlyWith = {NotMacOs.class, ClassPresent.class})
    static final class Target_MTLTexture {
    }

    // iOS uses the same CoreText backend
    static final class NotMacNorIos implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.DARWIN.class);
        }
    }

    static final class NotMacOs implements BooleanSupplier {
        public boolean getAsBoolean() {
            return !Platform.includedIn(Platform.MACOS.class);
        }
    }

}
