package com.macat.waystonemap.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the final container-screen layout values using remapped field access.
 * Reflection by Mojang field names is unreliable in a production Fabric runtime,
 * where inherited Minecraft fields use intermediary names.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int fastTravelWaypoints$getLeftPos();

    @Accessor("topPos")
    int fastTravelWaypoints$getTopPos();

    @Accessor("imageWidth")
    int fastTravelWaypoints$getImageWidth();

    @Accessor("imageHeight")
    int fastTravelWaypoints$getImageHeight();
}
