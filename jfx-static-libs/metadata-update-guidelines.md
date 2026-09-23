# Updating to a new JavaFX release

The exact process for moving `jfx-static-libs` to a new JavaFX release, written so that an agent can
follow it step by step. It covers the annotated fork [jfx](https://github.com/ennerf/jfx),
where the metadata is determined and committed, and this repository, where the build is consumed
and tested. Last done for 27 on 2026-09-15 (branch `jfx27-metadata`, base `27-ga`).

The fork adds no product code. Its annotations from `us.hebi.graalvm:reachability-annotations` have
`SOURCE` retention, and the processor generates `META-INF/native-image/` into each module's jar. A
release of the fork is therefore exactly one upstream tag plus three commits, and all the work is in
deciding what goes into the third one.

Steps 1 to 6 happen in the fork checkout: pick the base tag, rebuild the branch as three commits,
and verify the generated metadata against the previous release. Step 7 happens in this repository:
consume the fork build, extend the checks for the release's new features, and test on every
platform.

## 1. Pick the base

| Release | Upstream repository | Base | Fork branch |
|---|---|---|---|
| Feature release `N` | `openjdk/jfx`, branch `jfxN` | tag `N-ga` | `jfxN-metadata` |
| Update release `N.0.x` | `openjdk/jfxNu` | tag `N.0.x-ga` | `jfxNu-metadata` |

```bash
git fetch origin --tags                                        # openjdk/jfx
git fetch https://github.com/openjdk/jfx26u.git 'refs/tags/26.0.3-ga:refs/tags/26.0.3-ga'
```

Base on the `-ga` tag, not the head of the stabilization branch. OpenJFX does not build a separate
GA binary: the last promoted build becomes the release, and `N-ga` points at the same commit as
that build's `N+B` tag. Commits after the tag, like the release notes, are not part of the shipped
code. To confirm, the build number in Central's jar must name the tag's build:

```bash
git tag --points-at 27-ga                                      # 27+30 27-ga
curl -sfLO https://repo1.maven.org/maven2/org/openjfx/javafx-base/27/javafx-base-27-win.jar
unzip -p javafx-base-27-win.jar com/sun/javafx/runtime/VersionInfo.class | strings | grep '^27'
```

Before GA, the head of `jfxN` is fine as a temporary base when `git diff --stat <last build
tag>..jfxN` is docs only, and the `N-ea+B` artifacts on Central (built from tag `N+B`) allow
testing on the JVM. Once the `-ga` tag exists, rebuild the branch on it with the steps below and keep
the pre-GA branch under another name (`jfx27-metadata-tmp`) until the new one is verified.

The version the fork build reports does not depend on the base. It comes from
`PROMOTED_BUILD_NUMBER`, which only Oracle's CI sets, so every fork build is `N-internal` and its
release is named `N-internal-<commit>`.

## 2. Commit structure

Every release branch has exactly these three commits on the base, in this order, with these
subjects. The previous branch may have grown merges and follow-up commits since its release. They
are flattened into the first two commits, never cherry-picked one by one.

| # | Subject | Content |
|---|---|---|
| 1 | `added the reachability annotation processor and the static build workflow` | `build.gradle` (processor wiring, `REACHABILITY_ANNOTATED_MODULES`, `setupReachabilityMetadata(project)` per module, javadoc fix), `build.properties` (`reachability.version`), `gradle/verification-metadata.xml`, `.github/workflows/build-static-libs.yml` |
| 2 | `applied reachability metadata from <previous release>` | every annotation under `modules/` from the previous branch, unchanged |
| 3 | `applied reachability metadata delta <previous release> -> <new release>` | only what the upstream changes between the two bases require, found in step 5 |

Subjects are lowercase with no trailers. The third commit is left out when step 5 finds nothing. The
split lets a reviewer check commits 1 and 2 mechanically and spend their time on commit 3.

In the commands below, `OLD_BASE` and `OLD_BRANCH` are the previous release (`26.0.2-ga`,
`jfx26u-metadata`), `NEW_BASE` is the new tag (`27-ga`). The previous release is the one whose
metadata is the most complete, which for 27 was the 26.0.2 update branch, not the 26 GA branch.

```bash
OLD_BASE=26.0.2-ga OLD_BRANCH=jfx26u-metadata NEW_BASE=27-ga
git switch -c jfx27-metadata $NEW_BASE
```

## 3. Commit 1: infrastructure

```bash
git diff $OLD_BASE $OLD_BRANCH -- build.gradle build.properties gradle/verification-metadata.xml .github \
    | git apply --3way
```

Resolve conflicts against upstream's own `build.gradle` changes, which so far were only neighbouring
media and webkit lines. Then check that every `setupReachabilityMetadata(project)` landed in the
`project(":<module>")` block of a module listed in `REACHABILITY_ANNOTATED_MODULES`, since a shifted
hunk can apply cleanly into the wrong block:

```bash
git diff --cached -U8 build.gradle | grep -E '^@@|setupReachabilityMetadata\(project\)'
```

Check whether the workflow needs anything for the new release, such as a raised JDK floor (27
requires JDK 25 to build).

## 4. Commit 2: carried metadata

List the annotated files upstream touched first. These are where conflicts or misplaced hunks can
happen:

```bash
comm -12 <(git diff --name-only $OLD_BASE $OLD_BRANCH -- modules | sort) \
         <(git diff --name-only $OLD_BASE $NEW_BASE -- modules | sort)
git diff $OLD_BASE $OLD_BRANCH -- modules | git apply --3way
```

A clean apply is not the proof. The added and removed lines must be identical to the previous
branch's, and each annotation must still sit above the type it was written for:

```bash
diff <(git diff $OLD_BASE $OLD_BRANCH -- modules | grep -E '^[+-]') \
     <(git diff --cached -- modules | grep -E '^[+-]') && echo identical
for f in $(git diff --name-only $OLD_BASE $OLD_BRANCH -- modules); do
  diff -q <(git show $OLD_BRANCH:$f | grep -A1 '@Reachable') <(grep -A1 '@Reachable' $f) >/dev/null \
    || echo "moved: $f"
done
```

A context difference in the full diff is fine when it is upstream's own edit next to an inserted
import. An annotation now above a different declaration is not, and a file upstream deleted or
renamed shows up as a failed hunk. Either case has to be carried over by hand and gets re-checked in
step 5.

## 5. Commit 3: the delta

### The rule

Annotate only what native-image cannot resolve by itself. It constant-folds `Class.forName` and
`getMethod` when a constant name reaches the call inside one method graph, including through
compile-time string concatenation and one helper frame. It does not fold through a branch
(`cond ? "A" : "B"`), a non-final static field, or a name built at run time, and it never sees what
native code looks up with `FindClass`/`GetMethodID`. Only that second kind gets metadata. An
annotation on a folding site costs image size and adds nothing.

Left out on purpose: application reflection (FXML controllers, `-fx-skin` from application CSS,
`Application` subclasses, which are the feature's job), the j2d/AWT fallbacks (registering them
pulls AWT into every image), `javafx.swt`, and lookups whose names are assembled in native code at
run time.

### The sweeps

Run the sweep against both bases and read every difference. It covers every module in
`REACHABILITY_ANNOTATED_MODULES`, including media, web and swing, whose native trees are large but
whose JNI surface is small. Keep `MODULES` in sync with that list.

```bash
MODULES="javafx.base javafx.graphics javafx.controls javafx.fxml javafx.media javafx.web javafx.swing
         jfx.incubator.input jfx.incubator.richtext"
java_paths()   { for m in $MODULES; do echo "modules/$m/src/main/java" "modules/$m/src/main/jsl-*" "modules/$m/src/main/resources"; done; }
native_paths() { for m in $MODULES; do echo "modules/$m/src/main/native*"; done; }
strip() { sed "s|^$1:||" | sed -E 's/:\s+/:/' | sort -u; }

LITERALS='"(java|javax|javafx|com/sun|sun|jdk|org/w3c|netscape)/[A-Za-z0-9_/$]+"'
REFLECTION='Class\.forName|\.getMethod\(|\.getDeclared(Method|Field|Constructor)s?\(|\.getConstructor\(|newInstance\(|ServiceLoader\.load|ResourceBundle\.getBundle|getResource(AsStream)?\(|forceInit\(|skinClassNameProperty|loadLibrary\(|MethodHandles|findStatic|findVirtual'
JNI='JNIEnv|jclass|jmethodID|jfieldID|Get(Static)?(Method|Field)ID|FindClass|Call[A-Za-z]*Method|NewObject|RegisterNatives'

for rev in $OLD_BASE $NEW_BASE; do
  git grep -oE "$LITERALS" $rev -- $(native_paths) | strip $rev > sweep-literals-$rev.txt
  git grep -E "$REFLECTION" $rev -- $(java_paths) | strip $rev > sweep-reflection-$rev.txt
  git grep -lE '\bnative\b[^=;(]*\(' $rev -- $(java_paths) | strip $rev > sweep-natives-$rev.txt
done
echo "### native lookup literals";  diff sweep-literals-$OLD_BASE.txt sweep-literals-$NEW_BASE.txt
echo "### java reflective sites";   diff sweep-reflection-$OLD_BASE.txt sweep-reflection-$NEW_BASE.txt
echo "### classes with natives";    diff sweep-natives-$OLD_BASE.txt sweep-natives-$NEW_BASE.txt
echo "### added, deleted, renamed"; git diff --name-status -M $OLD_BASE $NEW_BASE -- $(java_paths) | grep -v '^M'
echo "### changed JNI lines";       git diff $OLD_BASE $NEW_BASE -- $(native_paths) \
    | grep -E '^(\+\+\+|[+-][^+-].*('"$JNI"'))' | grep -B1 -E '^[+-][^+-]'
```

Write the output files outside the checkout. For 26.0.2 -> 27 this reported the two new
`Utils.forceInit` helpers in its reflective section (`StyleablePropertyHelper`,
`EmbeddedImageHelper`), nothing for media, web and swing, and a handful of JNI lines that only call
through ids already cached by annotated classes.

What each finding needs:

| Finding | Annotation | Placed on |
|---|---|---|
| New `Utils.forceInit(X.class)` in a `*Helper` | `@Reachable(classes = X.class, memberAccess = {})` | the helper |
| New class with `native` methods whose native code caches ids (`_initIDs`, `GetFieldID`, `NewObject`) | `@Reachable(jniAccessible = true)`, self-conditioned | that class |
| New `FindClass` literal | add it to `@Reachable(jniAccessible = true, classes = {…})` | the Java class that owns that native library (`WinApplication`, `GtkApplication`, `PrismFontFactory`, `NativeMediaManager`, `WebPage`) |
| A class only native code names, reached from another class's natives | `@Reachable(jniAccessible = true, classes = X.class) // <native file>` | the class whose native does the lookup |
| New `Class.forName` over a runtime or branched name | `classNames = {…}` with the smallest `memberAccess` the follow-up call needs | the class doing the lookup |
| New resource or bundle read by name | `resources = "<glob>"` or `bundles = "<name>"`, the bundle name exactly as passed to `getBundle` | the class that reads it |
| A lookup that is meant to fail (a `.bss` next to a `.css`, an `@2x` image, a class or service file that may be absent) | the exact name, never a wildcard, and `memberAccess = {}` for classes | the class that makes the lookup, or whose call makes the JDK look it up |
| A control that sets its own skin by class name | `@Reachable(condition = <Control>.class, memberAccess = MemberAccess.ALL_DECLARED_CONSTRUCTORS)` | the skin |
| New hand-written or `.stg`-generated effect peer | `@ReachableMember(condition = <Renderer>.class)` on the constructor | the peer or its template |

Before writing a new entry, find an existing one of the same shape with `git grep '@Reachable'` and
copy its form. The conventions: annotation directly above the type, attribute order `condition`,
`jniAccessible`, `classes`/`classNames`, `memberAccess`, `resources`, `bundles`, `classes = X.class`
when `X` compiles in the same module and `classNames` otherwise (other platform, other module,
package-private or generated types), the default self-condition unless the annotated class is not
what reaches the target, one line unless it lists many items, no comments except a trailing
`// <native file>` when nothing else names the lookup, and no product code edits.

A self-conditioned entry for a class nothing in Java names never fires. Condition such an entry on
the class that does reach it, typically the platform `Application` or the library owner.

### What the sweep cannot see

The sweep only finds new instances of lookup patterns it already knows. Also read
`doc-files/release-notes-<N>.md` of the new release for anything that touches pipelines, platform
integration, CSS or resource loading, a new module, or a new native library. For 27 that was the
Metal default pipeline on macOS, CSS media queries and conditional stylesheet imports. A new
category of lookup only shows up when step 7 runs the render check, which is how the unregistered
software effect peers were found for 26.0.2.

Lookups that are meant to fail are invisible to both the sweep and a default-mode render check. The
default mode returns the expected `null` or `ClassNotFoundException`, but with
`--exact-reachability-metadata` every unregistered name throws a `Missing*RegistrationError`, which
jfx's `catch` blocks do not catch. Examples: `StyleManager` probing the `.bss` of every stylesheet,
`ImageStorage` probing `@2x` images on HiDPI screens, `PlatformImpl.checkForClass` testing for
optional modules, and the JDK's own service file lookups from `FXMLLoader` and `URI.toURL`. They are
registered by exact name. The 1.0.0 format's regex patterns only match files that exist, so a
wildcard does not cover them. Only step 7's exact-mode runs find new ones.

If there was a pre-GA branch, its delta commit was swept against the same `OLD_BASE`, and it can be
carried over with `git cherry-pick --no-commit <commit>`. The sweep still runs for everything that
branch did not annotate yet.

## 6. Verify the branch locally

A clean static SDK build, on Windows from PowerShell (JDK 25+ without bundled JavaFX as `JAVA_HOME`,
VS 2022 Build Tools):

```powershell
$env:VSCOMNTOOLS="C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build"
.\gradlew.bat --no-daemon clean sdk -PSTATIC_BUILD=true -PCOMPILE_MEDIA=false -PCOMPILE_WEBKIT=false
```

Then compare the generated metadata against the previous release's SDK:

```bash
mkdir -p old new
(cd old && unzip -oq <previous openjfx-…-static.zip> 'windows-x86_64/sdk/lib/*.jar')
for jar in build/sdk/lib/*.jar; do unzip -oq "$jar" 'META-INF/native-image/*' -d new 2>/dev/null; done
for jar in old/windows-x86_64/sdk/lib/*.jar; do unzip -oq "$jar" 'META-INF/native-image/*' -d old/meta 2>/dev/null; done
diff -r old/meta/META-INF new/META-INF
```

The difference must be exactly what commit 3 added. An annotation whose entry is missing from the
JSON was folded or misplaced, so read the generated JSON, not the annotation diff. Editing a `.stg`
template needs the `clean`, since gradle does not track `src/jslc/resources`.

## 7. Build, consume and test

1. Push the branch to the fork. A branch that replaces an older one of the same name needs
   `git push --force-with-lease`. The static build runs on pushes to `build-static*` branches, so
   push the same commit as `build-static-jfxN` too. The workflow publishes the combined archive as
   the prerelease `N-internal-<commit>`.
2. Both pushes also run upstream's `JavaFX pre-submit tests` (`submit.yml`). Its Windows headless
   tests have failed on one branch while passing on another branch at the same commit. Compare
   against the other run of the same SHA before treating such a failure as a metadata problem.
3. In this repository, set `jfx.version` and `jfx.commit` in `jfx-static-libs/pom.xml`. The artifact
   version is the JavaFX release without the fork's `-internal` suffix (`27`, or `27-1` when only
   the fork build changed). Update `javafx.version` in `jfx-static-examples` once Central has the GA
   artifacts.
4. Extend `example-render-check` for the release's new features, on the JVM first and as native
   images once at the end. The examples compile against the new release, so new Java API goes
   straight into the scenario it belongs to.
5. Regenerate the bundled all-OS reflect-config of `example-jni-metadata` from the new release's
   native classes and extend the feature's `@Delete` stubs for any new per-OS natives, then confirm
   it links on each OS.
6. Check what the shared libraries of `javafx.media` and `javafx.web` load. They stay dynamic, and
   the feature copies them from the platform classifier jars next to the image. Every library they
   link against has to be one of those bundled libraries, a library every supported OS install
   provides, or something the feature supplies. Otherwise the image fails with a load error only
   when the module is used. List the dependencies per platform and diff them against the previous
   release:

   ```bash
   for m in media web; do
     for os in linux linux-aarch64 mac-aarch64 win; do
       curl -sfLO https://repo1.maven.org/maven2/org/openjfx/javafx-$m/27/javafx-$m-27-$os.jar
       unzip -oq javafx-$m-27-$os.jar '*.so' '*.dylib' '*.dll' -d $os 2>/dev/null
     done
   done
   for f in linux*/*.so; do echo "$f: $(readelf -d $f | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p' | xargs)"; done
   for f in mac*/*.dylib; do echo "$f: $(otool -L $f | tail -n +2 | awk '{print $1}' | xargs)"; done  # on macOS
   for f in win/*.dll; do echo "$f: $(objdump -p $f | sed -n 's/.*DLL Name: //p' | xargs)"; done
   ```

   For 27, `libjfxwebkit.so` needs `libjvm.so` and `libjfxwebkit.dylib` needs `@rpath/libjvm.dylib`,
   which a native image does not have. The feature handles both: an empty `libjvm.dylib` from
   `writeJvmShim` on macOS, and `-Wl,--disable-new-dtags -Wl,-rpath,$ORIGIN` on Linux so the loader
   finds the `libjvm.so` next to the image. `jfxwebkit.dll` imports no `jvm.dll`. The
   `libavplugin-*` libraries link against the system's libavcodec and libavformat on purpose, one
   per ffmpeg version, and media picks the one matching the installed ffmpeg. Anything new is either shimmed in
   `JfxStaticFeature` or documented as an application requirement, and `example-dynamic-check`
   confirms it loads on each OS. The link-time list does not show libraries loaded with `dlopen` by
   name, so a new plugin mechanism in the release notes needs a runtime check too.
7. Run the render check as native images for every platform and pipeline: Windows `sw`, `d3d`,
   `default`; Linux and Linux aarch64 `sw`, `es2`, `default`; macOS `sw`, `es2`, `mtl`, `default`;
   plus a windowed run per OS with `-Dglass.platform`. Read the run logs for exceptions. A screenshot
   that looks right is not a pass. The examples build with `--exact-reachability-metadata`; add
   `-XX:MissingRegistrationReportingMode=Warn` to a run to log every missing registration instead of
   dying on the first. Run `example-dynamic-check` with `web` and `media` the same way, and the
   windowed runs on a HiDPI screen, which is the only place the `@2x` probes happen.
8. Release as described in the README's [Releasing](README.md#releasing) section.

## Agent checklist

- Do not commit, push or deploy unless the maintainer asked for that step. Prepare one commit at a
  time and report the verification output with it.
- Commit 2 must be proven identical with the diff in step 4, not by a clean `git apply`.
- Every sweep difference in step 5 gets an entry or a written reason why it needs none.
- Every shared library dependency in step 7.6 that is neither bundled nor provided by the OS gets a
  shim in the feature or a documented requirement.
- Report what was not verified, such as a platform whose render check did not run.
