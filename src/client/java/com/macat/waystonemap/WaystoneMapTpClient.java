package com.macat.waystonemap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class WaystoneMapTpClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            XaeroWaypointDisplayDefault.tick();
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
