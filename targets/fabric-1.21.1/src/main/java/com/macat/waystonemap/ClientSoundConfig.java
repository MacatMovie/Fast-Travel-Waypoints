package com.macat.waystonemap;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Locale;

/** Client-only persistent sound preference, not sent to or read by the server. */
public final class ClientSoundConfig {
    public enum TeleportSound { CUSTOM, ENDERMAN }
    public static final Value<TeleportSound> TELEPORT_SOUND = new Value<>(TeleportSound.CUSTOM);
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("fast_travel_waypoints-client.toml");
    private ClientSoundConfig() {}

    public static void register() { load(); if (!Files.exists(PATH)) save(); }
    public static boolean useEnderman() { return TELEPORT_SOUND.get() == TeleportSound.ENDERMAN; }

    public static void load() {
        try {
            if (!Files.isRegularFile(PATH)) return;
            for (String line : Files.readAllLines(PATH, StandardCharsets.UTF_8)) {
                String text = line.trim();
                if (text.startsWith("teleport_sound") && text.contains("=")) {
                    String raw = text.substring(text.indexOf('=') + 1).trim().replace("\"", "").replace("'", "");
                    try { TELEPORT_SOUND.set(TeleportSound.valueOf(raw.toUpperCase(Locale.ROOT))); }
                    catch (IllegalArgumentException ignored) { /* Unknown config values fall back safely. */ }
                }
            }
        } catch (IOException ignored) { }
    }
    public static void saveAll() { save(); }
    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, "# Fast Travel Waypoints client settings\n# CUSTOM = Hybrid A; ENDERMAN = vanilla Enderman sound.\nteleport_sound = \"" + TELEPORT_SOUND.get().name().toLowerCase(Locale.ROOT) + "\"\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }
    public static final class Value<T> {
        private T value;
        public Value(T value) { this.value=value; }
        public T get() { return value; }
        public void set(T value) { this.value=value; }
    }
}
