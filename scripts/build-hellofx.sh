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

echo '== cc (stubs)'
mkdir -p "$target"
stub_obj=$target/foreign_platform_stubs.o
cc -c -O2 -o "$stub_obj" "$root/native-jfx-feature/src/main/c/foreign_platform_stubs_$os.c"

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

# Libraries the static glass/prism/font code pulls in. STATIC_BUILD only drops the link flags from
# the archives, it does not vendor anything, so all of these stay dynamic dependencies of the image.
if [ "$os" = linux ]; then
    system_libs=(
        -lgtk-3 -lgdk-3 -lgdk_pixbuf-2.0 -lglib-2.0 -lgobject-2.0 -lgio-2.0 -lcairo
        -lpangoft2-1.0 -lpango-1.0 -lfreetype
        -lX11 -lXtst -lXxf86vm -lGL
        -lstdc++ -lm
    )
else
    # the frameworks buildSrc/mac.gradle links the dynamic libraries against, plus the font ones.
    # libglass.a is loaded whole because the GlassWindow+Java and +Overrides categories define no
    # symbol the linker looks for and get dropped ("-[GlassWindow _initWithContentRect:...]:
    # unrecognized selector"). -ObjC would also pull in prism_mtl's MetalShader.o, which duplicates
    # glass' _jStringToNSString.
    system_libs=("-Wl,-force_load,$fx_lib/libglass.a" -lobjc -lc++)
    for framework in AppKit ApplicationServices Carbon OpenGL QuartzCore Security Network Metal CoreText CoreGraphics CoreFoundation; do
        system_libs+=("-Wl,-framework,$framework")
    done
fi
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
    ${NATIVE_IMAGE_OPTS:-} \
    us.hebi.graalvm.javafx.example.Launcher
echo "== native-image done after $((SECONDS - start))s"
