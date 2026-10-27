package com.macat.waystonemap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.ChatFormatting;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.lang.reflect.Method;

@Mod("fast_travel_waypoints")
public class WaystoneMapTpMod {

    private static final int COUNTDOWN_SECONDS = 3;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<UUID, PendingTeleport> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, PostTeleportFx> POST_FX = new ConcurrentHashMap<>();
    private static final WaystoneGeometry GEOMETRY = new WaystoneGeometry(block -> ForgeRegistries.BLOCKS.getKey(block));
    private static final WaystoneReflection WAYSTONE_REFLECTION = new WaystoneReflection(GEOMETRY);

    public WaystoneMapTpMod() {
        ModSounds.SOUND_EVENTS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        ModConfigs.register();
        ClientSoundConfig.register();
        MinecraftForge.EVENT_BUS.register(this);
        invokeClientHook("initializeXaeroCompatibility");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        invokeClientHook("releasePendingWorldMapKey");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("wstp")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("pos", Vec3Argument.vec3())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal("This command can only be used by players."));
                            return 0;
                        }

                        Vec3 vec = Vec3Argument.getVec3(ctx, "pos");
                        BlockPos targetPos = BlockPos.containing(vec.x, vec.y, vec.z);
                        ServerLevel level = source.getLevel();

                        return handleTeleport(player, level, targetPos, vec, null);
                    })
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("This command can only be used by players."));
                                return 0;
                            }

                            Vec3 vec = Vec3Argument.getVec3(ctx, "pos");
                            BlockPos targetPos = BlockPos.containing(vec.x, vec.y, vec.z);
                            ServerLevel level = source.getLevel();
                            String name = StringArgumentType.getString(ctx, "name");

                            return handleTeleport(player, level, targetPos, vec, name);
                        })
                    )
                )
        );

        // Explicit cross-dimensional FTW command used by the client-side rewrite fallback.
        dispatcher.register(
            Commands.literal("wstpxd")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("dimension", ResourceLocationArgument.id())
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("This command can only be used by players."));
                                return 0;
                            }

                            Vec3 vec = Vec3Argument.getVec3(ctx, "pos");
                            ResourceLocation dimId = ResourceLocationArgument.getId(ctx, "dimension");
                            String dim = dimId.toString();
                            return handleTeleportInDimension(player, dim, vec, null);
                        })
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                if (!(source.getEntity() instanceof ServerPlayer player)) {
                                    source.sendFailure(Component.literal("This command can only be used by players."));
                                    return 0;
                                }

                                Vec3 vec = Vec3Argument.getVec3(ctx, "pos");
                                ResourceLocation dimId = ResourceLocationArgument.getId(ctx, "dimension");
                            String dim = dimId.toString();
                                String name = StringArgumentType.getString(ctx, "name");
                                return handleTeleportInDimension(player, dim, vec, name);
                            })
                        )
                    )
                )
        );
    }

    /**
     * Intercept vanilla /tp, /teleport and Xaero waypoint integration wrapped /execute teleports.
     * 
     * Forge 1.20.1 can still fire CommandEvent for commands that the player is not
     * allowed to execute normally, but the parsed node list can be incomplete. For
     * that reason we parse the raw command text ourselves instead of relying only on
     * Brigadier's parsed root node.
     */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;

        String raw = event.getParseResults().getReader().getString();
        if (raw == null || raw.isBlank()) return;

        if (tryHandleWrappedExecuteTeleport(event, player, raw)) {
            return;
        }

        if (tryHandleVanillaTeleportCommand(event, player, player.serverLevel(), raw, null)) {
            return;
        }
    }

    private boolean tryHandleVanillaTeleportCommand(CommandEvent event, ServerPlayer player, ServerLevel targetLevel, String raw, String forcedName) {
        return tryHandleVanillaTeleportCommand(event, player, targetLevel, raw, forcedName, false);
    }

    private boolean tryHandleVanillaTeleportCommand(CommandEvent event, ServerPlayer player, ServerLevel targetLevel, String raw, String forcedName, boolean executeAsFastTravelCommand) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 4) return false;

        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (!("tp".equals(root) || "teleport".equals(root) || "wstp".equals(root))) return false;

        int idx = 1;
        if (!"wstp".equals(root) && parts.length >= 5) {
            String maybeTarget = parts[1];
            if ("@s".equalsIgnoreCase(maybeTarget) || player.getGameProfile().getName().equalsIgnoreCase(maybeTarget)) {
                idx = 2;
            }
        }

        if (parts.length < idx + 3) return false;

        Double x = parseCoord(parts[idx]);
        Double y = parseCoord(parts[idx + 1]);
        Double z = parseCoord(parts[idx + 2]);
        if (x == null || y == null || z == null) return false;

        String name = forcedName;
        if (name == null && parts.length > idx + 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = idx + 3; i < parts.length; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(parts[i]);
            }
            name = sb.toString();
        }

        BlockPos targetPos = BlockPos.containing(x, y, z);
        Vec3 exactPos = new Vec3(x, y, z);

        if (executeAsFastTravelCommand) {
            event.setCanceled(true);
            handleTeleport(player, targetLevel, targetPos, exactPos, name);
            return true;
        }

        // Vanilla /tp safety hook only: do not synchronously load/wait for destination chunks
        // just to decide whether this mod should intercept the command.
        Optional<BlockPos> waystoneBottom = GEOMETRY.findWaystoneBottom(targetLevel, targetPos, false);
        if (waystoneBottom.isEmpty()) return false;

        event.setCanceled(true);
        if (!canStartFastTravel(player, targetLevel)) return true;
        FastTravelCost travelCost = resolveFastTravelCost(player, targetLevel, waystoneBottom.get());
        if (!canAffordFastTravelCost(player, travelCost, true)) return true;
        startWaystoneTeleportCountdown(player, targetLevel, waystoneBottom.get(), exactPos, name, travelCost);
        return true;
    }

    /**
     * Handles commands produced by Xaero waypoint integrations / Xaero World Map when a
     * different map dimension is selected, for example:
     *   execute in minecraft:the_nether run tp @s 8 65.5 -14
     *   execute as @s in minecraft:the_nether run tp 8 65.5 -14
     *   execute in minecraft:the_nether run wstp 8 65.5 -14
     */
    private boolean tryHandleWrappedExecuteTeleport(CommandEvent event, ServerPlayer player, String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }

        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("execute ")) {
            return false;
        }

        int runIdx = lower.indexOf(" run ");
        if (runIdx < 0) {
            return false;
        }

        String beforeRun = trimmed.substring(0, runIdx).trim();
        String payload = trimmed.substring(runIdx + " run ".length()).trim();

        String dimText = extractExecuteDimension(beforeRun);
        if (dimText == null || dimText.isBlank()) {
            return false;
        }

        ResourceLocation dimId = ResourceLocation.tryParse(dimText);
        if (dimId == null) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel targetLevel = server.getLevel(dimKey);
        if (targetLevel == null) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("Fast travel failed: destination dimension is not loaded: " + dimText)
                    .withStyle(ChatFormatting.RED), true);
            return true;
        }

        return tryHandleVanillaTeleportCommand(event, player, targetLevel, payload, null, true);
    }

    private String extractExecuteDimension(String beforeRun) {
        String[] tokens = beforeRun.trim().split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("in".equalsIgnoreCase(tokens[i])) {
                return tokens[i + 1];
            }
        }
        return null;
    }

    
    private boolean canStartFastTravel(ServerPlayer player, ServerLevel targetLevel) {
        ServerLevel level = player.serverLevel();
        boolean crossDimensional = isCrossDimensionalFastTravel(player, targetLevel);

        if (crossDimensional && !ModConfigs.ENABLE_CROSS_DIMENSIONAL_TRAVEL.get()) {
            player.displayClientMessage(
                    Component.literal("Cross-dimensional fast travel is disabled on this server.").withStyle(ChatFormatting.RED),
                    true
            );
            return false;
        }

        // Creative always bypasses restrictions and XP costs (and also bypasses the countdown), but not the server cross-dimensional travel toggle above.
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return true;

        if (ModConfigs.REQUIRE_NEARBY_WAYSTONE_FOR_USE.get()) {
            int radius = ModConfigs.NEARBY_WAYSTONE_USE_RADIUS.get();
            if (!isPlayerNearAnyWaystone(level, player.blockPosition(), radius)) {
                player.displayClientMessage(
                        Component.literal("You must be within " + radius + " blocks of a Waystone to fast travel.").withStyle(ChatFormatting.RED),
                        true
                );
                return false;
            }
        }

        // Open sky requirement (ignores leaves + transparent blocks like glass). Checked from the player's upper body (1 block above feet).
        if (ModConfigs.requireOpenSkyPlayer() && isOpenSkyCheckEnabledInThisDimension(level)) {
            BlockPos pos = player.blockPosition().above();
            if (!canSeeSkyIgnoringLeaves(level, pos)) {
                player.displayClientMessage(
                        Component.literal("Fast travel requires open sky.").withStyle(ChatFormatting.RED),
                        true
                );
                return false;
            }
        }

        // Min Y requirement
        if (ModConfigs.ENABLE_MIN_Y_CHECK.get()) {
            int minY = ModConfigs.MIN_Y.get();
            if (player.getY() < (double) minY) {
                player.displayClientMessage(
                        Component.literal("Fast travel requires Y ≥ " + minY + ".").withStyle(ChatFormatting.RED),
                        true
                );
                return false;
            }
        }

        return true;
    }

    private boolean isCrossDimensionalFastTravel(ServerPlayer player, ServerLevel targetLevel) {
        return targetLevel != null && !player.serverLevel().dimension().equals(targetLevel.dimension());
    }

    private boolean canBypassWaystoneVerification(ServerPlayer player) {
        GameType gameMode = player.gameMode.getGameModeForPlayer();
        return player.hasPermissions(2) && (gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR);
    }

    private int getConfiguredFastTravelLevelCost(ServerPlayer player, ServerLevel targetLevel) {
        int cost = ModConfigs.LEVEL_COST.get();
        if (isCrossDimensionalFastTravel(player, targetLevel)) {
            cost += ModConfigs.ADDITIONAL_LEVEL_COST_DIMENSIONAL_TRAVEL.get();
        }
        return Math.max(0, cost);
    }

    private FastTravelCost resolveFastTravelCost(ServerPlayer player, ServerLevel targetLevel, BlockPos waystoneBottom) {
        GameType gameMode = player.gameMode.getGameModeForPlayer();
        if (gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR) {
            return FastTravelCost.free();
        }

        if (!ModConfigs.USE_WAYSTONES_XP_COST_SCALING.get()) {
            return FastTravelCost.configuredLevels(getConfiguredFastTravelLevelCost(player, targetLevel));
        }

        return resolveWaystonesXpCost(player, targetLevel, waystoneBottom);
    }

    /**
     * Uses Waystones 14.x's own waystone-to-waystone XP level prediction. This
     * includes its configured distance scaling, dimensional cost, min/max values,
     * global-waystone multiplier, leashed-mob cost and waystone travel multiplier.
     *
     * Reflection keeps this source buildable without adding a compile-time
     * Waystones API dependency; Waystones remains a required runtime dependency.
     */
    private FastTravelCost resolveWaystonesXpCost(ServerPlayer player, ServerLevel targetLevel, BlockPos waystoneBottom) {
        try {
            Object targetWaystone = WAYSTONE_REFLECTION.getWaystoneObject(targetLevel, waystoneBottom);
            if (targetWaystone == null) {
                return FastTravelCost.failed();
            }

            Object sourceWaystone = null;
            int sourceRadius = ModConfigs.NEARBY_WAYSTONE_USE_RADIUS.get();
            Optional<BlockPos> sourceBottom = findNearbyWaystoneBottom(player.serverLevel(), player.blockPosition(), sourceRadius);
            if (sourceBottom.isPresent()) {
                sourceWaystone = WAYSTONE_REFLECTION.getWaystoneObject(player.serverLevel(), sourceBottom.get());
            }

            Class<?> warpModeClass = Class.forName("net.blay09.mods.waystones.core.WarpMode");
            Object warpMode = warpModeClass.getField("WAYSTONE_TO_WAYSTONE").get(null);

            Class<?> managerClass = Class.forName("net.blay09.mods.waystones.core.PlayerWaystoneManager");
            for (Method method : managerClass.getMethods()) {
                if (!method.getName().equals("predictExperienceLevelCost")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 4) continue;
                if (!params[0].isAssignableFrom(player.getClass())) continue;
                if (!params[1].isInstance(targetWaystone)) continue;
                if (!params[2].isInstance(warpMode)) continue;
                if (sourceWaystone != null && !params[3].isInstance(sourceWaystone)) continue;

                Object result = method.invoke(null, player, targetWaystone, warpMode, sourceWaystone);
                if (result instanceof Number number) {
                    return FastTravelCost.waystonesLevels(number.intValue());
                }
            }
        } catch (Throwable ignored) {
        }

        return FastTravelCost.failed();
    }

    private boolean canAffordFastTravelCost(ServerPlayer player, FastTravelCost cost, boolean showMessage) {
        GameType gameMode = player.gameMode.getGameModeForPlayer();
        if (gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR) return true;

        if (!cost.resolved) {
            if (showMessage) {
                player.displayClientMessage(
                        Component.literal("Fast travel could not calculate the Waystones XP cost.").withStyle(ChatFormatting.RED),
                        true
                );
            }
            return false;
        }

        if (player.experienceLevel >= cost.levels) return true;

        if (showMessage) {
            player.displayClientMessage(buildFastTravelCostMessage(cost).withStyle(ChatFormatting.RED), true);
        }
        return false;
    }

    private MutableComponent buildFastTravelCostMessage(FastTravelCost cost) {
        return Component.literal("Fast travel requires " + cost.levels + " "
                + (cost.levels == 1 ? "level" : "levels") + ".");
    }

    private boolean consumeFastTravelCost(ServerPlayer player, FastTravelCost cost) {
        if (cost.isFree()) return true;
        if (!canAffordFastTravelCost(player, cost, true)) return false;

        player.giveExperienceLevels(-cost.levels);
        return true;
    }

    private boolean canSeeSkyIgnoringLeaves(ServerLevel level, BlockPos pos) {
        // "Open sky" check that:
        // - ignores leaves
        // - ignores ALL fluids (water, lava, modded fluids)
        // - allows transparent skylight-propagating blocks like glass
        //
        // We treat the sky as "visible" if there is no block above that *stops skylight propagation*.
        // This matches the intuitive "outdoor" feel and still blocks under solid ceilings (including slabs/stairs),
        // while allowing glass roofs.
        int maxY = level.getMaxBuildHeight() - 1;
        int x = pos.getX();
        int z = pos.getZ();

        for (int y = pos.getY(); y <= maxY; y++) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(p);

            if (state.isAir()) continue;
            // Treat pure fluids (water/lava/modded) like air, but do NOT ignore waterlogged solid blocks.
            if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, p).isEmpty()) continue;
            if (state.is(net.minecraft.tags.BlockTags.LEAVES)) continue;

            // If skylight can't propagate through this block, it's not "open sky".
            if (!state.propagatesSkylightDown(level, p)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPlayerNearAnyWaystone(ServerLevel level, BlockPos playerPos, int radius) {
        return findNearbyWaystoneBottom(level, playerPos, radius).isPresent();
    }

    private Optional<BlockPos> findNearbyWaystoneBottom(ServerLevel level, BlockPos playerPos, int radius) {
        int minY = Math.max(level.getMinBuildHeight(), playerPos.getY() - Math.min(radius, 4));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, playerPos.getY() + Math.min(radius, 4));
        int radiusSq = radius * radius;

        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dz * dz) > radiusSq) continue;

                    checkPos.set(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!GEOMETRY.isWaystoneBlock(level, checkPos, false)) continue;

                    BlockPos bottom = checkPos.immutable();
                    while (GEOMETRY.isWaystoneBlock(level, bottom.below(), false)) {
                        bottom = bottom.below();
                    }

                    Vec3 playerCenter = new Vec3(playerPos.getX() + 0.5D, playerPos.getY() + 0.5D, playerPos.getZ() + 0.5D);
                    Vec3 waystoneCenter = new Vec3(bottom.getX() + 0.5D, bottom.getY() + 0.5D, bottom.getZ() + 0.5D);
                    if (playerCenter.distanceToSqr(waystoneCenter) <= radiusSq) {
                        return Optional.of(bottom);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean isOpenSkyCheckEnabledInThisDimension(ServerLevel level) {
        // Only enforce open-sky in whitelisted dimensions (defaults: overworld + the_end).
        // If the whitelist is empty, treat it as "disabled everywhere".
        var list = ModConfigs.OPEN_SKY_DIMENSION_WHITELIST.get();
        if (list == null || list.isEmpty()) return false;

        ResourceLocation dim = level.dimension().location();
        for (String s : list) {
            if (s == null || s.isBlank()) continue;
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null && rl.equals(dim)) return true;
        }
        return false;
    }


    private int handleTeleportInDimension(ServerPlayer player, String dimension, Vec3 vec, String waypointName) {
        ResourceLocation dimId = ResourceLocation.tryParse(dimension);
        if (dimId == null) {
            player.displayClientMessage(Component.literal("Fast travel failed: invalid destination dimension: " + dimension)
                    .withStyle(ChatFormatting.RED), true);
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel targetLevel = server.getLevel(dimKey);
        if (targetLevel == null) {
            player.displayClientMessage(Component.literal("Fast travel failed: destination dimension is not loaded: " + dimension)
                    .withStyle(ChatFormatting.RED), true);
            return 0;
        }

        BlockPos targetPos = BlockPos.containing(vec.x, vec.y, vec.z);
        return handleTeleport(player, targetLevel, targetPos, vec, waypointName);
    }

    private int handleTeleport(ServerPlayer player, ServerLevel level, BlockPos targetPos, Vec3 exactPos, String waypointName) {
        // First do a cheap loaded-chunk-only check. This prevents admin/map teleports
        // to unloaded areas from synchronously loading destination chunks just to see
        // whether the target is a Waystone.
        Optional<BlockPos> waystoneBottom = GEOMETRY.findWaystoneBottom(level, targetPos, false);

        // If it's an already-loaded Waystone waypoint, ALWAYS use safe-teleport.
        if (waystoneBottom.isPresent()) {
            if (!canStartFastTravel(player, level)) {
                return 0;
            }
            FastTravelCost travelCost = resolveFastTravelCost(player, level, waystoneBottom.get());
            if (!canAffordFastTravelCost(player, travelCost, true)) {
                return 0;
            }
            startWaystoneTeleportCountdown(player, level, waystoneBottom.get(), exactPos, waypointName, travelCost);
            return 1;
        }

        // Only OPs in Creative/Spectator keep the "teleport anywhere" behavior.
        // Do this before any chunk-loading Waystone verification so admin map
        // teleports do not hitch the server thread. OPs in Survival/Adventure
        // still fall through to the normal Waystone validation below.
        if (canBypassWaystoneVerification(player)) {
            teleportPlayer(player, level, exactPos);
            return 1;
        }

        // Survival/Adventure OPs and all non-OP players still need a real Waystone
        // verification. If the destination chunk is unloaded, this may load/wait
        // for it, but that is intentional: the mod has to confirm the waypoint is
        // actually a Waystone before it allows restricted fast travel.
        waystoneBottom = GEOMETRY.findWaystoneBottom(level, targetPos, true);
        if (waystoneBottom.isPresent()) {
            if (!canStartFastTravel(player, level)) {
                return 0;
            }
            FastTravelCost travelCost = resolveFastTravelCost(player, level, waystoneBottom.get());
            if (!canAffordFastTravelCost(player, travelCost, true)) {
                return 0;
            }
            startWaystoneTeleportCountdown(player, level, waystoneBottom.get(), exactPos, waypointName, travelCost);
            return 1;
        }

        player.displayClientMessage(Component.literal("No Waystone at the selected waypoint."), true);
        return 0;
    }

    private void teleportPlayer(ServerPlayer player, ServerLevel level, Vec3 exactPos) {
        double x = exactPos.x;
        double y = exactPos.y;
        double z = exactPos.z;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.teleportTo(level, x, y, z, yaw, pitch);
    }

    private void safeTeleportToWaystoneNow(ServerPlayer player, ServerLevel level, BlockPos waystoneBottom, Vec3 fallbackExact) {
        Optional<Vec3> safe = GEOMETRY.findSafeTeleportPos(level, waystoneBottom);
        if (safe.isPresent()) {
            teleportPlayer(player, level, safe.get());
            return;
        }

        // Never force-teleport into blocks (even for OPs). If no safe spot exists, fail with a clear message.
        player.displayClientMessage(Component.literal("No empty space next to that waystone.")
                .withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    private boolean shouldSkipCountdown(ServerPlayer player) {
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return true;
        }

        int radius = ModConfigs.NEARBY_WAYSTONE_USE_RADIUS.get();
        boolean nearWaystone = isPlayerNearAnyWaystone(player.serverLevel(), player.blockPosition(), radius);
        if (nearWaystone) {
            return ModConfigs.DISABLE_COUNTDOWN_WHEN_NEAR_A_WAYSTONE.get();
        }
        return ModConfigs.DISABLE_COUNTDOWN_FOR_TELEPORTING_FROM_ANYWHERE.get();
    }

    private void completeTeleportWithFx(ServerPlayer player, ServerLevel level, BlockPos waystoneBottom, Vec3 fallbackExact, FastTravelCost travelCost) {
        if (!consumeFastTravelCost(player, travelCost)) return;
        safeTeleportToWaystoneNow(player, level, waystoneBottom, fallbackExact);
        POST_FX.put(player.getUUID(), new PostTeleportFx(4));
    }

    private void startWaystoneTeleportCountdown(ServerPlayer player, ServerLevel level, BlockPos waystoneBottom, Vec3 fallbackExact, String waypointName, FastTravelCost travelCost) {
        // If the required client-side mods are missing, don't start a countdown
        // (but still allow the server-side teleport logic to run, so the mod remains usable).
        // The hard dependency is enforced via mods.toml on the client.

        String displayName = WAYSTONE_REFLECTION.chooseDisplayName(level, waystoneBottom, waypointName);

        Optional<Vec3> safeNow = GEOMETRY.findSafeTeleportPos(level, waystoneBottom);
        if (safeNow.isEmpty()) {
            player.displayClientMessage(Component.literal("No empty space next to that waystone.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }


        // Destination open-sky requirement (optional). Uses the computed safe landing spot as the origin (+1 block).
        if (!(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR)) {
        if (ModConfigs.REQUIRE_OPEN_SKY_DESTINATION.get() && isOpenSkyCheckEnabledInThisDimension(level)) {
            BlockPos destCheck = BlockPos.containing(safeNow.get()).above();
            if (!canSeeSkyIgnoringLeaves(level, destCheck)) {
                player.displayClientMessage(Component.literal("Fast travel destination requires open sky.")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        }


        if (shouldSkipCountdown(player)) {
            ServerLevel pl = player.serverLevel();
            pl.sendParticles(ParticleTypes.POOF,
                    player.getX(), player.getY() + 0.2D, player.getZ(),
                    18, 0.35D, 0.2D, 0.35D, 0.01D);
            completeTeleportWithFx(player, level, waystoneBottom, fallbackExact, travelCost);
            return;
        }

        PendingTeleport pending = new PendingTeleport(level.dimension().location().toString(), waystoneBottom, fallbackExact, displayName, travelCost);
        PENDING.put(player.getUUID(), pending);

        // Immediate first title/sound (3s)
        sendCountdownTitle(player, displayName, COUNTDOWN_SECONDS);
        playSoftNote(player);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        for (Map.Entry<UUID, PendingTeleport> e : PENDING.entrySet()) {
            UUID id = e.getKey();
            PendingTeleport p = e.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                PENDING.remove(id);
                continue;
            }

            p.ticksRemaining--;

            // Portal particles during countdown so others can see you're about to teleport.
            // Keep it light to avoid spam.
            if (p.ticksRemaining > 0 && (p.ticksRemaining % 4 == 0)) {
                ServerLevel pl = player.serverLevel();
                pl.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 0.8D, player.getZ(),
                        6, 0.45D, 0.7D, 0.45D, 0.02D);
            }

            // Titles at 2s and 1s
            if (p.ticksRemaining == 2 * TICKS_PER_SECOND) {
                sendCountdownTitle(player, p.waystoneName, 2);
                playSoftNote(player);
                // Darkness should start around here and run its course (no forced clear).
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
            } else if (p.ticksRemaining == 1 * TICKS_PER_SECOND) {
                sendCountdownTitle(player, p.waystoneName, 1);
                playSoftNote(player);
            }

            // Poof particles shortly before teleport
            if (p.ticksRemaining == 2) {
                ServerLevel pl = player.serverLevel();
                pl.sendParticles(ParticleTypes.POOF,
                        player.getX(), player.getY() + 0.2D, player.getZ(),
                        18, 0.35D, 0.2D, 0.35D, 0.01D);
            }

            if (p.ticksRemaining <= 0) {
                PENDING.remove(id);

                ServerLevel level = getPendingTargetLevel(server, p);
                if (level == null) {
                    player.displayClientMessage(
                            Component.literal("Fast travel failed: destination dimension is not loaded: " + p.dim).withStyle(ChatFormatting.RED),
                            true
                    );
                    continue;
                }

                // Re-check and consume the selected XP cost immediately before teleporting.
                if (!consumeFastTravelCost(player, p.travelCost)) {
                    continue;
                }

                safeTeleportToWaystoneNow(player, level, p.waystoneBottom, p.fallbackExact);

                // Post-teleport effects should happen at the ARRIVAL position.
                // Delay a few ticks so the client is fully at the new spot.
                POST_FX.put(player.getUUID(), new PostTeleportFx(4));
            }
        }

        // Run delayed post-teleport effects.
        for (Map.Entry<UUID, PostTeleportFx> e : POST_FX.entrySet()) {
            UUID id = e.getKey();
            PostTeleportFx fx = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                POST_FX.remove(id);
                continue;
            }
            fx.ticks--;
            if (fx.ticks <= 0) {
                POST_FX.remove(id);

                // Custom teleport sound (played at arrival).
                playTeleportSoundAtArrival(player);

                // Portal particles after arriving.
                ServerLevel arr = player.serverLevel();
                arr.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 0.8D, player.getZ(),
                        24, 0.55D, 0.8D, 0.55D, 0.05D);
            }
        }
    }


    private ServerLevel getPendingTargetLevel(MinecraftServer server, PendingTeleport pending) {
        ResourceLocation dimId = ResourceLocation.tryParse(pending.dim);
        if (dimId == null) return null;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        return server.getLevel(dimKey);
    }

    private void sendCountdownTitle(ServerPlayer player, String waystoneName, int seconds) {
        Component title = Component.literal(waystoneName).withStyle(ChatFormatting.YELLOW);
        Component subtitle = Component.literal("Teleporting in " + seconds + "s").withStyle(ChatFormatting.WHITE);

        // 0 fade-in, short stay, soft fade-out. Each second replaces previous.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 18, 10));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
    }

    private void playSoftNote(ServerPlayer player) {
        // Prefer a newer note block instrument sound if present; fall back to pling.
        SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.note_block.chime"));
        if (se == null) se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.note_block.bell"));
        if (se == null) se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.note_block.pling"));
        if (se != null) {
            player.playNotifySound(se, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    private void playTeleportSoundAtArrival(ServerPlayer player) {
        SoundEvent se = ModSounds.WAYSTONE_TELEPORT.get();
        if (se != null) {
            player.serverLevel().playSound(null, player.blockPosition(), se, SoundSource.PLAYERS, 0.9f, 1.0f);
        }
    }

    private void invokeClientHook(String methodName) {
        try {
            Class<?> clientHooksClass = Class.forName("com.macat.waystonemap.ClientHooks");
            clientHooksClass.getMethod(methodName).invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private boolean isLikelyBoundScroll(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (rl == null) return false;
        return "waystones".equals(rl.getNamespace()) && rl.getPath().contains("bound_scroll");
    }

    private static class FastTravelCost {
        final int levels;
        final boolean resolved;

        private FastTravelCost(int levels, boolean resolved) {
            this.levels = Math.max(0, levels);
            this.resolved = resolved;
        }

        static FastTravelCost free() {
            return new FastTravelCost(0, true);
        }

        static FastTravelCost configuredLevels(int levels) {
            return new FastTravelCost(levels, true);
        }

        static FastTravelCost waystonesLevels(int levels) {
            return new FastTravelCost(levels, true);
        }

        static FastTravelCost failed() {
            return new FastTravelCost(0, false);
        }

        boolean isFree() {
            return resolved && levels <= 0;
        }
    }

    private static class PendingTeleport {
        final String dim;
        final BlockPos waystoneBottom;
        final Vec3 fallbackExact;
        final String waystoneName;
        final FastTravelCost travelCost;
        int ticksRemaining;

        PendingTeleport(String dim, BlockPos waystoneBottom, Vec3 fallbackExact, String waystoneName, FastTravelCost travelCost) {
            this.dim = dim;
            this.waystoneBottom = waystoneBottom;
            this.fallbackExact = fallbackExact;
            this.waystoneName = waystoneName;
            this.travelCost = travelCost;
            this.ticksRemaining = COUNTDOWN_SECONDS * TICKS_PER_SECOND;
        }
    }

    private static class PostTeleportFx {
        int ticks;
        PostTeleportFx(int ticks) { this.ticks = ticks; }
    }

    /**
     * Finds a waystone block near the waypoint and returns the BOTTOM blockpos of the 2-block waystone.
     *
     * allowChunkLoad=true is used for intentional /wstp travel, where checking the destination is expected.
     * allowChunkLoad=false is used for cheap detection paths so getBlockState() does not synchronously
     * load/wait for an unloaded server chunk just to decide whether this mod should intercept or bypass.
     */
    private Double parseCoord(String s) {
        try {
            // Xaero uses absolute numbers here, but be permissive with decimals.
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}