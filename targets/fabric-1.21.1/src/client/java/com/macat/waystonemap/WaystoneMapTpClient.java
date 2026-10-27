package com.macat.waystonemap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.Minecraft;
import com.macat.waystonemap.client.gui.FastTravelConfigScreen;

public class WaystoneMapTpClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientSoundConfig.register();
        // Keep this client-only command as a fallback when optional Mod Menu is not installed.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("ftwconfig").executes(context -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.setScreen(new FastTravelConfigScreen(mc.screen)));
                return 1;
            }))
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            XaeroWaypointDisplayDefault.tick(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
            ClientHooks.releasePendingWorldMapKey();

            // Waystones sizes its destination list dynamically. Re-evaluating while that
            // screen is open mirrors NeoForge's post-init placement and also keeps the
            // button correct after GUI resizes or late layout updates.
            if (client.screen != null) {
                WaystoneSelectionScreenHook.tryAddButton(client.screen);
            }
        });
    }
}
