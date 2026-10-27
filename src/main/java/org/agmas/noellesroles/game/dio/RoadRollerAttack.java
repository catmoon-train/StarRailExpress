package org.agmas.noellesroles.game.dio;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.RoadRollerS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.DIORoleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 压路机服务端流程；使用服务器 tick，时停冻结世界后也照常推进。 */
public final class RoadRollerAttack {
    private static final Map<UUID, Attack> ACTIVE = new ConcurrentHashMap<>();

    private RoadRollerAttack() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(RoadRollerS2CPacket.ID, RoadRollerS2CPacket.CODEC);
        ServerTickEvents.START_SERVER_TICK.register(server -> ACTIVE.values().forEach(Attack::pin));
        ServerTickEvents.END_SERVER_TICK.register(RoadRollerAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.player, true));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Attack attack : new ArrayList<>(ACTIVE.values())) {
                finish(attack, true, true);
            }
        });
        OnGameEnd.EVENT.register((level, game) -> {
            for (Attack attack : new ArrayList<>(ACTIVE.values())) {
                if (attack.level == level) {
                    // 结算阶段只恢复物理标志，不能覆盖游戏系统安排的结算传送。
                    finish(attack, false, true);
                }
            }
            broadcast(level, new RoadRollerS2CPacket(new UUID(0, 0), RoadRollerS2CPacket.CLEAR,
                    Vec3.ZERO, 0, 0, 0, 0, 0, 0, 0));
        });
    }

    public static ShopEntry shopEntry() {
        return new ShopEntry(ModItems.ROAD_ROLLER.getDefaultInstance(), 200, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                DIORoleData data = RoleData.getNullable(DIORoleData.class, player);
                if (data == null || !SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.DIO)) {
                    setFailedMessage(Component.translatable("message.noellesroles.road_roller.only_dio"));
                    return false;
                }
                if (data.roadRollerBuyCooldown > 0) {
                    setFailedMessage(Component.translatable("message.noellesroles.road_roller.buy_cooldown",
                            (data.roadRollerBuyCooldown + 19) / 20));
                    return false;
                }
                setFailedMessage(null);
                return super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                if (!canBuy(player) || !super.onBuy(player)) {
                    return false;
                }
                DIORoleData data = RoleData.getNullable(DIORoleData.class, player);
                data.roadRollerBuyCooldown = RoadRollerMotion.BUY_COOLDOWN;
                data.sync();
                return true;
            }
        };
    }

    public static boolean isActive(UUID player) {
        return ACTIVE.containsKey(player);
    }

    private static boolean ownsTimeStop(ServerPlayer player) {
        return TimeStopEffect.freezeTime > 0 && player.hasEffect(ModEffects.TIME_STOP)
                && player.getUUID().equals(TimeStopEffect.timeStopOwner);
    }

    public static boolean tryStart(ServerPlayer player) {
        var game = SREGameWorldComponent.KEY.get(player.level());
        DIORoleData data = RoleData.getNullable(DIORoleData.class, player);
        if (data == null || !game.isRole(player, ModRoles.DIO)) {
            return fail(player, "only_dio");
        }
        if (!game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player) || isActive(player.getUUID())) {
            return false;
        }
        if (!ownsTimeStop(player)) {
            return fail(player, "own_time_stop");
        }
        if (data.roadRollerUseCooldown > 0) {
            player.displayClientMessage(Component.translatable("message.noellesroles.road_roller.use_cooldown",
                    (data.roadRollerUseCooldown + 19) / 20).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (TimeStopEffect.freezeTime < 8 || player.isPassenger() || data.isFeeding) {
            return fail(player, "not_ready");
        }
        ServerLevel level = player.serverLevel();
        AABB area = AreasWorldComponent.KEY.get(level).getPlayArea();
        Vec3 origin = player.position();
        if (origin.y < area.minY || origin.y >= area.maxY) {
            return fail(player, "no_floor");
        }
        double feetY = RoadRollerMotion.raisedFeetY(origin.y, area.maxY, level.getMaxBuildHeight(), player.getBbHeight());
        double startY = feetY - RoadRollerMotion.HEIGHT;
        double floor = findLowestFloor(level, origin, area);
        if (!Double.isFinite(floor) || floor >= startY || feetY <= origin.y) {
            return fail(player, "no_floor");
        }
        int duration = Math.min(40, TimeStopEffect.freezeTime - 2);
        Attack attack = new Attack(player, origin, startY, floor, Math.min(10, duration / 4), duration);
        data.roadRollerAttacking = true;
        ACTIVE.put(player.getUUID(), attack);
        player.stopUsingItem();
        player.setSprinting(false);
        attack.pin();
        data.roadRollerUseCooldown = RoadRollerMotion.USE_COOLDOWN;
        player.getCooldowns().addCooldown(ModItems.ROAD_ROLLER, RoadRollerMotion.USE_COOLDOWN);
        data.sync();
        broadcast(level, attack.packet(RoadRollerS2CPacket.START));
        level.playSound(null, origin.x, feetY, origin.z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 2.0F, 0.55F);
        SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(), Noellesroles.id("road_roller"), false);
        return true;
    }

    /** 从地图底边向上找真正有碰撞的地板，跳过空气与装饰，且不扫地图外的模板。 */
    private static double findLowestFloor(ServerLevel level, Vec3 origin, AABB area) {
        int low = Math.max(level.getMinBuildHeight(), Mth.floor(area.minY));
        int high = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(origin.y));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = low; y <= high; y++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (dx * dx + dz * dz > 25) continue;
                    pos.set(Mth.floor(origin.x) + dx, y, Mth.floor(origin.z) + dz);
                    if (pos.getX() < area.minX || pos.getX() >= area.maxX
                            || pos.getZ() < area.minZ || pos.getZ() >= area.maxZ || !level.hasChunkAt(pos)) continue;
                    var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        return Math.max(area.minY + 0.05, y + shape.max(net.minecraft.core.Direction.Axis.Y));
                    }
                }
            }
        }
        return Double.NaN;
    }

    private static boolean fail(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable("message.noellesroles.road_roller." + suffix)
                .withStyle(ChatFormatting.RED), true);
        return false;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DIORoleData data = RoleData.getNullable(DIORoleData.class, player);
            if (data == null) continue;
            boolean ready = data.roadRollerBuyCooldown == 1 || data.roadRollerUseCooldown == 1;
            if (data.roadRollerBuyCooldown > 0) data.roadRollerBuyCooldown--;
            if (data.roadRollerUseCooldown > 0) data.roadRollerUseCooldown--;
            if (ready) {
                if (data.roadRollerUseCooldown == 0) player.getCooldowns().removeCooldown(ModItems.ROAD_ROLLER);
                data.sync();
            }
        }
        for (Attack attack : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer player = attack.player;
            var game = SREGameWorldComponent.KEY.get(attack.level);
            if (player.isRemoved() || player.level() != attack.level || !game.isRunning()
                    || !game.isRole(player, ModRoles.DIO) || !GameUtils.isPlayerAliveAndSurvival(player)
                    || !ownsTimeStop(player) || !attack.data.roadRollerAttacking) {
                boolean returnToOrigin = attack.data.roadRollerAttacking && !player.isRemoved() && game.isRunning()
                        && RoleData.getNullable(DIORoleData.class, player) == attack.data
                        && GameUtils.isPlayerAliveAndSurvival(player);
                finish(attack, returnToOrigin, false);
                continue;
            }
            double previous = attack.y;
            attack.age++;
            attack.y = RoadRollerMotion.rollerY(attack.startY, attack.endY, attack.age, attack.hold, attack.duration);
            attack.pin();
            if (attack.age > attack.hold) {
                crush(attack, previous, attack.y);
            }
            if (RoadRollerMotion.isFinished(attack.age, attack.duration)) {
                attack.level.playSound(null, attack.origin.x, attack.endY, attack.origin.z,
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.5F, 0.5F);
                ParticleFx.sendCustom(attack.level, Noellesroles.id("road_roller_impact"),
                        new Vec3(attack.origin.x, attack.endY, attack.origin.z), 0, 5.0F);
                finish(attack, true, false);
            }
        }
    }

    private static void crush(Attack attack, double fromY, double toY) {
        for (ServerPlayer target : new ArrayList<>(attack.level.players())) {
            if (target == attack.player || attack.hit.contains(target.getUUID())
                    || !GameUtils.isPlayerAliveAndSurvival(target)) continue;
            AABB box = target.getBoundingBox();
            if (!RoadRollerMotion.intersects(attack.origin.x, attack.origin.z,
                    fromY + RoadRollerMotion.HEIGHT / 2, toY + RoadRollerMotion.HEIGHT / 2,
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) continue;
            attack.hit.add(target.getUUID());
            Vec3 deathPos = target.position();
            GameUtils.killPlayer(target, true, attack.player, GameConstants.DeathReasons.BOULDER_CRUSH);
            if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                attack.level.playSound(null, deathPos.x, deathPos.y, deathPos.z,
                        SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.4F, 0.6F);
                ParticleFx.burst(attack.level, ParticleTypes.CRIT, deathPos.x, deathPos.y + 1, deathPos.z,
                        32, 0.6, 0.8, 0.6, 0.2);
                ParticleFx.sendCustom(attack.level, Noellesroles.id("road_roller_impact"), deathPos, 0, 2.0F);
            }
        }
    }

    private static void cancel(ServerPlayer player, boolean teleport) {
        Attack attack = ACTIVE.get(player.getUUID());
        if (attack != null) finish(attack, teleport, false);
    }

    private static void finish(Attack attack, boolean teleport, boolean clear) {
        if (!ACTIVE.remove(attack.player.getUUID(), attack)) return;
        ServerPlayer player = attack.player;
        player.noPhysics = attack.oldNoPhysics;
        player.setNoGravity(attack.oldNoGravity);
        player.setPose(attack.oldPose);
        attack.data.roadRollerAttacking = false;
        attack.data.sync();
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        if (teleport && player.level() == attack.level && !player.isRemoved()) {
            player.teleportTo(attack.level, attack.origin.x, attack.origin.y, attack.origin.z, attack.yaw, attack.pitch);
        }
        int phase = clear ? RoadRollerS2CPacket.CLEAR
                : teleport ? RoadRollerS2CPacket.RETURN : RoadRollerS2CPacket.FINISH;
        broadcast(attack.level, attack.packet(phase));
    }

    private static void broadcast(ServerLevel level, RoadRollerS2CPacket packet) {
        for (ServerPlayer viewer : level.players()) {
            ServerPlayNetworking.send(viewer, packet);
        }
    }

    private static final class Attack {
        final ServerPlayer player;
        final DIORoleData data;
        final ServerLevel level;
        final Vec3 origin;
        final double startY;
        final double endY;
        final float yaw;
        final float pitch;
        final boolean oldNoPhysics;
        final boolean oldNoGravity;
        final Pose oldPose;
        final int hold;
        final int duration;
        final Set<UUID> hit = new HashSet<>();
        double y;
        int age;

        Attack(ServerPlayer player, Vec3 origin, double startY, double endY, int hold, int duration) {
            this.player = player;
            this.level = player.serverLevel();
            this.origin = origin;
            this.startY = this.y = startY;
            this.data = RoleData.getNullable(DIORoleData.class, player);
            this.endY = endY;
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.oldNoPhysics = player.noPhysics;
            this.oldNoGravity = player.isNoGravity();
            this.oldPose = player.getPose();
            this.hold = hold;
            this.duration = duration;
        }

        void pin() {
            if (player.isRemoved() || player.level() != level || !data.roadRollerAttacking
                    || RoleData.getNullable(DIORoleData.class, player) != data || !ownsTimeStop(player)) return;
            player.noPhysics = true;
            player.setNoGravity(true);
            player.fallDistance = 0;
            player.setDeltaMovement(Vec3.ZERO);
            player.setPose(Pose.CROUCHING);
            player.teleportTo(level, origin.x, y + RoadRollerMotion.HEIGHT, origin.z, yaw, 30.0F);
        }

        RoadRollerS2CPacket packet(int phase) {
            return new RoadRollerS2CPacket(player.getUUID(), phase, origin, startY, endY, yaw, pitch, hold, duration, age);
        }
    }
}
