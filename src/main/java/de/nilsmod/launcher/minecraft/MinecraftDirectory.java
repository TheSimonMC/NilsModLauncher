package de.nilsmod.launcher.minecraft;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class MinecraftDirectory {
    private final Path root;

    public MinecraftDirectory(Path root) {
        this.root = root;
    }

    public static MinecraftDirectory detect() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return new MinecraftDirectory(Paths.get(appData, ".minecraft"));
        }
        return new MinecraftDirectory(Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft"));
    }

    public Path root() {
        return root;
    }

    public Path nilsmodRoot() {
        return root.resolve("nilsmod");
    }

    public Path launcherStateDir() {
        return nilsmodRoot().resolve("launcher");
    }

    public Path localVersionFile() {
        return launcherStateDir().resolve("local-version.json");
    }

    public Path gameDir(String minecraftVersion) {
        return nilsmodRoot().resolve(minecraftVersion);
    }

    public Path modsDir(String minecraftVersion) {
        return gameDir(minecraftVersion).resolve("mods");
    }

    public Path legacyClientDir(String minecraftVersion) {
        return gameDir(minecraftVersion).resolve("client");
    }

    public Path versionsDir() {
        return root.resolve("versions");
    }

    public Path versionDir(String versionId) {
        return versionsDir().resolve(versionId);
    }

    public Path versionJson(String versionId) {
        return versionDir(versionId).resolve(versionId + ".json");
    }

    public Path launcherProfilesJson() {
        return root.resolve("launcher_profiles.json");
    }
}
