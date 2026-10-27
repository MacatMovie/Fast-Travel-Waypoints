package com.macat.waystonemap.mixin;

import com.macat.waystonemap.XaeroScopedCommandRewrite;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Runs only when the client sends a command, never once per tick.
 * Rewrites Xaero-origin teleport commands while preserving manually entered
 * commands and commands from other mods. Targets Minecraft's own mapped class,
 * avoiding fragile mixin references into Xaero's separately mapped JAR.
 */
@Mixin(ClientPacketListener.class)
public abstract class XaeroClientCommandSendMixin {
    @ModifyVariable(method = "sendUnsignedCommand", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private String fastTravelWaypoints$rewriteUnsignedCommand(String command) {
        return XaeroScopedCommandRewrite.rewriteIfXaero(command);
    }

    @ModifyVariable(method = "sendCommand", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private String fastTravelWaypoints$rewriteCommand(String command) {
        return XaeroScopedCommandRewrite.rewriteIfXaero(command);
    }

    @ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private String fastTravelWaypoints$rewriteChat(String message) {
        return XaeroScopedCommandRewrite.rewriteIfXaero(message);
    }
}
