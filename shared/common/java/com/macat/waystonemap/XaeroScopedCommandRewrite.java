package com.macat.waystonemap;

/**
 * A narrowly-scoped safety net for Xaero's client-side teleport commands.
 *
 * Xaero's World Map/Minimap releases use ClientPacketListener's command methods
 * directly. Injecting into those Minecraft methods is more robust across loader
 * mappings than attempting to inject into the bytecode of a separately mapped
 * third-party Xaero JAR. This helper deliberately rewrites ONLY when that send
 * is called by one of Xaero's actual teleport implementations.
 *
 * The candidate test makes the stack inspection virtually free in normal play:
 * it is never invoked for chat, most commands, or any frame/client tick.
 */
public final class XaeroScopedCommandRewrite {
    private static final StackWalker WALKER = StackWalker.getInstance();

    private XaeroScopedCommandRewrite() {}

    public static String rewriteIfXaero(String command) {
        if (!looksLikeTeleport(command)) return command;
        if (!fromXaeroTeleporter()) return command;
        return XaeroCommandRewriter.rewrite(command);
    }

    private static boolean fromXaeroTeleporter() {
        return WALKER.walk(frames -> frames.limit(24).anyMatch(frame -> {
            String type = frame.getClassName();
            return type.equals("xaero.map.teleport.MapTeleporter")
                    || type.equals("xaero.map.radar.tracker.PlayerTeleporter")
                    || type.equals("xaero.hud.minimap.waypoint.WaypointTeleport");
        }));
    }

    /** Avoid a stack walk unless the string could be a supported teleport format. */
    static boolean looksLikeTeleport(String command) {
        if (command == null) return false;
        int offset = 0;
        int length = command.length();
        while (offset < length && Character.isWhitespace(command.charAt(offset))) offset++;
        if (offset < length && command.charAt(offset) == '/') offset++;
        while (offset < length && Character.isWhitespace(command.charAt(offset))) offset++;
        return wordAt(command, offset, "tp")
                || wordAt(command, offset, "teleport")
                || wordAt(command, offset, "execute")
                || wordAt(command, offset, "wstp");
    }

    private static boolean wordAt(String str, int offset, String word) {
        int end = offset + word.length();
        return end < str.length() && str.regionMatches(true, offset, word, 0, word.length())
                && Character.isWhitespace(str.charAt(end));
    }
}
