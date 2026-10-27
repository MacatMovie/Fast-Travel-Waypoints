package com.macat.waystonemap.mixin;

import com.macat.waystonemap.XaeroCommandRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Rewrites Xaero's outgoing teleport request in memory. Never persists Xaero settings.
 * Xaero's class and method names are not remapped; Minecraft invocations are.
 */
@Pseudo
@Mixin(targets = "xaero.map.teleport.MapTeleporter", remap = false)
public abstract class XaeroMapTeleporterCommandMixin {

    @ModifyArg(
            method = "teleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendUnsignedCommand(Ljava/lang/String;)Z", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteUnsigned(String value) {
        return XaeroCommandRewriter.rewrite(value);
    }

    @ModifyArg(
            method = "teleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteCommand(String value) {
        return XaeroCommandRewriter.rewrite(value);
    }

    @ModifyArg(
            method = "teleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteChat(String value) {
        return XaeroCommandRewriter.rewrite(value);
    }
}
