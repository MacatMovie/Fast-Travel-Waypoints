package xaero.map.teleport;

import com.macat.waystonemap.XaeroScopedCommandRewrite;

/** Mimics the direct command send observed in Xaero's 1.21.1 World Map JARs. */
public final class MapTeleporter {
    public static String issue(String command) {
        return XaeroScopedCommandRewrite.rewriteIfXaero(command);
    }
}
