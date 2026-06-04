package com.macat.waystonemap.mixin;

import com.macat.waystonemap.XaeroCommandRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "xaero.hud.minimap.waypoint.WaypointTeleport", remap = false)
public class XaeroWaypointTeleportCommandMixin {
    @ModifyArg(
            method = "teleportToWaypoint(Lxaero/common/minimap/waypoints/Waypoint;Lxaero/hud/minimap/world/MinimapWorld;Lnet/minecraft/client/gui/screens/Screen;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendUnsignedCommand(Ljava/lang/String;)Z", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteUnsignedCommand(String command) {
        return XaeroCommandRewriter.rewrite(command);
    }

    @ModifyArg(
            method = "teleportToWaypoint(Lxaero/common/minimap/waypoints/Waypoint;Lxaero/hud/minimap/world/MinimapWorld;Lnet/minecraft/client/gui/screens/Screen;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteCommand(String command) {
        return XaeroCommandRewriter.rewrite(command);
    }

    @ModifyArg(
            method = "teleportToWaypoint(Lxaero/common/minimap/waypoints/Waypoint;Lxaero/hud/minimap/world/MinimapWorld;Lnet/minecraft/client/gui/screens/Screen;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V", remap = true),
            index = 0,
            require = 0
    )
    private String fastTravelWaypoints$rewriteChatCommand(String message) {
        return XaeroCommandRewriter.rewrite(message);
    }
}
