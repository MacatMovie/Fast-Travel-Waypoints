package com.macat.waystonemap.client.compat;

import com.macat.waystonemap.client.gui.FastTravelConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Optional Mod Menu integration. Fabric only initializes this entrypoint when
 * Mod Menu is installed; the mod itself has no mandatory runtime dependency.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FastTravelConfigScreen::new;
    }
}
