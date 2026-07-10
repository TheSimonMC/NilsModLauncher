$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    npm install
    npm run build
    if (Get-Command cargo -ErrorAction SilentlyContinue) {
        npm run tauri:build
    } else {
        Write-Warning "Rust/Cargo is not installed. Frontend build finished; install Rust to build native Tauri bundles."
    }
} finally {
    Pop-Location
}
