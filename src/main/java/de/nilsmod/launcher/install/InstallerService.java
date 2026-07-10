package de.nilsmod.launcher.install;

import de.nilsmod.launcher.download.Downloader;
import de.nilsmod.launcher.manifest.ManifestModel;
import de.nilsmod.launcher.manifest.VersionEntry;
import de.nilsmod.launcher.minecraft.MinecraftDirectory;
import de.nilsmod.launcher.util.Log;

public final class InstallerService {
    private final ManifestModel manifest;
    private final MinecraftDirectory minecraft;
    private final Downloader downloader;
    private final Log log;
    private final InstallOptions options;

    public InstallerService(ManifestModel manifest, MinecraftDirectory minecraft, Downloader downloader, Log log, InstallOptions options) {
        this.manifest = manifest;
        this.minecraft = minecraft;
        this.downloader = downloader;
        this.log = log;
        this.options = options;
    }

    public void install(String version) throws Exception {
        VersionEntry entry = manifest.entry(version);
        if (entry == null) {
            throw new IllegalArgumentException("Version not in manifest: " + version);
        }
        log.info("Version wird geprueft: " + version);
        if (entry.isFabric()) {
            new FabricInstaller(minecraft, downloader, log, options).install(entry);
        } else if (entry.isLegacy()) {
            new LegacyInstaller(minecraft, downloader, log).install(entry);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + entry.type());
        }
    }

    public void installAll() throws Exception {
        for (String version : manifest.versions()) {
            install(version);
        }
    }
}
