package com.macat.waystonemap;

import com.macat.waystonemap.client.gui.FastTravelConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Living Wind-style configuration entry in NeoForge's Mods list. */
@Mod(value = "fast_travel_waypoints", dist = Dist.CLIENT)
public final class WaystoneMapTpClientConfigMenu {
    public WaystoneMapTpClientConfigMenu(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (ignored, parent) -> new FastTravelConfigScreen(parent));
    }
}
