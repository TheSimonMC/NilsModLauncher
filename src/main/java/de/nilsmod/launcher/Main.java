package de.nilsmod.launcher;

import de.nilsmod.launcher.download.Downloader;
import de.nilsmod.launcher.install.InstallOptions;
import de.nilsmod.launcher.install.InstallerService;
import de.nilsmod.launcher.manifest.ManifestClient;
import de.nilsmod.launcher.manifest.ManifestModel;
import de.nilsmod.launcher.minecraft.MinecraftDirectory;
import de.nilsmod.launcher.ui.LauncherFrame;
import de.nilsmod.launcher.util.Log;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            SwingUtilities.invokeLater(() -> new LauncherFrame().setVisible(true));
            return;
        }
        try {
            runCli(args);
        } catch (Exception exception) {
            System.err.println("Error: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void runCli(String[] args) throws Exception {
        String manifestUrl = ManifestClient.DEFAULT_URL;
        String install = null;
        boolean installAll = false;
        boolean sodium = true;
        boolean voiceChat = true;

        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                case "--manifest" -> manifestUrl = requireValue(args, ++i, "--manifest");
                case "--install" -> install = requireValue(args, ++i, "--install");
                case "--install-all" -> installAll = true;
                case "--no-sodium" -> sodium = false;
                case "--no-voicechat", "--no-simple-voice-chat" -> voiceChat = false;
                default -> unknown.add(args[i]);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown args: " + unknown);
        }
        if (install == null && !installAll) {
            throw new IllegalArgumentException("Use --install <version>, --install-all or --help");
        }

        Downloader downloader = new Downloader();
        Log log = Log.stdout();
        log.info("Manifest wird geladen...");
        ManifestModel manifest = new ManifestClient(downloader).load(manifestUrl);
        InstallerService installer = new InstallerService(
                manifest,
                MinecraftDirectory.detect(),
                downloader,
                log,
                new InstallOptions(true, sodium, voiceChat)
        );
        if (installAll) {
            installer.installAll();
        } else {
            installer.install(install);
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    private static void printHelp() {
        System.out.println("""
                NilsMod Launcher

                Usage:
                  java -jar NilsModLauncher.jar --install 1.21.4
                  java -jar NilsModLauncher.jar --install 1.20.1
                  java -jar NilsModLauncher.jar --install 1.8.9
                  java -jar NilsModLauncher.jar --install-all
                  java -jar NilsModLauncher.jar --manifest https://example.com/nilsmod-manifest.json --install 1.21.4

                Options:
                  --no-sodium              Do not install/update Sodium for Fabric profiles
                  --no-voicechat           Do not install/update Simple Voice Chat for Fabric profiles
                  --help                   Show this help
                """);
    }
}
