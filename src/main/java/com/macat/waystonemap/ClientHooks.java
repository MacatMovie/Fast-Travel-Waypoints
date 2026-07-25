package com.macat.waystonemap;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClientHooks {

    private static KeyMapping pendingKeyMapping;
    private static boolean pendingRelease;

    private ClientHooks() {}

    public static void tryOpenXaeroWorldMap() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.options == null) return;

            List<Candidate> candidates = new ArrayList<>();
            for (KeyMapping keyMapping : minecraft.options.keyMappings) {
                if (keyMapping == null) continue;

                String name = safeString(keyMapping.getName());
                String category = safeString(keyMapping.getCategory());
                String combined = (name + " " + category).toLowerCase();
                int score = scoreCandidate(combined);
                if (score > 0) {
                    candidates.add(new Candidate(keyMapping, name, category, score));
                }
            }

            candidates.sort(Comparator.comparingInt((Candidate c) -> c.score).reversed());

            if (candidates.isEmpty()) {
                return;
            }

            Candidate target = candidates.get(0);
            KeyMapping.set(target.keyMapping.getKey(), true);
            target.keyMapping.setDown(true);
            KeyMapping.click(target.keyMapping.getKey());

            pendingKeyMapping = target.keyMapping;
            pendingRelease = true;
        } catch (Throwable t) {
        }
    }

    public static void applyXaeroWaypointDisplayDefault() {
        XaeroWaypointDisplayDefault.tick();
    }

    public static void releasePendingWorldMapKey() {
        if (!pendingRelease) return;
        pendingRelease = false;
        try {
            if (pendingKeyMapping != null) {
                pendingKeyMapping.setDown(false);
                KeyMapping.set(pendingKeyMapping.getKey(), false);
            }
        } catch (Throwable t) {
        } finally {
            pendingKeyMapping = null;
        }
    }

    private static int scoreCandidate(String combined) {
        int score = 0;

        if (combined.contains("gui.xaero_open_map")) score += 100;
        if (combined.contains("open_map")) score += 40;

        if (combined.contains("xaero")) score += 2;
        if (combined.contains("world map") || combined.contains("worldmap")) score += 5;
        if (combined.contains("xaero_world_map") || combined.contains("xaeros_world_map")) score += 5;
        if (combined.contains("map")) score += 1;
        if (combined.contains("open")) score += 3;

        if (combined.contains("server_settings") || combined.contains("open_settings") || combined.contains("settings")) score -= 50;
        if (combined.contains("zoom") || combined.contains("quick_confirm") || combined.contains("toggle_dimension")) score -= 20;

        return score;
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }

    private record Candidate(KeyMapping keyMapping, String name, String category, int score) {}
}
