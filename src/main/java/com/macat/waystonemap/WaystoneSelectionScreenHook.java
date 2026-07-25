package com.macat.waystonemap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = "fast_travel_waypoints", value = Dist.CLIENT)
public class WaystoneSelectionScreenHook {

    private static final String BASE_SCREEN_CLASS = "net.blay09.mods.waystones.client.gui.screen.WaystoneSelectionScreenBase";
    private static final String SELECT_VIA_WORLD_MAP = "Select via World Map";

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isWaystoneSelectionScreen(screen)) {
            return;
        }

        try {
            Button prev = (Button) getField(screen, "btnPrevPage");
            Button next = (Button) getField(screen, "btnNextPage");
            if (prev == null || next == null) {
                return;
            }

            int x = Math.min(prev.getX(), next.getX());
            int y = Math.max(prev.getY(), next.getY()) + 26;
            int width = prev.getWidth() + next.getWidth() + 10;

            Button openMapButton = Button.builder(Component.literal(SELECT_VIA_WORLD_MAP), button -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft != null) {
                    minecraft.setScreen(null);
                }
                ClientHooks.tryOpenXaeroWorldMap();
            }).pos(x, y).size(width, 20).build();

            event.addListener(openMapButton);
        } catch (Throwable ignored) {
        }
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
}
