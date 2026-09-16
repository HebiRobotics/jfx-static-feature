# jfx-static-libs

This artifact packages one JavaFX release as input for GraalVM native-image builds: the statically
linked JavaFX libraries of every platform and GraalVM reachability metadata that gets generated from source annotations in the [ennerf/jfx](https://github.com/ennerf/jfx) fork.

```xml
<dependency>
    <groupId>us.hebi.graalvm</groupId>
    <artifactId>jfx-static-libs</artifactId>
    <version>${openjfx.version}</version>
</dependency>
```

The static build needs to match the exact build of the OpenJFX runtime artifacts, so we follow the official releases and provide the static libs for the same commit with the same name. The artifacts are immutable, so any necessary update gets an optional patch suffix like `${openjfx.version}-${patch}`.

The artifact can be used standalone purely to serve as a source for `org.openjfx` metadata for native-image, or combined with [`jfx-static-feature`](../README.md), which extracts the archives and uses them for static compilation. 

The metadata entries are conditional on JavaFX's own types (`typeReachable`), so parts that do not get used do not get included in the image. Entries for other platforms are removed at build time.

## Structure

The artifact follows the Gluon Substrate convention for static libraries, with the following structure:

| Path | Content                                                                                                                                                             |
|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `us/hebi/graalvm/jfx/libs/<platform>/` | The `lib*.a` (`*.lib` on Windows) of `windows-x86_64`, `linux-x86_64`, `linux-aarch64`, `darwin-x86_64` and `darwin-aarch64` |
| `META-INF/native-image/reachability-generated/org.openjfx/<module>/` | JSON reachability metadata for all supported modules                                                                                                                |
| `META-INF/legal/` | GPLv2, the Classpath Exception and the third party notices for what the archives contain                                                                            |
| `META-INF/NOTICE` | The `ennerf/jfx` commit the archives were built from                                                                                                                |

The archives for all platforms are only about 10MB, so we package them into one jar without platform classifiers. The extra archives do not get included by GraalVM.


## Building the Jar

The contents come from the combined static SDK archive that the fork's `build-static-libs`
workflow publishes as the GitHub release `<jfx.version>-<jfx.commit>`. The build downloads the
archive next to this pom on the first run. You can also place one there manually or select one
with `-Djfx.zip=<archive>`.

The pom is standalone and has no reactor dependency in either direction. The feature resolves the
installed artifact by version like any other consumer. The archives get extracted from every
platform's SDK. Each build name contains the corresponding `jfx.commit` to be uniquely identifiable.

The steps for moving to a new JavaFX release are described in
[metadata-update-guidelines.md](metadata-update-guidelines.md).

## Building the Static SDK from Source

Each [ennerf/jfx release](https://github.com/ennerf/jfx/releases) is named `<version>-<commit>`
and links the source commit, which is also recorded in the jar's `META-INF/NOTICE`. To reproduce
an archive, check out that commit and build it with the stock OpenJFX gradle build and a plain
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
and publishes the combined archive as a GitHub release, which is what this artifact consumes.

## Releasing

The two artifacts do not depend on each other and are released separately with a `release` profile:

```
mvn -Prelease deploy -pl jfx-static-libs
mvn -Prelease deploy -pl jfx-static-feature
```

`jfx-static-libs` is only deployed for new JavaFX releases or when the metadata changes. The `${jfx.commit}` hash and `${jfx.static.version}` must match the release signature of the fork. The steps are described in [metadata-update-guidelines.md](metadata-update-guidelines.md).

The examples and the aggregator are never deployed.

## License

The contents are OpenJFX build output under the GNU General Public License, version 2, with the
Classpath Exception, built from a fork that adds reachability annotations and changes no product
code. The corresponding sources ship as `src.zip` in the fork's release archives.
