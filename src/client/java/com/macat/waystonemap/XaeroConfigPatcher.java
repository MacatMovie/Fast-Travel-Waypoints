package com.macat.waystonemap;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class XaeroConfigPatcher {

    private static final String WSTP_NO_ROT = "/wstp {x} {y} {z} {name}";
    private static final String WSTP_WITH_ROT = "/wstp {x} {y} {z} {name}";

    public static void onClientJoin() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path gameDir = FabricLoader.getInstance().getGameDir();

        patchSimpleKeyValueFile(configDir.resolve("xaero/minimap/profiles/default.cfg"), List.of(
                new KeyReplace("default_waypoint_teleport_format", WSTP_NO_ROT),
                new KeyReplace("default_waypoint_teleport_rotation_format", WSTP_WITH_ROT)
        ));

        patchSimpleKeyValueFile(configDir.resolve("xaero/world-map/profiles/default.cfg"), List.of(
                new KeyReplace("default_map_teleport_command_format", WSTP_NO_ROT)
        ));

        // Patch any existing per-world world-map config that may override the defaults.
        Path worldMapRoot = gameDir.resolve("xaero/world-map");
        if (Files.exists(worldMapRoot)) {
            try (Stream<Path> stream = Files.walk(worldMapRoot, 4)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase("server_config.txt"))
                        .forEach(path -> patchColonSeparatedFile(path, List.of(
                                new KeyReplace("normalTeleportCommandFormat", WSTP_NO_ROT)
                        )));
            } catch (IOException ignored) {
            }
        }
    }

    private static void patchSimpleKeyValueFile(Path path, List<KeyReplace> replacements) {
        if (!Files.exists(path)) return;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>(lines.size());
            boolean changed = false;
            for (String line : lines) {
                String updated = line;
                for (KeyReplace replacement : replacements) {
                    String prefix = replacement.key + " = ";
                    if (line.startsWith(prefix)) {
                        updated = prefix + replacement.value;
                        changed |= !updated.equals(line);
                        break;
                    }
                }
                out.add(updated);
            }
            if (changed) Files.write(path, out, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void patchColonSeparatedFile(Path path, List<KeyReplace> replacements) {
        if (!Files.exists(path)) return;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>(lines.size());
            boolean changed = false;
            for (String line : lines) {
                String updated = line;
                for (KeyReplace replacement : replacements) {
                    String prefix = replacement.key + ":";
                    if (line.startsWith(prefix)) {
                        updated = prefix + replacement.value;
                        changed |= !updated.equals(line);
                        break;
                    }
                }
                out.add(updated);
            }
            if (changed) Files.write(path, out, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private record KeyReplace(String key, String value) {}
}
