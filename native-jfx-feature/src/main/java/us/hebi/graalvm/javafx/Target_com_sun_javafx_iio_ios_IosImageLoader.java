package us.hebi.graalvm.javafx;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * The iOS image loader. No desktop SDK contains its eleven native methods, and no desktop image
 * pipeline reaches it, so it is dropped everywhere rather than stubbed out.
 *
 * @author Florian Enner
 * @since 25 Aug 2026
 */
@Delete
@TargetClass(className = "com.sun.javafx.iio.ios.IosImageLoader")
final class Target_com_sun_javafx_iio_ios_IosImageLoader {
}
