/*
 * The analysis keeps the Windows, macOS and iOS font and image back ends of javafx.graphics
 * reachable, so their JNI entry points have to resolve even though the Linux static libraries do
 * not contain them. Nothing on Linux ever reaches these code paths; aborting is the honest
 * behaviour if one ever does.
 */
#include <stdio.h>
#include <stdlib.h>

static long long unsupported(const char *name) {
    fprintf(stderr, "hellofx: %s is not available on Linux\n", name);
    abort();
    return 0;
}

#define STUB(name) long long name(void) { return unsupported(#name); }

/*
 * glib dropped g_thread_init in 2.32. glassgtk3 still calls it behind a run-time version check that
 * no current glib takes, and a shared library never has to resolve it, so only the static link
 * trips over the missing symbol.
 */
void g_thread_init(void *vtable) {
    (void) vtable;
    unsupported("g_thread_init");
}

STUB(Java_com_sun_javafx_font_PrismFontFactory_getFontPath)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getLCDContrastWin32)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getSystemFontNative)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getSystemFontSizeNative)
STUB(Java_com_sun_javafx_font_PrismFontFactory_getSystemLCID)
STUB(Java_com_sun_javafx_font_PrismFontFactory_populateFontFileNameMap)

STUB(Java_com_sun_javafx_font_DFontDecoder_createCTFont)
STUB(Java_com_sun_javafx_font_DFontDecoder_getCTFontFormat)
STUB(Java_com_sun_javafx_font_DFontDecoder_getCTFontTable)
STUB(Java_com_sun_javafx_font_DFontDecoder_getCTFontTags)
STUB(Java_com_sun_javafx_font_DFontDecoder_releaseCTFont)
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
