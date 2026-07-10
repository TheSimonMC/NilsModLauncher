$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    npm install
    npm run tauri:dev
} finally {
    Pop-Location
}
