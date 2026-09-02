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

package org.agmas.noellesroles.game.roles.killer.warlock;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import org.agmas.noellesroles.role_data.killer.WarlockRoleData;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 咒术师「领域展开·灰髓之境」管理器。
 *
 * 领域是位于高空虚空（{@link #DOMAIN_X}, {@link #DOMAIN_Y}, {@link #DOMAIN_Z}，
 * 与愚者塔罗会分处列车两端）的一片灰雾祭场：咒术师消耗至多
 * {@value #MAX_VICTIMS} 份咒物，把咒物主人连同<b>自己本体</b>一并拉入其中
 * （参考愚者的灰雾之上，但不留傀儡本体）。域内所有人蒙受灰白滤镜
 * （{@link ModEffects#NOSTALGIST_BACKWORLD}），咒术师获得迅捷；
 * {@value #DURATION_TICKS} tick 后领域自然消散，所有存活者原路返还，
 * 域内产生的尸体也会被送回死者原位。<b>在域内杀死咒术师会立刻破界。</b>
 *
 * 领域状态保存在静态表中并由全局 server tick 驱动，
 * 咒术师中途掉线 / 游戏结束都会强制收场，绝不把玩家留在虚空。
 */
public final class WarlockDomainManager {

    public static final double DOMAIN_X = 0.5D;
    public static final double DOMAIN_Y = 200.0D;
    public static final double DOMAIN_Z = -20000.5D;
    /** 领域持续时间。 */
    public static final int DURATION_TICKS = 25 * 20;
    /** 一次最多拉入的咒物主人数量。 */
    public static final int MAX_VICTIMS = 1;
    /** 领域活动范围（越界即被拉回）。 */
    private static final int BOUND_RADIUS = 15;
    private static final int BOUND_HEIGHT = 10;

    private static final Map<UUID, ActiveDomain> ACTIVE = new HashMap<>();
    private static boolean hooksRegistered;

    private WarlockDomainManager() {
    }

    private record ReturnPos(double x, double y, double z, float yaw, float pitch) {
    }

    private static final class ActiveDomain {
        final UUID warlock;
        final ServerLevel level;
        final long endTick;
        final Map<UUID, ReturnPos> returnPositions = new HashMap<>();
        final Set<UUID> victims = new LinkedHashSet<>();

        ActiveDomain(UUID warlock, ServerLevel level, long endTick) {
            this.warlock = warlock;
            this.level = level;
            this.endTick = endTick;
        }
    }

    /** 在职业初始化阶段调用一次，挂全局 tick 与游戏结束清理钩子。 */
    public static void register() {
        if (hooksRegistered)
            return;
        hooksRegistered = true;
        ServerTickEvents.END_SERVER_TICK.register(WarlockDomainManager::tickAll);
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            for (UUID warlock : new ArrayList<>(ACTIVE.keySet())) {
                forceEnd(warlock, serverLevel.getServer());
            }
        });
    }

    /**
     * 对指定目标展开领域（仅拉入这一人）。返回 false 表示条件不满足（不消耗冷却）。
     * 校验（角色/存活/冷却/目标处于诅咒中）由 {@link WarlockRoleData#tryOpenDomainOn} 负责。
     */
    public static boolean open(ServerPlayer warlock, WarlockRoleData comp, ServerPlayer victim) {
        if (ACTIVE.containsKey(warlock.getUUID()))
            return false;
        if (victim == null || !GameUtils.isPlayerAliveAndSurvival(victim)) {
            warlock.displayClientMessage(Component
                    .translatable("message.noellesroles.warlock.domain_no_victims")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ServerLevel level = warlock.serverLevel();

        new WarlockDomainSceneBuilder(level)
                .build(BlockPos.containing(DOMAIN_X, DOMAIN_Y, DOMAIN_Z));

        ActiveDomain domain = new ActiveDomain(warlock.getUUID(), level,
                GameUtils.getTicksFromGameStart(level) + DURATION_TICKS);

        // 咒术师站上祭坛中心，被诅咒者被拉到一侧
        pullIn(domain, warlock, DOMAIN_X, DOMAIN_Z, true);
        // 消耗该目标的诅咒 / 咒物
        comp.cursedPlayers.remove(victim.getUUID());
        comp.essences.remove(victim.getUUID());
        domain.victims.add(victim.getUUID());
        pullIn(domain, victim, DOMAIN_X + 8.0D, DOMAIN_Z, false);

        ACTIVE.put(warlock.getUUID(), domain);
        comp.domainOpen = true;
        comp.domainEndTick = domain.endTick;
        comp.sync();

        level.playSound(null, BlockPos.containing(DOMAIN_X, DOMAIN_Y, DOMAIN_Z),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.5F);
        warlock.displayClientMessage(Component
                .translatable("message.noellesroles.warlock.domain_open")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        // 回放记录：咒术师将玩家拉入角斗领域
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.warlock.pull_arena",
                        GameReplayUtils.getReplayPlayerDisplayText(warlock, true),
                        GameReplayUtils.getReplayPlayerDisplayText(victim, true)));
        return true;
    }

    private static void pullIn(ActiveDomain domain, ServerPlayer player, double x, double z, boolean isWarlock) {
        player.stopSleeping();
        player.stopRiding();
        domain.returnPositions.put(player.getUUID(), new ReturnPos(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

        float yaw = (float) (Math.atan2(DOMAIN_Z - z, DOMAIN_X - x) * 180.0D / Math.PI) - 90.0F;
        player.teleportTo(domain.level, x, DOMAIN_Y + 1.0D, z, Set.of(), yaw, 0.0F);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;

        // 转场黑屏 + 灰白滤镜
        player.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 20, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.NOSTALGIST_BACKWORLD, DURATION_TICKS + 40, 0,
                false, false, false));
        // 标记所处领域：咒术师角斗场领域 = 2 级（amplifier 1）
        player.addEffect(new MobEffectInstance(ModEffects.DOMAIN_MARK, -1, 1, false, false,
                true));
        if (isWarlock) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION_TICKS, 0,
                    false, false, false));
        } else {
            // 进入领域时目标获得黑暗 + 失明双重效果，并禁用技能
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DURATION_TICKS, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DURATION_TICKS, 0, false, false, true));
            player.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, DURATION_TICKS, 0, false, false, true));
            sendDomainTitle(player);
        }
    }

    private static void sendDomainTitle(ServerPlayer player) {
        Component title = Component.translatable("message.noellesroles.warlock.domain_title")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.warlock.domain_subtitle")
                .withStyle(ChatFormatting.GRAY);
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
    }

    private static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty())
            return;
        if (GameUtils.isTimeFrozen(server)) {
            return;
        }
        for (UUID warlockUuid : new ArrayList<>(ACTIVE.keySet())) {
            ActiveDomain domain = ACTIVE.get(warlockUuid);
            if (domain == null)
                continue;
            tick(domain, server);
        }
    }

    private static void tick(ActiveDomain domain, MinecraftServer server) {
        ServerLevel level = domain.level;
        long gameTime = level.getGameTime();
        long now = GameUtils.getTicksFromGameStart(level);
        ServerPlayer warlock = server.getPlayerList().getPlayer(domain.warlock);

        // 结束条件：时间到 / 咒术师死亡或掉线 / 所有猎物离场
        boolean warlockDown = warlock == null || !GameUtils.isPlayerAliveAndSurvival(warlock);
        boolean victimsGone = domain.victims.stream()
                .map(uuid -> server.getPlayerList().getPlayer(uuid))
                .noneMatch(p -> p != null && GameUtils.isPlayerAliveAndSurvival(p));
        if (now >= domain.endTick || warlockDown || victimsGone) {
            end(domain, server, warlockDown);
            return;
        }

        // 边界约束 + 氛围粒子
        AABB bounds = domainBounds();
        // bounds
        List<ItemEntity> items = server.overworld().getEntitiesOfClass(ItemEntity.class, bounds);
        if (items != null) {
            for (var t : items) {
                t.discard();
            }
        }

        for (UUID uuid : domain.returnPositions.keySet()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
            if (participant != null && GameUtils.isPlayerAliveAndSurvival(participant)) {
                GameUtils.limitPlayerToBox(participant, bounds);
            }
        }
        if (gameTime % 10 == 0) {
            level.sendParticles(ParticleTypes.ASH, DOMAIN_X, DOMAIN_Y + 3.0D, DOMAIN_Z, 30,
                    10.0D, 2.5D, 10.0D, 0.01D);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, DOMAIN_X, DOMAIN_Y + 1.2D, DOMAIN_Z, 2,
                    6.0D, 0.4D, 6.0D, 0.0D);
        }
    }

    private static void end(ActiveDomain domain, MinecraftServer server, boolean brokenByDeath) {
        ACTIVE.remove(domain.warlock);
        ServerLevel level = domain.level;

        for (Map.Entry<UUID, ReturnPos> entry : domain.returnPositions.entrySet()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(entry.getKey());
            ReturnPos pos = entry.getValue();
            if (participant != null && !participant.isSpectator()) {
                participant.teleportTo(level, pos.x(), pos.y(), pos.z(), Set.of(), pos.yaw(), pos.pitch());
                participant.setDeltaMovement(0.0D, 0.0D, 0.0D);
                participant.fallDistance = 0.0F;
                participant.removeEffect(ModEffects.NOSTALGIST_BACKWORLD);
                participant.removeEffect(ModEffects.DOMAIN_MARK);
                participant.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, 15, 0, false, false, false));
                participant.displayClientMessage(Component
                        .translatable(brokenByDeath
                                ? "message.noellesroles.warlock.domain_broken"
                                : "message.noellesroles.warlock.domain_end")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }

        // 域内产生的尸体送回死者原位，避免留在虚空泄露信息
        for (PlayerBodyEntity body : level.getEntities(TMMEntities.PLAYER_BODY,
                domainBounds().inflate(8.0D), e -> true)) {
            UUID owner = body.getPlayerUuid();
            ReturnPos pos = owner == null ? null : domain.returnPositions.get(owner);
            if (pos != null) {
                body.teleportTo(pos.x(), pos.y(), pos.z());
            }
        }

        ServerPlayer warlock = server.getPlayerList().getPlayer(domain.warlock);
        if (warlock != null) {
            WarlockRoleData comp = RoleData.getNullable(WarlockRoleData.class, warlock);
            if (comp != null) {
                comp.domainOpen = false;
                comp.domainEndTick = 0;
                comp.sync();
            }
        }
    }

    /** 强制结束指定咒术师的领域（游戏结束 / 组件清理时调用）。 */
    public static void forceEnd(UUID warlockUuid, MinecraftServer server) {
        ActiveDomain domain = ACTIVE.get(warlockUuid);
        if (domain != null) {
            end(domain, server, false);
        }
    }

    public static boolean isInDomain(UUID playerUuid) {
        for (ActiveDomain domain : ACTIVE.values()) {
            if (domain.returnPositions.containsKey(playerUuid))
                return true;
        }
        return false;
    }

    private static AABB domainBounds() {
        return new AABB(
                DOMAIN_X - BOUND_RADIUS, DOMAIN_Y - 2, DOMAIN_Z - BOUND_RADIUS,
                DOMAIN_X + BOUND_RADIUS, DOMAIN_Y + BOUND_HEIGHT, DOMAIN_Z + BOUND_RADIUS);
    }

    public static void returnAllPlayer(ServerLevel serverLevel) {
        for (UUID warlock : new ArrayList<>(ACTIVE.keySet())) {
            forceEnd(warlock, serverLevel.getServer());
        }
    }
}
