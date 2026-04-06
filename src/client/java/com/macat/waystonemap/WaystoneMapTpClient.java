package com.macat.waystonemap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class WaystoneMapTpClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WaystoneSelectionScreenHook.register();
        XaeroConfigPatcher.onClientJoin();
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHooks.releasePendingWorldMapKey());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> XaeroConfigPatcher.onClientJoin());
    }
}
