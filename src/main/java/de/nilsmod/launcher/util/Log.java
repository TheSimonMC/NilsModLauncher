package de.nilsmod.launcher.util;

@FunctionalInterface
public interface Log {
    void info(String message);

    static Log stdout() {
        return message -> System.out.println("[NilsModLauncher] " + message);
    }
}
