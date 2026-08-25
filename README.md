# native-jfx-feature

GraalVM native-image support for a statically linked JavaFX 26 on Windows, Linux x86_64 and macOS
aarch64. The image contains the FX native code, so on Windows the executable runs from an empty
directory with no FX DLLs next to it. On Linux the GTK stack and on macOS the system frameworks
stay dynamic, see below.

The feature jar carries three things:

- `JavaFXStaticFeature` registers the JNI package prefixes as builtin and adds the static FX
  libraries of the target platform as static JNI libraries. Windows has seven (`glass`,
  `prism_common`, `prism_d3d`, `prism_sw`, `decora_sse`, `javafx_font`, `javafx_iio`), Linux ten
  (`prism_es2` instead of `prism_d3d`, plus `glassgtk3`, `javafx_font_freetype` and
  `javafx_font_pango`) and macOS eight (`prism_es2` and `prism_mtl` instead of `prism_d3d`).

  The system libraries those archives reference are added the same way, as dynamic non-JNI
  libraries: 22 on Windows, 16 on Linux, and `objc` and `c++` on macOS. `STATIC_BUILD` only drops
  the link flags from the archive step, it does not vendor anything, so all of them stay dynamic
  dependencies of the image and something has to name them. Doing it here keeps every consumer
  from carrying the list. What is left over is linker syntax with no `NativeLibraries` API behind
  it: the macOS frameworks and `-force_load`, and `g_thread_init` on Linux.

  It also registers the no-argument constructor of every reachable `Application` subclass, through
  `registerSubtypeReachabilityHandler`. Oracle's built-in `JavaFXFeature` registers the subclasses
  it finds for reflection but not that constructor, which is the one member
  `LauncherImpl.launchApplication1` instantiates them through, so `getConstructor()` throws on a
  class the reflection system otherwise knows about. The handler only fires for subclasses the
  analysis already reached, and reaching one does not follow from the built-in feature registering
  it: none of the twelve `Application` subclasses in `gui` that no launcher starts appear in the
  hebi-charts image, which has that jar on its class path. Where they are reached the cost is real,
  343 classes in a Scope image for the three subclasses `LogViewer` launches, but the shipped
  library serves every launcher from one image and needs them. `registerSubtypeReachabilityHandler`
  is public API in GraalVM 21 through 25.2, unlike the internal `findSubclasses` it replaced, which
  25.2 removed.
- Eight substitutions, each on the platform that needs it:
  - `Target_directwrite_OS` in `OverloadedNatives` (Windows) - Substrate resolves builtin JNI entry
    points by their short name only, so the four overloaded `directwrite.OS` methods can never
    link, because the C side only has them under their signature-mangled names. `@Substitute` plus
    `@CFunction` routes them to the mangled symbols. No config file can fix this.
  - `Target_Application` in `StaticLibraryLoading` (Windows) - `JNI_OnLoad_glass` returns JNI 1.2, while the
    spec requires 1.8 or later for a statically linked library and Substrate enforces it, so
    `System.loadLibrary("glass")` fails. Every other FX library handshakes correctly under
    `#ifdef STATIC_BUILD`, glass (`native-glass/win/Utils.cpp`) does not. Upstream OpenJFX bug,
    one-line fix, worth reporting. The substitution calls the initializer directly.
  - `Target_NativeLibLoader` in `StaticLibraryLoading` (Linux, macOS) - Substrate finds the
    `JNI_OnLoad_<lib>` of a statically linked library with `dlsym`, but hides every symbol the
    image does not export itself behind a linker version script on Linux and an exported symbols
    list on macOS, so no FX library can be loaded that way. The substitution calls each library's
    initializer directly, which also covers the JNI version bug above
    (`native-glass/gtk/launcher.c` and `glass_general.cpp` report 1.6, `mac/GlassApplication.m`
    reports 1.4).
  - `Target_MacTimer` and `Target_coretext_OS` in `OverloadedNatives` (macOS) -
    the same overload problem as `directwrite.OS`, for `MacTimer._start` and
    `coretext.OS.CFStringCreateWithCharacters`. Both C sides use the JNIEnv, so these pass the
    thread's real environment and a local handle for the argument rather than null.
  - `Target_LauncherImpl` in `MacStartup` (macOS) - glass posts its run loop to the
    first thread of the process and waits for it, so that thread must be inside a CFRunLoop and not
    parked on the launcher's latch. The java launcher arranges this by starting main on a second
    thread and parking the first one, a native image calls main on the first thread itself, and FX
    hangs on startup without ever showing a window. The substitution keeps the upstream launcher
    thread and pumps the run loop instead of waiting on the latch, but only when called on the
    first thread: a launcher that already runs Cocoa and calls `main` from a background thread
    (native-launchers-maven-plugin, Gluon's `AppDelegate.m`) gets the upstream behavior, and glass
    takes its embedded path.
  - `Target_MacVariant` in `CompilerWorkarounds` (macOS) - `toString` appends to a variable of type
    Object, which the string concatenation outlining of GraalVM 25.0.4 fails to compile. Nothing
    calls it, so a shorter text keeps `-H:-OutlineIndyStringConcatenations` out of the build.
  - `DeleteMacNatives`, `DeleteLinuxNatives`, `DeleteWindowsNatives` and `DeleteIosNatives` - the font and
    image back ends of the *other* platforms, which the analysis keeps reachable even though this
    platform's static libraries contain none of their natives. Each holder groups the targets that
    belong to one platform's library behind one `NotMacOs`/`NotLinux`/`NotWindows`/`NotIos` predicate, so
    each applies on the two platforms that are not the owner. `@Delete` cuts the class out where
    that is possible: `DFontDecoder` and `MacFontFinder` off macOS, `FTFactory` and
    `FontConfigManager` off Linux (the headless glass platform registers the freetype factory for
    reflection everywhere, which is what drags in the 45 `OSFreetype`/`OSPango` natives), and
    `IosImageLoader` off iOS. `PrismFontFactory` is the exception: it is the base class of every
    platform factory, so its six Windows-only natives are substituted to throw instead. Every caller
    is overridden by the Linux and macOS factories.
    <br>The conditions are negations rather than `@Platforms` inclusion lists, which cannot express
    one: an inclusion list would silently start keeping these on a future iOS or Android port that
    has none of the libraries either.

`META-INF/native-image/us.hebi.graalvm/native-jfx-feature/native-image.properties` contributes only
`--features=us.hebi.graalvm.javafx.JavaFXStaticFeature`. The `--add-exports` the feature needs stay
in the build script, since it is unverified whether native-image applies them from a properties
file early enough to load the feature class. macOS needs one more than the other two, for the JNI
environment and object handles the coretext and MacTimer substitutions pass on.

## Building the static JavaFX 26 SDK on Windows

Checkout of `openjdk/jfx` at tag `26-ga-1` (`ba33d67176`), no source changes needed:

```powershell
$env:VSCOMNTOOLS="C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build"
cd <jfx>
.\gradlew.bat -PSTATIC_BUILD=true -PCOMPILE_MEDIA=false -PCOMPILE_WEBKIT=false sdk
```

Needs JDK 24+ (GraalVM 25.0.1 works), VS2022 BuildTools and a Windows SDK (rc.exe/fxc.exe). No
Cygwin. A missing `vswhere.exe` is a benign warning as long as `VSCOMNTOOLS` is set. The result
lands in `build\sdk`: `lib\` holds the jars plus the static libs, `bin\` only MSVC/UCRT
redistributables.

Build the `jfx26-metadata` branch of https://github.com/ennerf/jfx instead of the tag to get an SDK
whose jars embed their own reachability metadata (`reachability-annotations`, generated into
`META-INF/native-image/reachability-generated/org.openjfx/javafx.<module>/`). That is what makes an
image build without a single external config file.

## Building the static JavaFX 26 SDK on Linux

Same branch and the same gradle command, which `buildSrc/linux.gradle` already wires up for static
builds. `ubuntu:24.04` with `build-essential git ca-certificates curl unzip zip file ant pkg-config
libgl1-mesa-dev libx11-dev libxxf86vm-dev libxt-dev libxtst-dev libgtk-3-dev libgtk2.0-dev` and a
JDK 24+ is enough:

```bash
bash gradlew --no-daemon -PSTATIC_BUILD=true -PCOMPILE_MEDIA=false -PCOMPILE_WEBKIT=false sdk
```

`build/sdk/lib` then holds ten `lib*.a` next to the jars. `libjavafx_iio.a` is not an archive but an
`ld -r` merged object with an `.a` name (`LINUX.iio.linker = "ld"`), which links fine.

## Building the static JavaFX 26 SDK on macOS

Same branch and the same gradle command again, with Xcode 15 and a JDK 24+ as the boot JDK:

```bash
JAVA_HOME=<jdk> bash gradlew --no-daemon \
    -PSTATIC_BUILD=true -PCOMPILE_MEDIA=false -PCOMPILE_WEBKIT=false sdk
```

`build/sdk/lib` holds eight `lib*.a`: `glass`, `prism_common`, `prism_es2`, `prism_mtl`,
`prism_sw`, `decora_sse`, `javafx_font` and `javafx_iio`. There is no separate glass backend
library the way Linux has `glassgtk3`, the Cocoa one is in `libglass.a`.

## Using it on Windows

```powershell
scripts\build-hellofx.ps1                      # -FxSdk and -GraalHome override the defaults
target\hellofx.exe
```

For your own app, on top of an ordinary native-image invocation:

- `-cp` with the static SDK's `javafx.*.jar` files, the feature jar and your classes
- the feature enables itself through the jar's `native-image.properties`
- the six `--add-exports=org.graalvm.nativeimage.builder/...=ALL-UNNAMED` from
  `scripts\build-hellofx.ps1`
- `-H:CLibraryPath=<sdk>\lib` so the linker finds the static FX libraries
- `-H:+UnlockExperimentalVMOptions`, `--no-fallback`

No `-H:*ConfigurationFiles` and no `--initialize-at-run-time` are needed with the annotated SDK.

## Using it on Linux

```bash
FX_SDK=<sdk> GRAAL_HOME=<graalvm> scripts/build-hellofx.sh
target/hellofx
```

The same recipe as on Windows, plus
`-H:NativeLinkerOption=-Wl,--defsym,g_thread_init=abort`: glib dropped `g_thread_init` in 2.32 and
glassgtk3 still references it behind a run-time version check no current glib takes, so only the
static link trips over the missing symbol.

Building and running in a container, which is how this is developed from a Windows host:

```dockerfile
FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential zlib1g-dev maven ca-certificates curl \
        libgtk-3-0 libcairo2 libpango-1.0-0 libpangoft2-1.0-0 libfreetype6 \
        libxtst6 libxxf86vm1 libgl1 libglx-mesa0 \
        xvfb x11-utils libgl1-mesa-dri fonts-dejavu-core
RUN mkdir -p /opt/graalvm && curl -fsSL \
        "https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-x64_bin.tar.gz" \
    | tar -xz -C /opt/graalvm --strip-components=1
ENV JAVA_HOME=/opt/graalvm PATH=/opt/graalvm/bin:$PATH
```

```powershell
docker run -d --name jfxlink -m 12g --cpus 16 `
    -v "<repo>:/repo" -v "<sdk>:/sdk:ro" jfx-linux-link sleep infinity
docker exec jfxlink bash -c 'cd /repo && bash scripts/build-hellofx.sh'
docker exec jfxlink bash -c 'cd /repo/target && xvfb-run -a ./hellofx'
```

### What is static and what is not

The FX native code is in the image, the platform stack it calls is not: `STATIC_BUILD=true` only
drops the `-l` flags from the archive step on Linux, it does not vendor GTK. `ldd` on the HelloFX
image lists 17 direct dependencies, `libgtk-3` and `libgdk-3`, `libgdk_pixbuf-2.0`, glib
(`libglib`/`libgobject`/`libgio`), `libcairo`, `libpango-1.0` and `libpangoft2-1.0`, `libfreetype`,
`libX11`, `libXtst`, `libXxf86vm`, `libGL`, plus `libz`, `libstdc++`, `libm` and libc, which pull in
about 50 more transitively. A machine that can run any GTK application has all of them.

The es2 pipeline needs a GL driver that passes `isGLGPUQualify`, which llvmpipe does not, so a
container without a GPU either falls back to the software pipeline or needs `-Dprism.forceGPU=true`.

## Using it on macOS

```bash
FX_SDK=<sdk> GRAAL_HOME=<graalvm> scripts/build-hellofx.sh
target/hellofx
```

The same script as on Linux, which picks the macOS stubs, frameworks and libraries by `uname`. Two
things are different from the other platforms:

- The frameworks the image links against are the ones `buildSrc/mac.gradle` uses for the dynamic
  build (AppKit, ApplicationServices, Carbon, OpenGL, QuartzCore, Security, Network, Metal) plus
  CoreText and CoreGraphics for the font code and CoreFoundation for the run loop, with `-lobjc`
  and `-lc++`. `otool -L` on the image lists 18 entries, those plus Foundation, CoreServices,
  CoreVideo, `libSystem`, `libz`. They stay dynamic the same way GTK does on Linux, and every
  macOS has them.
- `libglass.a` is linked with `-force_load`. `GlassWindow+Java.m` and `GlassWindow+Overrides.m`
  are Objective-C categories, so their object files define nothing the linker goes looking for and
  are dropped from a static link, which surfaces as `-[GlassWindow _initWithContentRect:...]:
  unrecognized selector` when the first window opens. `-ObjC` would do the same but also loads
  `prism_mtl`'s `MetalShader.o`, whose `jStringToNSString` collides with the one in glass'
  `GlassAccessible.o`.

All three pipelines come up: es2 (the default), `-Dprism.order=sw` and `-Dprism.order=mtl`, which
was expected to fail for lack of metadata and does not. `MacTimer._initIDs` logs four
`CVDisplayLink...error: -6661` lines on every one of them, whether that also happens on a JVM is
unverified; the scene renders regardless.
