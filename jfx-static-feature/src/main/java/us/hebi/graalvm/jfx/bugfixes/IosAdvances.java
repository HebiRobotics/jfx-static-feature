package us.hebi.graalvm.jfx.bugfixes;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.sun.javafx.font.CharToGlyphMapper;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Arrays;

/**
 * Text on iOS is narrower than native, and semibold is spaced like regular. PrismFontFile.getAdvance()
 * (javafx.graphics com/sun/javafx/font/PrismFontFile.java:1167 in jfx 27) reads advances from the font
 * file's hmtx, which for the variable system font (SFUI.ttf) is its default instance, while the glyphs are
 * drawn from the CTFont for the actual weight and optical size. macOS only asks CoreText for color glyphs
 * (JDK-8290866), and the hmtx advances also miss the per size tracking that CoreText's advances include.
 * <p>
 * This asks CoreText for every glyph on iOS and caches the advances per font file and size. Kerning is
 * still not applied.
 * TODO: delete once PrismFontFile takes variable font advances from the platform upstream
 *
 * @author Florian Enner
 * @since 14 Oct 2026
 */
public final class IosAdvances {

    @Platforms(Platform.IOS.class)
    @TargetClass(className = "com.sun.javafx.font.PrismFontFile")
    static final class Target_PrismFontFile {

        @Inject
        private AdvanceCache advances;

        @Alias
        protected native float getAdvanceFromPlatform(int glyphCode, float ptSize);

        @Substitute
        public float getAdvance(int glyphCode, float ptSize) {
            // A zero size CTFont has the default size
            if (glyphCode == CharToGlyphMapper.INVISIBLE_GLYPH_ID || ptSize == 0) {
                return 0f;
            }

            // PrismTextLayout calls this per glyph without a strike, so the cache lives on the font file
            AdvanceCache cache = advances;
            if (cache == null) {
                advances = cache = new AdvanceCache();
            }
            float advance = cache.get(glyphCode, ptSize);
            if (!Float.isNaN(advance)) {
                return advance;
            }

            advance = getAdvanceFromPlatform(glyphCode, ptSize);
            cache.put(glyphCode, ptSize, advance);
            return advance;
        }

    }

    /**
     * Advances by glyph for the last size and up to maxSizes others, NaN where not computed yet. Unsynchronized,
     * a race only computes an advance twice. Arrays are only reachable through the final fields of an Advances,
     * so a reader never sees an unfilled array or the array of another size.
     */
    static final class AdvanceCache {

        static final int maxSizes = 16;

        float get(int glyphCode, float ptSize) {
            Advances entry = last;
            if (entry == null || entry.ptSize != ptSize) {
                entry = find(ptSize);
                if (entry == null) {
                    return Float.NaN;
                }
                last = entry;
            }
            float[] values = entry.values;
            return glyphCode < values.length ? values[glyphCode] : Float.NaN;
        }

        void put(int glyphCode, float ptSize, float advance) {
            Advances entry = last;
            if (entry == null || entry.ptSize != ptSize) {
                entry = find(ptSize);
            }
            if (entry == null || glyphCode >= entry.values.length) {
                entry = new Advances(ptSize, grow(entry == null ? null : entry.values, glyphCode + 1));
                store(entry);
            }
            entry.values[glyphCode] = advance;
            last = entry;
        }

        private Advances find(float ptSize) {
            Advances[] entries = sizes;
            if (entries != null) {
                for (Advances entry : entries) {
                    if (entry != null && entry.ptSize == ptSize) {
                        return entry;
                    }
                }
            }
            return null;
        }

        private void store(Advances entry) {
            Advances[] entries = sizes;
            int n = entries == null ? 0 : entries.length;
            for (int i = 0; i < n; i++) {
                if (entries[i] != null && entries[i].ptSize == entry.ptSize) {
                    Advances[] copy = entries.clone();
                    copy[i] = entry;
                    sizes = copy;
                    return;
                }
            }
            Advances[] copy = n == 0 || n == maxSizes ? new Advances[1] : Arrays.copyOf(entries, n + 1);
            copy[copy.length - 1] = entry;
            sizes = copy;
        }

        static float[] grow(float[] advances, int minLength) {
            int oldLength = advances == null ? 0 : advances.length;
            float[] grown = new float[Math.max(minLength, Math.max(256, 2 * oldLength))];
            Arrays.fill(grown, oldLength, grown.length, Float.NaN);
            if (advances != null) {
                System.arraycopy(advances, 0, grown, 0, oldLength);
            }
            return grown;
        }

        private Advances last;
        private Advances[] sizes;

        private static final class Advances {

            Advances(float ptSize, float[] values) {
                this.ptSize = ptSize;
                this.values = values;
            }

            final float ptSize;
            final float[] values;

        }

    }

    private IosAdvances() {
    }

}
