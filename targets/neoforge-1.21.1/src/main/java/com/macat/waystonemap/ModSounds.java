package com.macat.waystonemap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers FTW's own sound so vanilla Endermen retain their original sound. */
public final class ModSounds {
    private static final String MOD_ID = "fast_travel_waypoints";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WAYSTONE_TELEPORT = SOUND_EVENTS.register(
            "waystone_teleport",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "waystone_teleport"))
    );

    private ModSounds() {}
}
