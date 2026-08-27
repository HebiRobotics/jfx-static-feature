# jfx-static-feature

GraalVM native-image support for a statically linked JavaFX 26. The image contains the FX native
code, so on Windows the executable runs from an empty directory with no FX DLLs next to it. On
Linux the GTK stack and on macOS the system frameworks stay dynamic, see below.

Keep the ordinary `org.openjfx` dependencies from Maven Central and add two lines to the
native-image build:

```xml
<dependency>
    <groupId>us.hebi.graalvm</groupId>
    <artifactId>jfx-static-feature</artifactId>
    <version>1.0</version>
</dependency>
<dependency>
    <groupId>us.hebi.graalvm</groupId>
    <artifactId>jfx-static-libs</artifactId>
    <version>${javafx.version}</version>
    <scope>runtime</scope>
</dependency>
```

Nothing else: no `--add-exports`, no `-H:CLibraryPath`, no linker flags, no classifier, no OS
profile, no config files. `jfx-static-libs` carries the JavaFX reachability metadata and every
platform's static archives in one unclassified jar, so Maven and Gradle resolve it the same way
and nothing has to detect the host.

### Versions

The feature has its own version line and does not pin a JavaFX. `jfx-static-libs` is versioned by
the JavaFX release it was compiled from, so the consumer declares it with the same
`<javafx.version>` as the `org.openjfx` jars and picks another JavaFX, say a 26.0.1, by changing
that one property. The feature aborts the build when the jar is missing or its version differs
from the JavaFX on the class path, so a stale pin shows up as a message rather than as a link
error. The substitutions have not had to follow a JavaFX change in years.

`jfx-static-examples/example-hellofx` is the whole recipe, its parent pom holds the dependencies
and the plugin setup and `mvn -Pnative package` there produces a running executable.
`example-headless` is the same thing without a display: it renders a fixed scene on the headless
glass platform, checks its own pixels and writes the snapshot as a PNG, which is what
`.github/workflows/render-check.yml` runs on all five platforms. The windowed pipelines need a real
session and stay a manual check.

Linked and run on all five platforms: `windows-x86_64`, `linux-x86_64`, `linux-aarch64`,
`darwin-x86_64` and `darwin-aarch64`.

## What the feature does

`JfxStaticFeature` tells native-image that the JavaFX natives are compiled into the image rather
than loaded from a DLL. It registers the FX JNI package prefixes as builtin, adds the platform's
static FX libraries as static JNI libraries (seven on Windows, ten on Linux, eight on macOS, see
the SDK sections below), and adds the system libraries those archives reference as dynamic
libraries: `STATIC_BUILD` only drops the link flags from the archive step, it does not vendor
anything, so GTK on Linux and the Cocoa frameworks on macOS stay dynamic dependencies that
something has to name. Naming them here keeps every consumer from carrying the list. What has no
`NativeLibraries` API behind it goes through a linker invocation transformer in `beforeImageWrite`:
the macOS frameworks and the `-force_load` of `libglass.a`, and `g_thread_init` on Linux.

The archives themselves come out of the `jfx-static-libs` jar, see below. The feature only stays
off on a platform it has no archives for; a missing jar or one built from another JavaFX than the
jars on the class path aborts the build with the coordinates to declare.

It also registers the no-argument constructor of every reachable `Application` subclass. Oracle's
built-in `JavaFXFeature` registers the subclasses for reflection but not the constructor, which is
what `LauncherImpl.launchApplication1` instantiates them through, so `getConstructor()` throws on a
class the reflection system otherwise knows about. The handler only fires for subclasses the
analysis already reached, so unused launchers in the same jar cost nothing.

The rest of the jar is substitutions, grouped by the problem they solve:

| Class | Platform | Problem |
|---|---|---|
| `StaticLibraryLoading` | all | `System.loadLibrary` cannot load a static FX library, see below |
| `OverloadedNatives` | Windows, macOS | Substrate links builtin JNI methods by short name, so overloaded natives never resolve |
| `MacStartup` | macOS | glass needs the first thread inside a CFRunLoop, a native image runs `main` there |
| `CompilerWorkarounds` | macOS | GraalVM 25.0.4 fails to outline `MacVariant.toString` |
| `stubs/Delete*Natives` | all | classes with natives that only another platform's library implements |

### Static library loading

`System.loadLibrary("glass")` fails on every platform, for two unrelated reasons. On Windows
`JNI_OnLoad_glass` returns JNI 1.2, while the spec requires 1.8 or later from a statically linked
library and Substrate enforces it. Every other FX library handshakes correctly under
`#ifdef STATIC_BUILD`, glass (`native-glass/win/Utils.cpp`) does not; that is an upstream OpenJFX
bug with a one-line fix, and `Target_Application` calls the initializer directly until it lands.

On Linux and macOS no FX library can be loaded that way at all: Substrate finds the
`JNI_OnLoad_<lib>` of a static library with `dlsym`, but hides every symbol the image does not
export itself behind a linker version script on Linux and an exported symbols list on macOS.
`Target_NativeLibLoader` calls each library's initializer directly instead, which also covers the
version bug (glass reports 1.6 on Linux and 1.4 on macOS).

### Overloaded natives

Substrate resolves the JNI entry point of a builtin native method by its short name only. An
overloaded native only exists under its signature-mangled name on the C side, so it can never
link, and no config file can fix that. `@Substitute` plus `@CFunction` routes the four
`directwrite.OS` overloads (Windows), `MacTimer._start` and
`coretext.OS.CFStringCreateWithCharacters` (macOS) straight to the mangled symbols. The directwrite
functions use neither the JNIEnv nor the jclass; the macOS ones read through the JNIEnv, so they get
the thread's real environment and a local handle.

### Mac startup

glass posts its run loop to the first thread of the process and waits for it, so that thread must
be inside a CFRunLoop while FX starts. The java launcher arranges this by running `main` on a
second thread and parking the first one; a native image runs `main` on the first thread and hangs
without ever showing a window. `Target_LauncherImpl` keeps the upstream launcher thread and pumps
the run loop instead of waiting on the latch, but only when called on the first thread: a launcher
that already runs Cocoa and calls `main` from a background thread (native-launchers-maven-plugin,
Gluon's `AppDelegate.m`) gets the upstream behavior and glass takes its embedded path.

`MacVariant.toString` appends to a variable of type Object, which the string concatenation
outlining of GraalVM 25.0.4 fails to compile. Nothing calls it, so a shorter text keeps
`-H:-OutlineIndyStringConcatenations` out of the build.

### Natives of the other platforms

The analysis keeps the font and image back ends of the other platforms reachable, even though this
platform's static libraries contain none of their natives, and every unresolved native would need a
C stub. The `stubs` package deletes those classes instead: `DFontDecoder` and `MacFontFinder` off
macOS, `FTFactory` and `FontConfigManager` off Linux (the headless glass platform registers the
freetype factory for reflection everywhere, which drags in 45 `OSFreetype`/`OSPango` natives), and
`IosImageLoader` off iOS. `PrismFontFactory` is the base class of every platform factory and cannot
be deleted, so its six Windows-only natives throw instead; every caller is overridden by the Linux
and macOS factories. The conditions are negations (`NotLinux` etc.) rather than `@Platforms`
inclusion lists, so a future iOS or Android port does not silently keep them.

### The JavaFX artifacts

`jfx-static-libs` is one JavaFX build, versioned by it, in a single 10 MB jar with no classifier.
It holds every platform's `lib*.a` (`*.lib` on Windows) under
`us/hebi/graalvm/jfx/libs/<platform>/` for `windows-x86_64`, `linux-x86_64`, `linux-aarch64`,
`darwin-x86_64` and `darwin-aarch64`, spelled the way Gluon Substrate spells its targets. The
feature unpacks the ones it needs into the builder's temp directory in `beforeAnalysis` and adds
that directory to `NativeLibraries.getLibraryPaths()`, the same set `-H:CLibraryPath` feeds and
what `getStaticLibraries()` searches at link time. The other platforms' archives cost a download
and nothing else: no resource configuration names them, so they never enter an image.

It also carries the reachability metadata the fork's SDK generates into
`META-INF/native-image/reachability-generated/org.openjfx/javafx.*/`, which native-image reads off
the class path by itself and Central's `org.openjfx` jars do not have. **That makes the artifact
useful on its own**: added without the feature it gives an ordinary dynamic JavaFX image the
metadata it would otherwise need a tracing agent for, and such an image only has to register its
own `Application` subclass on top, since Oracle's built-in `JavaFXFeature` registers the subtypes
for reflection but not the no-argument constructor the launcher instantiates them through. Naming
it, with or without the feature, is the whole story of moving to a later JavaFX.

It builds on any host from the combined static SDK archive the jfx CI workflow publishes as a
GitHub release named `<fx.version>-<fx.commit>`, every platform's SDK under its own directory.
The pom downloads it next to itself when it is missing, `fx.zip` names an existing one. The
metadata and the license texts are byte identical in every platform's SDK, so both come out of the
one `fx.reference.platform` names, which is the Linux one because it is the only SDK carrying
`gcc.md`.

The archives are OpenJFX build output under GPLv2 with the Classpath Exception. `META-INF/legal/`
carries the license, the exception and the third party notices for what is actually in them, and
`META-INF/NOTICE` names the `ennerf/jfx` commit they were built from.

### Wiring

`META-INF/native-image/us.hebi.graalvm/jfx-static-feature/native-image.properties` contributes
`--features=us.hebi.graalvm.jfx.JfxStaticFeature` and the nine `--add-exports` the feature
and the substitutions need for the image builder internals, which the driver applies before it
loads the feature class. A jar's `Add-Exports` manifest attribute would be the tidier place, but
the driver only reads that one off the `-jar` main jar, not off class path entries.

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

## Building an image

The same command everywhere, from a shell that has the platform's C toolchain (`vcvars64.bat` on
Windows) and a GraalVM 25 in `JAVA_HOME`:

```
mvn -Pnative package -pl jfx-static-examples/example-hellofx -am
```

For your own app, the dependency from the top of this file plus `native-maven-plugin` with
`fallback` off. No `-H:*ConfigurationFiles`, no build arguments and no
`--initialize-at-run-time` are needed, and the plugin's `metadataRepository` can stay off: the
dependencies carry everything JavaFX needs. Verified with Oracle GraalVM 25.0.1 and 25.3.

## Releasing

The two artifacts are released separately, each from its own module with the `release` profile,
which adds the sources and javadoc jars, signs with the `gpg.keyname` from `settings.xml` and
uploads through the `central` server entry:

```
mvn -Prelease deploy -pl jfx-static-libs
mvn -Prelease deploy -pl jfx-static-feature
```

`jfx-static-libs` only when the archive changed, with `fx.commit` bumped and the version suffixed
(`26-1`) if the JavaFX release stayed the same. The examples and the aggregator are never deployed.

## Using it on Linux

The feature adds one linker option of its own here,
`-Wl,--defsym,g_thread_init=0`: glib dropped `g_thread_init` in 2.32 and glassgtk3 still
references it behind a run-time version check no current glib takes, so only the static link trips
over the missing symbol. The value is absolute rather than `abort`, which ld accepts only where
`abort` is already defined at that point in the link.

The link needs the **development** packages of the GTK stack, not just the runtime ones: `-lgtk-3`
and eleven more resolve through the `libfoo.so` symlink that only a `-dev` package installs. On
Debian and Ubuntu this covers all of them, since `libgtk-3-dev` pulls in the cairo, pango,
gdk-pixbuf, glib and X11 development packages:

```bash
apt-get install -y libgtk-3-dev libxtst-dev libxxf86vm-dev libgl1-mesa-dev
```

Without them native-image completes all eight of its stages, reports no undefined symbol, and then
fails in `ld` with `cannot find -lgtk-3`.

Building and running in a container, which is how this is developed from a Windows host:

```dockerfile
FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential zlib1g-dev maven ca-certificates curl \
        libgtk-3-dev libxtst-dev libxxf86vm-dev libgl1-mesa-dev \
        xvfb x11-utils libgl1-mesa-dri fonts-dejavu-core
RUN mkdir -p /opt/graalvm && curl -fsSL \
        "https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_linux-x64_bin.tar.gz" \
    | tar -xz -C /opt/graalvm --strip-components=1
ENV JAVA_HOME=/opt/graalvm PATH=/opt/graalvm/bin:$PATH
```

```powershell
docker run -d --name jfxlink -m 12g --cpus 16 -v "<repo>:/repo" jfx-linux-link sleep infinity
docker exec jfxlink bash -c 'cd /repo && mvn -Pnative package -pl jfx-static-examples/example-hellofx -am'
docker exec jfxlink bash -c 'cd /repo/jfx-static-examples/example-hellofx/target && xvfb-run -a ./hellofx'
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

Two things the feature does only here:

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
