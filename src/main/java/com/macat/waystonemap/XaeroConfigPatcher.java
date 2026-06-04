package com.macat.waystonemap;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = "fast_travel_waypoints", value = Dist.CLIENT)
public class XaeroConfigPatcher {

    private static final String WSTP_NO_ROT = "/wstp {x} {y} {z} {name}";
    private static final String WSTP_WITH_ROT = "/wstp {x} {y} {z} {name}";
    private static final String WSTP_DIM = "/wstpxd {d} {x} {y} {z} {name}";

    private static int retryTicks = 0;

    public static void patchEarly() {
        patchAll();
        retryTicks = Math.max(retryTicks, 20 * 60);
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        patchAll();
        retryTicks = Math.max(retryTicks, 20 * 60);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (retryTicks-- > 0 && retryTicks % 20 == 0) {
            patchAll();
        }
    }

    private static void patchAll() {
        patchXaeroTree(FMLPaths.CONFIGDIR.get().resolve("xaero"));
        patchXaeroTree(FMLPaths.GAMEDIR.get().resolve("xaero"));
    }

    private static void patchXaeroTree(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root, 8)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".cfg") || name.endsWith(".txt");
                    })
                    .forEach(XaeroConfigPatcher::patchFile);
        } catch (IOException ignored) {
        }
    }

    private static void patchFile(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>(lines.size() + 4);
            boolean changed = false;

            for (String line : lines) {
                String updated = line;

                // Xaero Minimap profile defaults, used by "Waypoint teleportation is configured by the minimap".
                updated = replaceEquals(updated, "default_waypoint_teleport_format", WSTP_NO_ROT);
                updated = replaceEquals(updated, "default_waypoint_teleport_rotation_format", WSTP_WITH_ROT);

                // Xaero World Map profile defaults.
                updated = replaceEquals(updated, "default_map_teleport_command_format", WSTP_NO_ROT);
                updated = replaceEquals(updated, "default_map_teleport_command_dimension_format", WSTP_DIM);
                updated = replaceEquals(updated, "default_player_teleport_command_format", WSTP_NO_ROT);

                // Older/alternate Xaero keys seen across versions.
                updated = replaceEquals(updated, "defaultWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceEquals(updated, "defaultWaypointTPCommandRotationFormat", WSTP_WITH_ROT);
                updated = replaceEquals(updated, "opWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceEquals(updated, "opWaypointTPCommandRotationFormat", WSTP_WITH_ROT);

                // Per-world minimap/world-map configs.
                updated = replaceColon(updated, "serverTeleportCommandFormat", WSTP_NO_ROT);
                updated = replaceColon(updated, "serverTeleportCommandRotationFormat", WSTP_WITH_ROT);
                updated = replaceColon(updated, "normalTeleportCommandFormat", WSTP_NO_ROT);
                updated = replaceColon(updated, "dimensionTeleportCommandFormat", WSTP_DIM);
                updated = replaceColon(updated, "playerTeleportCommandFormat", WSTP_NO_ROT);

                updated = replaceColon(updated, "defaultWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceColon(updated, "defaultWaypointTPCommandRotationFormat", WSTP_WITH_ROT);
                updated = replaceColon(updated, "opWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceColon(updated, "opWaypointTPCommandRotationFormat", WSTP_WITH_ROT);

                if (!updated.equals(line)) {
                    changed = true;
                }
                out.add(updated);
            }

            if (appendIfContains(path, out, "config.txt", "usingDefaultTeleportCommand:", "usingDefaultTeleportCommand:false")) changed = true;
            if (appendIfContains(path, out, "config.txt", "serverTeleportCommandFormat:", "serverTeleportCommandFormat:" + WSTP_NO_ROT)) changed = true;
            if (appendIfContains(path, out, "config.txt", "serverTeleportCommandRotationFormat:", "serverTeleportCommandRotationFormat:" + WSTP_WITH_ROT)) changed = true;

            if (changed) {
                Files.write(path, out, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean appendIfContains(Path path, List<String> lines, String fileName, String prefix, String fullLine) {
        if (!path.getFileName().toString().equalsIgnoreCase(fileName)) return false;
        for (String line : lines) {
            if (line.startsWith(prefix)) return false;
        }
        lines.add(fullLine);
        return true;
    }

    private static String replaceEquals(String line, String key, String value) {
        String prefix = key + " = ";
        return line.startsWith(prefix) ? prefix + value : line;
    }

    private static String replaceColon(String line, String key, String value) {
        String prefix = key + ":";
        return line.startsWith(prefix) ? prefix + value : line;
    }
}
