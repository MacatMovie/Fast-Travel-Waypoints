package com.macat.waystonemap;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public final class ModConfigs {

    private ModConfigs() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue LEVEL_COST;
    public static final ForgeConfigSpec.BooleanValue USE_WAYSTONES_XP_COST_SCALING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CROSS_DIMENSIONAL_TRAVEL;
    public static final ForgeConfigSpec.IntValue ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_OPEN_SKY_PLAYER;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_OPEN_SKY_DESTINATION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OPEN_SKY_DIMENSION_WHITELIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MIN_Y_CHECK;
    public static final ForgeConfigSpec.IntValue MIN_Y;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_NEARBY_WAYSTONE_FOR_USE;
    public static final ForgeConfigSpec.IntValue NEARBY_WAYSTONE_USE_RADIUS;
    public static final ForgeConfigSpec.BooleanValue DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE;
    public static final ForgeConfigSpec.BooleanValue DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("fast_travel");

        LEVEL_COST = b
                .comment("How many XP levels are consumed when a player fast-travels to a waystone waypoint. 0 = free. Ignored when use_waystones_xp_cost_scaling=true.")
                .defineInRange("level_cost", 0, 0, 1000);

        USE_WAYSTONES_XP_COST_SCALING = b
                .comment("If true, map-based fast travel uses Waystones' configured waystone-to-waystone XP level calculation, including distance scaling and dimensional costs, instead of level_cost and additional_level_cost_dimensional_travel. Default: false")
                .define("use_waystones_xp_cost_scaling", false);

        ENABLE_CROSS_DIMENSIONAL_TRAVEL = b
                .comment("If true, players can fast-travel to waystone waypoints in other dimensions using Xaero World Map's dimension toggle. If false, only same-dimension fast travel is allowed.")
                .define("enable_cross_dimensional_travel", true);

        ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL = b
                .comment("Additional XP levels consumed only when fast-traveling between dimensions. This is added on top of level_cost. 0 = no extra cross-dimensional cost. Ignored when use_waystones_xp_cost_scaling=true.")
                .defineInRange("additional_level_cost_dimensional_travel", 0, 0, 1000);

        REQUIRE_NEARBY_WAYSTONE_FOR_USE = b
                .comment("If true, players must be near any Waystone before they can start fast travel from the world map.")
                .define("require_nearby_waystone_for_use", true);

        NEARBY_WAYSTONE_USE_RADIUS = b
                .comment("How close the player must be to any Waystone to start fast travel when require_nearby_waystone_for_use=true. Measured in blocks. Default: 7")
                .defineInRange("nearby_waystone_use_radius", 7, 1, 64);


        DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE = b
                .comment("If true, remove the 3-second teleport countdown whenever the player is currently within nearby_waystone_use_radius blocks of any Waystone.")
                .define("disable_countdown_when_near_a_waystone", true);

        DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE = b
                .comment("If true, remove the 3-second teleport countdown for map teleports when the player is not currently within nearby_waystone_use_radius blocks of any Waystone.")
                .define("disable_countdown_for_teleporting_from_anywhere", false);

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
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
