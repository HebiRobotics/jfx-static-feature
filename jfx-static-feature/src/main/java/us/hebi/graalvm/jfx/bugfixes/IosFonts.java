package us.hebi.graalvm.jfx.bugfixes;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.sun.javafx.font.FontResource;
import com.sun.javafx.font.PrismFontFactory;
import com.sun.javafx.font.PrismFontFile;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.HashMap;

/**
 * Bold System text renders regular on iOS. The system font is one variable font file there (SFUI.ttf)
 * for all weights, and PrismFontFactory.createFontResource() (javafx.graphics
 * com/sun/javafx/font/PrismFontFactory.java:228 in jfx 27) caches font files by file name, so the
 * emphasized font gets the cached regular one. JDK-8246104 added the font name to the key, but on macOS
 * only. iOS also calls its emphasized font "System Font Semibold", which CTFontFile.isBold() and
 * getFontRef() only know by the macOS name "System Font Bold". With that name they draw the emphasized
 * system font, which is SF Semibold on iOS.
 * <p>
 * This restates the method with the name in the key on iOS too.
 * TODO: delete once fixed upstream (JDK-8246104 keyed by name on iOS too)
 *
 * @author Florian Enner
 * @since 13 Oct 2026
 */
public final class IosFonts {

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.PrismFontFactory")
    static final class Target_PrismFontFactory {

        @Alias
        private HashMap<String, PrismFontFile> fileNameToFontResourceMap;

        @Alias
        protected native PrismFontFile createFontFile(String name, String filename, int fIndex, boolean register,
                                                      boolean embedded, boolean copy) throws Exception;

        @Alias
        protected native void storeInMap(String name, FontResource resource);

        @Substitute
        private PrismFontFile createFontResource(String name, String filename, int index, boolean register,
                                                 boolean embedded, boolean copy) {
            if ("System Font Semibold".equals(name)) {
                name = "System Font Bold";
            }
            String key = (filename + index).toLowerCase();
            if (name != null && name.startsWith("System ")) {
                key += name;
            }
            PrismFontFile fr = fileNameToFontResourceMap.get(key);
            if (fr != null) {
                return fr;
            }
            try {
                fr = createFontFile(name, filename, index, register, embedded, copy);
                if (register) {
                    storeInMap(fr.getFullName(), fr);
                    fileNameToFontResourceMap.put(key, fr);
                }
                return fr;
            } catch (Exception e) {
                if (PrismFontFactory.debugFonts) {
                    e.printStackTrace();
                }
                return null;
            }
        }

    }

    private IosFonts() {
    }

}
