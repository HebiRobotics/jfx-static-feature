#!/usr/bin/env bash
# Builds the HelloFX example into a native executable against the annotated static JavaFX 26 SDK,
# the Linux and macOS counterpart of build-hellofx.ps1. The FX code is linked in statically; the
# GTK stack on Linux and the system frameworks on macOS stay dynamic. On Linux this runs inside the
# container described in the README.
set -euo pipefail

case "$(uname -s)" in
    Linux) os=linux ;;
    Darwin) os=macos ;;
    *) echo "Unsupported OS $(uname -s)" >&2; exit 1 ;;
esac

FX_SDK=${FX_SDK:-/sdk}
GRAAL_HOME=${GRAAL_HOME:-${GRAALVM_HOME:-/opt/graalvm}}

root=$(cd "$(dirname "$0")/.." && pwd)
target=$root/target
fx_lib=$FX_SDK/lib

fx_jars=()
for jar in javafx.base.jar javafx.graphics.jar javafx.controls.jar; do
    [ -f "$fx_lib/$jar" ] || { echo "Missing $fx_lib/$jar, build the static SDK first (see README)" >&2; exit 1; }
    fx_jars+=("$fx_lib/$jar")
done

if [ "${1:-}" != "--no-maven" ]; then
    echo '== mvn package'
    mvn -q -f "$root/pom.xml" package
fi

feature_jar=$(ls "$root"/native-jfx-feature/target/native-jfx-feature-*.jar)
example_jar=$(ls "$root"/hellofx-example/target/hellofx-example-*.jar)

# The feature reaches into the image builder, which lives in a named module at build time.
exports=(
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted.c=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.annotate=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.functions=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.headers=ALL-UNNAMED
)

# The feature adds every plain system library the static archives reference. What is left is
# platform linker syntax it has no API for: the macOS frameworks, force-loading libglass.a because
# its GlassWindow categories define no symbol the linker looks for, and a definition for
# g_thread_init, which glassgtk3 still references behind a glib version check no current glib takes.
if [ "$os" = linux ]; then
    linker_options=(-H:NativeLinkerOption=-Wl,--defsym,g_thread_init=abort)
else
    linker_options=("-H:NativeLinkerOption=-Wl,-force_load,$fx_lib/libglass.a")
    for framework in AppKit ApplicationServices Carbon OpenGL QuartzCore Security Network Metal CoreText CoreGraphics CoreFoundation; do
        linker_options+=("-H:NativeLinkerOption=-Wl,-framework,$framework")
    done
fi

class_path=$(IFS=:; echo "$feature_jar:$example_jar:${fx_jars[*]}")

mkdir -p "$target"
echo '== native-image'
start=$SECONDS
"$GRAAL_HOME/bin/native-image" \
    -cp "$class_path" \
    --no-fallback \
    -H:+UnlockExperimentalVMOptions \
    "-H:CLibraryPath=$fx_lib" \
    -o "$target/hellofx" \
    -J-Xmx8g \
    "${exports[@]}" \
    "${linker_options[@]}" \
    ${NATIVE_IMAGE_OPTS:-} \
    us.hebi.graalvm.javafx.example.Launcher
echo "== native-image done after $((SECONDS - start))s"
