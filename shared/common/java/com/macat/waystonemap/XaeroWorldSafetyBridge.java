package com.macat.waystonemap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Narrow compatibility bridge for Xaero's cross-dimensional waypoint safety check.
 *
 * Xaero normally requires users to manually connect waypoint sub-worlds before it
 * allows Survival-mode teleportation between them. For FTW, the server still validates
 * that the destination is a real Waystone, so we only bypass that client-side warning
 * when the target and current worlds are clearly the same Xaero sub-world/save in two
 * different dimensions.
 */
public final class XaeroWorldSafetyBridge {

    private XaeroWorldSafetyBridge() {
    }

    public static boolean isSameSubWorldAcrossDimensions(Object waypointTeleport, Object targetWorld) {
        if (waypointTeleport == null || targetWorld == null) {
            return false;
        }

        try {
            Object minimapSession = readField(waypointTeleport, "minimapSession");
            Object worldManager = invokeNoArg(minimapSession, "getWorldManager");
            Object autoWorld = invokeNoArg(worldManager, "getAutoWorld");
            if (autoWorld == null || autoWorld == targetWorld) {
                return false;
            }

            Object targetDimension = invokeNoArg(targetWorld, "getDimId");
            Object autoDimension = invokeNoArg(autoWorld, "getDimId");
            if (targetDimension == null || autoDimension == null || Objects.equals(targetDimension, autoDimension)) {
                return false;
            }

            Object targetContainer = invokeNoArg(targetWorld, "getContainer");
            Object autoContainer = invokeNoArg(autoWorld, "getContainer");
            if (targetContainer == null || autoContainer == null) {
                return false;
            }

            Object targetRoot = invokeNoArg(targetContainer, "getRoot");
            Object autoRoot = invokeNoArg(autoContainer, "getRoot");
            if (targetRoot == null || targetRoot != autoRoot) {
                return false;
            }

            // The node identifies Xaero's specific sub-world/world-save entry. Requiring
            // it to match prevents unrelated worlds on the same server address from being
            // treated as connected just because they share a root container.
            Object targetNode = invokeNoArg(targetWorld, "getNode");
            Object autoNode = invokeNoArg(autoWorld, "getNode");
            if (!(targetNode instanceof String targetNodeText)
                    || !(autoNode instanceof String autoNodeText)
                    || targetNodeText.isBlank()
                    || !targetNodeText.equals(autoNodeText)) {
                return false;
            }

            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
