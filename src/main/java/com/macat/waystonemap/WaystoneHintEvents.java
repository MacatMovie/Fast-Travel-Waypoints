package com.macat.waystonemap;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "fast_travel_waypoints")
public class WaystoneHintEvents {

    private static final String WAYSTONES_NS = "waystones";

    private static final Component HINT =
        Component.literal("You can also teleport via the world map by right-clicking Waystone waypoints.");

    // simple per-player cooldown so we don't spam every click
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();
    private static final long COOLDOWN_MS = 5000L; // 5 seconds

    private static boolean shouldShowHint(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUUID();
        Long last = LAST_HINT.get(id);
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        LAST_HINT.put(id, now);
        return true;
    }

    // 1) Right-clicking a Waystone block
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        if (player == null) return;

        BlockState state = level.getBlockState(event.getPos());
        ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (rl == null) return;

        if (WAYSTONES_NS.equals(rl.getNamespace()) && rl.getPath().contains("waystone")) {
            // DO NOT cancel the event – let Waystones open its normal UI
            if (shouldShowHint(player)) {
                // true = action bar (above hotbar)
                player.displayClientMessage(HINT, true);
            }
        }
    }

    // 2) Right-clicking Waystones teleport items (warp stone / scrolls)
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player == null) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (rl == null) return;

        if (WAYSTONES_NS.equals(rl.getNamespace())
                && (rl.getPath().contains("warp_stone") || rl.getPath().contains("warp_scroll"))) {

            // DO NOT cancel – still let Waystones do its thing
            if (shouldShowHint(player)) {
                player.displayClientMessage(HINT, true);
            }
        }
    }
}
