# NilsMod Launcher

NilsMod Launcher is a Tauri + TypeScript desktop app with a Rust backend. It installs, updates, and starts NilsMod directly on Windows, Linux, and macOS.

The launcher does not distribute Minecraft itself. Users must own Minecraft. For direct start it reuses existing authenticated account data when present and starts the installed Fabric profile with its own managed Java runtime.

## Features

- Native cross-platform app built with Tauri 2.
- TypeScript/Vite frontend with NilsMod branding.
- Rust backend for downloads, hashing, profile setup, runtime setup, and launching.
- Installs `NilsMod.jar` from GitHub Releases.
- Installs required Fabric API automatically.
- Optional toggles for Sodium and Simple Voice Chat.
- Downloads only HTTPS artifacts listed in the manifest.
- Verifies SHA-256 or SHA-512 for NilsMod and mod artifacts.
- Creates/updates an isolated `.minecraft/nilsmod/<version>` installation.
- Writes a compatible Fabric Loader version JSON.
- Downloads the required Mojang version metadata, client jar, libraries, assets, and natives when missing.
- Installs a managed Zulu Java runtime matching the selected Minecraft version under `.minecraft/nilsmod/launcher/runtimes`.
- Starts Minecraft directly from the NilsMod Launcher.

## Current Release Asset

Uploaded mod artifact:

```text
https://github.com/TheSimonMC/NilsModLauncher/releases/download/nilsmod-1.0.1/nilsmod-1.0.1.jar
```

SHA-256:

```text
c6d1d8e742a9ba3bd12f269cfb5f4b066893e3d2334f5c9ff844e9e96b56bfac
```

## How It Works

```text
NilsMod Launcher starts
 -> loads remote manifest
 -> user chooses Minecraft version and optional mods
 -> downloads NilsMod + required/selected mods
 -> installs Fabric Loader metadata
 -> prepares Minecraft client files, libraries, assets, and natives
 -> installs the matching managed Zulu Java runtime if missing
 -> reads the active Minecraft account from launcher_accounts.json
 -> starts Fabric/NilsMod directly
```

## Development

Requirements:

- Node.js 24+
- npm
- Rust stable with Cargo
- Platform Tauri prerequisites

Install and run in dev mode:

```bash
npm install
npm run tauri:dev
```

Frontend-only build:

```bash
npm run build
```

Native app build:

```bash
npm run tauri:build
```

On Windows without Rust installed, `npm run build` still validates the TypeScript/Vite frontend. Native bundles require Cargo.

## Manifest

Default manifest URL:

```text
https://github.com/TheSimonMC/NilsModLauncher/releases/latest/download/nilsmod-manifest.json
```

See [manifest/nilsmod-manifest.example.json](manifest/nilsmod-manifest.example.json).

## Local Files

```text
.minecraft/
`-- nilsmod/
    |-- launcher/
    |   |-- local-version.json
    |   `-- runtimes/
    |       `-- zulu-jre<major>-<os>-<arch>/
    `-- 1.21.11/
        |-- mods/
        |   |-- NilsMod.jar
        |   |-- fabric-api-*.jar
        |   |-- sodium-*.jar
        |   `-- voicechat-*.jar
        `-- natives/
```

## Legal Notes

NilsMod Launcher does not distribute Minecraft or Mojang files. It downloads required official game files from Mojang endpoints and requires an owned Minecraft account. For legacy versions, the launcher must only download NilsMod-owned patcher files and apply patches locally.
