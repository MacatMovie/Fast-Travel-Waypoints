package com.macat.waystonemap;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModConfigs {

    private ModConfigs() {}

    public static final IntValue CONFIG_VERSION = new IntValue(3);
    public static final IntValue LEVEL_COST = new IntValue(0);
    public static final BooleanValue ENABLE_CROSS_DIMENSIONAL_TRAVEL = new BooleanValue(true);
    public static final IntValue ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL = new IntValue(0);
    public static final BooleanValue REQUIRE_OPEN_SKY_PLAYER = new BooleanValue(false);
    public static final BooleanValue REQUIRE_OPEN_SKY_DESTINATION = new BooleanValue(false);
    public static final ConfigListValue<String> OPEN_SKY_DIMENSION_WHITELIST = new ConfigListValue<>(List.of("minecraft:overworld", "minecraft:the_end"));
    public static final BooleanValue ENABLE_MIN_Y_CHECK = new BooleanValue(false);
    public static final IntValue MIN_Y = new IntValue(60);
    public static final BooleanValue REQUIRE_NEARBY_WAYSTONE_FOR_USE = new BooleanValue(true);
    public static final IntValue NEARBY_WAYSTONE_USE_RADIUS = new IntValue(7);
    public static final BooleanValue DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE = new BooleanValue(true);
    public static final BooleanValue DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE = new BooleanValue(false);

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("fast_travel_waypoints-common.toml");

    public static void register() {
        load();
        save();
    }

    public static boolean requireOpenSkyPlayer() {
        return REQUIRE_OPEN_SKY_PLAYER.get();
    }

    private static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            Map<String, String> values = new HashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
            CONFIG_VERSION.set(parseInt(values.get("config_version"), CONFIG_VERSION.get(), 1, 9999));
            LEVEL_COST.set(parseInt(values.get("level_cost"), LEVEL_COST.get(), 0, 1000));
            ENABLE_CROSS_DIMENSIONAL_TRAVEL.set(parseBool(values.get("enable_cross_dimensional_travel"), ENABLE_CROSS_DIMENSIONAL_TRAVEL.get()));
            ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL.set(parseInt(values.get("additional_level_cost_dimensional_travel"), ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL.get(), 0, 1000));
            REQUIRE_NEARBY_WAYSTONE_FOR_USE.set(parseBool(values.get("require_nearby_waystone_for_use"), REQUIRE_NEARBY_WAYSTONE_FOR_USE.get()));
            NEARBY_WAYSTONE_USE_RADIUS.set(parseInt(values.get("nearby_waystone_use_radius"), NEARBY_WAYSTONE_USE_RADIUS.get(), 1, 64));
            DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE.set(parseBool(values.get("disable_countdown_when_near_a_waystone"), DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE.get()));
            DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE.set(parseBool(values.get("disable_countdown_for_teleporting_from_anywhere"), DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE.get()));
            REQUIRE_OPEN_SKY_PLAYER.set(parseBool(values.get("require_open_sky_player"), REQUIRE_OPEN_SKY_PLAYER.get()));
            REQUIRE_OPEN_SKY_DESTINATION.set(parseBool(values.get("require_open_sky_destination"), REQUIRE_OPEN_SKY_DESTINATION.get()));
            OPEN_SKY_DIMENSION_WHITELIST.set(parseStringList(values.get("open_sky_dimension_whitelist"), OPEN_SKY_DIMENSION_WHITELIST.get()));
            ENABLE_MIN_Y_CHECK.set(parseBool(values.get("enable_min_y_check"), ENABLE_MIN_Y_CHECK.get()));
            MIN_Y.set(parseInt(values.get("min_y"), MIN_Y.get(), -64, 320));
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        List<String> out = new ArrayList<>();
        out.add("# Fast Travel Waypoints common config");
        out.add("config_version = " + CONFIG_VERSION.get());
        out.add("");
        out.add("# How many XP levels are consumed when a player fast-travels to a waystone waypoint. 0 = free.");
        out.add("level_cost = " + LEVEL_COST.get());
        out.add("");
        out.add("# If false, same-dimension fast travel still works, but travel between dimensions is blocked.");
        out.add("enable_cross_dimensional_travel = " + ENABLE_CROSS_DIMENSIONAL_TRAVEL.get());
        out.add("");
        out.add("# Extra XP levels added on top of level_cost only when fast-traveling between dimensions. 0 = no extra cost.");
        out.add("additional_level_cost_dimensional_travel = " + ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL.get());
        out.add("");
        out.add("# If true, players must be near any Waystone before they can start fast travel from the world map.");
        out.add("require_nearby_waystone_for_use = " + REQUIRE_NEARBY_WAYSTONE_FOR_USE.get());
        out.add("");
        out.add("# How close the player must be to any Waystone to start fast travel when require_nearby_waystone_for_use=true. Measured in blocks.");
        out.add("nearby_waystone_use_radius = " + NEARBY_WAYSTONE_USE_RADIUS.get());
        out.add("");
        out.add("# If true, teleporting while within nearby_waystone_use_radius of any Waystone skips the countdown.");
        out.add("disable_countdown_when_near_a_waystone = " + DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE.get());
        out.add("");
        out.add("# If true, teleporting from anywhere also skips the countdown.");
        out.add("disable_countdown_for_teleporting_from_anywhere = " + DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE.get());
        out.add("");
        out.add("# If true, players can only fast-travel when they have open sky above them (ignores leaves, fluids, and transparent blocks like glass). Checked at the player position.");
        out.add("require_open_sky_player = " + REQUIRE_OPEN_SKY_PLAYER.get());
        out.add("");
        out.add("# If true, players can only fast-travel to waystones that have open sky above the destination area.");
        out.add("require_open_sky_destination = " + REQUIRE_OPEN_SKY_DESTINATION.get());
        out.add("");
        out.add("# Only enforce open sky in these dimensions.");
        out.add("open_sky_dimension_whitelist = " + toTomlList(OPEN_SKY_DIMENSION_WHITELIST.get()));
        out.add("");
        out.add("# Enable restricting fast travel based on the player's current Y level.");
        out.add("enable_min_y_check = " + ENABLE_MIN_Y_CHECK.get());
        out.add("");
        out.add("# Minimum Y level required to start fast travel.");
        out.add("min_y = " + MIN_Y.get());
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, out, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static boolean parseBool(String raw, boolean def) {
        if (raw == null) return def;
        return Boolean.parseBoolean(raw.trim());
    }

    private static int parseInt(String raw, int def, int min, int max) {
        if (raw == null) return def;
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return def;
        }
    }

    private static List<String> parseStringList(String raw, List<String> def) {
        if (raw == null) return def;
        String s = raw.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) return def;
        s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String p = part.trim();
            if ((p.startsWith("\"") && p.endsWith("\"")) || (p.startsWith("'") && p.endsWith("'"))) {
                p = p.substring(1, p.length() - 1);
            }
            if (!p.isBlank()) out.add(p);
        }
        return out.isEmpty() ? def : List.copyOf(out);
    }

    private static String toTomlList(List<String> list) {
        List<String> quoted = new ArrayList<>();
        for (String s : list) quoted.add("\"" + s + "\"");
        return "[" + String.join(", ", quoted) + "]";
    }

    public static final class BooleanValue {
        private boolean value;
        public BooleanValue(boolean value) { this.value = value; }
        public boolean get() { return value; }
        public void set(boolean value) { this.value = value; }
    }
    public static final class IntValue {
        private int value;
        public IntValue(int value) { this.value = value; }
        public int get() { return value; }
        public void set(int value) { this.value = value; }
    }
    public static final class ConfigListValue<T> {
        private List<T> value;
        public ConfigListValue(List<T> value) { this.value = List.copyOf(value); }
        public List<T> get() { return value; }
        public void set(List<T> value) { this.value = List.copyOf(value); }
    }
}
