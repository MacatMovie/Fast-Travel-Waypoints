package com.macat.waystonemap;

import com.macat.waystonemap.client.gui.FastTravelConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "fast_travel_waypoints", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WaystoneMapTpClientConfigMenu {
    private WaystoneMapTpClientConfigMenu() {}
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new FastTravelConfigScreen(parent))
        ));
    }
}
