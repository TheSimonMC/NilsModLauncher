package de.nilsmod.launcher.manifest;

import de.nilsmod.launcher.download.Downloader;
import de.nilsmod.launcher.util.JsonUtil;

public final class ManifestClient {
    public static final String DEFAULT_URL = "https://github.com/NilsMod/NilsModLauncher/releases/latest/download/nilsmod-manifest.json";

    private final Downloader downloader;

    public ManifestClient(Downloader downloader) {
        this.downloader = downloader;
    }

    public ManifestModel load(String url) throws Exception {
        String json = downloader.readString(url == null || url.isBlank() ? DEFAULT_URL : url);
        ManifestModel model = JsonUtil.GSON.fromJson(json, ManifestModel.class);
        if (model == null) {
            throw new IllegalArgumentException("Manifest is empty");
        }
        model.validate();
        return model;
    }
}
