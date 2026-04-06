package com.macat.waystonemap;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
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

    private WaystoneSelectionScreenHook() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> tryAddButton(screen));
    }

    private static void tryAddButton(Screen screen) {
        try {
            Class<?> base = Class.forName(BASE_SCREEN_CLASS);
            if (!base.isInstance(screen)) return;

            Button prev = (Button) getField(screen, "btnPrevPage");
            Button next = (Button) getField(screen, "btnNextPage");
            if (prev == null || next == null) return;

            int x = prev.getX();
            int y = Math.max(prev.getY(), next.getY()) + prev.getHeight() + 10;
            int width = (next.getX() + next.getWidth()) - x;
            int height = prev.getHeight();

            List<AbstractWidget> buttons = Screens.getButtons(screen);
            for (AbstractWidget widget : buttons) {
                if (widget instanceof Button existing && existing.getMessage() != null
                        && "Choose via World Map".contentEquals(existing.getMessage().getString())) {
                    return;
                }
            }

            Button button = Button.builder(Component.literal("Choose via World Map"), b -> {
                try {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft != null) {
                        minecraft.setScreen(null);
                        ClientHooks.tryOpenXaeroWorldMap();
                    }
                } catch (Throwable ignored) {}
            }).bounds(x, y, width, height).build();

            buttons.add(button);
        } catch (Throwable ignored) {}
    }

    private static Object getField(Object instance, String name) {
        Class<?> c = instance.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(instance);
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
