package com.macat.waystonemap.mixin;

import com.macat.waystonemap.WaystoneSelectionScreenHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the map-selection button after any client screen has completed initialization.
 * The hook itself filters this down to Waystones selection screens. Using a mixin here
 * is more reliable than depending on Fabric's global screen event for third-party screens.
 */
@Mixin(Screen.class)
public abstract class ScreenInitMixin {

    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("RETURN"), require = 1)
    private void fastTravelWaypoints$afterScreenInit(Minecraft minecraft, int width, int height, CallbackInfo ci) {
        WaystoneSelectionScreenHook.tryAddButton((Screen) (Object) this);
    }
}
