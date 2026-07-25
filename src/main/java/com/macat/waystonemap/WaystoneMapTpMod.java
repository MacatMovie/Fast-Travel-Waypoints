package com.macat.waystonemap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class WaystoneMapTpMod implements ModInitializer {


    private static final int COUNTDOWN_SECONDS = 3;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<UUID, PendingTeleport> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, PostTeleportFx> POST_FX = new ConcurrentHashMap<>();
    private static final Map<Block, Boolean> WAYSTONE_BLOCK_CACHE = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ModConfigs.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        UseBlockCallback.EVENT.register(this::onUseBlock);
    }
    public net.minecraft.world.InteractionResult onUseBlock(Player player, Level level, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        return net.minecraft.world.InteractionResult.PASS;
    }
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {

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

        // Creative/spectator bypass restrictions and XP costs, but not the server cross-dimensional travel toggle above.
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

        // Open sky requirement is checked in the player's origin dimension.
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

        // Min Y requirement is checked at the player's current position.
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
     * Resolves only Waystones' XP point / XP level requirements. Other Waystones
     * requirements (items, cooldowns, etc.) remain owned by Waystones and are not
     * applied to Fast Travel Waypoints' separate map-travel flow.
     *
     * Reflection keeps the source project buildable without adding a hard compile-time
     * Waystones API dependency; Waystones is still a required runtime dependency.
     */
    private FastTravelCost resolveWaystonesXpCost(ServerPlayer player, ServerLevel targetLevel, BlockPos waystoneBottom) {
        try {
            Object targetWaystone = getWaystoneObject(targetLevel, waystoneBottom);
            if (targetWaystone == null) {
                return FastTravelCost.failed();
            }

            Class<?> apiClass = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            Object context = null;
            for (Method method : apiClass.getMethods()) {
                if (!method.getName().equals("createUnboundTeleportContext")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0].isAssignableFrom(player.getClass()) && params[1].isInstance(targetWaystone)) {
                    context = method.invoke(null, player, targetWaystone);
                    break;
                }
            }
            if (context == null) {
                return FastTravelCost.failed();
            }

            // When map travel starts near a Waystone, expose that Waystone as the
            // source so custom Waystones requirements such as source_is_waystone
            // behave the same way they do in Waystones' own destination screen.
            int sourceRadius = ModConfigs.NEARBY_WAYSTONE_USE_RADIUS.get();
            Optional<BlockPos> sourceBottom = findNearbyWaystoneBottom(player.serverLevel(), player.blockPosition(), sourceRadius);
            if (sourceBottom.isPresent()) {
                Object sourceWaystone = getWaystoneObject(player.serverLevel(), sourceBottom.get());
                if (sourceWaystone != null) {
                    for (Method method : context.getClass().getMethods()) {
                        if (!method.getName().equals("setFromWaystone")) continue;
                        Class<?>[] params = method.getParameterTypes();
                        if (params.length == 1 && params[0].isInstance(sourceWaystone)) {
                            method.invoke(context, sourceWaystone);
                            break;
                        }
                    }
                }
            }

            Object requirement = null;
            for (Method method : apiClass.getMethods()) {
                if (!method.getName().equals("resolveRequirements")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isInstance(context)) {
                    requirement = method.invoke(null, context);
                    break;
                }
            }
            if (requirement == null) {
                return FastTravelCost.failed();
            }

            WaystonesXpAccumulator accumulator = new WaystonesXpAccumulator();
            collectWaystonesXpRequirements(requirement, accumulator);
            return FastTravelCost.waystones(accumulator.points, accumulator.levels);
        } catch (Throwable ignored) {
            return FastTravelCost.failed();
        }
    }

    private void collectWaystonesXpRequirements(Object requirement, WaystonesXpAccumulator accumulator) throws Exception {
        if (requirement == null) return;

        String className = requirement.getClass().getSimpleName();
        if ("ExperiencePointsRequirement".equals(className)) {
            Object value = requirement.getClass().getMethod("getPoints").invoke(requirement);
            if (value instanceof Number number) {
                accumulator.points += Math.max(0, number.intValue());
            }
            return;
        }

        if ("ExperienceLevelRequirement".equals(className)) {
            Object value = requirement.getClass().getMethod("getLevels").invoke(requirement);
            if (value instanceof Number number) {
                accumulator.levels += Math.max(0, number.intValue());
            }
            return;
        }

        if ("CombinedRequirement".equals(className)) {
            Object children = requirement.getClass().getMethod("getRequirements").invoke(requirement);
            if (children instanceof Iterable<?> iterable) {
                for (Object child : iterable) {
                    collectWaystonesXpRequirements(child, accumulator);
                }
            }
        }
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

        boolean enoughLevels = player.experienceLevel >= cost.levels;
        boolean enoughPoints = getTotalExperiencePoints(player) >= cost.xpPoints;
        if (enoughLevels && enoughPoints) return true;

        if (showMessage) {
            player.displayClientMessage(buildFastTravelCostMessage(cost).withStyle(ChatFormatting.RED), true);
        }
        return false;
    }

    private MutableComponent buildFastTravelCostMessage(FastTravelCost cost) {
        if (cost.xpPoints > 0 && cost.levels > 0) {
            return Component.literal("Fast travel requires " + cost.levels + " "
                    + (cost.levels == 1 ? "level" : "levels") + " and " + cost.xpPoints + " XP.");
        }
        if (cost.xpPoints > 0) {
            return Component.literal("Fast travel requires " + cost.xpPoints + " XP.");
        }
        return Component.literal("Fast travel requires " + cost.levels + " "
                + (cost.levels == 1 ? "level" : "levels") + ".");
    }

    private boolean consumeFastTravelCost(ServerPlayer player, FastTravelCost cost) {
        if (cost.isFree()) return true;
        if (!canAffordFastTravelCost(player, cost, true)) return false;

        if (cost.levels > 0) {
            player.giveExperienceLevels(-cost.levels);
        }
        if (cost.xpPoints > 0) {
            player.giveExperiencePoints(-cost.xpPoints);
        }
        return true;
    }

    private int getTotalExperiencePoints(ServerPlayer player) {
        int xpForLevel = getCumulativeXpNeededForLevel(player.experienceLevel);
        int xpForProgress = (int) Math.floor(player.experienceProgress * getXpNeededForNextLevel(player.experienceLevel));
        return xpForLevel + xpForProgress;
    }

    private int getXpNeededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    private int getCumulativeXpNeededForLevel(int targetLevel) {
        int total = 0;
        for (int level = 0; level < targetLevel; level++) {
            total += getXpNeededForNextLevel(level);
        }
        return total;
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
        Vec3 playerCenter = new Vec3(playerPos.getX() + 0.5D, playerPos.getY() + 0.5D, playerPos.getZ() + 0.5D);

        for (int y = minY; y <= maxY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dz * dz) > radiusSq) continue;

                    BlockPos checkPos = new BlockPos(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!isWaystoneBlock(level, checkPos, false)) continue;

                    BlockPos bottom = checkPos;
                    while (isWaystoneBlock(level, bottom.below(), false)) {
                        bottom = bottom.below();
                    }

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
        Optional<BlockPos> waystoneBottom = findWaystoneBottom(level, targetPos, false);

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
        waystoneBottom = findWaystoneBottom(level, targetPos, true);
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
        Optional<Vec3> safe = findSafeTeleportPos(level, waystoneBottom);
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

        String displayName = chooseDisplayName(level, waystoneBottom, waypointName);

        Optional<Vec3> safeNow = findSafeTeleportPos(level, waystoneBottom);
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
    public void onServerTick(MinecraftServer server) {

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

                // Ender-pearl-like teleport sound (played at arrival).
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
        SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.note_block.chime"));
        if (se == null) se = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.note_block.bell"));
        if (se == null) se = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.note_block.pling"));
        if (se != null) {
            player.playNotifySound(se, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    private void playTeleportSoundAtArrival(ServerPlayer player) {
        SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.enderman.teleport"));
        if (se != null) {
            player.serverLevel().playSound(null, player.blockPosition(), se, SoundSource.PLAYERS, 0.9f, 1.0f);
        }
    }

    private String chooseDisplayName(ServerLevel level, BlockPos waystoneBottom, String waypointName) {
        if (waypointName != null) {
            String cleaned = waypointName.trim();
            if (!cleaned.isBlank() && !"{name}".equals(cleaned)) {
                if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                }
                if (!cleaned.isBlank()) return cleaned;
            }
        }
        return getWaystoneNameSafe(level, waystoneBottom);
    }

    private String getWaystoneNameSafe(ServerLevel level, BlockPos waystoneBottom) {
        // Default if we can't extract a custom name.
        String fallback = "Waystone";

        try {
            BlockEntity be = level.getBlockEntity(waystoneBottom);
            if (be == null) be = level.getBlockEntity(waystoneBottom.above());
            if (be == null) return fallback;

            // Common patterns across Waystones versions (try a few via reflection)
            for (String mName : new String[]{"getName", "getDisplayName", "getWaystoneName", "getCustomName"}) {
                Method m = be.getClass().getMethod(mName);
                Object r = m.invoke(be);
                if (r instanceof Component c) {
                    String s = c.getString();
                    if (s != null && !s.isBlank()) return s;
                }
                if (r instanceof String s) {
                    if (!s.isBlank()) return s;
                }
            }
        } catch (Throwable ignored) {
        }

        return fallback;
    }

    /**
     * Resolves the Waystones database entry for a physical Waystone block.
     *
     * Destination chunks can be loaded just enough for the block and block entity to
     * exist before Waystones has run its block-entity onLoad initialization. During
     * that window, blockEntity.getWaystone() can still return an invalid placeholder
     * that cannot be used for XP cost calculation.
     *
     * A regular Waystone has block entities in both halves. Waystones may update the
     * shared database object's stored position from either half as they load, so a valid
     * Waystone can report either the lower or upper block position. A valid object read
     * directly from the requested block entity is therefore trusted once its dimension
     * matches. Database fallback matching accepts either half of the same structure.
     */
    private Object getWaystoneObject(ServerLevel level, BlockPos waystoneBottom) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(waystoneBottom);
            if (blockEntity == null) {
                blockEntity = level.getBlockEntity(waystoneBottom.above());
            }
            if (blockEntity != null) {
                Object localWaystone = blockEntity.getClass().getMethod("getWaystone").invoke(blockEntity);
                if (isUsableLocalWaystone(localWaystone, level)) {
                    return localWaystone;
                }
            }
        } catch (Throwable ignored) {
        }

        return findRegisteredWaystone(level, waystoneBottom);
    }

    private Object findRegisteredWaystone(ServerLevel level, BlockPos waystoneBottom) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }

        try {
            Class<?> apiClass = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            for (Method method : apiClass.getMethods()) {
                if (!method.getName().equals("getAllWaystones")) continue;

                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || !params[0].isInstance(server)) continue;

                Object result = method.invoke(null, server);
                if (!(result instanceof Stream<?> stream)) {
                    continue;
                }

                try (stream) {
                    return stream
                            .filter(waystone -> isRegisteredWaystoneAt(waystone, level, waystoneBottom))
                            .findFirst()
                            .orElse(null);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private boolean isUsableLocalWaystone(Object waystone, ServerLevel level) {
        if (waystone == null) {
            return false;
        }

        try {
            Method isValid = waystone.getClass().getMethod("isValid");
            Object validResult = isValid.invoke(waystone);
            if (!(validResult instanceof Boolean valid) || !valid) {
                return false;
            }

            Object storedDimension = waystone.getClass().getMethod("getDimension").invoke(waystone);
            return level.dimension().equals(storedDimension);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isRegisteredWaystoneAt(Object waystone, ServerLevel level, BlockPos waystoneBottom) {
        if (!isUsableLocalWaystone(waystone, level)) {
            return false;
        }

        try {
            Object storedPos = waystone.getClass().getMethod("getPos").invoke(waystone);
            return waystoneBottom.equals(storedPos) || waystoneBottom.above().equals(storedPos);
        } catch (Throwable ignored) {
            return false;
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
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (rl == null) return false;
        return "waystones".equals(rl.getNamespace()) && rl.getPath().contains("bound_scroll");
    }

    private boolean isWaystoneActivatedForPlayer(Player player, Level level, BlockPos pos) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null && isWaystoneBlock(level, pos.below())) {
                blockEntity = level.getBlockEntity(pos.below());
            }
            if (blockEntity == null) return false;

            Method getWaystone = blockEntity.getClass().getMethod("getWaystone");
            Object waystone = getWaystone.invoke(blockEntity);
            if (waystone == null) return false;

            // Prefer the stable public API first.
            try {
                Class<?> apiClass = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
                for (Method method : apiClass.getMethods()) {
                    if (!method.getName().equals("isWaystoneActivated")) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 2 && params[0].isAssignableFrom(player.getClass()) && params[1].isInstance(waystone)) {
                        Object result = method.invoke(null, player, waystone);
                        if (result instanceof Boolean b) return b;
                    }
                }
            } catch (Throwable ignored) {
            }

            Class<?> managerClass = Class.forName("net.blay09.mods.waystones.core.PlayerWaystoneManager");
            for (Method method : managerClass.getMethods()) {
                if (!method.getName().equals("isWaystoneActivated")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0].isAssignableFrom(player.getClass()) && params[1].isInstance(waystone)) {
                    Object result = method.invoke(null, player, waystone);
                    if (result instanceof Boolean b) return b;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static class WaystonesXpAccumulator {
        int points;
        int levels;
    }

    private static class FastTravelCost {
        final int xpPoints;
        final int levels;
        final boolean resolved;

        private FastTravelCost(int xpPoints, int levels, boolean resolved) {
            this.xpPoints = Math.max(0, xpPoints);
            this.levels = Math.max(0, levels);
            this.resolved = resolved;
        }

        static FastTravelCost free() {
            return new FastTravelCost(0, 0, true);
        }

        static FastTravelCost configuredLevels(int levels) {
            return new FastTravelCost(0, levels, true);
        }

        static FastTravelCost waystones(int xpPoints, int levels) {
            return new FastTravelCost(xpPoints, levels, true);
        }

        static FastTravelCost failed() {
            return new FastTravelCost(0, 0, false);
        }

        boolean isFree() {
            return resolved && xpPoints <= 0 && levels <= 0;
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
    private Optional<BlockPos> findWaystoneBottom(Level level, BlockPos targetPos) {
        return findWaystoneBottom(level, targetPos, true);
    }

    private Optional<BlockPos> findWaystoneBottom(Level level, BlockPos targetPos, boolean allowChunkLoad) {
        int radiusXZ = 1;
        int baseY = targetPos.getY();
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        for (int dy = -2; dy <= 2; dy++) {
            int y = baseY + dy;
            for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    checkPos.set(targetPos.getX() + dx, y, targetPos.getZ() + dz);
                    if (isWaystoneBlock(level, checkPos, allowChunkLoad)) {
                        // Walk down to bottom of the 2-block waystone.
                        BlockPos bottom = checkPos.immutable();
                        while (isWaystoneBlock(level, bottom.below(), allowChunkLoad)) {
                            bottom = bottom.below();
                        }
                        return Optional.of(bottom);
                    }
                }
            }
        }

        // Also handle the common case where the waypoint is on the TOP block.
        BlockPos belowTarget = targetPos.below();
        if (isWaystoneBlock(level, belowTarget, allowChunkLoad)) {
            BlockPos bottom = belowTarget;
            while (isWaystoneBlock(level, bottom.below(), allowChunkLoad)) {
                bottom = bottom.below();
            }
            return Optional.of(bottom);
        }

        return Optional.empty();
    }

    private boolean isWaystoneBlock(Level level, BlockPos pos) {
        return isWaystoneBlock(level, pos, true);
    }

    private boolean isWaystoneBlock(Level level, BlockPos pos, boolean allowChunkLoad) {
        if (level.isOutsideBuildHeight(pos)) return false;

        // getBlockState() on an unloaded server chunk can synchronously load/wait for that chunk.
        // hasChunkAt() lets detection/interception paths avoid causing ServerChunkCache.waitForTasks().
        if (!allowChunkLoad && !level.hasChunkAt(pos)) return false;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        // Cache per Block instance so repeated scans do not repeatedly ask the registry.
        return WAYSTONE_BLOCK_CACHE.computeIfAbsent(state.getBlock(), block -> {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
            return rl != null && "waystones".equals(rl.getNamespace()) && rl.getPath().contains("waystone");
        });
    }

    private static final Direction[] SIDE_ORDER = new Direction[]{
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /**
     * Preferred behavior:
     *  1) Try adjacent spots at waystone level (needs 2 blocks free + standable floor)
     *  2) Try standing on top of a 1-block-high neighbor (feet at adjacent+1)
     *  3) Try above the waystone (feet at bottom+2)
     *  4) If no floor exists at that level, try 1-2 blocks lower (still 2 blocks free + floor)
     *  5) Fallback: allow spots with 2 blocks free even without a floor (prevents "false no space")
     */
    private Optional<Vec3> findSafeTeleportPos(ServerLevel level, BlockPos waystoneBottom) {
        // 1) Same level with floor
        Optional<Vec3> same = findAdjacent(level, waystoneBottom, true, new int[]{0});
        if (same.isPresent()) return same;

        // 2) On top of a 1-block-high neighbor (lets players stand on a block next to the waystone)
        Optional<Vec3> atopNeighbor = findAdjacentTop(level, waystoneBottom);
        if (atopNeighbor.isPresent()) return atopNeighbor;

        // 3) Above waystone (two blocks above bottom, because waystone is 2 tall)
        // Prefer this BEFORE placing players next to floorless drop-offs.
        BlockPos aboveFeet = waystoneBottom.above(2);
        if (isTwoTallFree(level, aboveFeet, true)) {
            return Optional.of(centerFeet(aboveFeet));
        }

        // 4) Lower levels with floor (up to 2 blocks down)
        Optional<Vec3> lower = findAdjacent(level, waystoneBottom, true, new int[]{-1, -2});
        if (lower.isPresent()) return lower;

        // 5) Fallback without floor requirement (same -> lower -> above)
        Optional<Vec3> sameNoFloor = findAdjacent(level, waystoneBottom, false, new int[]{0, -1, -2});
        if (sameNoFloor.isPresent()) return sameNoFloor;

        if (isTwoTallFree(level, aboveFeet, false)) {
            return Optional.of(centerFeet(aboveFeet));
        }

        return Optional.empty();
    }

    /**
     * Allows teleporting onto a single block adjacent to the waystone, e.g. if there's a 1-block step.
     * Example: if the block next to the waystone is solid, we can place the player on top of it.
     */
    private Optional<Vec3> findAdjacentTop(ServerLevel level, BlockPos waystoneBottom) {
        for (Direction dir : SIDE_ORDER) {
            BlockPos neighbor = waystoneBottom.relative(dir);
            BlockPos feet = neighbor.above();
            // Require a floor (the neighbor block) and 2 blocks of headroom at the feet position.
            if (isTwoTallFree(level, feet, true)) {
                return Optional.of(centerFeet(feet));
            }
        }
        return Optional.empty();
    }

    private Optional<Vec3> findAdjacent(ServerLevel level, BlockPos waystoneBottom, boolean requireFloor, int[] yOffsets) {
        for (int yOff : yOffsets) {
            for (Direction dir : SIDE_ORDER) {
                BlockPos feet = waystoneBottom.relative(dir).offset(0, yOff, 0);
                if (isTwoTallFree(level, feet, requireFloor)) {
                    return Optional.of(centerFeet(feet));
                }
            }
        }
        return Optional.empty();
    }

    private boolean isTwoTallFree(ServerLevel level, BlockPos feet, boolean requireFloor) {
        if (!isNonColliding(level, feet)) return false;
        if (!isNonColliding(level, feet.above())) return false;

        if (requireFloor) {
            BlockPos floorPos = feet.below();
            BlockState floor = level.getBlockState(floorPos);
            // Waystone tops may not report as "sturdy" even though you can stand on them.
            // Allow standing on the waystone itself (used for the "above waystone" case).
            if (!floor.isFaceSturdy(level, floorPos, Direction.UP) && !isWaystoneBlock(level, floorPos)) return false;
        }

        return true;
    }

    private boolean isNonColliding(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private Vec3 centerFeet(BlockPos feet) {
        return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
    }

    private Double parseCoord(String s) {
        try {
            // Xaero uses absolute numbers here, but be permissive with decimals.
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

}
