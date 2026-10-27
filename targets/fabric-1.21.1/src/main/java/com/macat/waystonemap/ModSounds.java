package com.macat.waystonemap;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Registers FTW's own sound so vanilla Endermen retain their original sound. */
public final class ModSounds {
    private static final ResourceLocation TELEPORT_ID =
            ResourceLocation.fromNamespaceAndPath("fast_travel_waypoints", "waystone_teleport");

    public static final SoundEvent WAYSTONE_TELEPORT = SoundEvent.createVariableRangeEvent(TELEPORT_ID);

    private ModSounds() {}

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, TELEPORT_ID, WAYSTONE_TELEPORT);
    }
}
