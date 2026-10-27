package com.macat.waystonemap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;

@EventBusSubscriber(modid = "fast_travel_waypoints", value = Dist.CLIENT)
public class WaystoneSelectionScreenHook {

    private static final String BASE_SCREEN_CLASS = "net.blay09.mods.waystones.client.gui.screen.WaystoneSelectionScreenBase";
    private static final String SELECT_VIA_WORLD_MAP = "Select via World Map";

    // Waystones 21.1.37+ uses a 220 px-wide scrolling list and reserves a 25 px footer.
    private static final int CURRENT_LAYOUT_BUTTON_WIDTH = 220;
    private static final int CURRENT_LAYOUT_BUTTON_HEIGHT = 20;
    private static final int CURRENT_LAYOUT_BOTTOM_MARGIN = 2;

    private WaystoneSelectionScreenHook() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isWaystoneSelectionScreen(screen)) {
            return;
        }

        try {
            ButtonPlacement placement = findOldPaginationPlacement(screen);
            if (placement == null) {
                placement = findCurrentScrollingListPlacement(screen);
            }
            if (placement == null) {
                return;
            }

            Button openMapButton = Button.builder(Component.literal(SELECT_VIA_WORLD_MAP), button -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    minecraft.setScreen(null);
                }
                ClientHooks.tryOpenXaeroWorldMap();
            }).pos(placement.x(), placement.y()).size(placement.width(), CURRENT_LAYOUT_BUTTON_HEIGHT).build();

            event.addListener(openMapButton);
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
     * Waystones 21.1.37 replaced pagination with a scrolling list. Anchor the button inside
     * the footer that the new screen intentionally leaves below that list.
     */
    private static ButtonPlacement findCurrentScrollingListPlacement(Screen screen) throws ReflectiveOperationException {
        Integer leftPos = getIntField(screen, "leftPos");
        Integer topPos = getIntField(screen, "topPos");
        Integer imageWidth = getIntField(screen, "imageWidth");
        Integer imageHeight = getIntField(screen, "imageHeight");
        if (leftPos == null || topPos == null || imageWidth == null || imageHeight == null) {
            return null;
        }

        int width = Math.min(CURRENT_LAYOUT_BUTTON_WIDTH, Math.max(100, imageWidth - 16));
        int x = leftPos + (imageWidth - width) / 2;
        int y = topPos + imageHeight - CURRENT_LAYOUT_BUTTON_HEIGHT - CURRENT_LAYOUT_BOTTOM_MARGIN;
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

    private static Integer getIntField(Object instance, String fieldName) throws ReflectiveOperationException {
        Object value = getField(instance, fieldName);
        return value instanceof Number number ? number.intValue() : null;
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
