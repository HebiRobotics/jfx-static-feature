/*
 * The analysis keeps the macOS, Linux and iOS font and image back ends of javafx.graphics
 * reachable, so their JNI entry points have to resolve even though the Windows static libraries do
 * not contain them. Nothing on Windows ever reaches these code paths; aborting is the honest
 * behaviour if one ever does.
 */
#include <stdio.h>
#include <stdlib.h>

static long long unsupported(const char *name) {
    fprintf(stderr, "hellofx: %s is not available on Windows\n", name);
    abort();
    return 0;
}

#define STUB(name) long long name(void) { return unsupported(#name); }

STUB(Java_com_sun_javafx_font_DFontDecoder_createCTFont)
STUB(Java_com_sun_javafx_font_DFontDecoder_getCTFontFormat)
STUB(Java_com_sun_javafx_font_DFontDecoder_getCTFontTable)
STUB(Java_com_sun_javafx_font_DFontDecoder_getCTFontTags)
STUB(Java_com_sun_javafx_font_DFontDecoder_releaseCTFont)
STUB(Java_com_sun_javafx_font_FontConfigManager_getFontConfig)
STUB(Java_com_sun_javafx_font_FontConfigManager_populateMapsNative)
STUB(Java_com_sun_javafx_font_MacFontFinder_getFont)
STUB(Java_com_sun_javafx_font_MacFontFinder_getFontData)
STUB(Java_com_sun_javafx_font_MacFontFinder_getSystemFontSize)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_disposeLoader)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getColorSpaceCode)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getDelayTime)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getImageBuffer)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_getNumberOfComponents)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_initNativeLoading)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_loadImage)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_loadImageFromURL)
STUB(Java_com_sun_javafx_iio_ios_IosImageLoader_resizeImage)
