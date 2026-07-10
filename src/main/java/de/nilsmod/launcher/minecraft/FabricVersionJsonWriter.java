package de.nilsmod.launcher.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.nilsmod.launcher.download.Downloader;
import de.nilsmod.launcher.manifest.VersionEntry;
import de.nilsmod.launcher.util.JsonUtil;
import de.nilsmod.launcher.util.Log;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FabricVersionJsonWriter {
    private final MinecraftDirectory minecraft;
    private final Downloader downloader;
    private final Log log;

    public FabricVersionJsonWriter(MinecraftDirectory minecraft, Downloader downloader, Log log) {
        this.minecraft = minecraft;
        this.downloader = downloader;
        this.log = log;
    }

    public String write(VersionEntry entry) throws Exception {
        String versionId = versionId(entry.minecraftVersion());
        Path target = minecraft.versionJson(versionId);
        Files.createDirectories(target.getParent());

        String url = "https://meta.fabricmc.net/v2/versions/loader/"
                + segment(entry.minecraftVersion()) + "/"
                + segment(entry.fabricLoader()) + "/profile/json";
        log.info("Fabric Loader JSON wird geladen: " + entry.fabricLoader());
        String json = downloader.readString(url);
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        object.addProperty("id", versionId);
        object.addProperty("inheritsFrom", entry.minecraftVersion());
        object.addProperty("type", "release");
        JsonUtil.write(target, object);
        log.info("Version JSON geschrieben: " + target);
        return versionId;
    }

    public static String versionId(String minecraftVersion) {
        return "nilsmod-" + minecraftVersion;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
