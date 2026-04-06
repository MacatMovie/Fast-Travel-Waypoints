package com.macat.waystonemap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.ChatFormatting;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

@Mod("fast_travel_waypoints")
public class WaystoneMapTpMod {

    private static final int COUNTDOWN_SECONDS = 3;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<UUID, PendingTeleport> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, PostTeleportFx> POST_FX = new ConcurrentHashMap<>();

    public WaystoneMapTpMod() {
        ModConfigs.register();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
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
    }

    /**
     * Intercept vanilla /tp (or /teleport) when it targets the executing player and
     * the destination is (near) a waystone. This fixes Xaero's "OP uses /tp" behavior.
     */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = source.getLevel();

        // Determine root command name ("tp" or "teleport")
        String rootName = event.getParseResults().getContext().getNodes().isEmpty()
                ? ""
                : event.getParseResults().getContext().getNodes().get(0).getNode().getName();
        if (!("tp".equals(rootName) || "teleport".equals(rootName))) return;

        // Grab raw command text (without leading slash in many cases)
        String raw = event.getParseResults().getReader().getString();
        if (raw == null || raw.isBlank()) return;

        // Tokenize for simple patterns. We only intercept:
        //   tp <x> <y> <z>
        //   tp @s <x> <y> <z>
        //   tp <playerName> <x> <y> <z>  (when playerName == executing player)
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 4) return;

        int idx = 1;
        if (parts.length >= 5) {
            String maybeTarget = parts[1];
            if ("@s".equalsIgnoreCase(maybeTarget) || player.getGameProfile().getName().equalsIgnoreCase(maybeTarget)) {
                idx = 2;
            }
        }

        if (parts.length < idx + 3) return;

        Double x = parseCoord(parts[idx]);
        Double y = parseCoord(parts[idx + 1]);
        Double z = parseCoord(parts[idx + 2]);
        if (x == null || y == null || z == null) return;

        BlockPos targetPos = BlockPos.containing(x, y, z);

        Optional<BlockPos> waystoneBottom = findWaystoneBottom(level, targetPos);
        if (waystoneBottom.isEmpty()) return;

        // Redirect vanilla /tp -> safe teleport to that waystone.
        event.setCanceled(true);
        if (!canStartFastTravel(player, level)) return;
        startWaystoneTeleportCountdown(player, level, waystoneBottom.get(), new Vec3(x, y, z), null);
    }

    
    private boolean canStartFastTravel(ServerPlayer player, ServerLevel level) {
        // Creative always bypasses restrictions (and also bypasses the countdown).
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

        // Level cost requirement
        int cost = ModConfigs.LEVEL_COST.get();
        if (cost > 0 && player.experienceLevel < cost) {
            player.sendSystemMessage(
                    Component.literal("Fast travel requires " + cost + " " + (cost == 1 ? "level" : "levels") + ".").withStyle(ChatFormatting.RED)
            );
            return false;
        }

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
        int minY = Math.max(level.getMinBuildHeight(), playerPos.getY() - Math.min(radius, 4));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, playerPos.getY() + Math.min(radius, 4));
        int radiusSq = radius * radius;

        for (int y = minY; y <= maxY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dz * dz) > radiusSq) continue;

                    BlockPos checkPos = new BlockPos(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!isWaystoneBlock(level, checkPos)) continue;

                    BlockPos bottom = checkPos;
                    while (isWaystoneBlock(level, bottom.below())) {
                        bottom = bottom.below();
                    }

                    Vec3 playerCenter = new Vec3(playerPos.getX() + 0.5D, playerPos.getY() + 0.5D, playerPos.getZ() + 0.5D);
                    Vec3 waystoneCenter = new Vec3(bottom.getX() + 0.5D, bottom.getY() + 0.5D, bottom.getZ() + 0.5D);
                    if (playerCenter.distanceToSqr(waystoneCenter) <= radiusSq) {
                        return true;
                    }
                }
            }
        }

        return false;
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

private int handleTeleport(ServerPlayer player, ServerLevel level, BlockPos targetPos, Vec3 exactPos, String waypointName) {
        Optional<BlockPos> waystoneBottom = findWaystoneBottom(level, targetPos);

        // If it's a waystone waypoint, ALWAYS use safe-teleport (even for OPs).
        if (waystoneBottom.isPresent()) {
            if (!canStartFastTravel(player, level)) {
            return 0;
        }
        startWaystoneTeleportCountdown(player, level, waystoneBottom.get(), exactPos, waypointName);
            return 1;
        }

        // Not a waystone: non-OPs get denied, OPs can teleport anywhere.
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("No Waystone at the selected waypoint."), true);
            return 0;
        }

        teleportPlayer(player, level, exactPos);
        return 1;
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

    private void completeTeleportWithFx(ServerPlayer player, ServerLevel level, BlockPos waystoneBottom, Vec3 fallbackExact) {
        safeTeleportToWaystoneNow(player, level, waystoneBottom, fallbackExact);
        POST_FX.put(player.getUUID(), new PostTeleportFx(4));
    }

    private void startWaystoneTeleportCountdown(ServerPlayer player, ServerLevel level, BlockPos waystoneBottom, Vec3 fallbackExact, String waypointName) {
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
            completeTeleportWithFx(player, level, waystoneBottom, fallbackExact);
            return;
        }

        PendingTeleport pending = new PendingTeleport(level.dimension().location().toString(), waystoneBottom, fallbackExact, displayName);
        PENDING.put(player.getUUID(), pending);

        // Immediate first title/sound (3s)
        sendCountdownTitle(player, displayName, COUNTDOWN_SECONDS);
        playSoftNote(player);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
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

                ServerLevel level = server.getLevel(player.level().dimension());
                if (level == null) level = player.serverLevel();

                // Consume XP levels right before teleport (if configured).
                int cost = ModConfigs.LEVEL_COST.get();
                if (cost > 0 && player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
                    if (player.experienceLevel < cost) {
                        player.displayClientMessage(
                                Component.literal("Fast travel requires " + cost + " " + (cost == 1 ? "level" : "levels") + ".").withStyle(ChatFormatting.RED),
                                true
                        );
                        continue;
                    }
                    player.giveExperienceLevels(-cost);
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

    private static class PendingTeleport {
        final String dim;
        final BlockPos waystoneBottom;
        final Vec3 fallbackExact;
        final String waystoneName;
        int ticksRemaining;

        PendingTeleport(String dim, BlockPos waystoneBottom, Vec3 fallbackExact, String waystoneName) {
            this.dim = dim;
            this.waystoneBottom = waystoneBottom;
            this.fallbackExact = fallbackExact;
            this.waystoneName = waystoneName;
            this.ticksRemaining = COUNTDOWN_SECONDS * TICKS_PER_SECOND;
        }
    }

    private static class PostTeleportFx {
        int ticks;
        PostTeleportFx(int ticks) { this.ticks = ticks; }
    }

    /**
     * Checks around the target position for a Waystone block.
     * We search a 3x3 area in X/Z (radius 1) and a small vertical range
     * from Y-2 to Y+1 to account for player height and fractional coords.
     */
    /**
     * Finds a waystone block near the waypoint and returns the BOTTOM blockpos of the 2-block waystone.
     */
    private Optional<BlockPos> findWaystoneBottom(Level level, BlockPos targetPos) {
        int radiusXZ = 1;
        int baseY = targetPos.getY();

        for (int dy = -2; dy <= 2; dy++) {
            int y = baseY + dy;
            for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    BlockPos checkPos = new BlockPos(targetPos.getX() + dx, y, targetPos.getZ() + dz);
                    if (isWaystoneBlock(level, checkPos)) {
                        // Walk down to bottom of the 2-block waystone
                        BlockPos bottom = checkPos;
                        while (isWaystoneBlock(level, bottom.below())) {
                            bottom = bottom.below();
                        }
                        return Optional.of(bottom);
                    }
                }
            }
        }

        // Also handle the common case where the waypoint is on the TOP block.
        if (isWaystoneBlock(level, targetPos.below())) {
            BlockPos bottom = targetPos.below();
            while (isWaystoneBlock(level, bottom.below())) {
                bottom = bottom.below();
            }
            return Optional.of(bottom);
        }

        return Optional.empty();
    }

    private boolean isWaystoneBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (rl == null) return false;
        return "waystones".equals(rl.getNamespace()) && rl.getPath().contains("waystone");
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