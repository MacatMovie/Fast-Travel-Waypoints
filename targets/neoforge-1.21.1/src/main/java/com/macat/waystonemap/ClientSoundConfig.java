package com.macat.waystonemap;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;

/** Sound selection is per client; this config must not affect server teleport behaviour. */
public final class ClientSoundConfig {
    public enum TeleportSound { CUSTOM, ENDERMAN }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<TeleportSound> TELEPORT_SOUND;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("audio");
        TELEPORT_SOUND = builder.comment("Arrival sound heard by this client. CUSTOM: Hybrid A (default); ENDERMAN: original vanilla Enderman teleport sound.")
                .defineEnum("teleport_sound", TeleportSound.CUSTOM);
        builder.pop();
        SPEC = builder.build();
    }
    private ClientSoundConfig() {}
    public static void register() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
    public static void saveAll() { TELEPORT_SOUND.save(); }
    public static boolean useEnderman() { return TELEPORT_SOUND.get() == TeleportSound.ENDERMAN; }
}
