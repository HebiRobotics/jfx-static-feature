/*
 * The analysis keeps the Windows, Linux and iOS font and image back ends of javafx.graphics
 * reachable, so their JNI entry points have to resolve even though the macOS static libraries do
 * not contain them. Nothing on macOS ever reaches these, abort if one does.
 */
#include <stdio.h>
#include <stdlib.h>

static long long unsupported(const char *name) {
    fprintf(stderr, "hellofx: %s is not available on macOS\n", name);
    abort();
    return 0;
}

#define STUB(name) long long name(void) { return unsupported(#name); }

STUB(Java_com_sun_javafx_font_PrismFontFactory_getFontPath)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getLCDContrastWin32)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getSystemFontNative)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getSystemFontSizeNative)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getSystemLCID)
STUB(Java_com_sun_javafx_font_PrismFontFactory_populateFontFileNameMap)

STUB(Java_com_sun_javafx_font_FontConfigManager_getFontConfig)
STUB(Java_com_sun_javafx_font_FontConfigManager_populateMapsNative)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_disposeLoader)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getColorSpaceCode)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getDelayTime)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getImageBuffer)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getNumberOfComponents)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_initNativeLoading)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_loadImage)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_loadImageFromURL)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_resizeImage)
