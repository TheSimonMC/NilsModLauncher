package de.nilsmod.launcher.manifest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ManifestModel {
    private String launcherVersion;
    private Map<String, VersionEntry> latest = new LinkedHashMap<>();

    public String launcherVersion() {
        return launcherVersion;
    }

    public Map<String, VersionEntry> latest() {
        return latest == null ? Collections.emptyMap() : latest;
    }

    public Set<String> versions() {
        return latest().keySet();
    }

    public VersionEntry entry(String version) {
        return latest().get(version);
    }

    public void validate() {
        if (latest == null || latest.isEmpty()) {
            throw new IllegalArgumentException("Manifest contains no versions");
        }
        for (Map.Entry<String, VersionEntry> entry : latest.entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Manifest entry " + entry.getKey() + " is null");
            }
            entry.getValue().validate(entry.getKey());
        }
    }
}
