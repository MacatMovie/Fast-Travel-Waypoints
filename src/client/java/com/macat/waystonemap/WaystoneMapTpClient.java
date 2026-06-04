package com.macat.waystonemap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class WaystoneMapTpClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WaystoneSelectionScreenHook.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientHooks.releasePendingWorldMapKey();
        });
    }
}
