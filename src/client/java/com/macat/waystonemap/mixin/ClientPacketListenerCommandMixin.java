package com.macat.waystonemap.mixin;

import com.macat.waystonemap.XaeroCommandRewriter;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fabric fallback for Xaero versions/paths that send the command after the targeted
 * WaypointTeleport/MapTeleporter methods or through a slightly different helper.
 *
 * The targeted Xaero mixins are still preferred, but this catches the exact admin-only
 * commands Xaero emits on fresh configs: tp @s x y z and execute ... run tp @s x y z.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerCommandMixin {
    @ModifyVariable(method = "sendUnsignedCommand", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String fastTravelWaypoints$rewriteUnsignedCommand(String command) {
        return XaeroCommandRewriter.rewrite(command);
    }

    @ModifyVariable(method = "sendCommand", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String fastTravelWaypoints$rewriteCommand(String command) {
        return XaeroCommandRewriter.rewrite(command);
    }

    @ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String fastTravelWaypoints$rewriteChat(String message) {
        return XaeroCommandRewriter.rewrite(message);
    }
}
