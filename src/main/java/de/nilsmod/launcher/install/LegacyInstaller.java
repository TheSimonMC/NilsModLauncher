package de.nilsmod.launcher.install;

import com.google.gson.JsonObject;
import de.nilsmod.launcher.download.Downloader;
import de.nilsmod.launcher.hash.HashVerifier;
import de.nilsmod.launcher.manifest.VersionEntry;
import de.nilsmod.launcher.minecraft.MinecraftDirectory;
import de.nilsmod.launcher.util.JsonUtil;
import de.nilsmod.launcher.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyInstaller {
    private final MinecraftDirectory minecraft;
    private final Downloader downloader;
    private final Log log;

    public LegacyInstaller(MinecraftDirectory minecraft, Downloader downloader, Log log) {
        this.minecraft = minecraft;
        this.downloader = downloader;
        this.log = log;
    }

    public void install(VersionEntry entry) throws Exception {
        Files.createDirectories(minecraft.legacyClientDir(entry.minecraftVersion()));
        if (!HashVerifier.isRealHash(entry.sha256(), 64)) {
            throw new IOException("Manifest SHA-256 is missing/invalid for legacy " + entry.minecraftVersion());
        }
        Path patcher = minecraft.legacyClientDir(entry.minecraftVersion()).resolve("NilsModPatcher.jar");
        if (Files.exists(patcher) && HashVerifier.matchesSha256(patcher, entry.sha256())) {
            log.info("Legacy patcher ist aktuell: " + entry.minecraftVersion());
        } else {
            log.info("Legacy patcher wird heruntergeladen: " + entry.minecraftVersion());
            downloader.download(entry.patcherUrl(), patcher);
            if (!HashVerifier.matchesSha256(patcher, entry.sha256())) {
                Files.deleteIfExists(patcher);
                throw new IOException("SHA-256 mismatch for legacy patcher");
            }
        }
        JsonObject local = JsonUtil.readObject(minecraft.localVersionFile());
        JsonObject record = new JsonObject();
        record.addProperty("type", entry.type());
        record.addProperty("minecraftVersion", entry.minecraftVersion());
        record.addProperty("nilsmodVersion", entry.nilsmodVersion());
        record.addProperty("sha256", entry.sha256());
        local.add(entry.minecraftVersion(), record);
        JsonUtil.write(minecraft.localVersionFile(), local);
        log.info("Legacy 1.8.9 patch installation is prepared, but patch application is not implemented yet.");
    }
}
