/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role.anime.chino;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.role.anime.AnimeRoles;
import org.agmas.noellesroles.role_data.innocence.ChinoRoleData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 卡布奇诺咖啡师「把兔兔抱到头顶」的服务端状态机。
 *
 * <p>
 * 右键带兔兔修饰符的玩家即把对方抱到自己头顶，最多 {@link #MAX_RIDE_TICKS} tick；
 * 期间乘客免窒息伤害（{@code positionRider} 直接写入坐标会被载具顶进天花板），
 * 载具自己的客户端还需要定期补发乘客列表包（原版不会发给载具本人）。
 *
 * <p>
 * 同时只允许乘坐 1 只兔兔：{@link #tryMount} 校验载具头顶必须为空，
 * 混入的 {@code canAddPassenger} 也只对登记过的那一对放行。
 *
 * <p>
 * 状态以<b>载具 UUID</b> 为键存在静态表里而不是挂在玩家实体上，
 * 因此咖啡师死亡、退出、跨维度或兔兔掉线都能在这一处收尾。
 * 所有结束路径（超时 / 潜行下骑 / 技能放下 / 载具丢失）都把兔兔放到咖啡师身前
 * （{@link RoleUtils#placeInFrontOf}，无空位则与其重合）。
 */
public final class ChinoHeadRideManager {

    /** 最多乘骑 60 秒（1200 tick）。 */
    public static final int MAX_RIDE_TICKS = GameConstants.getInTicks(1, 0);
    /** 成功抱人后的冷却 75 秒（1500 tick）。 */
    public static final int RIDE_COOLDOWN_TICKS = GameConstants.getInTicks(1, 15);
    /** 兔兔自己按潜行提前下来时，冷却缩短为 10 秒（200 tick）。 */
    public static final int EARLY_DISMOUNT_COOLDOWN_TICKS = GameConstants.getInTicks(0, 10);
    /** 放下兔兔时，在咖啡师身前搜索空位的最大距离。 */
    public static final double RELEASE_PLACE_DISTANCE = 1.5D;
    /** 骑乘期间向载具客户端补发乘客列表包的间隔（tick）。 */
    private static final int PASSENGER_RESYNC_INTERVAL = 20;

    /** 玩家模型站立高度（未缩放），用来把乘客放到载具的视觉头顶。 */
    public static final float PLAYER_VISUAL_HEIGHT = 1.8F;

    /** no_collide 的续期余量：剩余时长低于它才重新挂一次。 */
    private static final int NO_COLLIDE_MARGIN = 40;

    /** 载具 UUID -> 骑乘状态。 */
    private static final Map<UUID, RideState> RIDES = new ConcurrentHashMap<>();
    /** 正在被抱着的兔兔 UUID（O(1) 判定，供 mixin、免窒息伤害与唯一性校验使用）。 */
    private static final Set<UUID> CARRIED_RIDERS = ConcurrentHashMap.newKeySet();

    private ChinoHeadRideManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ChinoHeadRideManager::serverTick);
        // 开局与结束都清空乘骑状态（先把还骑着的兔兔放下来，再清表）
        GameInitializeEvent.EVENT.register((level, game, players) -> clearRides(level));
        OnGameEnd.EVENT.register((level, game) -> clearRides(level));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof Player player) || !source.is(DamageTypes.IN_WALL)) {
                return true;
            }
            // 头顶乘客的坐标由载具的 positionRider 直接写入并穿过方块，会被顶进天花板；
            // 这并非乘客自己卡墙，故免除窒息伤害；溺水等其它伤害照常。
            return !isCarriedRider(player);
        });
    }

    /**
     * 清空乘骑状态与冷却。
     * <p>
     * 必须先把还骑着的兔兔放下来再清表，否则状态没了而上骑关系还在，
     * 会变成「兔兔还在头上却查不到状态」（按技能键提示头上没有兔兔）。
     *
     * @param level 用于解析玩家；为 null 时退回 {@link SRE#SERVER}
     */
    public static void clearRides(@Nullable Level level) {
        MinecraftServer server = level instanceof ServerLevel serverLevel ? serverLevel.getServer() : SRE.SERVER;
        if (server != null) {
            for (RideState state : RIDES.values()) {
                ServerPlayer rider = server.getPlayerList().getPlayer(state.rider);
                if (rider == null) {
                    continue;
                }
                if (rider.getVehicle() != null) {
                    rider.stopRiding();
                }
                removeOurNoCollide(rider);
            }
            // 冷却也存在职业数据里，一并清掉
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RoleData.ifPresent(ChinoRoleData.class, player, ChinoRoleData::clearRideCooldown);
            }
        }
        RIDES.clear();
        CARRIED_RIDERS.clear();
    }

    /**
     * 兔兔自己提前下来时，把咖啡师正在走的冷却改成
     * {@link #EARLY_DISMOUNT_COOLDOWN_TICKS}（10 秒）。
     * <p>
     * 只在冷却还没走完时改，避免凭空给一个不在冷却的人加冷却、也避免把更短的冷却变长。
     */
    private static void shortenCooldownForEarlyDismount(ServerPlayer chino) {
        ChinoRoleData roleData = RoleData.getNullable(ChinoRoleData.class, chino);
        if (roleData == null) {
            return;
        }
        long left = roleData.getRideCooldownLeft();
        if (left > EARLY_DISMOUNT_COOLDOWN_TICKS) {
            roleData.startRideCooldown(EARLY_DISMOUNT_COOLDOWN_TICKS);
        }
    }

    /**
     * 续期兔兔身上的 no_collide。
     * <p>
     * 挂载时已经给了覆盖整段骑乘的时长，所以正常情况下这里不会发包；
     * 只有会议/时停让骑乘计时暂停、而效果按真实 tick 走完时才会补挂一次。
     */
    private static void keepNoCollide(ServerPlayer rider, long endTick, long now) {
        MobEffectInstance instance = rider.getEffect(ModEffects.NO_COLLIDE);
        if (instance != null && instance.getDuration() > NO_COLLIDE_MARGIN) {
            return;
        }
        int duration = (int) Math.max(20L,
                Math.min(MAX_RIDE_TICKS + NO_COLLIDE_MARGIN, endTick - now + NO_COLLIDE_MARGIN));
        applyNoCollide(rider, duration);
    }

    /**
     * 骑乘期间给兔兔挂 no_collide：否则它与咖啡师会互相碰撞推挤，挡住咖啡师跳跃。
     * 隐藏粒子与图标，避免骑乘者看到莫名的效果。
     */
    private static void applyNoCollide(ServerPlayer rider, int duration) {
        rider.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, duration, 0, true, false, false));
    }

    /** 只清掉本机制加的那一份（时长不超过骑乘窗口），避免误删别的系统给的 no_collide。 */
    private static void removeOurNoCollide(ServerPlayer rider) {
        MobEffectInstance instance = rider.getEffect(ModEffects.NO_COLLIDE);
        if (instance != null && instance.getDuration() <= MAX_RIDE_TICKS + NO_COLLIDE_MARGIN) {
            rider.removeEffect(ModEffects.NO_COLLIDE);
        }
    }

    /**
     * 乘客挂点 Y：让乘客的脚正好落在载具的视觉头顶。
     * <p>
     * {@code Entity#positionRider} 最终把乘客放在
     * {@code 载具Y + 挂点Y - 乘客自身 getVehicleAttachmentPoint(载具).y}，
     * 而玩家的 VEHICLE 挂点不为 0，所以要把乘客那一段加回来，否则乘客会陷进载具身体里。
     * 载具的视觉头顶按 {@code 1.8 * 载具缩放} 计（玩家模型高度乘以 SCALE 属性），
     * 算法与双子叠罗汉一致。
     */
    public static double headPassengerAttachmentY(Player vehicle, Entity passenger) {
        return PLAYER_VISUAL_HEIGHT * vehicle.getScale() + passenger.getVehicleAttachmentPoint(vehicle).y;
    }

    /** 该玩家是否正被抱在别人头顶。 */
    public static boolean isCarriedRider(@Nullable Entity player) {
        if (player == null) {
            return false;
        }
        return CARRIED_RIDERS.contains(player.getUUID()) || isRidingChino(player, player.getVehicle());
    }

    /** 该乘客是否正被指定载具抱着（供挂点 / 站位混入精确放行）。 */
    public static boolean isCarriedBy(@Nullable Entity passenger, @Nullable Entity vehicle) {
        if (passenger == null || vehicle == null) {
            return false;
        }
        RideState state = RIDES.get(vehicle.getUUID());
        if (state != null && state.rider.equals(passenger.getUUID())) {
            return true;
        }
        // 客户端没有登记表，只能靠同步下来的“乘客骑着这个玩家”+ 载具职业判定，
        // 与上面服务端的登记结果保持一致。
        return isRidingChino(passenger, vehicle);
    }

    /** 该乘客是否正骑着一名卡布奇诺咖啡师（两端都可见的判定）。 */
    private static boolean isRidingChino(@Nullable Entity passenger, @Nullable Entity vehicle) {
        if (!(vehicle instanceof Player vehiclePlayer) || vehicle.isRemoved()) {
            return false;
        }
        if (passenger == null || passenger.getVehicle() != vehicle) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(vehiclePlayer.level()).isRole(vehiclePlayer, AnimeRoles.KAFU_CHINO);
    }

    /**
     * 可以被抱到头顶的目标：带兔兔修饰符的玩家（原设定），
     * 或矮小（TINY）/ 侏儒（DWARF）修饰符的玩家。
     * <p>
     * 三个判定要分开调用：{@code isPlayerTheModifier} 传多个修饰符时要求同时具备。
     */
    public static boolean isRideableTarget(@Nullable Player player) {
        return player != null && (RoleUtils.isPlayerTheModifier(player, NRModifiers.RABBIT_SHAPE)
                || RoleUtils.isPlayerTheModifier(player, SEModifiers.TINY)
                || RoleUtils.isPlayerTheModifier(player, TraitorAndModifiers.DWARF));
    }

    /** 该玩家头顶是否正抱着兔兔（客户端没有登记表，用同步下来的乘客+职业判定）。 */
    public static boolean isCarrying(@Nullable Player vehicle) {
        if (vehicle == null) {
            return false;
        }
        if (RIDES.containsKey(vehicle.getUUID())) {
            return true;
        }
        return isRidingChino(vehicle.getFirstPassenger(), vehicle);
    }

    /**
     * 尝试把兔兔抱到咖啡师头顶。
     *
     * @return 是否真的抱起来了（冷却中、头顶已有人、对方已在骑乘等情况返回 false 并提示）
     */
    public static boolean tryMount(ServerPlayer chino, ServerPlayer rabbit) {
        if (chino == null || rabbit == null || chino == rabbit) {
            return false;
        }
        if (!SREGameWorldComponent.getInstance(chino).isRunning()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(chino) || !GameUtils.isPlayerAliveAndSurvival(rabbit)
                || chino.level() != rabbit.level()) {
            return false;
        }

        long now = GameUtils.getTicksFromGameStart(chino.level());
        ChinoRoleData roleData = RoleData.getNullable(ChinoRoleData.class, chino);
        long cooldownLeft = roleData == null ? 0L : roleData.getRideCooldownLeft();
        if (cooldownLeft > 0) {
            chino.displayClientMessage(
                    Component.translatable("message.noellesroles.chino_head_ride.cooldown",
                            (cooldownLeft + 19) / 20).withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        // 同时只允许乘坐 1 只兔兔
        if (!chino.getPassengers().isEmpty() || RIDES.containsKey(chino.getUUID())
                || CARRIED_RIDERS.contains(rabbit.getUUID()) || rabbit.isPassenger()) {
            chino.displayClientMessage(
                    Component.translatable("message.noellesroles.chino_head_ride.busy")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }

        // 先登记再上骑：挂点与人数限制的混入要靠这张表认出这一对
        RideState state = new RideState(rabbit.getUUID(), now + MAX_RIDE_TICKS, chino);
        RIDES.put(chino.getUUID(), state);
        CARRIED_RIDERS.add(rabbit.getUUID());
        if (!rabbit.startRiding(chino, true)) {
            RIDES.remove(chino.getUUID());
            CARRIED_RIDERS.remove(rabbit.getUUID());
            chino.displayClientMessage(
                    Component.translatable("message.noellesroles.chino_head_ride.busy")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        if (roleData != null) {
            // 冷却记在职业数据里，客户端 HUD 才能显示剩余秒数
            roleData.startRideCooldown(RIDE_COOLDOWN_TICKS);
        }

        // 一次骑乘只挂一次 no_collide（时长覆盖整段骑乘），避免逐 tick 刷效果包
        applyNoCollide(rabbit, MAX_RIDE_TICKS + NO_COLLIDE_MARGIN);
        broadcastPassengers(chino);
        chino.displayClientMessage(
                Component.translatable("message.noellesroles.chino_head_ride.mounted", rabbit.getName())
                        .withStyle(ChatFormatting.GREEN),
                true);
        rabbit.displayClientMessage(
                Component.translatable("message.noellesroles.chino_head_ride.rider", chino.getName())
                        .withStyle(ChatFormatting.YELLOW),
                true);
        return true;
    }

    /**
     * 技能：把头顶的兔兔放到身前。
     *
     * @return 是否真的放下了兔兔（头上没兔兔时返回 false，调用方据此不消耗冷却）
     */
    public static boolean forceRelease(ServerPlayer chino) {
        if (chino == null) {
            return false;
        }
        UUID vehicleId = chino.getUUID();
        RideState state = RIDES.get(vehicleId);
        ServerPlayer rider = state != null ? chino.server.getPlayerList().getPlayer(state.rider) : null;
        if (rider == null && chino.getFirstPassenger() instanceof ServerPlayer passenger
                && isRideableTarget(passenger)) {
            // 状态因故丢失（例如兔兔中途死亡变旁观）时，仍然允许把头顶的人放下；
            // 限定可抱目标，避免误伤骑在头上的其它机制（幻灵等）
            rider = passenger;
        }
        if (state == null && rider == null) {
            return false;
        }
        release(vehicleId, state, chino, rider, true);
        return true;
    }

    private static void serverTick(MinecraftServer server) {
        if (RIDES.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, RideState> entry : RIDES.entrySet()) {
            UUID vehicleId = entry.getKey();
            RideState state = entry.getValue();
            ServerPlayer vehicle = server.getPlayerList().getPlayer(vehicleId);
            ServerPlayer rider = server.getPlayerList().getPlayer(state.rider);

            // 兔兔掉线/实体被移除：清掉位置缓存与乘骑状态，不传送，但要把残留的上骑关系解除
            if (rider == null || rider.isRemoved()) {
                if (vehicle != null && !vehicle.isRemoved()) {
                    ejectPassengerOfUuid(vehicle, state.rider);
                    broadcastPassengers(vehicle);
                }
                RIDES.remove(vehicleId);
                CARRIED_RIDERS.remove(state.rider);
                continue;
            }
            // 兔兔已死亡/变旁观：只清理状态，不传送（但要清掉载具客户端上的乘客渲染）
            if (!GameUtils.isPlayerAliveAndSurvival(rider)) {
                release(vehicleId, state, vehicle, rider, false);
                continue;
            }
            // 咖啡师退出：放到其最后所在位置
            if (vehicle == null || vehicle.isRemoved()) {
                release(vehicleId, state, null, rider, true);
                continue;
            }
            // 咖啡师死亡 / 换维度 / 游戏结束
            if (vehicle.level() != rider.level() || !GameUtils.isPlayerAliveAndSurvival(vehicle)
                    || !SREGameWorldComponent.getInstance(vehicle).isRunning()) {
                release(vehicleId, state, vehicle, rider, true);
                continue;
            }

            long now = GameUtils.getTicksFromGameStart(rider.level());
            state.rememberVehiclePos(vehicle);
            ejectUnregisteredPassengers(vehicle, state);

            // 兔兔主动下骑（按潜行，MC 默认逻辑）：结束乘骑并把咖啡师的冷却缩短为 10 秒
            if (rider.getVehicle() != vehicle) {
                shortenCooldownForEarlyDismount(vehicle);
                release(vehicleId, state, vehicle, rider, true);
                continue;
            }
            // 骑满 60 秒（或其它脱离方式）
            if (now >= state.endTick) {
                release(vehicleId, state, vehicle, rider, true);
                continue;
            }
            // 原版不会把乘客列表发给载具自己的客户端，定期补发避免头顶乘客消失
            if (now % PASSENGER_RESYNC_INTERVAL == 0) {
                vehicle.connection.send(new ClientboundSetPassengersPacket(vehicle));
            }
            // 兔兔身上的 no_collide 只在快过期时补一次（会议暂停会让骑乘计时比效果更晚结束）
            keepNoCollide(rider, state.endTick, now);
        }
    }

    /**
     * 结束乘骑：先把人放下来，再广播乘客列表（顺序反了载具客户端会继续渲染头顶乘客）。
     *
     * @param state      乘骑状态，状态丢失时可为 null（此时没有缓存位置）
     * @param vehicle    咖啡师，已退出/被移除时传 null，此时改用缓存的最后位置落点
     * @param rider      兔兔，已掉线/被移除时传 null，此时只清状态
     * @param placeRider 是否把兔兔放到咖啡师身前（死亡/旁观等情况下不传送）
     */
    private static void release(UUID vehicleId, @Nullable RideState state, @Nullable ServerPlayer vehicle,
            @Nullable ServerPlayer rider, boolean placeRider) {
        RIDES.remove(vehicleId);
        if (state != null) {
            CARRIED_RIDERS.remove(state.rider);
        }
        if (rider != null) {
            CARRIED_RIDERS.remove(rider.getUUID());
        }
        // 无论因为什么结束（包括兔兔死亡变旁观、咖啡师死亡后重生换了实体）都必须真的把人放下来。
        // 咖啡师重生会让乘客手里的 vehicle 指向已被移除的旧实体，所以这里也接受“载具已消失”的情况。
        if (rider != null && rider.getVehicle() != null
                && (vehicle == null || rider.getVehicle() == vehicle || rider.getVehicle().isRemoved())) {
            rider.stopRiding();
        }
        if (vehicle != null) {
            broadcastPassengers(vehicle);
        }
        if (rider != null) {
            removeOurNoCollide(rider);
        }
        if (rider == null || !placeRider) {
            return;
        }

        if (vehicle != null && vehicle.level() == rider.level()) {
            RoleUtils.placeInFrontOf(vehicle, rider, RELEASE_PLACE_DISTANCE);
        } else if (state != null && state.hasVehiclePos) {
            // 咖啡师已不在场：放到它最后所在的位置（等效于与咖啡师重合）
            rider.teleportTo(rider.serverLevel(), state.lastVehicleX, state.lastVehicleY, state.lastVehicleZ,
                    Set.of(), rider.getYRot(), rider.getXRot());
        }
        rider.setDeltaMovement(Vec3.ZERO);
        rider.fallDistance = 0.0F;
        rider.displayClientMessage(
                Component.translatable("message.noellesroles.chino_head_ride.released")
                        .withStyle(ChatFormatting.YELLOW),
                true);
    }

    /** 只允许同时乘坐 1 只兔兔：踢掉登记之外的玩家乘客。 */
    private static void ejectUnregisteredPassengers(ServerPlayer vehicle, RideState state) {
        List<Entity> passengers = vehicle.getPassengers();
        if (passengers.size() <= 1) {
            return;
        }
        boolean ejected = false;
        for (Entity passenger : List.copyOf(passengers)) {
            if (!(passenger instanceof Player) || passenger.getUUID().equals(state.rider)) {
                continue;
            }
            passenger.stopRiding();
            ejected = true;
        }
        if (ejected) {
            broadcastPassengers(vehicle);
        }
    }

    /** 把指定 UUID 的残留乘客从载具身上摘下来（玩家实体已被移除时也能生效）。 */
    private static void ejectPassengerOfUuid(ServerPlayer vehicle, UUID passengerId) {
        for (Entity passenger : List.copyOf(vehicle.getPassengers())) {
            if (passenger.getUUID().equals(passengerId)) {
                passenger.stopRiding();
            }
        }
    }

    /**
     * 原版只把乘客列表同步给追踪载具的玩家，载具自己的客户端收不到，
     * 服务端发起的下骑也不会到达，所以这里广播给所有人。
     */
    private static void broadcastPassengers(ServerPlayer vehicle) {
        ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(vehicle);
        for (ServerPlayer viewer : vehicle.server.getPlayerList().getPlayers()) {
            viewer.connection.send(packet);
        }
    }

    /** 一次乘骑的状态。咖啡师的位置会被逐 tick 缓存，用于其退出后的落点。 */
    private static final class RideState {
        private final UUID rider;
        private final long endTick;
        private double lastVehicleX;
        private double lastVehicleY;
        private double lastVehicleZ;
        private boolean hasVehiclePos;

        private RideState(UUID rider, long endTick, ServerPlayer vehicle) {
            this.rider = rider;
            this.endTick = endTick;
            rememberVehiclePos(vehicle);
        }

        private void rememberVehiclePos(ServerPlayer vehicle) {
            this.lastVehicleX = vehicle.getX();
            this.lastVehicleY = vehicle.getY();
            this.lastVehicleZ = vehicle.getZ();
            this.hasVehiclePos = true;
        }
    }
}
