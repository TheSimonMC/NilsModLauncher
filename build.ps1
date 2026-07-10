$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $Root "build\libs\NilsModLauncher.jar"
$DistRoot = Join-Path $Root "dist"
$Dist = Join-Path $DistRoot "NilsModLauncher"
$Zip = Join-Path $DistRoot "NilsModLauncher-1.0.1.zip"

& (Join-Path $Root "gradlew.bat") clean build

Remove-Item -Recurse -Force $Dist -ErrorAction SilentlyContinue
Remove-Item -Force $Zip -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Dist | Out-Null

Copy-Item -Force $Jar (Join-Path $Dist "NilsModLauncher.jar")
Copy-Item -Force (Join-Path $Root "NilsModLauncher.bat") (Join-Path $Dist "NilsModLauncher.bat")
Copy-Item -Force (Join-Path $Root "Start-NilsMod.bat") (Join-Path $Dist "Start-NilsMod.bat")
Copy-Item -Force (Join-Path $Root "run.ps1") (Join-Path $Dist "run.ps1")
Copy-Item -Force (Join-Path $Root "README.md") (Join-Path $Dist "README.md")
Copy-Item -Recurse -Force (Join-Path $Root "manifest") (Join-Path $Dist "manifest")

Compress-Archive -Path (Join-Path $Dist "*") -DestinationPath $Zip -Force
Write-Host "Built $Jar"
Write-Host "Packaged $Dist"
Write-Host "Created $Zip"
