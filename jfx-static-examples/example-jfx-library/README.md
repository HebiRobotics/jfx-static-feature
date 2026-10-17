# JavaFX as a shared library

This example builds a small JavaFX application into a shared library instead of an executable, for desktop hosts written in C or C++ and for Android and iOS apps. CI links it for every desktop platform and for both mobile ones. It stops at the library: a host creates the isolate with `hellofx_create_isolate` and calls `hellofx_launch` on a thread of its own, which blocks until the application exits. On the phones the host also has to give glass its window, which is not part of this repository.

## Desktop

The `native` profile builds `target/libhellofx` (`.dll` with its import `.lib` on Windows, `.so` on Linux, `.dylib` on macOS) with the same dependencies as the other examples:

```
mvn package -Pnative
```

## Mobile

The mobile profiles swap jfx-static-libs for jfx-mobile-libs and add jdk-mobile-libs for the JDK and GraalVM parts, which matches one Oracle GraalVM release (currently 25.4.4.1.1). Only one of the two libs jars may be on the class path. JavaFX itself still comes from Maven Central: the Linux jars that Maven picks on the build host are the ones Android uses, the macOS jars the ones iOS uses.

### Android

Android libraries build on a Linux x86_64 host with Oracle GraalVM 25.4 and the Android NDK (r29 is tested). Point `ANDROID_NDK_HOME` at the NDK and build with the `android` profile:

```
export JAVA_HOME=/path/to/graalvm-25.4
export ANDROID_NDK_HOME=/path/to/android-ndk-r29
mvn package -Pandroid
```

The result is `target/libhellofx.so`. The profile passes the target platform, the NDK's clang for the API level in the `android.api` property (26 by default), and `-J-Djdk.internal.foreign.CABI=LINUX_AARCH_64`. The last one is needed because the builder generates the FFM stubs for its own calling convention, which is x86_64 on the build host.

### iOS

iOS libraries build on an Apple Silicon Mac with Xcode and Oracle GraalVM 25.4, with the `ios` profile for devices and `ios-simulator` for the simulator:

```
export JAVA_HOME=/path/to/graalvm-25.4/Contents/Home
mvn package -Pios
```

The result is `target/libhellofx.dylib` with the functions of the generated `libhellofx.h`. The `ios` profile passes `-R:ReservedAddressSpaceSize=2g`, because iOS grants an app less address space than an isolate reserves by default; without it, creating the isolate fails on a device but not on the simulator. The `ios-simulator` profile selects the simulator with `-H:CCompilerOption=--target=arm64-apple-ios15.0-simulator`.

Both mobile profiles also pass `-H:CAPCacheDir` and `-H:CLibraryPath` into `target`, which keeps the unpacked archives of jdk-mobile-libs there and can be left out without Maven.
