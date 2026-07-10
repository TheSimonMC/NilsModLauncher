package de.nilsmod.launcher.manifest;

import java.util.Locale;

public final class VersionEntry {
    private String type;
    private String minecraftVersion;
    private String fabricLoader;
    private String nilsmodVersion;
    private String modUrl;
    private String patcherUrl;
    private String sha256;

    public String type() {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String fabricLoader() {
        return fabricLoader;
    }

    public String nilsmodVersion() {
        return nilsmodVersion;
    }

    public String modUrl() {
        return modUrl;
    }

    public String patcherUrl() {
        return patcherUrl;
    }

    public String sha256() {
        return sha256;
    }

    public boolean isFabric() {
        return "fabric".equals(type());
    }

    public boolean isLegacy() {
        return "legacy".equals(type());
    }

    public void validate(String key) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Manifest entry " + key + " misses minecraftVersion");
        }
        if (nilsmodVersion == null || nilsmodVersion.isBlank()) {
            throw new IllegalArgumentException("Manifest entry " + key + " misses nilsmodVersion");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("Manifest entry " + key + " misses sha256");
        }
        if (isFabric()) {
            if (fabricLoader == null || fabricLoader.isBlank()) {
                throw new IllegalArgumentException("Fabric entry " + key + " misses fabricLoader");
            }
            if (modUrl == null || modUrl.isBlank()) {
                throw new IllegalArgumentException("Fabric entry " + key + " misses modUrl");
            }
        } else if (isLegacy()) {
            if (patcherUrl == null || patcherUrl.isBlank()) {
                throw new IllegalArgumentException("Legacy entry " + key + " misses patcherUrl");
            }
        } else {
            throw new IllegalArgumentException("Unsupported NilsMod type in " + key + ": " + type);
        }
    }
}
