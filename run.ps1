$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $Root "NilsModLauncher.jar"
if (!(Test-Path $Jar)) {
    $Jar = Join-Path $Root "build\libs\NilsModLauncher.jar"
}
if (!(Test-Path $Jar)) {
    $Build = Join-Path $Root "build.ps1"
    if (!(Test-Path $Build)) {
        throw "NilsModLauncher.jar wurde nicht gefunden."
    }
    & $Build
}
java -jar $Jar
