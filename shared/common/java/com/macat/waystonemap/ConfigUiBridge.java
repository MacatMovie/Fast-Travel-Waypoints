package com.macat.waystonemap;

import java.util.ArrayList;
import java.util.List;

/** UI adapter. Keeps the old config property paths unchanged on every loader. */
public final class ConfigUiBridge {
    private ConfigUiBridge() {}
    public static boolean bool(String id) {
        return switch(id) {
            case "xpScaling" -> ModConfigs.USE_WAYSTONES_XP_COST_SCALING.get();
            case "crossDimension" -> ModConfigs.ENABLE_CROSS_DIMENSIONAL_TRAVEL.get();
            case "nearby" -> ModConfigs.REQUIRE_NEARBY_WAYSTONE_FOR_USE.get();
            case "skipNearby" -> ModConfigs.DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE.get();
            case "skipAnywhere" -> ModConfigs.DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE.get();
            case "openSkyPlayer" -> ModConfigs.REQUIRE_OPEN_SKY_PLAYER.get();
            case "openSkyDestination" -> ModConfigs.REQUIRE_OPEN_SKY_DESTINATION.get();
            case "minYEnabled" -> ModConfigs.ENABLE_MIN_Y_CHECK.get();
            default -> throw new IllegalArgumentException(id);
        };
    }
    public static void setBool(String id, boolean value) {
        switch(id) {
            case "xpScaling" -> ModConfigs.USE_WAYSTONES_XP_COST_SCALING.set(value);
            case "crossDimension" -> ModConfigs.ENABLE_CROSS_DIMENSIONAL_TRAVEL.set(value);
            case "nearby" -> ModConfigs.REQUIRE_NEARBY_WAYSTONE_FOR_USE.set(value);
            case "skipNearby" -> ModConfigs.DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE.set(value);
            case "skipAnywhere" -> ModConfigs.DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE.set(value);
            case "openSkyPlayer" -> ModConfigs.REQUIRE_OPEN_SKY_PLAYER.set(value);
            case "openSkyDestination" -> ModConfigs.REQUIRE_OPEN_SKY_DESTINATION.set(value);
            case "minYEnabled" -> ModConfigs.ENABLE_MIN_Y_CHECK.set(value);
            default -> throw new IllegalArgumentException(id);
        }
    }
    public static int integer(String id) {
        return switch(id) {
            case "levelCost" -> ModConfigs.LEVEL_COST.get();
            case "dimensionCost" -> ModConfigs.ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL.get();
            case "nearbyRadius" -> ModConfigs.NEARBY_WAYSTONE_USE_RADIUS.get();
            case "minY" -> ModConfigs.MIN_Y.get();
            default -> throw new IllegalArgumentException(id);
        };
    }
    public static int min(String id) { return switch(id) {case "minY" -> ModConfigs.uiMinY(); case "nearbyRadius" -> 1; default -> 0;}; }
    public static int max(String id) {return switch(id){case "minY" -> ModConfigs.uiMaxY();case "nearbyRadius" -> 64;default -> 1000;};}
    public static void setInteger(String id, int v) {
        if(v < min(id) || v > max(id)) return;
        switch(id) {
            case "levelCost" -> ModConfigs.LEVEL_COST.set(v);
            case "dimensionCost" -> ModConfigs.ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL.set(v);
            case "nearbyRadius" -> ModConfigs.NEARBY_WAYSTONE_USE_RADIUS.set(v);
            case "minY" -> ModConfigs.MIN_Y.set(v);
            default -> throw new IllegalArgumentException(id);
        }
    }
    public static String whitelist() {return String.join(", ", ModConfigs.OPEN_SKY_DIMENSION_WHITELIST.get());}
    public static void setWhitelist(String raw) {
        List<String> result = new ArrayList<>();
        for (String part : raw.split(",", -1)) {
            String p = part.trim();
            if(p.isEmpty()) continue;
            // Keep this simple field strict: no invalid resource-location strings.
            if(!p.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return;
            result.add(p);
        }
        ModConfigs.OPEN_SKY_DIMENSION_WHITELIST.set(List.copyOf(result));
    }
    public static boolean useEnderman() {return ClientSoundConfig.useEnderman();}
    public static void setEnderman(boolean enabled) {
        ClientSoundConfig.TELEPORT_SOUND.set(enabled ? ClientSoundConfig.TeleportSound.ENDERMAN : ClientSoundConfig.TeleportSound.CUSTOM);
    }
    public static void saveAll() {ModConfigs.saveAll(); ClientSoundConfig.saveAll();}
}
