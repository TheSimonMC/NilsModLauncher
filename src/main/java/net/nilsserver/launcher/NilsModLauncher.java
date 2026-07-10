package net.nilsserver.launcher;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NilsModLauncher {
    private static final String LAUNCHER_VERSION = "1.0.1";
    private static final String MOD_VERSION = "1.0.1";
    private static final String MINECRAFT_VERSION = "1.21.11";
    private static final String LOADER_VERSION = "0.19.3";
    private static final String FABRIC_VERSION_ID = "fabric-loader-" + LOADER_VERSION + "-" + MINECRAFT_VERSION;
    private static final String PROFILE_ID = "nilsmod-1-21-11";
    private static final String PROFILE_NAME = "NilsMod 1.21.11";
    private static final Color BG = new Color(13, 9, 20);
    private static final Color PANEL = new Color(25, 17, 36);
    private static final Color PANEL_SOFT = new Color(39, 27, 55);
    private static final Color PRIMARY = new Color(255, 173, 0);
    private static final Color PRIMARY_DARK = new Color(154, 93, 0);
    private static final Color GREEN = new Color(44, 174, 101);
    private static final Color RED = new Color(224, 78, 78);
    private static final Color TEXT = new Color(245, 241, 255);
    private static final Color MUTED = new Color(178, 169, 196);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final Path launcherDir;
    private final Path outputsDir;
    private final Path minecraftDir;
    private final Path instanceDir;
    private final Path modsDir;
    private final Path settingsFile;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final JTextArea log = new JTextArea();
    private final JLabel status = new JLabel("Bereit");
    private final JLabel profileState = new JLabel("-");
    private final JLabel fabricState = new JLabel("-");
    private final JLabel nilsState = new JLabel("-");
    private final JLabel fabricApiState = new JLabel("-");
    private final JLabel sodiumState = new JLabel("-");
    private final JLabel voiceState = new JLabel("-");
    private final JLabel instanceState = new JLabel("-");
    private JCheckBox sodiumBox;
    private JCheckBox voiceBox;
    private JCheckBox openLauncherBox;
    private JComboBox<String> ramBox;
    private JTextField usernameField;
    private JButton startButton;
    private JButton installButton;
    private JButton openLauncherButton;
    private JButton openFolderButton;
    private boolean installing;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new NilsModLauncher().show();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    private NilsModLauncher() {
        this.launcherDir = findLauncherDir();
        this.outputsDir = findOutputsDir(this.launcherDir);
        this.minecraftDir = defaultMinecraftDir();
        this.instanceDir = this.minecraftDir.resolve("nilsmod").resolve(MINECRAFT_VERSION);
        this.modsDir = this.instanceDir.resolve("mods");
        this.settingsFile = this.launcherDir.resolve("launcher-settings.properties");
    }

    private void show() {
        JFrame frame = new JFrame("NilsMod Launcher");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 700));
        frame.setContentPane(createContent());
        frame.setSize(1020, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        loadSettings();
        refreshStatus();
    }

    private JPanel createContent() {
        JPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(18, 18));
        root.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createMain(), BorderLayout.CENTER);
        root.add(createLog(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel createHeader() {
        JPanel header = transparent(new BorderLayout(18, 0));
        JPanel brand = transparent(new FlowLayout(FlowLayout.LEFT, 14, 0));
        JLabel logo = new JLabel(loadLogo(76));
        JPanel titles = transparent();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("NilsMod Launcher");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 34F));
        title.setForeground(TEXT);
        JLabel subtitle = new JLabel("Fabric Profil-Manager fuer NilsMod " + MOD_VERSION + " / Minecraft " + MINECRAFT_VERSION);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 13F));
        subtitle.setForeground(MUTED);
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);
        brand.add(logo);
        brand.add(titles);
        header.add(brand, BorderLayout.WEST);

        JPanel badge = new RoundedPanel(PANEL, PRIMARY_DARK);
        badge.setLayout(new BoxLayout(badge, BoxLayout.Y_AXIS));
        badge.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        JLabel version = new JLabel("Launcher " + LAUNCHER_VERSION);
        version.setForeground(PRIMARY);
        version.setFont(version.getFont().deriveFont(Font.BOLD, 13F));
        JLabel mc = new JLabel(FABRIC_VERSION_ID);
        mc.setForeground(MUTED);
        mc.setFont(mc.getFont().deriveFont(11F));
        badge.add(version);
        badge.add(Box.createVerticalStrut(4));
        badge.add(mc);
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private JPanel createMain() {
        JPanel main = transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 18);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0.42;
        c.weighty = 1.0;
        c.gridx = 0;
        c.gridy = 0;
        main.add(createOptionsCard(), c);

        c.insets = new Insets(0, 0, 0, 0);
        c.weightx = 0.58;
        c.gridx = 1;
        main.add(createStatusCard(), c);
        return main;
    }

    private JPanel createOptionsCard() {
        JPanel card = new RoundedPanel(PANEL, new Color(88, 61, 120));
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = sectionTitle("Mod-Auswahl");
        card.add(title, BorderLayout.NORTH);

        JPanel form = transparent();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        sodiumBox = optionBox("Sodium", "Performance-Mod automatisch installieren");
        voiceBox = optionBox("Simple Voice Chat", "Voice Chat automatisch installieren");
        openLauncherBox = optionBox("Minecraft Launcher nach Installation oeffnen", "Praktisch, wenn du nicht direkt starten willst");
        form.add(sodiumBox);
        form.add(Box.createVerticalStrut(10));
        form.add(voiceBox);
        form.add(Box.createVerticalStrut(18));
        form.add(requiredRow("NilsMod", "immer aktiv"));
        form.add(Box.createVerticalStrut(8));
        form.add(requiredRow("Fabric API", "immer aktiv"));
        form.add(Box.createVerticalStrut(16));

        JLabel ramTitle = smallTitle("RAM fuer das Profil");
        form.add(ramTitle);
        form.add(Box.createVerticalStrut(8));
        ramBox = new JComboBox<>(new String[]{"2048 MB", "4096 MB", "6144 MB", "8192 MB", "12288 MB", "16384 MB"});
        ramBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        ramBox.setBackground(new Color(18, 13, 27));
        ramBox.setForeground(TEXT);
        ramBox.setFont(ramBox.getFont().deriveFont(Font.BOLD, 13F));
        form.add(ramBox);
        form.add(Box.createVerticalStrut(12));

        JLabel nameTitle = smallTitle("Offline-Name fuer Direktstart");
        form.add(nameTitle);
        form.add(Box.createVerticalStrut(8));
        usernameField = new JTextField(System.getProperty("user.name", "NilsMod"));
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        usernameField.setBackground(new Color(18, 13, 27));
        usernameField.setForeground(TEXT);
        usernameField.setCaretColor(PRIMARY);
        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(89, 66, 118)),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        usernameField.setFont(usernameField.getFont().deriveFont(Font.BOLD, 13F));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(12));
        form.add(openLauncherBox);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(null);
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.getVerticalScrollBar().setUnitIncrement(18);
        card.add(formScroll, BorderLayout.CENTER);

        startButton = primaryButton("NilsMod starten");
        startButton.addActionListener(event -> startGameAsync());
        installButton = ghostButton("Nur installieren / Profil aktualisieren");
        installButton.addActionListener(event -> installAsync());
        openLauncherButton = ghostButton("Minecraft Launcher oeffnen");
        openLauncherButton.addActionListener(event -> openMinecraftLauncher());
        openFolderButton = ghostButton("Instanz-Ordner oeffnen");
        openFolderButton.addActionListener(event -> openPath(instanceDir));

        JPanel buttons = transparent();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.add(startButton);
        buttons.add(Box.createVerticalStrut(10));
        buttons.add(installButton);
        buttons.add(Box.createVerticalStrut(10));
        buttons.add(openLauncherButton);
        buttons.add(Box.createVerticalStrut(10));
        buttons.add(openFolderButton);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = new RoundedPanel(new Color(16, 12, 24), new Color(91, 67, 126));
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel top = transparent(new BorderLayout());
        JLabel title = sectionTitle("Installation");
        top.add(title, BorderLayout.WEST);
        status.setForeground(MUTED);
        status.setHorizontalAlignment(JLabel.RIGHT);
        top.add(status, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        JPanel rows = transparent();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(statusRow("Profil", profileState));
        rows.add(statusRow("Fabric Loader", fabricState));
        rows.add(statusRow("NilsMod", nilsState));
        rows.add(statusRow("Fabric API", fabricApiState));
        rows.add(statusRow("Sodium", sodiumState));
        rows.add(statusRow("Simple Voice Chat", voiceState));
        rows.add(statusRow("Instanz", instanceState));
        card.add(rows, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html>Der Launcher nutzt ein eigenes GameDir:<br><b>" + html(instanceDir.toString()) + "</b><br>Deaktivierte optionale Mods werden dort entfernt, nicht global.</html>");
        hint.setForeground(MUTED);
        hint.setFont(hint.getFont().deriveFont(12F));
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    private JScrollPane createLog() {
        log.setEditable(false);
        log.setRows(8);
        log.setBackground(new Color(5, 4, 8));
        log.setForeground(new Color(231, 226, 242));
        log.setCaretColor(PRIMARY);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(60, 43, 82)));
        scroll.setPreferredSize(new Dimension(100, 150));
        return scroll;
    }

    private void installAsync() {
        if (installing) {
            append("Installation laeuft bereits.");
            return;
        }
        installing = true;
        setButtons(false);
        readSettingsFromUi();
        saveSettings();
        status.setText("Installiere...");
        append("Starte Profil-Update fuer " + PROFILE_NAME + ".");

        new Thread(() -> {
            try {
                installProfile();
                append("Fertig. Profil '" + PROFILE_NAME + "' ist im offiziellen Minecraft Launcher angelegt.");
                if (openLauncherBox.isSelected()) {
                    openMinecraftLauncher();
                }
            } catch (Exception exception) {
                append("Fehler: " + exception.getMessage());
            } finally {
                installing = false;
                SwingUtilities.invokeLater(() -> {
                    setButtons(true);
                    refreshStatus();
                });
            }
        }, "nilsmod-install").start();
    }

    private void startGameAsync() {
        if (installing) {
            append("Installation/Start laeuft bereits.");
            return;
        }
        installing = true;
        setButtons(false);
        readSettingsFromUi();
        saveSettings();
        status.setText("Starte...");
        append("Bereite NilsMod-Start vor.");

        new Thread(() -> {
            try {
                installProfile();
                launchLocalMinecraft();
                append("NilsMod-Prozess gestartet.");
            } catch (Exception exception) {
                append("Direktstart nicht moeglich: " + exception.getMessage());
                append("Oeffne stattdessen den offiziellen Minecraft Launcher mit dem NilsMod-Profil.");
                openMinecraftLauncher();
            } finally {
                installing = false;
                SwingUtilities.invokeLater(() -> {
                    setButtons(true);
                    refreshStatus();
                });
            }
        }, "nilsmod-start").start();
    }

    private void installProfile() throws Exception {
        Files.createDirectories(instanceDir);
        Files.createDirectories(modsDir);
        Files.createDirectories(minecraftDir.resolve("versions").resolve(FABRIC_VERSION_ID));

        installFabricVersion();
        downloadFabricLibraries();
        syncLocalNilsModJar();
        syncModrinth("fabric-api", "Fabric API", List.of("fabric-api-"), true);
        syncModrinth("sodium", "Sodium", List.of("sodium-", "sodium-fabric-"), sodiumBox.isSelected());
        syncModrinth("simple-voice-chat", "Simple Voice Chat", List.of("voicechat-", "simple-voice-chat-"), voiceBox.isSelected());
        writeInstanceReadme();
        writeLauncherProfile();
    }

    private void installFabricVersion() throws Exception {
        Path versionDir = minecraftDir.resolve("versions").resolve(FABRIC_VERSION_ID);
        Path versionJson = versionDir.resolve(FABRIC_VERSION_ID + ".json");
        String url = "https://meta.fabricmc.net/v2/versions/loader/" + MINECRAFT_VERSION + "/" + LOADER_VERSION + "/profile/json";
        append("Lade Fabric-Profil: " + FABRIC_VERSION_ID);
        String json = httpGetText(url);
        Files.writeString(versionJson, json, StandardCharsets.UTF_8);
    }

    private void downloadFabricLibraries() throws Exception {
        Path versionJson = minecraftDir.resolve("versions").resolve(FABRIC_VERSION_ID).resolve(FABRIC_VERSION_ID + ".json");
        if (!Files.isRegularFile(versionJson)) {
            return;
        }
        String json = Files.readString(versionJson, StandardCharsets.UTF_8);
        int downloaded = 0;
        for (MavenLibrary library : fabricLibraries(json)) {
            if (downloadLibrary(library.url(), library.path())) {
                downloaded++;
            }
        }
        append(downloaded == 0 ? "Fabric Loader Libraries sind aktuell." : "Fabric Loader Libraries geladen: " + downloaded);
    }

    private void syncLocalNilsModJar() throws IOException {
        removeManagedJars(List.of("nilsmod-"));
        Path source = findNilsModJar();
        if (source == null) {
            throw new IOException("NilsMod-JAR nicht gefunden. Bitte erst outputs\\NilsMod bauen.");
        }
        Path target = modsDir.resolve("nilsmod-" + MOD_VERSION + ".jar");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        append("NilsMod kopiert: " + target.getFileName());
    }

    private void syncModrinth(String slug, String label, List<String> prefixes, boolean enabled) throws Exception {
        removeManagedJars(prefixes);
        if (!enabled) {
            append(label + " deaktiviert.");
            return;
        }
        String url = "https://api.modrinth.com/v2/project/" + slug + "/version?loaders="
                + encodeJsonArrayValue("fabric") + "&game_versions=" + encodeJsonArrayValue(MINECRAFT_VERSION);
        append("Suche " + label + " fuer " + MINECRAFT_VERSION + "...");
        String json = httpGetText(url);
        ModFile file = firstPrimaryJar(json);
        if (file == null) {
            throw new IOException(label + " hat keine passende Fabric-JAR fuer " + MINECRAFT_VERSION + ".");
        }
        Path target = modsDir.resolve(file.filename());
        append("Lade " + label + ": " + file.filename());
        downloadFile(file.url(), target);
    }

    private void writeLauncherProfile() throws IOException {
        Path profiles = minecraftDir.resolve("launcher_profiles.json");
        String json = Files.exists(profiles) ? Files.readString(profiles, StandardCharsets.UTF_8) : "{\n  \"profiles\" : {},\n  \"version\" : 6\n}\n";
        Files.writeString(profiles.resolveSibling("launcher_profiles.nilsmod.bak"), json, StandardCharsets.UTF_8);

        json = removeExistingProfile(json, PROFILE_ID);
        String profile = profileJson();
        int insert = json.indexOf("\"profiles\"");
        if (insert < 0) {
            json = "{\n  \"profiles\" : {\n" + profile + "\n  },\n  \"version\" : 6\n}\n";
        } else {
            int open = json.indexOf('{', insert);
            if (open < 0) {
                throw new IOException("launcher_profiles.json ist ungueltig: profiles-Objekt fehlt.");
            }
            boolean empty = json.substring(open + 1).stripLeading().startsWith("}");
            String prefix = json.substring(0, open + 1);
            String suffix = json.substring(open + 1);
            json = prefix + "\n" + profile + (empty ? "" : ",") + suffix;
        }
        Files.writeString(profiles, json, StandardCharsets.UTF_8);
        append("Launcher-Profil geschrieben: " + PROFILE_NAME);
    }

    private String profileJson() {
        String now = ISO.format(Instant.now());
        int ramMb = selectedRamMb();
        String icon = logoDataUri();
        return "    \"" + PROFILE_ID + "\" : {\n"
                + "      \"created\" : \"" + now + "\",\n"
                + "      \"gameDir\" : \"" + json(instanceDir.toString()) + "\",\n"
                + "      \"icon\" : \"" + json(icon) + "\",\n"
                + "      \"javaArgs\" : \"-Xmx" + ramMb + "M -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M\",\n"
                + "      \"lastUsed\" : \"" + now + "\",\n"
                + "      \"lastVersionId\" : \"" + FABRIC_VERSION_ID + "\",\n"
                + "      \"name\" : \"" + PROFILE_NAME + "\",\n"
                + "      \"type\" : \"custom\"\n"
                + "    }";
    }

    private void writeInstanceReadme() throws IOException {
        String text = "NilsMod " + MOD_VERSION + " instance\n\n"
                + "This folder is managed by the NilsMod Launcher.\n"
                + "Minecraft: " + MINECRAFT_VERSION + "\n"
                + "Fabric Loader: " + LOADER_VERSION + "\n"
                + "Optional mods: Sodium=" + sodiumBox.isSelected() + ", Simple Voice Chat=" + voiceBox.isSelected() + "\n";
        Files.writeString(instanceDir.resolve("README-NilsMod.txt"), text, StandardCharsets.UTF_8);
    }

    private void refreshStatus() {
        state(profileState, launcherProfileExists(), "angelegt", "fehlt");
        state(fabricState, Files.exists(minecraftDir.resolve("versions").resolve(FABRIC_VERSION_ID).resolve(FABRIC_VERSION_ID + ".json")), "installiert", "fehlt");
        state(nilsState, hasJarStarting("nilsmod-"), "installiert", "fehlt");
        state(fabricApiState, hasJarStarting("fabric-api-"), "installiert", "fehlt");
        state(sodiumState, hasJarStarting("sodium-") || hasJarStarting("sodium-fabric-"), "installiert", sodiumBox != null && sodiumBox.isSelected() ? "fehlt" : "deaktiviert");
        state(voiceState, hasJarStarting("voicechat-") || hasJarStarting("simple-voice-chat-"), "installiert", voiceBox != null && voiceBox.isSelected() ? "fehlt" : "deaktiviert");
        state(instanceState, Files.isDirectory(instanceDir), "bereit", "fehlt");
        status.setText(Files.isDirectory(instanceDir) ? "Bereit" : "Noch nicht installiert");
    }

    private boolean launcherProfileExists() {
        Path profiles = minecraftDir.resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) {
            return false;
        }
        try {
            return Files.readString(profiles, StandardCharsets.UTF_8).contains("\"" + PROFILE_ID + "\"");
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean hasJarStarting(String prefix) {
        if (!Files.isDirectory(modsDir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(modsDir)) {
            return files.anyMatch(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.startsWith(prefix.toLowerCase(Locale.ROOT)) && name.endsWith(".jar");
            });
        } catch (IOException ignored) {
            return false;
        }
    }

    private void removeManagedJars(List<String> prefixes) throws IOException {
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(modsDir)) {
            List<Path> targets = files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".jar") && prefixes.stream().anyMatch(prefix -> name.startsWith(prefix.toLowerCase(Locale.ROOT)));
                    })
                    .toList();
            for (Path target : targets) {
                Files.deleteIfExists(target);
            }
        }
    }

    private String httpGetText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "NilsModLauncher/" + LAUNCHER_VERSION)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " fuer " + url);
        }
        return response.body();
    }

    private void downloadFile(String url, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "NilsModLauncher/" + LAUNCHER_VERSION)
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Download fehlgeschlagen: HTTP " + response.statusCode());
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
            in.transferTo(out);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private ModFile firstPrimaryJar(String json) {
        int filesIndex = json.indexOf("\"files\"");
        if (filesIndex < 0) {
            return null;
        }
        Pattern pattern = Pattern.compile("\"url\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\".*?\"filename\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\".*?\"primary\"\\s*:\\s*(true|false)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json.substring(filesIndex));
        ModFile first = null;
        while (matcher.find()) {
            String url = unescapeJson(matcher.group(1));
            String filename = unescapeJson(matcher.group(2));
            boolean primary = Boolean.parseBoolean(matcher.group(3));
            if (!filename.endsWith(".jar") || filename.toLowerCase(Locale.ROOT).contains("sources")) {
                continue;
            }
            ModFile file = new ModFile(url, filename);
            if (primary) {
                return file;
            }
            if (first == null) {
                first = file;
            }
        }
        return first;
    }

    private void launchLocalMinecraft() throws Exception {
        Path vanillaJsonPath = ensureVanillaVersionJson();
        String vanillaJson = Files.readString(vanillaJsonPath, StandardCharsets.UTF_8);
        Path fabricJsonPath = minecraftDir.resolve("versions").resolve(FABRIC_VERSION_ID).resolve(FABRIC_VERSION_ID + ".json");
        String fabricJson = Files.readString(fabricJsonPath, StandardCharsets.UTF_8);

        ensureVanillaClient(vanillaJson);
        downloadVanillaLibraries(vanillaJson);
        prepareAssetIndex(vanillaJson);
        extractNatives(vanillaJson);

        Files.createDirectories(instanceDir.resolve("logs"));
        Path gameLog = instanceDir.resolve("logs").resolve("nilsmod-game.log");
        List<String> command = buildLaunchCommand(vanillaJson, fabricJson);
        ProcessBuilder process = new ProcessBuilder(command);
        process.directory(instanceDir.toFile());
        process.redirectErrorStream(true);
        process.redirectOutput(ProcessBuilder.Redirect.appendTo(gameLog.toFile()));
        process.start();
        append("Game-Log: " + gameLog);
    }

    private Path ensureVanillaVersionJson() throws Exception {
        Path versionDir = minecraftDir.resolve("versions").resolve(MINECRAFT_VERSION);
        Path versionJson = versionDir.resolve(MINECRAFT_VERSION + ".json");
        if (Files.isRegularFile(versionJson)) {
            return versionJson;
        }
        Files.createDirectories(versionDir);
        append("Minecraft " + MINECRAFT_VERSION + " fehlt lokal, suche Mojang-Manifest...");
        String manifest = httpGetText("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        Pattern pattern = Pattern.compile("\\{[^{}]*\"id\"\\s*:\\s*\"" + Pattern.quote(MINECRAFT_VERSION) + "\"[^{}]*\"url\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"[^{}]*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(manifest);
        if (!matcher.find()) {
            throw new IOException("Minecraft " + MINECRAFT_VERSION + " ist nicht lokal vorhanden und nicht im Mojang-Manifest auffindbar.");
        }
        String json = httpGetText(unescapeJson(matcher.group(1)));
        Files.writeString(versionJson, json, StandardCharsets.UTF_8);
        return versionJson;
    }

    private void ensureVanillaClient(String versionJson) throws Exception {
        Path clientJar = minecraftDir.resolve("versions").resolve(MINECRAFT_VERSION).resolve(MINECRAFT_VERSION + ".jar");
        if (Files.isRegularFile(clientJar)) {
            return;
        }
        Matcher matcher = Pattern.compile("\"client\"\\s*:\\s*\\{.*?\"url\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL).matcher(versionJson);
        if (!matcher.find()) {
            throw new IOException("Client-JAR-URL fehlt in " + MINECRAFT_VERSION + ".json.");
        }
        append("Lade Minecraft Client-JAR...");
        downloadFile(unescapeJson(matcher.group(1)), clientJar);
    }

    private void downloadVanillaLibraries(String versionJson) throws Exception {
        int downloaded = 0;
        Matcher matcher = artifactPattern().matcher(versionJson);
        while (matcher.find()) {
            String path = unescapeJson(matcher.group(1));
            String url = unescapeJson(matcher.group(2));
            if (downloadLibrary(url, path)) {
                downloaded++;
            }
        }
        append(downloaded == 0 ? "Minecraft Libraries sind aktuell." : "Minecraft Libraries geladen: " + downloaded);
    }

    private void prepareAssetIndex(String versionJson) throws Exception {
        Matcher matcher = Pattern.compile("\"assetIndex\"\\s*:\\s*\\{.*?\"id\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\".*?\"url\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL).matcher(versionJson);
        if (!matcher.find()) {
            return;
        }
        String id = unescapeJson(matcher.group(1));
        String url = unescapeJson(matcher.group(2));
        Path index = minecraftDir.resolve("assets").resolve("indexes").resolve(id + ".json");
        if (!Files.isRegularFile(index)) {
            append("Lade Asset-Index " + id + "...");
            downloadFile(url, index);
        }
    }

    private void extractNatives(String versionJson) throws IOException {
        Path nativesDir = instanceDir.resolve("natives");
        Files.createDirectories(nativesDir);
        Matcher matcher = artifactPattern().matcher(versionJson);
        int extracted = 0;
        while (matcher.find()) {
            String libraryPath = unescapeJson(matcher.group(1));
            String name = Path.of(libraryPath).getFileName().toString().toLowerCase(Locale.ROOT);
            if (!isPreferredWindowsNative(name)) {
                continue;
            }
            Path jar = minecraftDir.resolve("libraries").resolve(libraryPath);
            if (!Files.isRegularFile(jar)) {
                continue;
            }
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entry.isDirectory() || !entryName.toLowerCase(Locale.ROOT).endsWith(".dll")) {
                        continue;
                    }
                    Path target = nativesDir.resolve(Path.of(entryName).getFileName().toString());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    extracted++;
                }
            }
        }
        append(extracted == 0 ? "Natives waren bereits vorbereitet oder fehlen lokal." : "Windows-Natives vorbereitet: " + extracted);
    }

    private List<String> buildLaunchCommand(String vanillaJson, String fabricJson) throws IOException {
        String assets = assetIndexId(vanillaJson);
        String username = safeUsername(usernameField.getText());
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        String classpath = buildClasspath(vanillaJson, fabricJson);
        Path java = findJavaExecutable();
        Path nativesDir = instanceDir.resolve("natives");

        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-Xmx" + selectedRamMb() + "M");
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:+UseG1GC");
        command.add("-XX:G1NewSizePercent=20");
        command.add("-XX:G1ReservePercent=20");
        command.add("-XX:MaxGCPauseMillis=50");
        command.add("-XX:G1HeapRegionSize=32M");
        command.add("-Djava.library.path=" + nativesDir);
        command.add("-Djna.tmpdir=" + nativesDir);
        command.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + nativesDir);
        command.add("-Dio.netty.native.workdir=" + nativesDir);
        command.add("-Dminecraft.launcher.brand=NilsModLauncher");
        command.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION);
        command.add("-DFabricMcEmu= net.minecraft.client.main.Main ");
        command.add("-cp");
        command.add(classpath);
        command.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
        command.add("--username");
        command.add(username);
        command.add("--version");
        command.add(PROFILE_NAME);
        command.add("--gameDir");
        command.add(instanceDir.toString());
        command.add("--assetsDir");
        command.add(minecraftDir.resolve("assets").toString());
        command.add("--assetIndex");
        command.add(assets);
        command.add("--uuid");
        command.add(uuid);
        command.add("--accessToken");
        command.add("0");
        command.add("--clientId");
        command.add("0");
        command.add("--xuid");
        command.add("0");
        command.add("--userType");
        command.add("legacy");
        command.add("--versionType");
        command.add("NilsMod");
        return command;
    }

    private String buildClasspath(String vanillaJson, String fabricJson) {
        Set<String> paths = new LinkedHashSet<>();
        Matcher vanilla = artifactPattern().matcher(vanillaJson);
        while (vanilla.find()) {
            String path = unescapeJson(vanilla.group(1));
            String name = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.contains("natives-")) {
                Path file = minecraftDir.resolve("libraries").resolve(path);
                if (Files.isRegularFile(file)) {
                    paths.add(file.toString());
                }
            }
        }
        for (MavenLibrary library : fabricLibraries(fabricJson)) {
            Path file = minecraftDir.resolve("libraries").resolve(library.path());
            if (Files.isRegularFile(file)) {
                paths.add(file.toString());
            }
        }
        paths.add(minecraftDir.resolve("versions").resolve(MINECRAFT_VERSION).resolve(MINECRAFT_VERSION + ".jar").toString());
        return String.join(File.pathSeparator, paths);
    }

    private String assetIndexId(String versionJson) {
        Matcher matcher = Pattern.compile("\"assetIndex\"\\s*:\\s*\\{.*?\"id\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL).matcher(versionJson);
        return matcher.find() ? unescapeJson(matcher.group(1)) : "legacy";
    }

    private boolean downloadLibrary(String url, String libraryPath) throws Exception {
        Path target = minecraftDir.resolve("libraries").resolve(libraryPath);
        if (Files.isRegularFile(target)) {
            return false;
        }
        append("Lade Library: " + Path.of(libraryPath).getFileName());
        downloadFile(url, target);
        return true;
    }

    private List<MavenLibrary> fabricLibraries(String fabricJson) {
        List<MavenLibrary> libraries = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"\\s*,\\s*\"url\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL).matcher(fabricJson);
        while (matcher.find()) {
            String name = unescapeJson(matcher.group(1));
            String baseUrl = unescapeJson(matcher.group(2));
            String path = mavenPath(name);
            if (path == null) {
                continue;
            }
            String url = baseUrl.endsWith("/") ? baseUrl + path : baseUrl + "/" + path;
            libraries.add(new MavenLibrary(url, path));
        }
        return libraries;
    }

    private static String mavenPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) {
            return null;
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static Pattern artifactPattern() {
        return Pattern.compile("\"artifact\"\\s*:\\s*\\{\\s*\"path\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\".*?\"url\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
    }

    private static boolean isPreferredWindowsNative(String name) {
        return name.contains("natives-windows") && !name.contains("windows-x86") && !name.contains("windows-arm64");
    }

    private static String safeUsername(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) {
            cleaned = "NilsPlayer";
        }
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    private Path findJavaExecutable() {
        Path home = Paths.get(System.getProperty("java.home", ""));
        Path javaw = home.resolve("bin").resolve("javaw.exe");
        if (Files.isRegularFile(javaw)) {
            return javaw;
        }
        Path java = home.resolve("bin").resolve("java.exe");
        if (Files.isRegularFile(java)) {
            return java;
        }
        return Paths.get("java");
    }

    private Path findNilsModJar() throws IOException {
        List<Path> candidates = new ArrayList<>();
        candidates.add(outputsDir.resolve("NilsMod").resolve("build").resolve("libs").resolve("nilsmod-" + MOD_VERSION + ".jar"));
        candidates.add(launcherDir.resolve("mods").resolve("nilsmod-" + MOD_VERSION + ".jar"));
        candidates.add(launcherDir.resolve("nilsmod-" + MOD_VERSION + ".jar"));
        candidates.add(outputsDir.resolve("NilsMod").resolve("build").resolve("devlibs").resolve("nilsmod-" + MOD_VERSION + "-dev.jar"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path root = outputsDir.resolve("NilsMod").resolve("build").resolve("libs");
        if (Files.isDirectory(root)) {
            try (Stream<Path> files = Files.list(root)) {
                return files.filter(path -> path.getFileName().toString().matches("nilsmod-.*\\.jar"))
                        .filter(path -> !path.getFileName().toString().contains("sources"))
                        .max(Comparator.comparing(path -> path.getFileName().toString()))
                        .orElse(null);
            }
        }
        return null;
    }

    private String removeExistingProfile(String json, String id) {
        Pattern profile = Pattern.compile("\\s*\"" + Pattern.quote(id) + "\"\\s*:\\s*\\{.*?\\}\\s*,?", Pattern.DOTALL);
        String cleaned = profile.matcher(json).replaceAll("");
        cleaned = cleaned.replaceAll("\\{\\s*,", "{");
        cleaned = cleaned.replaceAll(",\\s*\\}", "}");
        return cleaned;
    }

    private void openMinecraftLauncher() {
        new Thread(() -> {
            Path exe = findMinecraftLauncher();
            try {
                if (exe != null) {
                    new ProcessBuilder(exe.toString()).start();
                    append("Minecraft Launcher geoeffnet.");
                } else {
                    new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "minecraft:").start();
                    append("Minecraft Launcher per minecraft:-Protokoll geoeffnet.");
                }
            } catch (IOException exception) {
                append("Minecraft Launcher konnte nicht geoeffnet werden: " + exception.getMessage());
                openPath(minecraftDir);
            }
        }, "open-minecraft-launcher").start();
    }

    private Path findMinecraftLauncher() {
        List<Path> candidates = List.of(
                Paths.get(System.getenv().getOrDefault("LOCALAPPDATA", ""), "Programs", "Minecraft Launcher", "MinecraftLauncher.exe"),
                Paths.get(System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)"), "Minecraft Launcher", "MinecraftLauncher.exe"),
                Paths.get(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Minecraft Launcher", "MinecraftLauncher.exe")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void openPath(Path path) {
        try {
            Files.createDirectories(path);
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException exception) {
            append("Ordner konnte nicht geoeffnet werden: " + exception.getMessage());
        }
    }

    private void loadSettings() {
        Properties properties = new Properties();
        if (Files.exists(settingsFile)) {
            try (InputStream in = Files.newInputStream(settingsFile)) {
                properties.load(in);
            } catch (IOException exception) {
                append("Settings konnten nicht geladen werden: " + exception.getMessage());
            }
        }
        sodiumBox.setSelected(Boolean.parseBoolean(properties.getProperty("sodium", "true")));
        voiceBox.setSelected(Boolean.parseBoolean(properties.getProperty("voicechat", "true")));
        openLauncherBox.setSelected(Boolean.parseBoolean(properties.getProperty("openLauncher", "true")));
        ramBox.setSelectedItem(properties.getProperty("ram", "4096 MB"));
        usernameField.setText(properties.getProperty("username", safeUsername(System.getProperty("user.name", "NilsPlayer"))));
    }

    private void readSettingsFromUi() {
        Objects.requireNonNull(sodiumBox);
        Objects.requireNonNull(voiceBox);
        Objects.requireNonNull(openLauncherBox);
        Objects.requireNonNull(usernameField);
    }

    private void saveSettings() {
        Properties properties = new Properties();
        properties.setProperty("sodium", Boolean.toString(sodiumBox.isSelected()));
        properties.setProperty("voicechat", Boolean.toString(voiceBox.isSelected()));
        properties.setProperty("openLauncher", Boolean.toString(openLauncherBox.isSelected()));
        properties.setProperty("ram", String.valueOf(ramBox.getSelectedItem()));
        properties.setProperty("username", safeUsername(usernameField.getText()));
        try (OutputStream out = Files.newOutputStream(settingsFile)) {
            properties.store(out, "NilsMod Launcher");
        } catch (IOException exception) {
            append("Settings konnten nicht gespeichert werden: " + exception.getMessage());
        }
    }

    private int selectedRamMb() {
        String item = String.valueOf(ramBox.getSelectedItem());
        Matcher matcher = Pattern.compile("(\\d+)").matcher(item);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 4096;
    }

    private ImageIcon loadLogo(int size) {
        for (Path candidate : logoCandidates()) {
            try {
                if (Files.exists(candidate)) {
                    Image image = ImageIO.read(candidate.toFile()).getScaledInstance(size, size, Image.SCALE_SMOOTH);
                    return new ImageIcon(image);
                }
            } catch (IOException ignored) {
            }
        }
        return new ImageIcon();
    }

    private String logoDataUri() {
        for (Path candidate : logoCandidates()) {
            try {
                if (Files.exists(candidate)) {
                    byte[] data = Files.readAllBytes(candidate);
                    return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(data);
                }
            } catch (IOException ignored) {
            }
        }
        return "Furnace";
    }

    private List<Path> logoCandidates() {
        return List.of(
                launcherDir.resolve("assets").resolve("nilsmod_logo_vanilla.png"),
                outputsDir.resolve("NilsMod").resolve("src").resolve("main").resolve("resources")
                        .resolve("assets").resolve("nilsmod").resolve("textures").resolve("nilsserver").resolve("nilsmod_logo_vanilla.png"),
                Paths.get(System.getProperty("user.home"), "Downloads", "NilsMod_Logo_Vanilla.png")
        );
    }

    private void setButtons(boolean enabled) {
        startButton.setEnabled(enabled);
        installButton.setEnabled(enabled);
        openLauncherButton.setEnabled(enabled);
        openFolderButton.setEnabled(enabled);
    }

    private void state(JLabel label, boolean ok, String yes, String no) {
        label.setText(ok ? yes : no);
        label.setForeground(ok ? GREEN : ("deaktiviert".equals(no) ? MUTED : RED));
    }

    private void append(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + System.lineSeparator());
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private JPanel statusRow(String name, JLabel value) {
        JPanel row = transparent(new BorderLayout());
        row.setBorder(BorderFactory.createEmptyBorder(9, 0, 9, 0));
        JLabel left = new JLabel(name);
        left.setForeground(TEXT);
        left.setFont(left.getFont().deriveFont(Font.BOLD, 14F));
        value.setHorizontalAlignment(JLabel.RIGHT);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 13F));
        row.add(left, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20F));
        return label;
    }

    private JLabel smallTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13F));
        return label;
    }

    private JPanel requiredRow(String name, String detail) {
        JPanel row = new RoundedPanel(new Color(17, 12, 24), new Color(54, 41, 68));
        row.setLayout(new BorderLayout(10, 0));
        row.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel title = new JLabel(name);
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14F));
        JLabel right = new JLabel(detail);
        right.setForeground(GREEN);
        right.setFont(right.getFont().deriveFont(Font.BOLD, 12F));
        row.add(title, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return row;
    }

    private JCheckBox optionBox(String title, String detail) {
        JCheckBox box = new JCheckBox(title);
        box.setToolTipText(detail);
        box.setOpaque(false);
        box.setForeground(TEXT);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        box.setFont(box.getFont().deriveFont(Font.BOLD, 13F));
        box.setFocusPainted(false);
        box.setIconTextGap(12);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.addActionListener(event -> {
            saveSettings();
            refreshStatus();
        });
        return box;
    }

    private JButton primaryButton(String text) {
        return styledButton(text, true);
    }

    private JButton ghostButton(String text) {
        return styledButton(text, false);
    }

    private JButton styledButton(String text, boolean primary) {
        JButton button = new JButton(text) {
            private boolean hover;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 14;
                Color fill = primary ? (hover ? PRIMARY.brighter() : PRIMARY) : (hover ? PANEL_SOFT : new Color(18, 13, 27));
                Color border = primary ? PRIMARY_DARK : new Color(89, 66, 118);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(2F));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }
        };
        button.setForeground(primary ? Color.BLACK : TEXT);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14F));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return button;
    }

    private static JPanel transparent() {
        return transparent(null);
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = layout == null ? new JPanel() : new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static String encodeJsonArrayValue(String value) {
        return URLEncoder.encode("[\"" + value + "\"]", StandardCharsets.UTF_8);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescapeJson(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static Path defaultMinecraftDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Paths.get(appData, ".minecraft");
        }
        return Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft");
    }

    private static Path findLauncherDir() {
        try {
            Path code = Paths.get(NilsModLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(code) ? code.getParent() : code;
        } catch (URISyntaxException exception) {
            return Paths.get("").toAbsolutePath();
        }
    }

    private static Path findOutputsDir(Path launcherDir) {
        List<Path> seeds = new ArrayList<>();
        seeds.add(Paths.get("").toAbsolutePath());
        seeds.add(launcherDir);
        for (Path seed : seeds) {
            Path current = seed;
            while (current != null) {
                if (Files.isDirectory(current.resolve("NilsMod")) && Files.isDirectory(current.resolve("NilsModLauncher"))) {
                    return current;
                }
                Path outputs = current.resolve("outputs");
                if (Files.isDirectory(outputs.resolve("NilsMod")) && Files.isDirectory(outputs.resolve("NilsModLauncher"))) {
                    return outputs;
                }
                current = current.getParent();
            }
        }
        return Paths.get("outputs").toAbsolutePath();
    }

    private record ModFile(String url, String filename) {
    }

    private record MavenLibrary(String url, String path) {
    }

    private static final class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setPaint(new GradientPaint(0, 0, new Color(9, 7, 13), getWidth(), getHeight(), new Color(34, 20, 45)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(255, 173, 0, 22));
            for (int x = 0; x < getWidth(); x += 48) {
                g2.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += 48) {
                g2.drawLine(0, y, getWidth(), y);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        GradientPanel() {
            setOpaque(false);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color border;

        RoundedPanel(Color fill, Color border) {
            this.fill = fill;
            this.border = border;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.6F));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
