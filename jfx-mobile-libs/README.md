# jfx-mobile-libs

This artifact is the mobile counterpart of [`jfx-static-libs`](../jfx-static-libs/README.md): the statically
linked JavaFX libraries for Android (`android-aarch64`) and iOS (`ios-arm64`, `ios-arm64-simulator`) and the
same reachability metadata, plus the `javafx.graphics` classes that only the mobile builds contain.

```xml
<dependency>
    <groupId>us.hebi.graalvm</groupId>
    <artifactId>jfx-mobile-libs</artifactId>
    <version>${openjfx.version}</version>
</dependency>
```

OpenJFX publishes no Android or iOS jars, so a mobile image takes the regular `org.openjfx` jars of the
build host and this jar adds what they lack: the Android, Monocle and iOS glass toolkits and the matching
es2 GL classes, about 180 classes. Android images are built on Linux and iOS images on macOS, which is
what selects the host jars. The remaining desktop classes get removed by `jfx-static-feature` at build
time.

The added GL classes share a package with the OpenJFX jar, so mobile images need to be built from the
class path rather than the module path.

The jar is additive to `jfx-static-libs`, and a build may have both on the class path. Desktop-only
projects should not add it, because its classes would end up in every desktop image's analysis and it
excludes the Metal shaders, which a macOS image needs.

## Building the Jar

The build works like the one of `jfx-static-libs`: it downloads the combined static SDK of the fork
release `<jfx.version>-<jfx.commit>` next to this pom on the first run. The classes are the entries of
each target's `javafx.graphics.jar` that are missing from the `org.openjfx:javafx-graphics` jar of its
build host (`linux` for Android, `mac-aarch64` for iOS). The build fails if any of them lies outside the
mobile glass packages, which would mean that the two builds no longer come from the same sources.
