package com.macat.waystonemap.mixin;

import com.macat.waystonemap.ClientSoundConfig;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Swap FTW's sound only on the receiving client; world and server sounds are unchanged. */
@Mixin(ClientPacketListener.class)
public abstract class TeleportSoundPacketMixin {
    @Redirect(method = "handleSoundEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;getSound()Lnet/minecraft/core/Holder;"), require = 1)
    private Holder<SoundEvent> ftw$chooseTeleportSound(ClientboundSoundPacket packet) {
        Holder<SoundEvent> incoming = packet.getSound();
        if (ClientSoundConfig.useEnderman() &&
                incoming.value().getLocation().toString().equals("fast_travel_waypoints:waystone_teleport")) {
            return Holder.direct(SoundEvents.ENDERMAN_TELEPORT);
        }
        return incoming;
    }
}
