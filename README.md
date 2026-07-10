# NilsMod Launcher

NilsMod Launcher is a standalone installer/updater for NilsMod. It does not ship Minecraft files and does not replace account ownership. Its main job is to download NilsMod-owned release artifacts, verify them, prepare Fabric or legacy install folders, and create/update official Minecraft Launcher profiles.

## Features

- Manifest-driven NilsMod installs and updates
- SHA-256 verification for NilsMod artifacts
- Fabric profile creation for 1.21.x and 1.20.x
- Prepared legacy 1.8.9 patcher flow
- Optional Fabric API, Sodium and Simple Voice Chat installation for Fabric profiles
- JSON-safe `launcher_profiles.json` editing with backup
- CLI and simple Swing GUI
- Gradle build with executable Fat-JAR

## How It Works

```text
NilsModLauncher.jar starten
-> Remote Manifest laden
-> lokale Version pruefen
-> passende NilsMod.jar herunterladen
-> SHA-256 pruefen
-> Fabric Loader / Legacy-Version vorbereiten
-> Minecraft Launcher Profil erstellen oder aktualisieren
-> Nutzer startet danach ueber den offiziellen Minecraft Launcher
```

## Installation

Build the launcher:

```powershell
.\gradlew.bat build
```

Run the GUI:

```powershell
java -jar build\libs\NilsModLauncher.jar
```

Or use the packaged helper:

```powershell
.\NilsModLauncher.bat
```

## CLI Usage

```bash
java -jar build/libs/NilsModLauncher.jar --install 1.21.4
java -jar build/libs/NilsModLauncher.jar --install 1.20.1
java -jar build/libs/NilsModLauncher.jar --install 1.8.9
java -jar build/libs/NilsModLauncher.jar --install-all
java -jar build/libs/NilsModLauncher.jar --manifest https://example.com/nilsmod-manifest.json --install 1.21.4
java -jar build/libs/NilsModLauncher.jar --help
```

Optional flags:

```bash
--no-sodium
--no-voicechat
```

## Manifest Format

Default manifest URL:

```text
https://github.com/NilsMod/NilsModLauncher/releases/latest/download/nilsmod-manifest.json
```

Example:

```json
{
  "launcherVersion": "1.0.1",
  "latest": {
    "1.21.4": {
      "type": "fabric",
      "minecraftVersion": "1.21.4",
      "fabricLoader": "0.16.10",
      "nilsmodVersion": "1.0.2",
      "modUrl": "https://github.com/NilsMod/NilsMod/releases/download/v1.0.2/NilsMod-1.21.4-fabric.jar",
      "sha256": "PUT_HASH_HERE"
    }
  }
}
```

See [manifest/nilsmod-manifest.example.json](manifest/nilsmod-manifest.example.json).

## Local Files

The launcher writes to:

```text
.minecraft/
└── nilsmod/
    ├── launcher/
    │   └── local-version.json
    ├── 1.21.4/
    │   └── mods/
    │       └── NilsMod.jar
    ├── 1.20.1/
    │   └── mods/
    │       └── NilsMod.jar
    └── 1.8.9/
        └── client/
            └── NilsModPatcher.jar
```

Minecraft Launcher profiles are updated in:

```text
.minecraft/launcher_profiles.json
```

A backup is created first:

```text
.minecraft/launcher_profiles.json.bak
```

## Fabric Versions

For Fabric entries, the launcher:

1. Creates `.minecraft/nilsmod/<mcVersion>/mods/`.
2. Downloads `NilsMod.jar`.
3. Verifies SHA-256.
4. Downloads the official Fabric profile JSON from Fabric Meta.
5. Writes `.minecraft/versions/nilsmod-<mcVersion>/nilsmod-<mcVersion>.json`.
6. Creates/updates the official Minecraft Launcher profile `NilsMod <mcVersion>`.

Fabric API is installed by default. Sodium and Simple Voice Chat can be toggled in the GUI or disabled through CLI flags.

## Legacy 1.8.9

For legacy entries, the launcher downloads only NilsMod-owned patcher files and stores them under:

```text
.minecraft/nilsmod/1.8.9/client/
```

Patch application is intentionally stubbed for now:

```text
Legacy 1.8.9 patch installation is prepared, but patch application is not implemented yet.
```

No Mojang files are bundled or redistributed.

## Security Notes

- Downloads must use HTTPS.
- NilsMod and legacy patcher downloads require SHA-256 verification.
- Hash mismatch aborts installation.
- `launcher_profiles.json` is parsed and written as JSON, not edited with fragile string replacements.
- Existing Minecraft Launcher profiles are preserved.
- No account tokens are stored.
- No Minecraft/Mojang files are distributed by this repository.

## Legal Notes

NilsMod Launcher does not distribute Minecraft or Mojang files.
For legacy versions, the launcher only downloads NilsMod-owned patcher files and applies patches locally.
Users must own Minecraft and use the official Minecraft Launcher.

## Development

Project layout:

```text
src/main/java/de/nilsmod/launcher/
├── Main.java
├── manifest/
├── install/
├── minecraft/
├── download/
├── hash/
├── ui/
└── util/
```

## Building

```powershell
.\gradlew.bat clean build
```

Output:

```text
build/libs/NilsModLauncher.jar
```
