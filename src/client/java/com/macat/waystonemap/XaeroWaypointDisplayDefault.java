package com.macat.waystonemap;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Applies Fast Travel Waypoints' preferred Xaero World Map waypoint-display
 * default once per client instance. After the marker is written, the player is
 * free to change the Xaero option without this mod overriding it again.
 */
public final class XaeroWaypointDisplayDefault {

    private static final String MARKER_NAME = "xaero_current_map_waypoints_default_v1.applied";
    private static final int RETRY_INTERVAL_TICKS = 20;

    private static boolean finished;
    private static int retryDelay;

    private XaeroWaypointDisplayDefault() {}

    public static void tick() {
        if (finished) return;
        if (retryDelay-- > 0) return;
        retryDelay = RETRY_INTERVAL_TICKS;

        Path marker = FabricLoader.getInstance().getConfigDir()
                .resolve("fast_travel_waypoints")
                .resolve(MARKER_NAME);

        if (Files.isRegularFile(marker)) {
            finished = true;
            return;
        }

        try {
            if (!applyThroughXaeroConfig()) return;

            Files.createDirectories(marker.getParent());
            Files.writeString(
                    marker,
                    "Fast Travel Waypoints applied Xaero World Map's current-map waypoint default once.\n"
                            + "Delete this marker to apply the default again.\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            finished = true;
        } catch (Throwable ignored) {
            // Xaero can still be initializing. Retry later without breaking the client.
        }
    }

    private static boolean applyThroughXaeroConfig() throws ReflectiveOperationException {
        Class<?> worldMapClass = Class.forName("xaero.map.WorldMap");
        Object worldMap = worldMapClass.getField("INSTANCE").get(null);
        if (worldMap == null) return false;

        Object channel = worldMapClass.getMethod("getConfigs").invoke(worldMap);
        if (channel == null) return false;

        Object manager = channel.getClass().getMethod("getPrimaryClientConfigManager").invoke(channel);
        if (manager == null) return false;

        Object config = manager.getClass().getMethod("getConfig").invoke(manager);
        if (config == null) return false;

        Class<?> optionsClass = Class.forName(
                "xaero.map.config.primary.option.WorldMapPrimaryClientConfigOptions"
        );
        Object option = optionsClass.getField("ONLY_CURRENT_MAP_WAYPOINTS").get(null);
        if (option == null) return false;

        Object currentValue = invokeCompatible(config, "get", option);
        if (!(currentValue instanceof Boolean)) return false;

        if (!((Boolean) currentValue)) {
            invokeCompatible(config, "set", option, Boolean.TRUE);
        }

        Object configIO = channel.getClass().getMethod("getPrimaryClientConfigManagerIO").invoke(channel);
        if (configIO == null) return false;
        configIO.getClass().getMethod("save").invoke(configIO);
        return true;
    }

    private static Object invokeCompatible(Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;

            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                Object argument = arguments[i];
                if (argument != null && !parameterTypes[i].isAssignableFrom(argument.getClass())) {
                    compatible = false;
                    break;
                }
            }

            if (compatible) {
                return method.invoke(target, arguments);
            }
        }

        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
}
