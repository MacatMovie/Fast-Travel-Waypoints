package com.macat.waystonemap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers FTW's own sound so vanilla Endermen retain their original sound. */
public final class ModSounds {
    private static final String MOD_ID = "fast_travel_waypoints";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    public static final RegistryObject<SoundEvent> WAYSTONE_TELEPORT = SOUND_EVENTS.register(
            "waystone_teleport",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "waystone_teleport"))
    );

    private ModSounds() {}
}
