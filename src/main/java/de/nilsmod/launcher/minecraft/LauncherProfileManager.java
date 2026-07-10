package de.nilsmod.launcher.minecraft;

import com.google.gson.JsonObject;
import de.nilsmod.launcher.manifest.VersionEntry;
import de.nilsmod.launcher.util.JsonUtil;
import de.nilsmod.launcher.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class LauncherProfileManager {
    private final MinecraftDirectory minecraft;
    private final Log log;

    public LauncherProfileManager(MinecraftDirectory minecraft, Log log) {
        this.minecraft = minecraft;
        this.log = log;
    }

    public void upsertFabricProfile(VersionEntry entry, String versionId) throws Exception {
        Path file = minecraft.launcherProfilesJson();
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) {
            Files.copy(file, file.resolveSibling("launcher_profiles.json.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        JsonObject root = JsonUtil.readObject(file);
        JsonObject profiles = root.has("profiles") && root.get("profiles").isJsonObject()
                ? root.getAsJsonObject("profiles")
                : new JsonObject();
        root.add("profiles", profiles);
        if (!root.has("version")) {
            root.addProperty("version", 6);
        }

        String profileId = profileId(entry.minecraftVersion());
        JsonObject existing = profiles.has(profileId) && profiles.get(profileId).isJsonObject()
                ? profiles.getAsJsonObject(profileId)
                : new JsonObject();
        String now = Instant.now().toString();
        if (!existing.has("created")) {
            existing.addProperty("created", now);
        }
        existing.addProperty("lastUsed", now);
        existing.addProperty("name", "NilsMod " + entry.minecraftVersion());
        existing.addProperty("type", "custom");
        existing.addProperty("lastVersionId", versionId);
        existing.addProperty("gameDir", minecraft.gameDir(entry.minecraftVersion()).toString());
        existing.addProperty("icon", "Furnace");
        profiles.add(profileId, existing);

        JsonUtil.write(file, root);
        log.info("Minecraft Launcher Profil erstellt/aktualisiert: NilsMod " + entry.minecraftVersion());
    }

    public static String profileId(String minecraftVersion) {
        return "nilsmod-" + minecraftVersion;
    }
}
