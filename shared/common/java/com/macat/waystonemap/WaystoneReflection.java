package com.macat.waystonemap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.reflect.Method;
import java.util.stream.Stream;

/** Shared Waystones reflective lookups and destination display-name resolution. */
public final class WaystoneReflection {
    private final WaystoneGeometry geometry;

    public WaystoneReflection(WaystoneGeometry geometry) {
        this.geometry = geometry;
    }

    public String chooseDisplayName(ServerLevel level, BlockPos waystoneBottom, String waypointName) {
        if (waypointName != null) {
            String cleaned = waypointName.trim();
            if (!cleaned.isBlank() && !"{name}".equals(cleaned)) {
                if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                }
                if (!cleaned.isBlank()) return cleaned;
            }
        }
        return getWaystoneNameSafe(level, waystoneBottom);
    }

    private String getWaystoneNameSafe(ServerLevel level, BlockPos waystoneBottom) {
        // Default if we can't extract a custom name.
        String fallback = "Waystone";

        try {
            BlockEntity be = level.getBlockEntity(waystoneBottom);
            if (be == null) be = level.getBlockEntity(waystoneBottom.above());
            if (be == null) return fallback;

            // Common patterns across Waystones versions (try a few via reflection)
            for (String mName : new String[]{"getName", "getDisplayName", "getWaystoneName", "getCustomName"}) {
                Method m = be.getClass().getMethod(mName);
                Object r = m.invoke(be);
                if (r instanceof Component c) {
                    String s = c.getString();
                    if (s != null && !s.isBlank()) return s;
                }
                if (r instanceof String s) {
                    if (!s.isBlank()) return s;
                }
            }
        } catch (Throwable ignored) {
        }

        return fallback;
    }

    /**
     * Resolves the Waystones database entry for a physical Waystone block.
     *
     * Destination chunks can be loaded just enough for the block and block entity to
     * exist before Waystones has run its block-entity onLoad initialization. During
     * that window, blockEntity.getWaystone() returns InvalidWaystone, whose random UUID
     * and fallback Overworld dimension cannot be used for XP requirement calculation.
     *
     * A regular Waystone has block entities in both halves. Waystones may update the
     * shared database object's stored position from either half as they load, so a valid
     * Waystone can report either the lower or upper block position. A valid object read
     * directly from the requested block entity is therefore trusted once its dimension
     * matches. Database fallback matching accepts either half of the same structure.
     */
    public Object getWaystoneObject(ServerLevel level, BlockPos waystoneBottom) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(waystoneBottom);
            if (blockEntity == null) {
                blockEntity = level.getBlockEntity(waystoneBottom.above());
            }
            if (blockEntity != null) {
                Object localWaystone = blockEntity.getClass().getMethod("getWaystone").invoke(blockEntity);
                if (isUsableLocalWaystone(localWaystone, level)) {
                    return localWaystone;
                }
            }
        } catch (Throwable ignored) {
        }

        return findRegisteredWaystone(level, waystoneBottom);
    }

    private Object findRegisteredWaystone(ServerLevel level, BlockPos waystoneBottom) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }

        try {
            Class<?> apiClass = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            for (Method method : apiClass.getMethods()) {
                if (!method.getName().equals("getAllWaystones")) continue;

                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || !params[0].isInstance(server)) continue;

                Object result = method.invoke(null, server);
                if (!(result instanceof Stream<?> stream)) {
                    continue;
                }

                try (stream) {
                    return stream
                            .filter(waystone -> isRegisteredWaystoneAt(waystone, level, waystoneBottom))
                            .findFirst()
                            .orElse(null);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private boolean isUsableLocalWaystone(Object waystone, ServerLevel level) {
        if (waystone == null) {
            return false;
        }

        try {
            Method isValid = waystone.getClass().getMethod("isValid");
            Object validResult = isValid.invoke(waystone);
            if (!(validResult instanceof Boolean valid) || !valid) {
                return false;
            }

            Object storedDimension = waystone.getClass().getMethod("getDimension").invoke(waystone);
            return level.dimension().equals(storedDimension);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isRegisteredWaystoneAt(Object waystone, ServerLevel level, BlockPos waystoneBottom) {
        if (!isUsableLocalWaystone(waystone, level)) {
            return false;
        }

        try {
            Object storedPos = waystone.getClass().getMethod("getPos").invoke(waystone);
            return waystoneBottom.equals(storedPos) || waystoneBottom.above().equals(storedPos);
        } catch (Throwable ignored) {
            return false;
        }
    }


    public boolean isWaystoneActivatedForPlayer(Player player, Level level, BlockPos pos) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null && geometry.isWaystoneBlock(level, pos.below())) {
                blockEntity = level.getBlockEntity(pos.below());
            }
            if (blockEntity == null) return false;

            Method getWaystone = blockEntity.getClass().getMethod("getWaystone");
            Object waystone = getWaystone.invoke(blockEntity);
            if (waystone == null) return false;

            Class<?> managerClass = Class.forName("net.blay09.mods.waystones.core.PlayerWaystoneManager");
            for (Method method : managerClass.getMethods()) {
                if (!method.getName().equals("isWaystoneActivated")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0].isAssignableFrom(player.getClass()) && params[1].isInstance(waystone)) {
                    Object result = method.invoke(null, player, waystone);
                    if (result instanceof Boolean b) return b;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }


}
