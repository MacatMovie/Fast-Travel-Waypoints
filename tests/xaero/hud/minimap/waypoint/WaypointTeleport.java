package xaero.hud.minimap.waypoint;

import com.macat.waystonemap.XaeroScopedCommandRewrite;

/** Mimics the direct command send observed in Xaero's 1.21.1 Minimap JARs. */
public final class WaypointTeleport {
    public static String issue(String command) {
        return XaeroScopedCommandRewrite.rewriteIfXaero(command);
    }
}
