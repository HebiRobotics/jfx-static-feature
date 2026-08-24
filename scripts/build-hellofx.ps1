# Builds the HelloFX example into a statically linked native executable against the annotated
# static JavaFX 26 SDK. No external reflection or JNI config files: the SDK jars carry their own
# reachability metadata, everything else comes from the feature jar.
param(
    [string] $FxSdk = 'C:\code\HEBI\claude\jfx\build\sdk',
    [string] $GraalHome = 'C:\toolchains\jdk\graalvm-jdk-25.0.1+8.1',
    [switch] $NoMaven
)
$ErrorActionPreference = 'Stop'

$root   = Split-Path -Parent $PSScriptRoot
$target = Join-Path $root 'target'
$fxLib  = Join-Path $FxSdk 'lib'

$fxJars = @('javafx.base.jar', 'javafx.graphics.jar', 'javafx.controls.jar') |
    ForEach-Object { Join-Path $fxLib $_ }
foreach ($jar in $fxJars) {
    if (-not (Test-Path $jar)) { throw "Missing $jar, build the static SDK first (see README)" }
}

if (-not $NoMaven) {
    Write-Host '== mvn package'
    & mvn -q -f (Join-Path $root 'pom.xml') package
    if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
}

$featureJar = (Get-ChildItem (Join-Path $root 'native-jfx-feature\target\native-jfx-feature-*.jar')).FullName
$exampleJar = (Get-ChildItem (Join-Path $root 'hellofx-example\target\hellofx-example-*.jar')).FullName

Write-Host '== cl (stubs)'
New-Item -ItemType Directory -Force $target | Out-Null
$stubObj = Join-Path $target 'foreign_platform_stubs.obj'
& cl.exe /nologo /c /O2 "/Fo$stubObj" (Join-Path $root 'native-jfx-feature\src\main\c\foreign_platform_stubs_windows.c')
if ($LASTEXITCODE -ne 0) { throw 'cl failed' }

# The feature reaches into the image builder, which lives in a named module at build time.
$exports = @(
    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED',
    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted=ALL-UNNAMED',
    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted.c=ALL-UNNAMED',
    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.annotate=ALL-UNNAMED',
    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.functions=ALL-UNNAMED',
    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.headers=ALL-UNNAMED'
)

# Windows libraries the static glass/prism/font code pulls in.
$systemLibs = @(
    'comdlg32.lib', 'comctl32.lib', 'imm32.lib', 'shell32.lib', 'ole32.lib', 'oleaut32.lib',
    'gdi32.lib', 'user32.lib', 'urlmon.lib', 'winmm.lib', 'd3d9.lib', 'uiautomationcore.lib',
    'dwrite.lib', 'd2d1.lib', 'windowscodecs.lib', 'usp10.lib', 'advapi32.lib', 'shlwapi.lib',
    'dwmapi.lib', 'uuid.lib', 'msimg32.lib', 'propsys.lib'
)
$linkerOptions = ($systemLibs + $stubObj) | ForEach-Object { "-H:NativeLinkerOption=$_" }

$imageArgs = @(
    '-cp', ((@($featureJar, $exampleJar) + $fxJars) -join ';'), # the feature jar enables itself via native-image.properties
    '--no-fallback',
    '-H:+UnlockExperimentalVMOptions',
    "-H:CLibraryPath=$fxLib",
    '-o', (Join-Path $target 'hellofx'),
    '-J-Xmx8g'
) + $exports + $linkerOptions + @('us.hebi.graalvm.javafx.example.Launcher')

Write-Host '== native-image'
$sw = [Diagnostics.Stopwatch]::StartNew()
& "$GraalHome\bin\native-image.cmd" @imageArgs
$code = $LASTEXITCODE
$sw.Stop()
Write-Host "== native-image exit $code after $($sw.Elapsed)"
exit $code
