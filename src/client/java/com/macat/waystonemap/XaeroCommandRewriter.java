package com.macat.waystonemap;

import java.util.Locale;

/**
 * Last-resort client-side safety net for Xaero/XMXW teleport commands.
 *
 * Xaero can cache its teleport command formats before our config patcher edits the files,
 * especially on the first use of a dimension/world. Instead of trying to win that timing
 * race, targeted mixins call this right before Xaero sends the actual command packet.
 */
public final class XaeroCommandRewriter {
    private XaeroCommandRewriter() {}

    public static String rewrite(String original) {
        if (original == null || original.isBlank()) return original;

        String command = original.trim();
        boolean hadSlash = command.startsWith("/");
        if (hadSlash) command = command.substring(1).trim();

        String rewritten = rewriteNoSlash(command);
        if (rewritten == null || rewritten.isBlank()) return original;

        // ClientPacketListener#sendUnsignedCommand expects no leading slash, while sendChat expects one.
        return hadSlash ? "/" + rewritten : rewritten;
    }

    private static String rewriteNoSlash(String command) {
        String lower = command.toLowerCase(Locale.ROOT);

        // Waystones' built-in Xaero integration can prefix a waypoint command like:
        // execute in minecraft:the_nether run tp @s x y z
        if (lower.startsWith("execute in ")) {
            ExecuteParts parts = parseExecuteIn(command, "execute in ");
            if (parts != null) {
                String inner = rewriteTeleportCommand(parts.innerCommand, parts.dimension);
                if (inner != null) return inner;
            }
        }

        // Xaero's own dimension format commonly uses:
        // execute as @s in minecraft:the_nether run tp x y z
        if (lower.startsWith("execute as @s in ")) {
            ExecuteParts parts = parseExecuteIn(command, "execute as @s in ");
            if (parts != null) {
                String inner = rewriteTeleportCommand(parts.innerCommand, parts.dimension);
                if (inner != null) return inner;
            }
        }

        String direct = rewriteTeleportCommand(command, null);
        return direct != null ? direct : command;
    }

    private static ExecuteParts parseExecuteIn(String command, String prefix) {
        if (command.length() <= prefix.length()) return null;
        String rest = command.substring(prefix.length()).trim();
        int runIndex = indexOfRunSeparator(rest);
        if (runIndex < 0) return null;
        String dimension = rest.substring(0, runIndex).trim();
        String inner = rest.substring(runIndex + " run ".length()).trim();
        if (dimension.isEmpty() || inner.isEmpty()) return null;
        return new ExecuteParts(dimension, inner);
    }

    private static int indexOfRunSeparator(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.indexOf(" run ");
    }

    private static String rewriteTeleportCommand(String command, String forcedDimension) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 4) return null;

        String op = parts[0].toLowerCase(Locale.ROOT);
        if (!op.equals("tp") && !op.equals("teleport") && !op.equals("wstp")) return null;

        int index = 1;
        // Xaero often sends tp @s x y z. Our wstp command does not want @s.
        if (index < parts.length && (parts[index].equals("@s") || parts[index].equalsIgnoreCase("@p"))) {
            index++;
        }

        if (parts.length - index < 3) return null;

        String x = parts[index];
        String y = parts[index + 1];
        String z = parts[index + 2];
        String name = joinRemaining(parts, index + 3);

        if (forcedDimension != null && !forcedDimension.isBlank()) {
            return "wstpxd " + forcedDimension + " " + x + " " + y + " " + z + (name.isBlank() ? "" : " " + name);
        }
        return "wstp " + x + " " + y + " " + z + (name.isBlank() ? "" : " " + name);
    }

    private static String joinRemaining(String[] parts, int start) {
        if (start >= parts.length) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private record ExecuteParts(String dimension, String innerCommand) {}
}
