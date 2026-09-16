# jfx-static-feature - Statically Linked JavaFX Native Images

This project provides a [GraalVM Feature](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html) that builds JavaFX applications as [GraalVM Native Images](https://www.graalvm.org/native-image/)
with the JavaFX native code statically linked into a standalone executable.

Its main highlights are support for

* **All JavaFX Features** including `Richtext`, `WebView`, and `Media` (see [AtlantaFX sampler](https://youtu.be/GA_iAnxznO8)).

* **All Pipelines** including `sw` fallbacks, as well as the new `mtl` pipeline and `Headless` mode introduced in JavaFX 26. The behavior is the same as on a JVM.

* **All Desktop Platforms** including `windows-amd64` as well as `linux` / `macOS` on both Intel and Arm.

* **Official Sources** without source patches. Static archives are provided for the official `org.openjfx` release commit.

* **Oracle GraalVM** under the free [GFTC](https://www.oracle.com/downloads/licenses/graal-free-license.html) license for better escape analysis, the `G1` GC on Linux, and `PGO`.

* **Simple Configuration** requiring only two runtime dependencies. All configs, linker flags, os classifiers, and profiles are picked up by GraalVM from the classpath.

Note that modules that are not available as static archives (web, media) fall back to dynamic linking and shared libraries placed at the output. The system requirements are the same as on a normal JVM, so Linux and macOS still require a working GTK stack.

## Getting Started

Set up the standard [GraalVM Build Tools](https://graalvm.github.io/native-build-tools/latest/index.html), e.g.,

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.example.HelloFX</mainClass>
        <fallback>false</fallback>
    </configuration>
</plugin>
```

and add the two artifacts in addition to your normal JavaFX dependencies:

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

`jfx-static-libs` contains the reachability metadata and every platform's static archives in
one unclassified jar. The `jfx-static-feature` extracts the archives and hands native-image everything it needs to include JavaFX.

Note that you still need to fulfill the GraalVM prerequisites like a working compiler toolchain.

### Versions

The `jfx-static-libs` archives must be based on the exact same commit as the jars on the class path, so the versioning follows the official `<javafx.version>`. A `jfx-static-libs:27` pairs with the
`org.openjfx` jars at `27`, but neither is compatible with a `27.0.1`. Bugfixes get published with a patch suffix `${javafx.version}-${patch}`. The feature checks the pairing and reports an error in case there is a mismatch.

`jfx-static-feature` is comparatively independent of the JavaFX release and maintains its own version line. It is likely that a feature will work with different GraalVM and JavaFX versions, but here are some combinations that we confirmed working via CI test suites on all platforms:

| JavaFX / `jfx-static-libs` | `jfx-static-feature` | GraalVM | Notes |
|---|---|---|---|
| 27 | 1.0 | 25.0.1, 25.3 | |
| 26.0.2-1 | 1.0 | 25.0.1, 25.3 | added media, web and swing support |
| 26.0.2 | 1.0 | 25.0.1, 25.3 | graphics, controls, fxml and the incubator modules only |

## Platform Notes

### Windows

Executables that contain a JavaFX `Application` subclass are automatically built without a console window. If you want to get it back for e.g. a console tool that renders off-screen, you can override the heuristic with a compile time build option:

```xml
<buildArgs>
  <buildArg>-Djfx.static.gui=false</buildArg> <!-- or auto | true -->
</buildArgs>
```

The property has no effect on other platforms or when building shared libraries.

### Linux

JavaFX requires the standard GTK stack that is generally available on all GUI targets. However, on a minimal or headless server, you may need to install the shared libraries:

```bash
# run time (Debian/Ubuntu)
apt-get install -y libgtk-3-0 libxtst6 libxxf86vm1 libgl1
```

Note that it does not require a display server. The `Headless` glass platform with the software pipeline (`-Dglass.platform=Headless -Dprism.order=sw`) can be used to, e.g., generate images over SSH.

Building the image additionally needs the development packages of the same stack:

```bash
# build time (Debian/Ubuntu)
apt-get install -y libgtk-3-dev libxtst-dev libxxf86vm-dev libgl1-mesa-dev
```

### macOS

macOS requires Cocoa to own the main thread, which tends to complicate things. We were able to fix `Application::launch` by doing a proper handoff using substitutions, but without hacking into brittle GraalVM behavior, non-Headless applications that boot the toolkit via `Platform::startup` from `main` hang forever. We added an error explaining the options to at least remove unexpected application hangs:

Such cases need a separate launcher that keeps the first thread in a run loop and calls `main` from a background thread, e.g., the
[native-launchers-maven-plugin](https://github.com/HebiRobotics/native-launchers-maven-plugin). The same goes for shared libraries where user applications own the main thread.

### Media and Web

The `-PSTATIC_BUILD=true` on OpenJFX does not build static archives for `javafx.media` and `javafx.web`, so we use the shared libraries contained in the OpenJFX platform jars and link against them dynamically. The result is that we need to place some extra libraries next to the executable and can't have everything self-contained.

Note that Media on Linux requires [ffmpeg](https://ffmpeg.org/) to be installed.

```bash
apt-get install -y ffmpeg
```

### Swing interop

Oracle GraalVM 25 ships static libraries for awt, but no metadata for it. Our metadata covers only the `javafx.swing` part, so you need to figure out the metadata for your application's Swing/AWT yourself (see `example-dynamic-check` for some starting points).


## How It Works

JavaFX is a complex framework with a lot of native platform-specific code. Integrating it into a native-image needs three parts that a normal JavaFX build does not provide:

* static archives of the JavaFX native code
* reachability metadata for the framework's internal reflection and JNI
* glue code that bridges the differences between a JVM and a native-image

`jfx-static-libs` covers the first two parts. It is a single jar per JavaFX release that contains the static archives for all platforms as well as the matching reachability metadata. The archives come from the [ennerf/jfx](https://github.com/ennerf/jfx) fork, which adds `@Reachable` annotations on top of the official release tag and generates the metadata at compile time, without changing any product code. GraalVM automatically picks up metadata from the class path, so the jar can also be used standalone for dynamically linked images that would otherwise need a tracing agent.

`jfx-static-feature` covers the glue. At build time it extracts the archives for the current platform and registers them as built-in JNI libraries, which lets native-image verify every native method at link time. It also adds the linker flags for the referenced system libraries, e.g., GTK on Linux and the Cocoa frameworks on macOS, and registers the no-argument constructor of all reachable `Application` subclasses for a reflective launch. The `media` and `web` natives only exist as shared libraries, so the feature instead copies them from the platform jars next to the executable and loads them from there.

The rest is a small set of workarounds for issues that should eventually be fixed upstream:

| Workaround | Platform | Problem                                                                                                                                                                                     |
|---|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `NativeLibraryLoading` | all | `System.loadLibrary` cannot load a static FX library: glass reports JNI versions below the required 1.8, and the image hides the `JNI_OnLoad_<lib>` symbols, so the feature calls the initializers directly |
| `OverloadedNatives` | Windows, macOS | Substrate links builtin JNI methods by short name, so overloaded natives never resolve and get routed to their mangled symbols via `@CFunction`                                             |
| `MacStartup` | macOS | glass needs the first thread inside a CFRunLoop, but a native image runs `main` there. Covers `Application.launch()`; `Platform.startup()` without a launcher that pumps the first thread fails fast instead of hanging |
| `MacMedia` | macOS | AVFoundation rejects the `resource:` URIs of media files inside the image                                                                                                                   |
| `CompilerWorkarounds` | macOS | GraalVM 25.0.4 fails to outline `MacVariant.toString`                                                                                                                                       |
| `stubs/Delete*Natives` | all | classes whose natives only another platform's library implements are deleted, so bad cross-OS metadata fails with a named error instead of unresolved symbols                               |
| `-force_load libglass.a` | macOS | Objective-C categories get dropped from static links                                                                                                                                        |
| `g_thread_init=0` defsym | Linux | glassgtk3 references a symbol that glib removed in 2.32; the zero stub satisfies the linker but crashes if a code path ever calls it                                                        |
| `libjvm` shim / `$ORIGIN` rpath | Linux, macOS | `libjfxwebkit` links against a `libjvm` that does not exist in a native image                                                                                                               |

## Building the Project

The repository builds with Maven and a GraalVM 25+ JDK. Everything under `jfx-static-examples/`
builds a native image with the same command:

```bash
mvn -Pnative package -pl jfx-static-examples/<example> -am
```

| Example | Purpose |
|---|---|
| `example-hellofx` | a minimal app with a button and window for a simple starting point |
| `example-render-check` | renders a fixed set of scenes without a display (2D, 3D, effects, controls, FXML, dialogs, rich text, etc.) and checks its own pixels. `-Dprism.order=d3d` (`es2`, `mtl`, `default`) checks the GPU path of the same image, and `-Dglass.platform=Win` (`Gtk`, `Mac`) renders the same scenes in real windows |
| `example-dynamic-check` | one image with media, web and swing, checked one module per run (`check-dynamic media\|web\|swing`) |
| `example-jni-metadata` | registers every `javafx.graphics` native class across all OS to verify that wrong-platform metadata cannot break the static link |

`.github/workflows/render-check.yml` builds and runs the render check and the web check on all
five platforms for every push, and collects the snapshots into one report artifact.

### Building the Static SDK from Source

The `jfx-static-libs` jar repackages the combined static SDK archive that the fork's CI publishes,
so nothing here has to be built by hand. To reproduce it, build the `jfx27-metadata` branch of
[ennerf/jfx](https://github.com/ennerf/jfx) with the stock OpenJFX gradle build and a plain
JDK 25+ without bundled JavaFX:

```bash
bash gradlew --no-daemon -PSTATIC_BUILD=true -PCOMPILE_MEDIA=false -PCOMPILE_WEBKIT=false sdk
```

`build/sdk/lib` then holds the static libraries next to jars whose `META-INF/native-image/`
embeds the metadata. Per platform:

- **Windows**: VS2022 BuildTools and a Windows SDK (rc.exe/fxc.exe), no Cygwin, and
  `VSCOMNTOOLS` pointing at `...\VC\Auxiliary\Build`.
- **Linux**: `ubuntu:22.04` with `build-essential git ca-certificates curl unzip zip file ant
  pkg-config libgl1-mesa-dev libx11-dev libxxf86vm-dev libxt-dev libxtst-dev libgtk-3-dev
  libgtk2.0-dev`. The published archives build on Ubuntu 22.04 so that the static libraries do
  not inherit newer glibc/GLib symbol redirects.
- **macOS**: Xcode 15.

Each platform's SDK builds on its own OS. The fork's `build-static-libs` workflow does all five
and publishes the combined archive as a GitHub release, which is what `jfx-static-libs` consumes.
How the jar is put together is described in [its own README](jfx-static-libs/README.md).

## Releasing

The two artifacts are released separately, each from its own module with the `release` profile.
The profile adds the sources and javadoc jars, signs with the `gpg.keyname` from `settings.xml`
and uploads through the `central` server entry:

```
mvn -Prelease deploy -pl jfx-static-libs
mvn -Prelease deploy -pl jfx-static-feature
```

`jfx-static-libs` is only deployed for new JavaFX releases or when the metadata changes. The `${jfx.commit}` hash and `${jfx.static.version}` must match the release signature of the fork. See [jfx-static-libs/README.md](jfx-static-libs/README.md) for more information.

The examples and the aggregator are never deployed.

## License

Both artifacts are licensed under the GNU General Public License, version 2, with the Classpath
Exception. That is the same license as OpenJFX itself, whose build output and adapted launcher
code they contain. The Classpath Exception means that applications using these artifacts as
libraries are not affected by the copyleft and keep their own license. The static archives are
OpenJFX build output: `META-INF/legal/` carries the license and third party notices, and
`META-INF/NOTICE` names the `ennerf/jfx` commit they were built from.
