package com.macat.waystonemap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Migration for the persistent Xaero command templates written by Forge <= v1.4.0.
 *
 * Does not configure Xaero for FTW. Only cleans up exact templates left by old
 * versions, keeping user-created formats and all other settings untouched.
 * The scan runs once on a daemon thread, never on the Minecraft render/client tick.
 */
public final class XaeroLegacyConfigRepair {
    private static final String MARKER = "xaero_v1.4.0_teleport_repair_v1.completed";
    private static final String OLD_POSITION = "/wstp {x} {y} {z} {name}";
    private static final String OLD_DIMENSION = "/wstpxd {d} {x} {y} {z} {name}";
    private static final String DEFAULT_POSITION = "/tp @s {x} {y} {z}";
    private static final String DEFAULT_ROTATION = "/tp @s {x} {y} {z} {yaw} ~";
    private static final String DEFAULT_DIMENSION = "/execute as @s in {d} run tp {x} {y} {z}";
    private static final String DEFAULT_PLAYER = "/tp @s {name}";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static final Map<String, String> DEFAULTS = buildDefaults();

    private XaeroLegacyConfigRepair() {}

    public static void runOnceAsync(Path gameDir, Path configDir) {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            Path marker = configDir.resolve("fast_travel_waypoints").resolve(MARKER);
            if (Files.isRegularFile(marker)) return;
            try {
                int changed = scan(gameDir.resolve("xaero"));
                changed += scan(configDir.resolve("xaero"));
                Files.createDirectories(marker.getParent());
                Files.writeString(marker,
                        "One-time v1.4.0 Xaero teleport template repair.\n"
                                + "Changed files: " + changed + "\n"
                                + "Original files have .ftw-v1.4.0.backup copies next to them.\n"
                                + "Delete this marker to repeat the repair if you restore old worlds.\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException ex) {
                System.err.println("[Fast Travel Waypoints] Could not finish one-time Xaero legacy repair: " + ex);
                // Leave marker absent so a subsequent launch can retry.
            }
        }, "FTW-Xaero-Legacy-Migration");
        thread.setDaemon(true);
        thread.start();
    }

    private static int scan(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return 0;
        int changed = 0;
        try (Stream<Path> files = Files.walk(root, 8)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String filename = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (!(filename.endsWith(".txt") || filename.endsWith(".cfg"))) continue;
                if (repairFile(file)) changed++;
            }
        }
        return changed;
    }

    private static boolean repairFile(Path file) throws IOException {
        // Fast rejection without following links outside the Xaero directory.
        String original = Files.readString(file, StandardCharsets.UTF_8);
        if (!original.contains("/wstp ") && !original.contains("/wstpxd ")) return false;
        String repaired = repairText(original);
        if (repaired.equals(original)) return false;

        // Never overwrite a pre-existing backup; no migration should destroy user data.
        Path backup = file.resolveSibling(file.getFileName() + ".ftw-v1.4.0.backup");
        if (!Files.exists(backup)) Files.copy(file, backup);

        // Write temp beside source for atomic same-volume replacement where supported.
        Path temp = Files.createTempFile(file.getParent(), ".ftw-xaero-", ".tmp");
        try {
            Files.writeString(temp, repaired, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    /** Package-visible for isolated regression tests. Preserves original line endings. */
    static String repairText(String original) {
        StringBuilder out = new StringBuilder(original.length());
        int position = 0;
        while (position < original.length()) {
            int end = original.indexOf('\n', position);
            if (end < 0) end = original.length();
            String line = original.substring(position, end);
            boolean hasCr = line.endsWith("\r");
            String plain = hasCr ? line.substring(0, line.length() - 1) : line;
            int delimiter = plain.indexOf(':');
            if (delimiter < 0 || !DEFAULTS.containsKey(plain.substring(0, delimiter))) {
                delimiter = plain.indexOf(" = ");
                if (delimiter >= 0 && DEFAULTS.containsKey(plain.substring(0, delimiter))) {
                    out.append(repairEntry(plain.substring(0, delimiter), " = ", plain.substring(delimiter + 3)));
                } else {
                    out.append(plain);
                }
            } else {
                out.append(repairEntry(plain.substring(0, delimiter), ":", plain.substring(delimiter + 1)));
            }
            if (hasCr) out.append('\r');
            if (end < original.length()) out.append('\n');
            position = end + 1;
        }
        return out.toString();
    }

    private static String repairEntry(String key, String delimiter, String value) {
        // Do not touch any other command template, even if it uses FTW's command name.
        if (!OLD_POSITION.equals(value) && !OLD_DIMENSION.equals(value)) return key + delimiter + value;
        String replacement = DEFAULTS.get(key);
        if (replacement == null) return key + delimiter + value;
        if (value.equals(OLD_DIMENSION) && !replacement.equals(DEFAULT_DIMENSION)) return key + delimiter + value;
        return key + delimiter + replacement;
    }

    private static Map<String, String> buildDefaults() {
        Map<String, String> map = new LinkedHashMap<>();
        // Xaero World Map 1.47.0 (MapWorld defaults and WorldMapProfiledConfigOptions).
        map.put("playerTeleportCommandFormat", DEFAULT_PLAYER);
        map.put("normalTeleportCommandFormat", DEFAULT_POSITION);
        map.put("dimensionTeleportCommandFormat", DEFAULT_DIMENSION);
        map.put("default_map_teleport_command_format", DEFAULT_POSITION);
        map.put("default_map_teleport_command_dimension_format", DEFAULT_DIMENSION);
        map.put("default_player_teleport_command_format", DEFAULT_PLAYER);
        // Xaero Minimap 26.6.0 (RootConfig, MinimapProfiledConfigOptions).
        map.put("serverTeleportCommandFormat", DEFAULT_POSITION);
        map.put("serverTeleportCommandRotationFormat", DEFAULT_ROTATION);
        map.put("default_waypoint_teleport_format", DEFAULT_POSITION);
        map.put("default_waypoint_teleport_rotation_format", DEFAULT_ROTATION);
        // Older aliases written by our <=v1.4.0 patcher.
        map.put("defaultWaypointTPCommandFormat", DEFAULT_POSITION);
        map.put("defaultWaypointTPCommandRotationFormat", DEFAULT_ROTATION);
        map.put("opWaypointTPCommandFormat", DEFAULT_POSITION);
        map.put("opWaypointTPCommandRotationFormat", DEFAULT_ROTATION);
        return Map.copyOf(map);
    }
}
