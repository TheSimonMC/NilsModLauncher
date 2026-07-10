package de.nilsmod.launcher.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class HashVerifier {
    private HashVerifier() {
    }

    public static String sha256(Path path) throws IOException {
        return hash(path, "SHA-256");
    }

    public static String sha512(Path path) throws IOException {
        return hash(path, "SHA-512");
    }

    public static boolean matchesSha256(Path path, String expected) throws IOException {
        return normalize(expected).equals(sha256(path));
    }

    public static boolean matchesSha512(Path path, String expected) throws IOException {
        return normalize(expected).equals(sha512(path));
    }

    public static boolean isRealHash(String hash, int expectedHexLength) {
        String normalized = normalize(hash);
        return normalized.length() == expectedHexLength && normalized.matches("[0-9a-f]+");
    }

    private static String hash(Path path, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            try (InputStream in = Files.newInputStream(path)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String normalize(String hash) {
        return hash == null ? "" : hash.trim().toLowerCase(Locale.ROOT);
    }
}
