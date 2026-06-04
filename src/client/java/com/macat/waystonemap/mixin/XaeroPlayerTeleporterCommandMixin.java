package com.macat.waystonemap.mixin;

import com.macat.waystonemap.XaeroCommandRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "xaero.map.radar.tracker.PlayerTeleporter", remap = false)
public class XaeroPlayerTeleporterCommandMixin {
    @ModifyArg(
            method = "teleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendUnsignedCommand(Ljava/lang/String;)Z", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteUnsignedCommand(String command) {
        return XaeroCommandRewriter.rewrite(command);
    }

    @ModifyArg(
            method = "teleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteCommand(String command) {
        return XaeroCommandRewriter.rewrite(command);
    }

    @ModifyArg(
            method = "teleport",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteChatCommand(String message) {
        return XaeroCommandRewriter.rewrite(message);
    }
}
