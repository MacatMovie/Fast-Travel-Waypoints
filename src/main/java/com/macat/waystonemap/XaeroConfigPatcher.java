package com.macat.waystonemap;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "fast_travel_waypoints", value = Dist.CLIENT)
public class XaeroConfigPatcher {

    // The exact lines we want Xaero to use by default
    private static final String FORMAT_LINE =
            "defaultWaypointTPCommandFormat:/wstp {x} {y} {z} {name}";
    private static final String ROTATION_FORMAT_LINE =
            "defaultWaypointTPCommandRotationFormat:/wstp {x} {y} {z} {name}";

    // Some Xaero versions use separate keys for OP/creative teleport formats.
    private static final String OP_FORMAT_LINE =
            "opWaypointTPCommandFormat:/wstp {x} {y} {z} {name}";
    private static final String OP_ROTATION_FORMAT_LINE =
            "opWaypointTPCommandRotationFormat:/wstp {x} {y} {z} {name}";

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // This runs when the client joins any world/server.
        Path configDir = FMLPaths.CONFIGDIR.get();

        // Xaero minimap global config
        patchXaeroConfig(configDir.resolve("xaerominimap.txt"));

        // Xaero world map global config (if present – safe to call even if missing)
        patchXaeroConfig(configDir.resolve("xaeroworldmap.txt"));
    }

    private static void patchXaeroConfig(Path path) {
        if (!Files.exists(path)) {
            // Xaero hasn't created this config yet – nothing to do.
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>(lines.size());
            boolean changed = false;

            for (String line : lines) {
                if (line.startsWith("defaultWaypointTPCommandFormat:")) {
                    if (!line.equals(FORMAT_LINE)) {
                        out.add(FORMAT_LINE);
                        changed = true;
                    } else {
                        out.add(line);
                    }
                } else if (line.startsWith("defaultWaypointTPCommandRotationFormat:")) {
                    if (!line.equals(ROTATION_FORMAT_LINE)) {
                        out.add(ROTATION_FORMAT_LINE);
                        changed = true;
                    } else {
                        out.add(line);
                    }
                } else if (line.startsWith("opWaypointTPCommandFormat:")) {
                    if (!line.equals(OP_FORMAT_LINE)) {
                        out.add(OP_FORMAT_LINE);
                        changed = true;
                    } else {
                        out.add(line);
                    }
                } else if (line.startsWith("opWaypointTPCommandRotationFormat:")) {
                    if (!line.equals(OP_ROTATION_FORMAT_LINE)) {
                        out.add(OP_ROTATION_FORMAT_LINE);
                        changed = true;
                    } else {
                        out.add(line);
                    }
                } else {
                    out.add(line);
                }
            }

            if (changed) {
                Files.write(path, out, StandardCharsets.UTF_8);
                System.out.println("[fast_travel_waypoints] Patched Xaero config: " + path.getFileName());
            }

        } catch (IOException e) {
            System.err.println("[fast_travel_waypoints] Failed to patch Xaero config " + path + ": " + e.getMessage());
        }
    }
}
