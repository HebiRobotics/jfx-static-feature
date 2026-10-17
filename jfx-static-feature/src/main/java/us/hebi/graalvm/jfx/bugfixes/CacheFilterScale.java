package us.hebi.graalvm.jfx.bugfixes;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.geom.Rectangle;
import com.sun.javafx.geom.transform.Affine2D;
import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.sg.prism.NGNode;

/**
 * With CacheHint.SCALE, CacheFilter.render() (javafx.graphics com/sun/javafx/sg/prism/CacheFilter.java:565
 * in jfx 27) renders the cache with (cachedScaleX, 0, 0, cachedScaleX), but updateScreenXform() divides
 * by cachedScaleY. A cached node whose cache gets rebuilt under a non-uniform scale such as Scale(1, s) is
 * drawn at the wrong height, e.g., the Android overscroll stretch was ~100 px off at a bottom pivot, stepped
 * on every repaint and jumped when the cache got dropped.
 * <p>
 * render() is too large to restate, so the fix hooks getCacheBounds(), whose only caller runs right after
 * the faulty setTransform and before anything is drawn into the cache. This only fixes native images, JVM
 * runs keep the bug.
 * TODO: delete once fixed upstream (jfx 28+ candidate, the fix is cachedScaleY in the Myy slot)
 *
 * @author Florian Enner
 * @since 12 Oct 2026
 */
public final class CacheFilterScale {

    @TargetClass(className = "com.sun.javafx.sg.prism.CacheFilter")
    static final class Target_CacheFilter {

        @Alias
        private static RectBounds TEMP_BOUNDS;

        @Alias
        private Affine2D cachedXform;

        @Alias
        private double cachedScaleX;

        @Alias
        private double cachedScaleY;

        @Alias
        private NGNode node;

        @Alias
        private boolean scaleHint;

        @Substitute
        Rectangle getCacheBounds(Rectangle bounds, BaseTransform xform) {
            if (scaleHint && xform == cachedXform) {
                cachedXform.setTransform(cachedScaleX, 0.0,
                        0.0, cachedScaleY,
                        0.0, 0.0);
            }
            final BaseBounds b = node.getClippedBounds(TEMP_BOUNDS, xform);
            bounds.setBounds(b);
            return bounds;
        }

    }

    private CacheFilterScale() {
    }

}
