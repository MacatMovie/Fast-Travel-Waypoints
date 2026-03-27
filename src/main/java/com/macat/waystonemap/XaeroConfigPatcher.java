package com.macat.waystonemap;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@EventBusSubscriber(modid = "fast_travel_waypoints", value = Dist.CLIENT)
public class XaeroConfigPatcher {

    private static final String WSTP_NO_ROT = "/wstp {x} {y} {z} {name}";
    private static final String WSTP_WITH_ROT = "/wstp {x} {y} {z} {name}";

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        patchXaeroTree(FMLPaths.CONFIGDIR.get().resolve("xaero"));
        patchXaeroTree(FMLPaths.GAMEDIR.get().resolve("xaero"));
    }

    private static void patchXaeroTree(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root, 6)) {
            stream.filter(Files::isRegularFile).forEach(XaeroConfigPatcher::patchFile);
        } catch (IOException ignored) {
        }
    }

    private static void patchFile(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>(lines.size());
            boolean changed = false;

            for (String line : lines) {
                String updated = line;
                updated = replaceEquals(updated, "default_waypoint_teleport_format", WSTP_NO_ROT);
                updated = replaceEquals(updated, "default_waypoint_teleport_rotation_format", WSTP_WITH_ROT);
                updated = replaceEquals(updated, "default_map_teleport_command_format", WSTP_NO_ROT);
                updated = replaceEquals(updated, "defaultWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceEquals(updated, "defaultWaypointTPCommandRotationFormat", WSTP_WITH_ROT);
                updated = replaceEquals(updated, "opWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceEquals(updated, "opWaypointTPCommandRotationFormat", WSTP_WITH_ROT);

                updated = replaceColon(updated, "normalTeleportCommandFormat", WSTP_NO_ROT);
                updated = replaceColon(updated, "defaultWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceColon(updated, "defaultWaypointTPCommandRotationFormat", WSTP_WITH_ROT);
                updated = replaceColon(updated, "opWaypointTPCommandFormat", WSTP_NO_ROT);
                updated = replaceColon(updated, "opWaypointTPCommandRotationFormat", WSTP_WITH_ROT);

                if (!updated.equals(line)) {
                    changed = true;
                }
                out.add(updated);
            }

            if (changed) {
                Files.write(path, out, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
        }
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
