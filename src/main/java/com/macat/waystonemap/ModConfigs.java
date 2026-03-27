package com.macat.waystonemap;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;

import java.util.List;

public final class ModConfigs {

    private ModConfigs() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue LEVEL_COST;
    public static final ModConfigSpec.BooleanValue REQUIRE_OPEN_SKY_PLAYER;
    public static final ModConfigSpec.BooleanValue REQUIRE_OPEN_SKY_DESTINATION;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> OPEN_SKY_DIMENSION_WHITELIST;
    public static final ModConfigSpec.BooleanValue ENABLE_MIN_Y_CHECK;
    public static final ModConfigSpec.IntValue MIN_Y;
    public static final ModConfigSpec.BooleanValue REQUIRE_NEARBY_WAYSTONE_FOR_USE;
    public static final ModConfigSpec.IntValue NEARBY_WAYSTONE_USE_RADIUS;
    public static final ModConfigSpec.BooleanValue REPLACE_WAYSTONE_SCREEN_WITH_WORLD_MAP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("fast_travel");

        LEVEL_COST = b
                .comment("How many XP levels are consumed when a player fast-travels to a waystone waypoint. 0 = free.")
                .defineInRange("level_cost", 0, 0, 1000);

        REQUIRE_NEARBY_WAYSTONE_FOR_USE = b
                .comment("If true, players must be near any Waystone before they can start fast travel from the world map.")
                .define("require_nearby_waystone_for_use", false);

        NEARBY_WAYSTONE_USE_RADIUS = b
                .comment("How close the player must be to any Waystone to start fast travel when require_nearby_waystone_for_use=true. Measured in blocks. Default: 5")
                .defineInRange("nearby_waystone_use_radius", 5, 1, 64);

        REPLACE_WAYSTONE_SCREEN_WITH_WORLD_MAP = b
                .comment("If true, right-clicking an already-activated Waystone opens Xaero's World Map instead of the default Waystone destination list. Sneak-right-click still opens Waystone settings.")
                .define("replace_waystone_screen_with_world_map", true);

        REQUIRE_OPEN_SKY_PLAYER = b
                .comment("If true, players can only fast-travel when they have open sky above them (ignores leaves, fluids, and transparent blocks like glass). Checked at the player position.")
                .define("require_open_sky_player", false);

        REQUIRE_OPEN_SKY_DESTINATION = b
                .comment("If true, players can only fast-travel to waystones that have open sky above the destination area. Uses the same open_sky_dimension_whitelist as require_open_sky_player.")
                .define("require_open_sky_destination", false);

        OPEN_SKY_DIMENSION_WHITELIST = b
                .comment("When require_open_sky_player=true or require_open_sky_destination=true, only enforce it in these dimensions (resource locations). Default: overworld + the_end. Example: [\"minecraft:overworld\", \"minecraft:the_end\"]")
                .defineListAllowEmpty("open_sky_dimension_whitelist", List.of("minecraft:overworld", "minecraft:the_end"), o -> o instanceof String);

        ENABLE_MIN_Y_CHECK = b
                .comment("Enable restricting fast travel based on the player's current Y level.")
                .define("enable_min_y_check", false);

        MIN_Y = b
                .comment("Minimum Y level required to start fast travel (only used when enable_min_y_check=true).")
                .defineInRange("min_y", 60, -64, 320);

        b.pop();

        SPEC = b.build();
    }

    public static boolean requireOpenSkyPlayer() {
        return REQUIRE_OPEN_SKY_PLAYER.get();
    }

    public static void register() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
