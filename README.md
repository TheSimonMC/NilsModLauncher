# NilsMod Launcher

NilsMod Launcher is a Tauri + TypeScript desktop app with a Rust backend. It installs and updates isolated NilsMod Fabric profiles for the official Minecraft Launcher on Windows, Linux, and macOS.

The launcher does not distribute Minecraft or Mojang files. Users must own Minecraft and use the official Minecraft Launcher.

## Features

- Native cross-platform app built with Tauri 2.
- TypeScript/Vite frontend with NilsMod branding.
- Rust installer backend for filesystem work, downloads, hashing, and profile updates.
- Installs `NilsMod.jar` from GitHub Releases.
- Installs required Fabric API automatically.
- Optional toggles for Sodium and Simple Voice Chat.
- Downloads only HTTPS artifacts listed in the manifest.
- Verifies SHA-256 or SHA-512 before replacing files.
- Writes official Fabric Loader profile JSON from Fabric Meta.
- Creates/updates `launcher_profiles.json` without deleting existing profiles.
- Keeps each version isolated under `.minecraft/nilsmod/<version>`.

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
 -> verifies file hashes
 -> writes Fabric Loader version JSON
 -> creates/updates official Minecraft Launcher profile
 -> user starts NilsMod from the official launcher
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
└── nilsmod/
    ├── launcher/
    │   └── local-version.json
    └── 1.21.11/
        └── mods/
            ├── NilsMod.jar
            ├── fabric-api-*.jar
            ├── sodium-*.jar
            └── voicechat-*.jar
```

## Legal Notes

NilsMod Launcher does not distribute Minecraft or Mojang files. For legacy versions, the launcher must only download NilsMod-owned patcher files and apply patches locally. Users must own Minecraft and use the official Minecraft Launcher.
