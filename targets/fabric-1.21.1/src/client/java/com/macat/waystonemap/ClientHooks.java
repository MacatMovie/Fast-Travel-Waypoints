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

            if (openXaeroWorldMapDirect(minecraft)) {
                return;
            }

            KeyMapping directXaeroOpenMap = getXaeroOpenMapKeybinding();
            if (directXaeroOpenMap != null) {
                Object key = extractKey(directXaeroOpenMap);
                if (key != null) {
                    invokeKeyMappingMethod("set", new Class[]{key.getClass(), boolean.class}, new Object[]{key, true});
                    directXaeroOpenMap.setDown(true);
                    invokeKeyMappingMethod("click", new Class[]{key.getClass()}, new Object[]{key});
                    pendingKeyMapping = directXaeroOpenMap;
                    pendingRelease = true;
                    return;
                }
            }

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
            Object key = extractKey(target.keyMapping);
            if (key == null) return;

            invokeKeyMappingMethod("set", new Class[]{key.getClass(), boolean.class}, new Object[]{key, true});
            target.keyMapping.setDown(true);
            invokeKeyMappingMethod("click", new Class[]{key.getClass()}, new Object[]{key});

            pendingKeyMapping = target.keyMapping;
            pendingRelease = true;
        } catch (Throwable t) {
        }
    }

    public static void releasePendingWorldMapKey() {
        if (!pendingRelease) return;
        pendingRelease = false;
        try {
            if (pendingKeyMapping != null) {
                Object key = extractKey(pendingKeyMapping);
                pendingKeyMapping.setDown(false);
                if (key != null) {
                    invokeKeyMappingMethod("set", new Class[]{key.getClass(), boolean.class}, new Object[]{key, false});
                }
            }
        } catch (Throwable t) {
        } finally {
            pendingKeyMapping = null;
        }
    }



    private static boolean openXaeroWorldMapDirect(Minecraft minecraft) {
        try {
            Class<?> sessionClass = Class.forName("xaero.map.WorldMapSession");
            Object session = sessionClass.getMethod("getCurrentSession").invoke(null);
            if (session == null) return false;

            Object mapProcessor = sessionClass.getMethod("getMapProcessor").invoke(session);
            if (mapProcessor == null) return false;

            Class<?> mapProcessorClass = Class.forName("xaero.map.MapProcessor");
            Class<?> guiMapClass = Class.forName("xaero.map.gui.GuiMap");
            java.lang.reflect.Constructor<?> ctor = guiMapClass.getConstructor(
                    net.minecraft.client.gui.screens.Screen.class,
                    net.minecraft.client.gui.screens.Screen.class,
                    mapProcessorClass,
                    net.minecraft.world.entity.Entity.class
            );
            Object guiMap = ctor.newInstance(null, null, mapProcessor, minecraft.player);
            if (guiMap instanceof net.minecraft.client.gui.screens.Screen screen) {
                minecraft.setScreen(screen);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static KeyMapping getXaeroOpenMapKeybinding() {
        try {
            Class<?> controlsRegisterClass = Class.forName("xaero.map.controls.ControlsRegister");
            Object value = controlsRegisterClass.getField("keyOpenMap").get(null);
            if (value instanceof KeyMapping keyMapping) {
                return keyMapping;
            }
        } catch (Throwable ignored) {
        }
        return null;
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

    private static Object extractKey(KeyMapping keyMapping) {
        try {
            for (String methodName : new String[]{"getKey", "getBoundKey"}) {
                try {
                    return keyMapping.getClass().getMethod(methodName).invoke(keyMapping);
                } catch (Throwable ignored) {
                }
            }
            for (String fieldName : new String[]{"key", "boundKey"}) {
                try {
                    java.lang.reflect.Field f = keyMapping.getClass().getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get(keyMapping);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void invokeKeyMappingMethod(String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            KeyMapping.class.getMethod(methodName, paramTypes).invoke(null, args);
        } catch (Throwable ignored) {
        }
    }

    private record Candidate(KeyMapping keyMapping, String name, String category, int score) {}
}
