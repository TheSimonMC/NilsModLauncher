package de.nilsmod.launcher.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Downloader {
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String readString(String url) throws IOException, InterruptedException {
        URI uri = requireHttps(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "NilsModLauncher")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureOk(response.statusCode(), url);
        return response.body();
    }

    public void download(String url, Path target) throws IOException, InterruptedException {
        URI uri = requireHttps(url);
        Files.createDirectories(target.getParent());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "NilsModLauncher")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        ensureOk(response.statusCode(), url);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
            in.transferTo(out);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static URI requireHttps(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Only HTTPS downloads are allowed: " + url);
        }
        return uri;
    }

    private static void ensureOk(int statusCode, String url) throws IOException {
        if (statusCode / 100 != 2) {
            throw new IOException("HTTP " + statusCode + " for " + url);
        }
    }
}
