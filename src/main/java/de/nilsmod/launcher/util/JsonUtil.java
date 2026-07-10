package de.nilsmod.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonUtil {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonUtil() {
    }

    public static JsonObject readObject(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new JsonObject();
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonElement element = JsonParser.parseString(json);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    public static void write(Path path, JsonElement element) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(element), StandardCharsets.UTF_8);
    }
}
