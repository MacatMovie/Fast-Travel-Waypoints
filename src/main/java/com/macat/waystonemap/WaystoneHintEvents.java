package com.macat.waystonemap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WaystoneHintEvents {
    private static final String WAYSTONES_NS = "waystones";
    private static final Component HINT = Component.literal("You can also teleport via the world map by right-clicking Waystone waypoints.");
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();
    private static final long COOLDOWN_MS = 5000L;

    private static boolean shouldShowHint(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUUID();
        Long last = LAST_HINT.get(id);
        if (last != null && now - last < COOLDOWN_MS) return false;
        LAST_HINT.put(id, now);
        return true;
    }

    public static void maybeShowWaystoneBlockHint(Player player, Level level, BlockState state) {
    }

    public static void maybeShowWaystoneItemHint(Player player, InteractionHand hand) {
    }
}
