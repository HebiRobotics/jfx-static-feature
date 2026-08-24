# native-jfx-feature

GraalVM native-image support for a statically linked JavaFX 26 on Windows and Linux x86_64. The
image contains the FX native code, so on Windows the executable runs from an empty directory with
no FX DLLs next to it. On Linux the GTK stack stays dynamic, see below.

The feature jar carries three things:

- `JavaFXStaticFeature` registers the JNI package prefixes as builtin and adds the static FX
  libraries of the target platform as static JNI libraries. Windows has seven (`glass`,
  `prism_common`, `prism_d3d`, `prism_sw`, `decora_sse`, `javafx_font`, `javafx_iio`), Linux ten
  (`prism_es2` instead of `prism_d3d`, plus `glassgtk3`, `javafx_font_freetype` and
  `javafx_font_pango`). It also registers the `Application` subclass constructors, which Oracle's
  built-in `JavaFXFeature` misses even though `Application.launch` uses them.
- Three substitutions for real bugs, each on the platform that has it:
  - `Target_com_sun_javafx_font_directwrite_OS` (Windows) - Substrate resolves builtin JNI entry
    points by their short name only, so the four overloaded `directwrite.OS` methods can never
    link, because the C side only has them under their signature-mangled names. `@Substitute` plus
    `@CFunction` routes them to the mangled symbols. No config file can fix this.
  - `Target_com_sun_glass_ui_Application` (Windows) - `JNI_OnLoad_glass` returns JNI 1.2, while the
    spec requires 1.8 or later for a statically linked library and Substrate enforces it, so
    `System.loadLibrary("glass")` fails. Every other FX library handshakes correctly under
    `#ifdef STATIC_BUILD`, glass (`native-glass/win/Utils.cpp`) does not. Upstream OpenJFX bug,
    one-line fix, worth reporting. The substitution calls the initializer directly.
  - `Target_com_sun_glass_utils_NativeLibLoader` (Linux) - Substrate finds the `JNI_OnLoad_<lib>` of
    a statically linked library with `dlsym`, but hides every symbol the image does not export
    itself behind a linker version script, so no FX library can be loaded that way. The
    substitution calls each library's initializer directly, which also covers the JNI version bug
    above (`native-glass/gtk/launcher.c` and `glass_general.cpp` both report 1.6).
- `src/main/c/foreign_platform_stubs_{windows,linux}.c` - print-and-abort stubs for the other
  platforms' font and image natives that the analysis keeps reachable and that the static libraries
  of this platform do not contain. Nothing ever reaches them. The Linux file also stubs
  `g_thread_init`, which glib dropped in 2.32 and glassgtk3 still references behind a run-time
  version check.

`META-INF/native-image/us.hebi.graalvm/native-jfx-feature/native-image.properties` contributes only
`--features=us.hebi.graalvm.javafx.JavaFXStaticFeature`. The six `--add-exports` the feature needs
stay in the build script, since it is unverified whether native-image applies them from a
properties file early enough to load the feature class.

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
- the 22 Windows system libs and `foreign_platform_stubs.obj` as `-H:NativeLinkerOption=...`
  (the list is not minimized)
- `-H:+UnlockExperimentalVMOptions`, `--no-fallback`

No `-H:*ConfigurationFiles` and no `--initialize-at-run-time` are needed with the annotated SDK.

## Using it on Linux

```bash
FX_SDK=<sdk> GRAAL_HOME=<graalvm> scripts/build-hellofx.sh
target/hellofx
```

The same recipe as on Windows, with the system libraries from `scripts/build-hellofx.sh` and
`foreign_platform_stubs_linux.o` instead of the Windows ones.

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
