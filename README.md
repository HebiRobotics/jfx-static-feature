# native-jfx-feature

GraalVM native-image support for a statically linked JavaFX 26 on Windows. The image contains the
FX native code, so the executable runs from an empty directory with no FX DLLs next to it.

The feature jar carries three things:

- `JavaFXStaticFeature` registers the JNI package prefixes as builtin and adds the seven static FX
  libraries (`glass`, `prism_common`, `prism_d3d`, `prism_sw`, `decora_sse`, `javafx_font`,
  `javafx_iio`) as static JNI libraries. It also registers the `Application` subclass constructors,
  which Oracle's built-in `JavaFXFeature` misses even though `Application.launch` uses them.
- Two substitutions for real bugs:
  - `Target_com_sun_javafx_font_directwrite_OS` - Substrate resolves builtin JNI entry points by
    their short name only, so the four overloaded `directwrite.OS` methods can never link, because
    the C side only has them under their signature-mangled names. `@Substitute` plus `@CFunction`
    routes them to the mangled symbols. No config file can fix this.
  - `Target_com_sun_glass_ui_Application` - `JNI_OnLoad_glass` returns JNI 1.2, while the spec
    requires 1.8 or later for a statically linked library and Substrate enforces it, so
    `System.loadLibrary("glass")` fails. Every other FX library handshakes correctly under
    `#ifdef STATIC_BUILD`, glass (`native-glass/win/Utils.cpp`) does not. Upstream OpenJFX bug,
    one-line fix, worth reporting. The substitution calls the initializer directly.
- `src/main/c/foreign_platform_stubs.c` - 19 print-and-abort stubs for the macOS, Linux and iOS
  font and image natives that the analysis keeps reachable and that the Windows static libraries do
  not contain. Nothing on Windows reaches them.

`META-INF/native-image/us.hebi.graalvm/native-jfx-feature/native-image.properties` contributes only
`--features=us.hebi.graalvm.javafx.JavaFXStaticFeature`. The six `--add-exports` the feature needs
stay in the build script, since it is unverified whether native-image applies them from a
properties file early enough to load the feature class.

## Building the static JavaFX 26 SDK

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

## Using it

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
