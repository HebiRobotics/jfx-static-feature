#!/usr/bin/env bash
# Builds the HelloFX example into a native executable against the annotated static JavaFX 26 SDK,
# the Linux counterpart of build-hellofx.ps1. The FX code is linked in statically, the GTK stack it
# calls stays dynamic. Runs inside the container described in the README.
set -euo pipefail

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

echo '== cc (stubs)'
mkdir -p "$target"
stub_obj=$target/foreign_platform_stubs.o
cc -c -O2 -o "$stub_obj" "$root/native-jfx-feature/src/main/c/foreign_platform_stubs_linux.c"

# The feature reaches into the image builder, which lives in a named module at build time.
exports=(
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted.c=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.annotate=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.functions=ALL-UNNAMED
    --add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.headers=ALL-UNNAMED
)

# Libraries the static glass/prism/font code pulls in. STATIC_BUILD only drops the link flags from
# the archives, it does not vendor GTK, so all of these stay dynamic dependencies of the image.
system_libs=(
    -lgtk-3 -lgdk-3 -lgdk_pixbuf-2.0 -lglib-2.0 -lgobject-2.0 -lgio-2.0 -lcairo
    -lpangoft2-1.0 -lpango-1.0 -lfreetype
    -lX11 -lXtst -lXxf86vm -lGL
    -lstdc++ -lm
)
linker_options=()
for option in "${system_libs[@]}" "$stub_obj"; do
    linker_options+=("-H:NativeLinkerOption=$option")
done

class_path=$(IFS=:; echo "$feature_jar:$example_jar:${fx_jars[*]}")

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
    us.hebi.graalvm.javafx.example.Launcher
echo "== native-image done after $((SECONDS - start))s"
