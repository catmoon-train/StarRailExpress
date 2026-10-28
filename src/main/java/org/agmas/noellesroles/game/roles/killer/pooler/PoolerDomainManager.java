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

package org.agmas.noellesroles.game.roles.killer.pooler;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.ParticleFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 祈月者（{@code pooler}）的两个领域：
 * <ul>
 * <li>{@link Kind#MOON 吞月} —— 半径 8 格内除自己以外的所有玩家被拉上<b>月球</b>，
 * 领域标记为 4 级（amplifier 3），持续 {@value #DURATION_TICKS} tick。</li>
 * <li>{@link Kind#AQUARIUM 水中镜} —— 半径 6 格内除杀手阵营与魔术师以外（但包含自己）的玩家
 * 被拉进一只<b>大水缸</b>，领域标记为 5 级（amplifier 4），持续 {@value #DURATION_TICKS} tick；
 * 祈月者需要在水缸里杀光被拉进来的人，只剩自己活着时领域立即结束。</li>
 * </ul>
 * 实现方式与咒术师的灰髓之境一致（{@code WarlockDomainManager}）：
 * 场景在虚空高空现搭、领域状态放在静态表里、由全局 server tick 驱动，
 * 中途掉线 / 游戏结束都会强制收场，绝不把玩家留在虚空里。
 * <b>坐标约定：所有领域场景只沿 Z 轴（主世界水平方向）排布</b>，X 固定为 0.5，
 * Y 统一取 200（虚空高空），彼此靠 Z 拉开距离：
 * <pre>
 * 咒术师灰髓之境  X = 0.5, Y = 200, Z = -20000.5
 * 愚者塔罗会      X = 0.5, Y = 200, Z = +20000.5
 * 冒险家游记      X = 0.5, Y = 200, Z = +21000.5
 * 祈月者·水中镜   X = 0.5, Y = 200, Z = +22000.5   （水缸半径 14）
 * 祈月者·月球     X = 0.5, Y = 200, Z = +23000.5   （月面半径 18）
 * </pre>
 * <p>
 * <b>Z 必须 ≥ 19000</b>：{@code SREGameWorldComponent#playerEnvironmentDetection}
 * 只对 {@code player.getZ() < 19000} 的玩家做环境检测，越界即按
 * {@code FELL_OUT_OF_TRAIN}（列车碾压）击杀。把领域放在 +22000 / +23000
 * 就能天然跳过这套检测，不需要往玩家身上加任何豁免效果。
 * （咒术师放在 -20000.5 之所以没事，是因为它另外给玩家挂了
 * {@code NOSTALGIST_BACKWORLD} 走里世界豁免；祈月者不走那条路。）
 */
public final class PoolerDomainManager {

    /** 领域中心的统一 X / Y：只沿 Z 轴排布，X 固定 0.5，Y 固定 200。 */
    private static final double DOMAIN_X = 0.5D;
    private static final double DOMAIN_Y = 200.0D;

    /**
     * 月球领域的 Z。
     * <p>
     * 必须 ≥ 19000，否则玩家一进去就被环境检测判为「掉出列车」而碾压致死。
     */
    public static final double MOON_Z = 23000.5D;
    /** 水中镜（水缸）领域的 Z（同样必须 ≥ 19000）。 */
    public static final double TANK_Z = 22000.5D;

    /** 领域持续时间（30 秒）。 */
    public static final int DURATION_TICKS = 30 * 20;

    /** 吞月：拉人半径。 */
    public static final double MOON_RADIUS = 8.0D;
    /** 水中镜：拉人半径。 */
    public static final double AQUARIUM_RADIUS = 6.0D;

    /** 领域标记等级（amplifier）：吞月 = 4 级，水中镜 = 5 级。 */
    public static final int MOON_MARK_LEVEL = 3;
    public static final int AQUARIUM_MARK_LEVEL = 4;

    /** 活动范围（越界即被拉回）。 */
    private static final int MOON_BOUND_RADIUS = 17;
    private static final int MOON_BOUND_HEIGHT = 9;
    private static final int TANK_BOUND_RADIUS = 13;
    private static final int TANK_BOUND_HEIGHT = 13;

    private static final Map<UUID, ActiveDomain> ACTIVE = new HashMap<>();
    private static boolean hooksRegistered;

    /** 领域种类。 */
    public enum Kind {
        /** 吞月：月球领域。 */
        MOON,
        /** 水中镜：大水缸领域。 */
        AQUARIUM
    }

    private PoolerDomainManager() {
    }

    /** 玩家原来的位置（领域结束后原路返回）。 */
    public record ReturnPos(double x, double y, double z, float yaw, float pitch) {
    }

    private static final class ActiveDomain {
        final UUID caster;
        final Kind kind;
        final ServerLevel level;
        final long endTick;
        final Map<UUID, ReturnPos> returnPositions = new HashMap<>();
        /** 需要在本领域里被杀光的玩家（水中镜用；吞月只用来统计）。 */
        final Set<UUID> victims = new LinkedHashSet<>();

        ActiveDomain(UUID caster, Kind kind, ServerLevel level, long endTick) {
            this.caster = caster;
            this.kind = kind;
            this.level = level;
            this.endTick = endTick;
        }
    }

    /** 在职业初始化阶段调用一次，挂全局 tick 与游戏结束清理钩子。 */
    public static void register() {
        if (hooksRegistered)
            return;
        hooksRegistered = true;
        ServerTickEvents.END_SERVER_TICK.register(PoolerDomainManager::tickAll);
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> returnAllPlayer(serverLevel));
    }

    // ── 查询 ────────────────────────────────────────────────────

    /** 该玩家是否是某个祈月者领域的主人。 */
    public static boolean isCasting(UUID casterUuid) {
        return ACTIVE.containsKey(casterUuid);
    }

    /** 某个玩家是否正在某个祈月者领域里（作为参与者）。 */
    public static boolean isInDomain(UUID playerUuid) {
        for (ActiveDomain domain : ACTIVE.values()) {
            if (domain.returnPositions.containsKey(playerUuid))
                return true;
        }
        return false;
    }

    /** 指定玩家所在的祈月者领域种类；不在任何祈月者领域里返回 {@code null}。 */
    public static Kind domainKindOf(UUID playerUuid) {
        for (ActiveDomain domain : ACTIVE.values()) {
            if (domain.returnPositions.containsKey(playerUuid))
                return domain.kind;
        }
        return null;
    }

    /** 当前是否有水中镜（水缸）领域存在 —— 只有此时三叉戟击杀不进入物品冷却。 */
    public static boolean isAnyAquariumActive() {
        for (ActiveDomain domain : ACTIVE.values()) {
            if (domain.kind == Kind.AQUARIUM)
                return true;
        }
        return false;
    }

    // ── 展开领域 ────────────────────────────────────────────────

    /**
     * 展开「吞月」：把半径 8 格内除祈月者以外的所有存活玩家拉上月球。
     *
     * @return 成功展开返回 true；已有领域 / 时间暂停 / 没有人可拉时返回 false（不消耗冷却）
     */
    public static boolean openMoon(ServerPlayer caster) {
        if (ACTIVE.containsKey(caster.getUUID()))
            return false;
        ServerLevel level = caster.serverLevel();
        if (GameUtils.isTimeFrozen(caster.level()))
            return false;

        List<ServerPlayer> targets = new ArrayList<>();
        AABB area = caster.getBoundingBox().inflate(MOON_RADIUS);
        for (ServerPlayer candidate : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (candidate == caster || candidate.isSpectator())
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(candidate))
                continue;
            if (candidate.distanceTo(caster) > MOON_RADIUS)
                continue;
            // 领域庇护（摘星者光环）的持有者无法被拉入任何领域
            if (ModEffects.hasDomainSanctuary(candidate)) {
                candidate.displayClientMessage(Component
                        .translatable("message.noellesroles.domain.sanctuary_hold")
                        .withStyle(ChatFormatting.GOLD), true);
                continue;
            }
            targets.add(candidate);
        }
        if (targets.isEmpty()) {
            fail(caster, "message.noellesroles.pooler.domain_no_target");
            return false;
        }

        new PoolerDomainSceneBuilder(level).buildMoon(BlockPos.containing(DOMAIN_X, DOMAIN_Y, MOON_Z));
        ActiveDomain domain = new ActiveDomain(caster.getUUID(), Kind.MOON, level,
                GameUtils.getTicksFromGameStart(level) + DURATION_TICKS);

        // 施法者本人**不**被拉进月球：留在原地观察，领域结束后也不需要传送回来。
        // （之前 pullIn(caster) 会把祈月者一起拉进去，导致他也吃月面环境、也被碾压。）
        int index = 0;
        for (ServerPlayer target : targets) {
            double angle = Math.PI * 2.0D * index / Math.max(1, targets.size());
            double x = DOMAIN_X + Math.cos(angle) * 9.0D;
            double z = MOON_Z + Math.sin(angle) * 9.0D;
            domain.victims.add(target.getUUID());
            pullIn(domain, target, x, z, false);
            index++;
        }

        ACTIVE.put(caster.getUUID(), domain);

        level.playSound(null, BlockPos.containing(DOMAIN_X, DOMAIN_Y, MOON_Z),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.6F);
        ParticleFx.sendCustom(level, PoolerFx.MOON_ASCEND,
                net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.containing(DOMAIN_X, DOMAIN_Y + 1.0D, MOON_Z)),
                DURATION_TICKS, 12.0F, 1.0F);
        sendTitle(caster, "message.noellesroles.pooler.moon_title",
                "message.noellesroles.pooler.moon_subtitle", ChatFormatting.WHITE);
        caster.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.moon_open", targets.size())
                .withStyle(ChatFormatting.AQUA), true);
        recordDomain(caster, "replay.event.pooler.moon", targets.size());
        return true;
    }

    /**
     * 展开「水中镜」：把半径 6 格内除杀手阵营与魔术师以外的存活玩家（含祈月者自己）拉进大水缸。
     *
     * @return 成功展开返回 true；已有领域 / 时间暂停时返回 false（不消耗冷却）
     */
    public static boolean openAquarium(ServerPlayer caster) {
        if (ACTIVE.containsKey(caster.getUUID()))
            return false;
        ServerLevel level = caster.serverLevel();
        if (GameUtils.isTimeFrozen(caster.level()))
            return false;

        List<ServerPlayer> targets = new ArrayList<>();
        AABB area = caster.getBoundingBox().inflate(AQUARIUM_RADIUS);
        for (ServerPlayer candidate : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (candidate.isSpectator())
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(candidate))
                continue;
            if (candidate.distanceTo(caster) > AQUARIUM_RADIUS)
                continue;
            if (candidate != caster && isExcludedFromAquarium(caster, candidate))
                continue;
            // 领域庇护（摘星者光环）的持有者无法被拉入任何领域；祈月者自己始终可以进去
            if (candidate != caster && ModEffects.hasDomainSanctuary(candidate)) {
                candidate.displayClientMessage(Component
                        .translatable("message.noellesroles.domain.sanctuary_hold")
                        .withStyle(ChatFormatting.GOLD), true);
                continue;
            }
            targets.add(candidate);
        }

        new PoolerDomainSceneBuilder(level).buildAquarium(BlockPos.containing(DOMAIN_X, DOMAIN_Y, TANK_Z));
        ActiveDomain domain = new ActiveDomain(caster.getUUID(), Kind.AQUARIUM, level,
                GameUtils.getTicksFromGameStart(level) + DURATION_TICKS);

        // 祈月者落在水缸中央的水面附近，其余人被均匀分布在水里
        pullIn(domain, caster, DOMAIN_X, TANK_Z, true);
        int index = 0;
        for (ServerPlayer target : targets) {
            if (target == caster)
                continue;
            double angle = Math.PI * 2.0D * index
                    / Math.max(1, targets.size() - 1);
            double x = DOMAIN_X + Math.cos(angle) * 8.0D;
            double z = TANK_Z + Math.sin(angle) * 8.0D;
            domain.victims.add(target.getUUID());
            pullIn(domain, target, x, z, false);
            index++;
        }

        ACTIVE.put(caster.getUUID(), domain);

        level.playSound(null, BlockPos.containing(DOMAIN_X, DOMAIN_Y, TANK_Z),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.8F);
        ParticleFx.sendCustom(level, PoolerFx.AQUARIUM_OPEN,
                net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.containing(DOMAIN_X, DOMAIN_Y + 4.0D, TANK_Z)),
                DURATION_TICKS, 14.0F, 0.0F);
        sendTitle(caster, "message.noellesroles.pooler.aquarium_title",
                "message.noellesroles.pooler.aquarium_subtitle", ChatFormatting.AQUA);
        caster.displayClientMessage(Component
                .translatable("message.noellesroles.pooler.aquarium_open", domain.victims.size())
                .withStyle(ChatFormatting.AQUA), true);
        recordDomain(caster, "replay.event.pooler.aquarium", domain.victims.size());
        return true;
    }

    /**
     * 水中镜的排除规则：杀手阵营（{@link io.wifi.starrailexpress.api.SRERole#canUseKiller()}）
     * 与魔术师不会被拉进水缸。
     */
    private static boolean isExcludedFromAquarium(ServerPlayer caster, ServerPlayer candidate) {
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(caster.level());
        if (gameWorld.isRole(candidate, ModRoles.MAGICIAN))
            return true;
        var role = gameWorld.getRole(candidate);
        return role != null && role.canUseKiller();
    }

    // ── 传送进出 ────────────────────────────────────────────────

    private static void pullIn(ActiveDomain domain, ServerPlayer player, double x, double z, boolean isCaster) {
        player.stopSleeping();
        player.stopRiding();
        domain.returnPositions.put(player.getUUID(), new ReturnPos(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

        double centerX = DOMAIN_X;
        double centerY = DOMAIN_Y;
        double centerZ = domain.kind == Kind.MOON ? MOON_Z : TANK_Z;

        float yaw = (float) (Math.atan2(centerZ - z, centerX - x) * 180.0D / Math.PI) - 90.0F;
        // 水缸里的落点在水面之下（水中镜必须在水里打），月球则站在地表
        double spawnY = domain.kind == Kind.MOON
                ? centerY
                : centerY + PoolerDomainSceneBuilder.TANK_WATER_LEVEL - 3.0D;
        player.teleportTo(domain.level, x, spawnY, z, Set.of(), yaw, 0.0F);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;

        player.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 20, 0, false, false, false));
        int markLevel = domain.kind == Kind.MOON ? MOON_MARK_LEVEL : AQUARIUM_MARK_LEVEL;
        // 领域标记：3 = 吞月（4 级），4 = 水中镜（5 级）。除标记外不额外施加减益 / 增益效果。
        player.addEffect(new MobEffectInstance(ModEffects.DOMAIN_MARK, -1, markLevel, false, false, true));
        if (!isCaster) {
            sendTitle(player, "message.noellesroles.pooler.moon_title",
                    "message.noellesroles.pooler.moon_subtitle", ChatFormatting.WHITE);
        }
    }

    private static void sendTitle(ServerPlayer player, String titleKey, String subtitleKey,
            ChatFormatting color) {
        Component title = Component.translatable(titleKey).withStyle(color, ChatFormatting.BOLD);
        Component subtitle = Component.translatable(subtitleKey).withStyle(ChatFormatting.GRAY);
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
    }

    private static void recordDomain(ServerPlayer caster, String replayKey, int victims) {
        try {
            SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable(replayKey,
                    GameReplayUtils.getReplayPlayerDisplayText(caster, true), victims));
        } catch (Throwable ignored) {
            // 回放系统不可用时不影响技能本身
        }
    }

    // ── 每 tick 维护 ────────────────────────────────────────────

    private static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty())
            return;
        if (GameUtils.isTimeFrozen(server))
            return;
        for (UUID casterUuid : new ArrayList<>(ACTIVE.keySet())) {
            ActiveDomain domain = ACTIVE.get(casterUuid);
            if (domain == null)
                continue;
            tick(domain, server);
        }
    }

    private static void tick(ActiveDomain domain, MinecraftServer server) {
        ServerLevel level = domain.level;
        long gameTime = level.getGameTime();
        long now = GameUtils.getTicksFromGameStart(level);
        ServerPlayer caster = server.getPlayerList().getPlayer(domain.caster);

        boolean casterDown = caster == null || !GameUtils.isPlayerAliveAndSurvival(caster);
        if (now >= domain.endTick || casterDown) {
            end(domain, server, casterDown);
            return;
        }

        // 水中镜：被拉进来的人全死光（只剩祈月者）→ 立即结束
        if (domain.kind == Kind.AQUARIUM) {
            boolean victimsAlive = domain.victims.stream()
                    .map(uuid -> server.getPlayerList().getPlayer(uuid))
                    .anyMatch(p -> p != null && GameUtils.isPlayerAliveAndSurvival(p));
            if (!victimsAlive) {
                end(domain, server, false);
                return;
            }
        }

        AABB bounds = boundsOf(domain.kind);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            item.discard();
        }
        for (UUID uuid : domain.returnPositions.keySet()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
            if (participant != null && GameUtils.isPlayerAliveAndSurvival(participant)) {
                GameUtils.limitPlayerToBox(participant, bounds);
            }
        }

        if (gameTime % 10 == 0) {
            double cx = DOMAIN_X;
            double cy = DOMAIN_Y;
            double cz = domain.kind == Kind.MOON ? MOON_Z : TANK_Z;
            if (domain.kind == Kind.MOON) {
                level.sendParticles(ParticleTypes.END_ROD, cx, cy + 4.0D, cz, 20, 12.0D, 3.0D, 12.0D, 0.01D);
                level.sendParticles(ParticleTypes.WHITE_ASH, cx, cy + 2.0D, cz, 15, 10.0D, 1.5D, 10.0D, 0.005D);
            } else {
                level.sendParticles(ParticleTypes.BUBBLE, cx, cy + 6.0D, cz, 30, 8.0D, 3.0D, 8.0D, 0.02D);
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, cx, cy + 1.0D, cz, 6, 2.0D, 1.0D, 2.0D, 0.01D);
            }
        }
    }

    private static void end(ActiveDomain domain, MinecraftServer server, boolean brokenByDeath) {
        ACTIVE.remove(domain.caster);
        ServerLevel level = domain.level;

        // 先释放领域标记，再把人送回去，避免残留
        for (Map.Entry<UUID, ReturnPos> entry : domain.returnPositions.entrySet()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(entry.getKey());
            if (participant == null || participant.isSpectator())
                continue;
            ReturnPos pos = entry.getValue();
            participant.teleportTo(level, pos.x(), pos.y(), pos.z(), Set.of(), pos.yaw(), pos.pitch());
            participant.setDeltaMovement(0.0D, 0.0D, 0.0D);
            participant.fallDistance = 0.0F;
            participant.removeEffect(ModEffects.DOMAIN_MARK);
            participant.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 12, 0, false, false, false));
            participant.displayClientMessage(Component
                    .translatable(brokenByDeath
                            ? "message.noellesroles.pooler.domain_broken"
                            : "message.noellesroles.pooler.domain_end")
                    .withStyle(ChatFormatting.GRAY), true);
        }

        // 域内产生的尸体送回死者原位，避免留在虚空泄露信息
        for (PlayerBodyEntity body : level.getEntities(TMMEntities.PLAYER_BODY,
                boundsOf(domain.kind).inflate(8.0D), e -> true)) {
            UUID owner = body.getPlayerUuid();
            ReturnPos pos = owner == null ? null : domain.returnPositions.get(owner);
            if (pos != null) {
                body.teleportTo(pos.x(), pos.y(), pos.z());
            }
        }

        level.playSound(null, BlockPos.containing(
                DOMAIN_X,
                DOMAIN_Y,
                domain.kind == Kind.MOON ? MOON_Z : TANK_Z),
                SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** 强制结束指定祈月者的领域（游戏结束 / 组件清理时调用）。 */
    public static void forceEnd(UUID casterUuid, MinecraftServer server) {
        ActiveDomain domain = ACTIVE.get(casterUuid);
        if (domain != null) {
            end(domain, server, false);
        }
    }

    /** 把所有领域清空（游戏结束时调用）。 */
    public static void returnAllPlayer(ServerLevel serverLevel) {
        MinecraftServer server = serverLevel.getServer();
        if (server == null)
            return;
        for (UUID caster : new ArrayList<>(ACTIVE.keySet())) {
            forceEnd(caster, server);
        }
    }

    /**
     * 领域活动范围。
     * <p>
     * 下沿必须紧贴地板（Y = DOMAIN_Y 就是站立面，地板在 -1），否则
     * {@code GameUtils.limitPlayerToBox} 允许玩家掉到 {@code minY - 20} 的虚空，
     * 一落地就被列车判定碾压。这里把下沿抬到 DOMAIN_Y，只留 1 格容差。
     */
    private static AABB boundsOf(Kind kind) {
        if (kind == Kind.MOON) {
            return new AABB(
                    DOMAIN_X - MOON_BOUND_RADIUS, DOMAIN_Y, MOON_Z - MOON_BOUND_RADIUS,
                    DOMAIN_X + MOON_BOUND_RADIUS, DOMAIN_Y + MOON_BOUND_HEIGHT, MOON_Z + MOON_BOUND_RADIUS);
        }
        return new AABB(
                DOMAIN_X - TANK_BOUND_RADIUS, DOMAIN_Y, TANK_Z - TANK_BOUND_RADIUS,
                DOMAIN_X + TANK_BOUND_RADIUS, DOMAIN_Y + TANK_BOUND_HEIGHT, TANK_Z + TANK_BOUND_RADIUS);
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
