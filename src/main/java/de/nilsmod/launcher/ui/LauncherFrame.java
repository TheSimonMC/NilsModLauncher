package de.nilsmod.launcher.ui;

import de.nilsmod.launcher.download.Downloader;
import de.nilsmod.launcher.install.InstallOptions;
import de.nilsmod.launcher.install.InstallerService;
import de.nilsmod.launcher.manifest.ManifestClient;
import de.nilsmod.launcher.manifest.ManifestModel;
import de.nilsmod.launcher.minecraft.MinecraftDirectory;
import de.nilsmod.launcher.util.Log;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;

public final class LauncherFrame extends JFrame {
    private final Downloader downloader = new Downloader();
    private final JTextField manifestUrl = new JTextField(ManifestClient.DEFAULT_URL);
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JCheckBox sodiumBox = new JCheckBox("Sodium installieren", true);
    private final JCheckBox voiceChatBox = new JCheckBox("Simple Voice Chat installieren", true);
    private final JTextArea logArea = new JTextArea();
    private ManifestModel manifest;

    public LauncherFrame() {
        super("NilsMod Launcher");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(880, 560);
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        setContentPane(content());
        loadManifestAsync();
    }

    private JPanel content() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(15, 11, 23));

        JLabel title = new JLabel("NilsMod Launcher");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30F));
        title.setForeground(Color.WHITE);
        root.add(title, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        controls.add(label("Manifest URL"), c);
        c.gridx = 1;
        c.weightx = 1;
        controls.add(manifestUrl, c);
        c.gridx = 2;
        c.weightx = 0;
        JButton reload = button("Manifest laden");
        reload.addActionListener(event -> loadManifestAsync());
        controls.add(reload, c);

        c.gridy++;
        c.gridx = 0;
        controls.add(label("Version"), c);
        c.gridx = 1;
        controls.add(versionBox, c);
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        checks.setOpaque(false);
        sodiumBox.setForeground(Color.WHITE);
        sodiumBox.setOpaque(false);
        voiceChatBox.setForeground(Color.WHITE);
        voiceChatBox.setOpaque(false);
        checks.add(sodiumBox);
        checks.add(voiceChatBox);
        c.gridx = 2;
        controls.add(checks, c);

        c.gridy++;
        c.gridx = 1;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        JButton install = button("Install / Update");
        install.addActionListener(event -> installSelectedAsync());
        JButton installAll = button("Install All");
        installAll.addActionListener(event -> installAllAsync());
        JButton folder = button(".minecraft/nilsmod oeffnen");
        folder.addActionListener(event -> openNilsmodFolder());
        buttons.add(install);
        buttons.add(installAll);
        buttons.add(folder);
        controls.add(buttons, c);

        root.add(controls, BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setRows(14);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(new Color(230, 230, 230));
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        root.add(new JScrollPane(logArea), BorderLayout.SOUTH);
        return root;
    }

    private void loadManifestAsync() {
        append("Manifest wird geladen...");
        new Thread(() -> {
            try {
                manifest = new ManifestClient(downloader).load(manifestUrl.getText().trim());
                SwingUtilities.invokeLater(() -> {
                    versionBox.removeAllItems();
                    for (String version : manifest.versions()) {
                        versionBox.addItem(version);
                    }
                });
                append("Manifest geladen. Versionen: " + manifest.versions());
            } catch (Exception exception) {
                append("Manifest konnte nicht geladen werden: " + exception.getMessage());
            }
        }, "manifest-load").start();
    }

    private void installSelectedAsync() {
        Object selected = versionBox.getSelectedItem();
        if (selected == null) {
            append("Keine Version ausgewaehlt.");
            return;
        }
        installAsync(String.valueOf(selected), false);
    }

    private void installAllAsync() {
        installAsync(null, true);
    }

    private void installAsync(String version, boolean all) {
        new Thread(() -> {
            try {
                if (manifest == null) {
                    append("Manifest ist noch nicht geladen.");
                    return;
                }
                InstallerService installer = new InstallerService(
                        manifest,
                        MinecraftDirectory.detect(),
                        downloader,
                        this::append,
                        new InstallOptions(true, sodiumBox.isSelected(), voiceChatBox.isSelected())
                );
                if (all) {
                    installer.installAll();
                } else {
                    installer.install(version);
                }
            } catch (Exception exception) {
                append("Fehler: " + exception.getMessage());
            }
        }, "install").start();
    }

    private void openNilsmodFolder() {
        try {
            var folder = MinecraftDirectory.detect().nilsmodRoot();
            Files.createDirectories(folder);
            Desktop.getDesktop().open(folder.toFile());
        } catch (Exception exception) {
            append("Ordner konnte nicht geoeffnet werden: " + exception.getMessage());
        }
    }

    private void append(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        return label;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        return button;
    }
}
