package de.nilsmod.launcher.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.nilsmod.launcher.download.Downloader;
import de.nilsmod.launcher.hash.HashVerifier;
import de.nilsmod.launcher.manifest.VersionEntry;
import de.nilsmod.launcher.minecraft.FabricVersionJsonWriter;
import de.nilsmod.launcher.minecraft.LauncherProfileManager;
import de.nilsmod.launcher.minecraft.MinecraftDirectory;
import de.nilsmod.launcher.util.JsonUtil;
import de.nilsmod.launcher.util.Log;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

public final class FabricInstaller {
    private final MinecraftDirectory minecraft;
    private final Downloader downloader;
    private final Log log;
    private final InstallOptions options;

    public FabricInstaller(MinecraftDirectory minecraft, Downloader downloader, Log log, InstallOptions options) {
        this.minecraft = minecraft;
        this.downloader = downloader;
        this.log = log;
        this.options = options;
    }

    public void install(VersionEntry entry) throws Exception {
        Files.createDirectories(minecraft.modsDir(entry.minecraftVersion()));
        Files.createDirectories(minecraft.launcherStateDir());

        installNilsMod(entry);
        if (options.installFabricApi()) {
            installModrinth(entry.minecraftVersion(), "fabric-api", List.of("fabric-api-"), true);
        }
        installModrinth(entry.minecraftVersion(), "sodium", List.of("sodium-", "sodium-fabric-"), options.installSodium());
        installModrinth(entry.minecraftVersion(), "simple-voice-chat", List.of("voicechat-", "simple-voice-chat-"), options.installSimpleVoiceChat());

        String versionId = new FabricVersionJsonWriter(minecraft, downloader, log).write(entry);
        new LauncherProfileManager(minecraft, log).upsertFabricProfile(entry, versionId);
        writeLocalVersion(entry);
        log.info("Installation abgeschlossen: NilsMod " + entry.minecraftVersion());
    }

    private void installNilsMod(VersionEntry entry) throws Exception {
        Path target = minecraft.modsDir(entry.minecraftVersion()).resolve("NilsMod.jar");
        if (!HashVerifier.isRealHash(entry.sha256(), 64)) {
            throw new IOException("Manifest SHA-256 is missing/invalid for " + entry.minecraftVersion() + ". Refusing unsafe install.");
        }
        if (Files.exists(target) && HashVerifier.matchesSha256(target, entry.sha256()) && localVersionMatches(entry)) {
            log.info("NilsMod ist aktuell: " + entry.nilsmodVersion());
            return;
        }

        log.info("Datei wird heruntergeladen: NilsMod " + entry.nilsmodVersion());
        Path temp = target.resolveSibling("NilsMod.jar.download");
        downloader.download(entry.modUrl(), temp);
        log.info("SHA-256 wird geprueft...");
        if (!HashVerifier.matchesSha256(temp, entry.sha256())) {
            Files.deleteIfExists(temp);
            throw new IOException("SHA-256 mismatch for downloaded NilsMod.jar");
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileSystemException exception) {
            throw new IOException("NilsMod.jar ist gesperrt. Bitte Minecraft/NilsMod schliessen und erneut installieren.", exception);
        }
    }

    private boolean localVersionMatches(VersionEntry entry) throws IOException {
        JsonObject local = JsonUtil.readObject(minecraft.localVersionFile());
        if (!local.has(entry.minecraftVersion()) || !local.get(entry.minecraftVersion()).isJsonObject()) {
            return false;
        }
        JsonObject record = local.getAsJsonObject(entry.minecraftVersion());
        return entry.nilsmodVersion().equals(text(record, "nilsmodVersion"))
                && entry.sha256().equalsIgnoreCase(text(record, "sha256"));
    }

    private void writeLocalVersion(VersionEntry entry) throws IOException {
        JsonObject local = JsonUtil.readObject(minecraft.localVersionFile());
        JsonObject record = new JsonObject();
        record.addProperty("type", entry.type());
        record.addProperty("minecraftVersion", entry.minecraftVersion());
        record.addProperty("fabricLoader", entry.fabricLoader());
        record.addProperty("nilsmodVersion", entry.nilsmodVersion());
        record.addProperty("sha256", entry.sha256());
        local.add(entry.minecraftVersion(), record);
        JsonUtil.write(minecraft.localVersionFile(), local);
    }

    private void installModrinth(String minecraftVersion, String slug, List<String> prefixes, boolean enabled) throws Exception {
        if (!enabled) {
            removeManaged(minecraftVersion, prefixes);
            log.info(slug + " deaktiviert.");
            return;
        }
        String url = "https://api.modrinth.com/v2/project/" + slug + "/version?loaders="
                + encodedArray("fabric") + "&game_versions=" + encodedArray(minecraftVersion);
        JsonArray versions = JsonParser.parseString(downloader.readString(url)).getAsJsonArray();
        if (versions.isEmpty()) {
            log.info(slug + " hat keine Fabric-Version fuer " + minecraftVersion + ".");
            return;
        }
        JsonObject file = firstPrimaryJar(versions.get(0).getAsJsonObject().getAsJsonArray("files"));
        if (file == null) {
            log.info(slug + " hat keine passende JAR.");
            return;
        }
        String filename = file.get("filename").getAsString();
        Path target = minecraft.modsDir(minecraftVersion).resolve(filename);
        String sha512 = file.has("hashes") && file.getAsJsonObject("hashes").has("sha512")
                ? file.getAsJsonObject("hashes").get("sha512").getAsString()
                : "";
        if (Files.exists(target) && HashVerifier.isRealHash(sha512, 128) && HashVerifier.matchesSha512(target, sha512)) {
            log.info(slug + " ist aktuell: " + filename);
            return;
        }
        removeManaged(minecraftVersion, prefixes);
        log.info("Datei wird heruntergeladen: " + filename);
        downloader.download(file.get("url").getAsString(), target);
        if (HashVerifier.isRealHash(sha512, 128) && !HashVerifier.matchesSha512(target, sha512)) {
            Files.deleteIfExists(target);
            throw new IOException("SHA-512 mismatch for " + filename);
        }
    }

    private void removeManaged(String minecraftVersion, List<String> prefixes) throws IOException {
        Path mods = minecraft.modsDir(minecraftVersion);
        if (!Files.isDirectory(mods)) {
            return;
        }
        try (var stream = Files.list(mods)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar") || prefixes.stream().noneMatch(prefix -> name.startsWith(prefix.toLowerCase(Locale.ROOT)))) {
                    continue;
                }
                try {
                    Files.deleteIfExists(file);
                } catch (FileSystemException exception) {
                    log.info(file.getFileName() + " ist gesperrt; bitte Minecraft schliessen, wenn du die Mod entfernen willst.");
                }
            }
        }
    }

    private static JsonObject firstPrimaryJar(JsonArray files) {
        JsonObject first = null;
        for (var element : files) {
            JsonObject file = element.getAsJsonObject();
            String filename = file.get("filename").getAsString();
            if (!filename.endsWith(".jar") || filename.toLowerCase(Locale.ROOT).contains("sources")) {
                continue;
            }
            if (first == null) {
                first = file;
            }
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                return file;
            }
        }
        return first;
    }

    private static String encodedArray(String value) {
        return URLEncoder.encode("[\"" + value + "\"]", StandardCharsets.UTF_8);
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }
}
