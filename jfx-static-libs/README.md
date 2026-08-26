# jfx-static-libs

One JavaFX release as GraalVM native-image input: the statically linked JavaFX libraries of every
platform and the reachability metadata the [ennerf/jfx](https://github.com/ennerf/jfx) fork
generates from its annotated sources, in a single jar with no classifier.

```xml
<dependency>
    <groupId>us.hebi.graalvm</groupId>
    <artifactId>jfx-static-libs</artifactId>
    <version>26</version>
</dependency>
```

The version is the JavaFX release the contents were built from, so it moves with JavaFX and not
with anything else in this repository. On its own the artifact does two things and no more: it lets
Central's ordinary `org.openjfx` jars build as a dynamic native image without a tracing agent, and
it gives [`jfx-static-feature`](../README.md) the archives to link statically. It does not register
the application's own code. An `Application` subclass still needs its no-argument constructor in
the consumer's own metadata, because Oracle's built-in `JavaFXFeature` registers the subtypes for
reflection but not the constructor `LauncherImpl` calls.

## What is in the jar

| Path | Content |
|---|---|
| `us/hebi/graalvm/jfx/libs/<platform>/` | The `lib*.a` (`*.lib` on Windows) of `windows-x86_64`, `linux-x86_64`, `linux-aarch64`, `darwin-x86_64` and `darwin-aarch64`, spelled the way Gluon Substrate spells its targets |
| `META-INF/native-image/reachability-generated/org.openjfx/<module>/` | `jni-config.json`, `reflect-config.json` and `resource-config.json` of `javafx.base`, `javafx.graphics`, `javafx.controls`, `javafx.fxml`, `jfx.incubator.input` and `jfx.incubator.richtext` |
| `META-INF/legal/` | GPLv2, the Classpath Exception and the third party notices for what the archives contain |
| `META-INF/NOTICE` | The `ennerf/jfx` commit the archives were built from |

The archives are inert without the feature. No resource configuration names them, so they never
enter an image, and the other platforms cost a download and nothing else. native-image reads the
metadata off any class path jar by itself.

The metadata is conditional on JavaFX's own types (`typeReachable`), so an image only carries the
parts of JavaFX it reaches, and the Windows entries in a macOS image fold away at build time.
`javafx.media`, `javafx.web` and `javafx.swing` are not annotated in the fork and have no metadata
here.

## Building it

The input is the combined static SDK archive that the fork's `build-static-libs` workflow
produces, every platform's SDK under its own directory. Put it next to this pom as
`openjfx-26-internal-static.zip`, or name it with `-Dfx.zip=<archive>`, then

```
mvn install
```

There is no reactor dependency in either direction: this pom has no parent, and the feature pulls
the installed artifact by version like any other consumer. The build takes the archives of every
platform, the metadata and the legal texts out of one reference SDK (`fx.reference.platform`,
Linux, because it is the only SDK that carries `gcc.md`), and records `fx.commit` in
`META-INF/NOTICE`. Bump `fx.commit` together with the archive.

## License

The contents are OpenJFX build output under the GNU General Public License, version 2, with the
Classpath Exception, from a fork that adds reachability annotations and changes no product code.
The corresponding sources ship as `src.zip` in the SDK archives the fork's releases publish.
