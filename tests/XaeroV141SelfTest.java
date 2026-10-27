package com.macat.waystonemap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Standalone, Minecraft-free regression checks: javac and java (17+) suffice. */
public final class XaeroV141SelfTest {
    public static void main(String[] args) throws Exception {
        check("/wstp 12 70 -4", XaeroCommandRewriter.rewrite("/tp @s 12 70 -4"));
        check("wstpxd minecraft:the_nether 12 70 -4", XaeroCommandRewriter.rewrite(
                "execute as @s in minecraft:the_nether run tp 12 70 -4"));
        check("wstpxd minecraft:the_end 0 ~ 30", XaeroCommandRewriter.rewrite(
                "execute in minecraft:the_end run teleport @s 0 ~ 30"));
        check("/tp @s Steve", XaeroCommandRewriter.rewrite("/tp @s Steve"));
        check("/say hi", XaeroCommandRewriter.rewrite("/say hi"));

        // Only Xaero's actual teleport call chain is allowed to trigger rewriting.
        check("wstp 34 79.5 6", xaero.map.teleport.MapTeleporter.issue("tp @s 34 79.5 6"));
        check("wstpxd minecraft:the_nether 12 70 -4", xaero.map.teleport.MapTeleporter.issue(
                "execute as @s in minecraft:the_nether run tp 12 70 -4"));
        check("wstp 2 ~ 9", xaero.hud.minimap.waypoint.WaypointTeleport.issue("tp @s 2 ~ 9"));
        check("/wstp 34 79.5 6", xaero.map.teleport.MapTeleporter.issue("/tp @s 34 79.5 6"));
        check("/tp @s 34 79.5 6", XaeroScopedCommandRewrite.rewriteIfXaero("/tp @s 34 79.5 6"));
        check("tp @s 34 79.5 6", XaeroScopedCommandRewrite.rewriteIfXaero("tp @s 34 79.5 6"));
        check("say hi", xaero.map.teleport.MapTeleporter.issue("say hi"));
        check("tp @s Steve", xaero.map.teleport.MapTeleporter.issue("tp @s Steve"));
        check("wstp 34 79.5 6", xaero.map.teleport.MapTeleporter.issue("wstp 34 79.5 6"));
        ensure(!XaeroScopedCommandRewrite.looksLikeTeleport("/tp"), "incomplete tp command not processed");
        ensure(!XaeroScopedCommandRewrite.looksLikeTeleport("/tpanything 1 2 3"), "not a teleport verb");

        String legacy = "ignoreHeightmaps:false\r\n"
                + "playerTeleportCommandFormat:/wstp {x} {y} {z} {name}\r\n"
                + "normalTeleportCommandFormat:/wstp {x} {y} {z} {name}\r\n"
                + "dimensionTeleportCommandFormat:/wstpxd {d} {x} {y} {z} {name}\r\n"
                + "useDefaultMapTeleport:true\r\nuseDefaultPlayerTeleport:true\r\n"
                + "customUserCommand:/wstp {x} {y} {z} {name}\r\n";
        String repaired = XaeroLegacyConfigRepair.repairText(legacy);
        ensure(repaired.contains("playerTeleportCommandFormat:/tp @s {name}\r\n"), "player template");
        ensure(repaired.contains("normalTeleportCommandFormat:/tp @s {x} {y} {z}\r\n"), "map template");
        ensure(repaired.contains("dimensionTeleportCommandFormat:/execute as @s in {d} run tp {x} {y} {z}\r\n"), "dimension template");
        ensure(repaired.contains("customUserCommand:/wstp {x} {y} {z} {name}\r\n"), "untouched user config");
        ensure(repaired.contains("useDefaultMapTeleport:true\r\n"), "untouched boolean");
        ensure(repaired.contains("ignoreHeightmaps:false\r\n"), "untouched unrelated option");
        check(repaired, XaeroLegacyConfigRepair.repairText(repaired));
        check("serverTeleportCommandRotationFormat:/tp @s {x} {y} {z} {yaw} ~", XaeroLegacyConfigRepair.repairText(
                "serverTeleportCommandRotationFormat:/wstp {x} {y} {z} {name}"));
        check("default_map_teleport_command_format = /tp @s {x} {y} {z}", XaeroLegacyConfigRepair.repairText(
                "default_map_teleport_command_format = /wstp {x} {y} {z} {name}"));
        check("useDefaultMapTeleport:false", XaeroLegacyConfigRepair.repairText("useDefaultMapTeleport:false"));

        Path tmp = Files.createTempDirectory("ftw-141-test-");
        Path gameDir = tmp.resolve("game");
        Path configDir = gameDir.resolve("config");
        Path worldDir = gameDir.resolve("xaero/world-map/MyWorld");
        Files.createDirectories(worldDir);
        Files.createDirectories(configDir);
        Path original = worldDir.resolve("server_config.txt");
        Files.writeString(original, legacy, StandardCharsets.UTF_8);
        XaeroLegacyConfigRepair.runOnceAsync(gameDir, configDir);
        Path marker = configDir.resolve("fast_travel_waypoints/xaero_v1.4.0_teleport_repair_v1.completed");
        for (int i = 0; i < 100 && !Files.exists(marker); i++) {
            TimeUnit.MILLISECONDS.sleep(30);
        }
        ensure(Files.exists(marker), "migration marker created");
        check(repaired, Files.readString(original, StandardCharsets.UTF_8));
        check(legacy, Files.readString(worldDir.resolve("server_config.txt.ftw-v1.4.0.backup")));
        System.out.println("v1.4.1: Xaero-scoped 1.21.1 rewriting and per-world backup/migration checks passed");
    }

    private static void check(String expected, String actual) {
        ensure(expected.equals(actual), "expected " + expected + " but got " + actual);
    }

    private static void ensure(boolean test, String detail) {
        if (!test) throw new AssertionError(detail);
    }
}
