package de.nilsmod.launcher.install;

public record InstallOptions(boolean installFabricApi, boolean installSodium, boolean installSimpleVoiceChat) {
    public static InstallOptions defaults() {
        return new InstallOptions(true, true, true);
    }
}
