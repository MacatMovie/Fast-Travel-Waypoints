package com.macat.waystonemap;

import com.macat.waystonemap.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;

public final class WaystoneSelectionScreenHook {
    private static final String BASE_SCREEN_CLASS = "net.blay09.mods.waystones.client.gui.screen.WaystoneSelectionScreenBase";
    private static final String SELECT_VIA_WORLD_MAP = "Select via World Map";

    // Waystones 21.1.37+ uses a 220 px-wide scrolling list and already reserves
    // a 25 px footer. The button belongs in that native footer, matching NeoForge.
    private static final int CURRENT_LAYOUT_BUTTON_WIDTH = 220;
    private static final int CURRENT_LAYOUT_BUTTON_HEIGHT = 20;
    private static final int CURRENT_LAYOUT_BOTTOM_MARGIN = 2;

    private WaystoneSelectionScreenHook() {
    }

    /**
     * Adds the button when missing and refreshes its position if the screen is resized.
     */
    public static void tryAddButton(Screen screen) {
        if (!isWaystoneSelectionScreen(screen)) {
            return;
        }

        try {
            ButtonPlacement placement = null;
            try {
                placement = findOldPaginationPlacement(screen);
            } catch (Throwable ignored) {
            }
            if (placement == null) {
                try {
                    placement = findCurrentScrollingListPlacement(screen);
                } catch (Throwable ignored) {
                }
            }
            if (placement == null) {
                placement = findFallbackPlacement(screen);
            }

            List<AbstractWidget> buttons = Screens.getButtons(screen);
            for (AbstractWidget widget : buttons) {
                if (widget instanceof Button existing
                        && existing.getMessage() != null
                        && SELECT_VIA_WORLD_MAP.contentEquals(existing.getMessage().getString())) {
                    existing.setX(placement.x());
                    existing.setY(placement.y());
                    existing.setWidth(placement.width());
                    return;
                }
            }

            Button button = Button.builder(Component.literal(SELECT_VIA_WORLD_MAP), ignored -> {
                try {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft != null) {
                        minecraft.setScreen(null);
                        ClientHooks.tryOpenXaeroWorldMap();
                    }
                } catch (Throwable ignoredError) {
                }
            }).pos(placement.x(), placement.y())
                    .size(placement.width(), CURRENT_LAYOUT_BUTTON_HEIGHT)
                    .build();

            buttons.add(button);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Compatibility with older Waystones releases that used Previous/Next pagination buttons.
     */
    private static ButtonPlacement findOldPaginationPlacement(Screen screen) throws ReflectiveOperationException {
        Object prevValue = getField(screen, "btnPrevPage");
        Object nextValue = getField(screen, "btnNextPage");
        if (!(prevValue instanceof Button prev) || !(nextValue instanceof Button next)) {
            return null;
        }

        int x = Math.min(prev.getX(), next.getX());
        int y = Math.max(prev.getY(), next.getY()) + 26;
        int width = prev.getWidth() + next.getWidth() + 10;
        return new ButtonPlacement(x, y, width);
    }

    /**
     * Waystones 21.1.37 replaced pagination with a dynamically sized scrolling list.
     * Read the final remapped container layout and anchor the button inside Waystones'
     * native footer. This follows the same positioning used by the NeoForge build and
     * keeps tracking the screen height correctly at every GUI scale.
     */
    private static ButtonPlacement findCurrentScrollingListPlacement(Screen screen) {
        if (!(screen instanceof AbstractContainerScreenAccessor layout)) {
            return null;
        }

        int leftPos = layout.fastTravelWaypoints$getLeftPos();
        int topPos = layout.fastTravelWaypoints$getTopPos();
        int imageWidth = layout.fastTravelWaypoints$getImageWidth();
        int imageHeight = layout.fastTravelWaypoints$getImageHeight();

        int width = Math.min(CURRENT_LAYOUT_BUTTON_WIDTH, Math.max(100, imageWidth - 16));
        int x = leftPos + (imageWidth - width) / 2;
        int y = topPos + imageHeight - CURRENT_LAYOUT_BUTTON_HEIGHT - CURRENT_LAYOUT_BOTTOM_MARGIN;
        return new ButtonPlacement(x, y, width);
    }

    /**
     * Last-resort placement matching Waystones' minimum centered layout.
     */
    private static ButtonPlacement findFallbackPlacement(Screen screen) {
        int width = CURRENT_LAYOUT_BUTTON_WIDTH;
        int x = (screen.width - width) / 2;
        int y = Math.min(screen.height - CURRENT_LAYOUT_BUTTON_HEIGHT - 2,
                screen.height / 2 + 78);
        return new ButtonPlacement(x, y, width);
    }

    private static boolean isWaystoneSelectionScreen(Screen screen) {
        Class<?> type = screen.getClass();
        while (type != null) {
            if (BASE_SCREEN_CLASS.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static Object getField(Object instance, String fieldName) throws ReflectiveOperationException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private record ButtonPlacement(int x, int y, int width) {
    }
}
