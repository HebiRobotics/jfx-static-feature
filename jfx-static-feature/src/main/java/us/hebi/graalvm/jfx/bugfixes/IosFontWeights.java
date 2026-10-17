package us.hebi.graalvm.jfx.bugfixes;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.sun.javafx.font.FontFactory;
import com.sun.javafx.font.FontResource;
import com.sun.javafx.font.PGFont;
import com.sun.javafx.font.PrismFontFactory;
import com.sun.javafx.font.PrismFontFile;
import com.sun.javafx.scene.text.FontHelper;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Medium, semibold and true bold System text render as regular or semibold on iOS. PrismFontLoader.font()
 * (javafx.graphics com/sun/javafx/font/PrismFontLoader.java:195 in jfx 27) reduces the weight to a bold flag,
 * and CTFontFile.getFontRef() (coretext/CTFontFile.java:265) only knows the regular and the emphasized system
 * font, which is SF Semibold on iOS and the platform's own bold trait.
 * <p>
 * This maps 600 to the emphasized font as well, and 500 and 800/900 to two more members of the system font
 * that draw a copy of it with the UIFontWeight trait. Other families keep the bold flag.
 * TODO: delete once jfx passes font weights to the platform upstream
 *
 * @author Florian Enner
 * @since 14 Oct 2026
 */
public final class IosFontWeights {

    static final String SYSTEM_FONT = "System Font";
    // Display name of the regular UI font on iOS, macOS calls it "System Font"
    static final String SYSTEM_FONT_REGULAR = "System Font Regular";
    static final String MEDIUM_MEMBER = "System Font Medium";
    static final String TRUE_BOLD_MEMBER = "System Font Bold (true)";

    // UIFontWeightMedium and UIFontWeightBold in UIKit's UIFontDescriptor.h
    static final double MEDIUM_WEIGHT = 0.23;
    static final double BOLD_WEIGHT = 0.4;

    /** Member of the System family for weights the bold flag cannot reach, null for the others */
    static String memberName(FontWeight weight) {
        if (weight == null) {
            return null;
        }
        return switch (weight) {
            case MEDIUM -> MEDIUM_MEMBER;
            case EXTRA_BOLD, BLACK -> TRUE_BOLD_MEMBER;
            default -> null;
        };
    }

    // The emphasized System font is iOS's bold trait and SF Semibold, so 600 gets it too
    static boolean isBold(FontWeight weight, boolean systemFamily) {
        FontWeight boldFrom = systemFamily ? FontWeight.SEMI_BOLD : FontWeight.BOLD;
        return weight != null && weight.ordinal() >= boldFrom.ordinal();
    }

    // CTFactory excludes "System Font" from family lookups and maps it to the logical System font
    static boolean isSystemFamily(String family) {
        return "System".equalsIgnoreCase(family) || SYSTEM_FONT.equalsIgnoreCase(family);
    }

    /** UIFontWeight of a member drawn as a weighted copy of the system font, NaN for all other fonts */
    static double weightTrait(String fullName) {
        return switch (fullName) {
            case MEDIUM_MEMBER -> MEDIUM_WEIGHT;
            case TRUE_BOLD_MEMBER -> BOLD_WEIGHT;
            default -> Double.NaN;
        };
    }

    // Not in the family list, so lookups by family and bold flag still see the regular and emphasized fonts only
    static void addMembers(Map<String, String> fontToFileMap, Map<String, String> fontToFamilyNameMap) {
        String systemFont = SYSTEM_FONT_REGULAR.toLowerCase(Locale.ENGLISH);
        String file = fontToFileMap.get(systemFont);
        if (file == null) {
            return;
        }
        for (String member : new String[]{MEDIUM_MEMBER, TRUE_BOLD_MEMBER}) {
            String lcMember = member.toLowerCase(Locale.ENGLISH);
            fontToFileMap.put(lcMember, file);
            fontToFamilyNameMap.put(lcMember, fontToFamilyNameMap.get(systemFont));
        }
    }

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.PrismFontLoader")
    static final class Target_PrismFontLoader {

        @Alias
        private boolean embeddedFontsLoaded;

        @Alias
        private native void loadEmbeddedFonts();

        @Alias
        private native FontFactory getFontFactoryFromPipeline();

        @Substitute
        public Font font(String family, FontWeight weight, FontPosture posture, float size) {
            FontFactory fontFactory = getFontFactoryFromPipeline();
            if (!embeddedFontsLoaded && !fontFactory.isPlatformFont(family)) {
                loadEmbeddedFonts();
            }

            // CTFactory.isExcluded() makes createFont(name, size) refuse the "System Font" names
            boolean italic = posture == FontPosture.ITALIC;
            boolean systemFamily = isSystemFamily(family);
            String member = systemFamily && !italic ? memberName(weight) : null;
            FontResource resource = member != null && fontFactory instanceof PrismFontFactory factory
                    ? factory.getFontResource(member, null, true) : null;
            PGFont prismFont = resource != null
                    ? (PGFont) (Object) new Target_PrismFont(resource, resource.getFullName(), size)
                    : fontFactory.createFont(family, isBold(weight, systemFamily), italic, size);

            return FontHelper.nativeFont(prismFont, prismFont.getName(), prismFont.getFamilyName(),
                    prismFont.getStyleName(), size);
        }

    }

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.PrismFont")
    static final class Target_PrismFont {

        @Alias
        Target_PrismFont(FontResource fontResource, String name, float size) {
        }

    }

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.MacFontFinder")
    static final class Target_MacFontFinder {

        @Alias
        private static native String[] getFontData();

        @Substitute
        public static boolean populateFontFileNameMap(HashMap<String, String> fontToFileMap,
                                                      HashMap<String, String> fontToFamilyNameMap,
                                                      HashMap<String, ArrayList<String>> familyToFontListMap,
                                                      Locale locale) {
            if (fontToFileMap == null || fontToFamilyNameMap == null || familyToFontListMap == null) {
                return false;
            }
            if (locale == null) {
                locale = Locale.ENGLISH;
            }
            String[] fontData = getFontData();
            if (fontData == null) {
                return false;
            }

            int i = 0;
            while (i < fontData.length) {
                String name = fontData[i++];
                String family = fontData[i++];
                String file = fontData[i++];

                if (PrismFontFactory.debugFonts) {
                    System.err.println("[MacFontFinder] Name=" + name);
                    System.err.println("\tFamily=" + family);
                    System.err.println("\tFile=" + file);
                }

                if (name == null || family == null || file == null) {
                    continue;
                }
                String lcName = name.toLowerCase(locale);
                String lcFamily = family.toLowerCase(locale);
                fontToFileMap.put(lcName, file);
                fontToFamilyNameMap.put(lcName, family);
                ArrayList<String> list = familyToFontListMap.get(lcFamily);
                if (list == null) {
                    list = new ArrayList<>();
                    familyToFontListMap.put(lcFamily, list);
                }
                list.add(name);
            }
            addMembers(fontToFileMap, fontToFamilyNameMap);
            return true;
        }

    }

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.coretext.CTFontFile")
    static final class Target_CTFontFile {

        @Alias
        private long cgFontRef;

        @Alias
        private long ctFontRef;

        @Substitute
        long getFontRef(float size, Target_CGAffineTransform matrix) {
            PrismFontFile file = (PrismFontFile) (Object) this;
            long retRef = 0;
            if (file.isEmbeddedFont()) {
                if (cgFontRef != 0) {
                    retRef = Target_OS.CTFontCreateWithGraphicsFont(cgFontRef, size, matrix, 0);
                }
            } else if (ctFontRef != 0) {
                retRef = Target_OS.CTFontCreateCopyWithAttributes(ctFontRef, size, matrix, 0);
            } else {
                String psName = file.getPSName();
                if (psName.startsWith(".")) {
                    // Checked first, indexOf("Bold") would also match the true bold member
                    double weight = weightTrait(file.getFullName());
                    if (!Double.isNaN(weight)) {
                        return CoreText.createWeightedSystemFont(size, matrix, weight);
                    }
                    boolean bold = file.getFullName().indexOf("Bold") > 0;
                    retRef = Target_OS.CTFontCreateUIFontForLanguage(size, matrix, bold);
                } else {
                    final long psNameRef = Target_OS.CFStringCreate(psName);
                    if (psNameRef != 0) {
                        retRef = Target_OS.CTFontCreateWithName(psNameRef, size, matrix);
                        Target_OS.CFRelease(psNameRef);
                    }
                }
            }
            return retRef;
        }

    }

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.coretext.CGAffineTransform")
    static final class Target_CGAffineTransform {
    }

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.coretext.OS")
    static final class Target_OS {

        @Alias
        static native void CFRelease(long cf);

        @Alias
        static native long CFStringCreate(String string);

        @Alias
        static native long CTFontCreateCopyWithAttributes(long cgFont, double size, Target_CGAffineTransform matrix,
                                                          long attributes);

        @Alias
        static native long CTFontCreateWithGraphicsFont(long cgFont, double size, Target_CGAffineTransform matrix,
                                                        long attributes);

        @Alias
        static native long CTFontCreateUIFontForLanguage(double size, Target_CGAffineTransform matrix, boolean bold);

        @Alias
        static native long CTFontCreateWithName(long name, double size, Target_CGAffineTransform matrix);

    }

    // The constants are looked up at run time, a CGlobalData would need build time initialization and an export
    @Platforms(Platform.IOS.class)
    private static final class CoreText {

        private static final int kCFNumberDoubleType = 13;
        private static final long RTLD_DEFAULT = -2;

        // Unannotated methods are not allowed in a substitution class, so this lives here
        static long createWeightedSystemFont(float size, Target_CGAffineTransform matrix, double weight) {
            long font = Target_OS.CTFontCreateUIFontForLanguage(size, null, false);
            if (font == 0) {
                return 0;
            }
            long descriptor = createWeightDescriptor(weight);
            long weighted = Target_OS.CTFontCreateCopyWithAttributes(font, size, matrix, descriptor);
            if (descriptor != 0) {
                Target_OS.CFRelease(descriptor);
            }
            Target_OS.CFRelease(font);
            return weighted;
        }

        /** CTFontDescriptorRef with kCTFontTraitsAttribute {kCTFontWeightTrait: weight}, owned by the caller */
        static long createWeightDescriptor(double weight) {
            PointerBase keyCallBacks = symbol("kCFTypeDictionaryKeyCallBacks");
            PointerBase valueCallBacks = symbol("kCFTypeDictionaryValueCallBacks");

            CDoublePointer value = StackValue.get(CDoublePointer.class);
            value.write(weight);
            PointerBase number = createNumber(WordFactory.nullPointer(), kCFNumberDoubleType, value);
            PointerBase traits = createDictionary(symbol("kCTFontWeightTrait").read(), number,
                    keyCallBacks, valueCallBacks);
            PointerBase attributes = createDictionary(symbol("kCTFontTraitsAttribute").read(), traits,
                    keyCallBacks, valueCallBacks);
            PointerBase descriptor = createDescriptor(attributes);
            release(attributes);
            release(traits);
            release(number);
            return descriptor.rawValue();
        }

        private static PointerBase createDictionary(PointerBase key, PointerBase value,
                                                    PointerBase keyCallBacks, PointerBase valueCallBacks) {
            WordPointer keys = StackValue.get(WordPointer.class);
            keys.write(key);
            WordPointer values = StackValue.get(WordPointer.class);
            values.write(value);
            return createDictionary(WordFactory.nullPointer(), keys, values, 1, keyCallBacks, valueCallBacks);
        }

        private static WordPointer symbol(String name) {
            WordPointer address;
            try (CTypeConversion.CCharPointerHolder symbol = CTypeConversion.toCString(name)) {
                address = lookup(WordFactory.pointer(RTLD_DEFAULT), symbol.get());
            }
            if (address.isNull()) {
                throw new IllegalStateException("CoreText symbol " + name + " not found");
            }
            return address;
        }

        @CFunction("dlsym")
        private static native WordPointer lookup(PointerBase handle, CCharPointer symbol);

        @CFunction("CFNumberCreate")
        private static native PointerBase createNumber(PointerBase allocator, long type, CDoublePointer value);

        @CFunction("CFDictionaryCreate")
        private static native PointerBase createDictionary(PointerBase allocator, WordPointer keys,
                                                           WordPointer values, long count,
                                                           PointerBase keyCallBacks, PointerBase valueCallBacks);

        @CFunction("CTFontDescriptorCreateWithAttributes")
        private static native PointerBase createDescriptor(PointerBase attributes);

        @CFunction("CFRelease")
        private static native void release(PointerBase reference);

        private CoreText() {
        }

    }

    private IosFontWeights() {
    }

}
