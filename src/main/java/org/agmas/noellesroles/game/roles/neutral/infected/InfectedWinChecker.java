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

package org.agmas.noellesroles.game.roles.neutral.infected;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 疫使胜利检测器
 * 使用纵火犯的逻辑来防止游戏结束
 */
public class InfectedWinChecker {

    private static boolean wasAccelerated = false; // 记录上一个tick的加速状态
    private static int tickCounter = 0; // tick 计数器，用于节流
    private static final int TICK_INTERVAL = 20; // 每20 tick（1秒）执行一次检查（原来每tick执行，减少95%）

    /*
     * 清除所有玩家的感染状态会在游戏resetPlayer时进行。
     */

    /**
     * 注册疫使胜利检测事件
     */
    public static void registerEvent() {
        // 胜利检测事件
        AllowGameEnd.EVENT.register((serverWorld, winStatus, isLooseEndsMode) -> {
            // 时间耗尽时疫使不阻止，判定乘客胜利
            if (winStatus == WinStatus.TIME) {
                return WinStatus.NOT_MODIFY;
            }

            var gameWorldComponent = SREGameWorldComponent.KEY.get(serverWorld);

            var players = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
            boolean infectedAlive = false;
            int infectedCount = 0;
            int totalAlive = players.size();
            int infectedInfectedCount = 0; // 被感染的非疫使玩家数量
            boolean hasDoctor = false, hasRobot = false;
            // 记录哪些是疫使
            ArrayList<ServerPlayer> inftectRolePlayers = new ArrayList<>();

            for (var player : players) {
                if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                    infectedAlive = true;
                    infectedCount++;
                    inftectRolePlayers.add(player);
                    continue;
                }
                if (gameWorldComponent.getRole(player) instanceof SRERole role && !role.canBePoisoned()) {
                    hasRobot = true;
                } else if (gameWorldComponent.isRole(player, ModRoles.DOCTOR)) {
                    hasDoctor = true;
                }
                // 检查玩家是否被感染
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
                if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                    if (!gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                        infectedInfectedCount++;
                    }
                }
            }
            if (!infectedAlive || !wasAccelerated) {
                // 没有疫使就别操控游戏了。
                return WinStatus.NOT_MODIFY;
            }

            // 只有疫使存活（必须进入疫使时刻才能胜利）
            if (totalAlive <= infectedCount) {
                // 疫使胜利 - 算作杀手胜利
                RoleUtils.customWinnerWin(serverWorld, WinStatus.KILLERS,
                        org.agmas.noellesroles.role.ModRoles.INFECTED.identifier().getPath(),
                        java.util.OptionalInt.of(org.agmas.noellesroles.role.ModRoles.INFECTED.color()));
                return WinStatus.KILLERS;
            }

            // 疫使存活且其他所有玩家都被感染（必须进入疫使时刻才能胜利）
            if (infectedInfectedCount >= totalAlive - infectedCount) {
                // 清除所有感染状态
                // 疫使胜利 - 算作杀手胜利
                RoleUtils.customWinnerWin(serverWorld, WinStatus.KILLERS,
                        org.agmas.noellesroles.role.ModRoles.INFECTED.identifier().getPath(),
                        java.util.OptionalInt.of(org.agmas.noellesroles.role.ModRoles.INFECTED.color()));
                return WinStatus.KILLERS;
            }
            // 没法结束且医生/glitch robot存活：杀死疫使。
            if (hasDoctor || hasRobot) {
                for (var player : inftectRolePlayers) {
                    GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.CANNOT_WIN);
                }
            }
            // 无需防止乘客胜利，疫使会在结算计算存活杀手数量时被看作杀手。
            return WinStatus.NOT_MODIFY;
        });

        // 服务器tick事件 - 检查疫使加速条件（节流：每20tick执行一次，从20次/秒降至1次/秒）
        // 触发条件：所有杀手全部阵亡 且 平民中没有医生
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 节流：每 TICK_INTERVAL 才执行一次，减少 95% 的 tick 开销
            tickCounter++;
            if (tickCounter < TICK_INTERVAL) {
                return;
            }
            tickCounter = 0;

            ServerLevel level = server.overworld();
            var gameWorldComponent = SREGameWorldComponent.KEY.maybeGet(level).orElse(null);
            if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
                return;
            }

            // 单次遍历完成所有检查（原来分4次遍历，现在合并为1次）

            ArrayList<ServerPlayer> inftectRolePlayers = new ArrayList<>();
            boolean hasInfected = false;
            boolean hasKiller = false;
            boolean hasDoctor = false;
            boolean hasLooseEnd = false;
            boolean hasSafeTime = false;

            for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                // 检查是否处于安全时间（游戏开始安全时间、阳光自选、职业轮抽的选择阶段）
                if (!hasSafeTime && player.hasEffect(ModEffects.SAFE_TIME)) {
                    hasSafeTime = true;
                }
                if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                    hasInfected = true;
                    inftectRolePlayers.add(player);
                    continue;
                }
                // 只检查真正的杀手阵营（isKiller），不含杀手方中立
                var role = gameWorldComponent.getRole(player);
                if (role != null && role.isKiller() && !hasKiller) {
                    hasKiller = true;
                }
                if (!hasDoctor && (gameWorldComponent.isRole(player, ModRoles.DOCTOR)
                        || (gameWorldComponent.getRole(player) instanceof SRERole r && !r.canBePoisoned()))) {
                    hasDoctor = true;
                }
                if (!hasLooseEnd && gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                    hasLooseEnd = true;
                }
            }

            if (!hasInfected) {
                // 检查疫使是否在鹈鹕肚子里——如果在肚子里，则不取消加速状态，等待释放后重新判断
                boolean infectedInPelicanBelly = false;
                for (ServerPlayer player : level.getPlayers(p -> true)) {
                    if (PelicanManager.isStashed(player) && gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                        infectedInPelicanBelly = true;
                        break;
                    }
                }
                if (infectedInPelicanBelly) {
                    return; // 疫使在鹈鹕肚子里，保持当前状态不变
                }

                // 没有疫使，取消加速
                if (wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                    wasAccelerated = false;
                }
                return;
            }

            // 检查触发条件：所有杀手已阵亡 且 没有医生 且 不处于亡命时刻 且 不处于安全时间
            boolean killersAllDead = !hasKiller;
            boolean shouldAccelerate = killersAllDead && !hasDoctor && !hasLooseEnd && !hasSafeTime;

            if (shouldAccelerate) {
                // 设置加速传播（病毒传染时间缩短至10秒）
                if (!wasAccelerated) {
                    // 有医生直接趋势，直接不管后面逻辑避免一堆神秘bug和性能消耗
                    if (hasDoctor) {
                        for (ServerPlayer p : inftectRolePlayers) {
                            GameUtils.forceKillPlayer(p, true, null, GameConstants.DeathReasons.CANNOT_WIN);
                            return;
                        }
                    }
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, true);
                    wasAccelerated = true;
                    // 同步加速状态到疫使玩家自身的组件（供客户端HUD读取）
                    for (ServerPlayer p : inftectRolePlayers) {
                        {
                            InfectedPlayerComponent comp = ModComponents.INFECTED.get(p);
                            comp.spreadAccelerated = true;
                            comp.sync();
                        }
                    }
                    // 全场播放疫使时刻音效
                    for (ServerPlayer p : level.players()) {
                        level.playSound(null, p.getX(), p.getY(), p.getZ(),
                                SoundEvents.WITCH_CELEBRATE, SoundSource.MASTER, 1.0F, 1.0F);
                    }
                    // 全场广播疫使时刻提示
                    Component broadcast = Component.translatable("message.noellesroles.infected.time.triggered")
                            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD);
                    for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                        ServerPlayNetworking.send(p, new BroadcastMessageS2CPacket(broadcast));
                    }
                    // 回放记录：进入疫使时刻
                    for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                        if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
                            SRE.REPLAY_MANAGER.recordCustomEvent(
                                    Component.translatable("replay.event.infected.plague_time",
                                            GameReplayUtils.getReplayPlayerDisplayText(p, true)));
                            break;
                        }
                    }
                    // 疫使技能冷却立刻清零（同时重置统一冷却和独立技能状态冷却）
                    for (ServerPlayer p : inftectRolePlayers) {
                        {
                            SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(p);
                            abilityComponent.resetAllCooldowns();
                            abilityComponent.status = 2;
                        }
                    }
                }
                checkAndTriggerLastInfected(level, inftectRolePlayers);
            } else {
                // 取消加速传播
                if (wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                    wasAccelerated = false;
                    // 同步取消加速状态到疫使玩家自身的组件（供客户端HUD读取）
                    for (ServerPlayer p : inftectRolePlayers) {
                        {
                            InfectedPlayerComponent comp = ModComponents.INFECTED.get(p);
                            comp.spreadAccelerated = false;
                            comp.sync();
                        }
                    }
                }
            }
        });
    }

    /**
     * 检查场上所有非疫使玩家是否都被感染，如果是则触发全部致死并刷新疫使冷却至3秒。
     */
    static void checkAndTriggerLastInfected(ServerLevel serverWorld, List<ServerPlayer> infectedRolePlayers) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(serverWorld);
        var players = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
        int totalNonInfected = 0;
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED))
                continue;
            InfectedPlayerComponent ic = ModComponents.INFECTED.get(player);
            if (ic.infectedTicks <= 0)
                totalNonInfected++;
        }
        if (infectedRolePlayers.isEmpty() || totalNonInfected > 0)
            return;
        for (var infectedPlayer : infectedRolePlayers) {
            SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(infectedPlayer);
            abilityComponent.resetAllCooldowns();
            abilityComponent.cooldown = GameConstants.getInTicks(0, 3);
            abilityComponent.getSkillState(SRE.id("infected_infect")).cooldown = abilityComponent.cooldown;
            abilityComponent.sync();
        }
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED))
                continue;
            InfectedPlayerComponent ic = ModComponents.INFECTED.get(player);
            if (ic.infectedTicks > 0 && InfectedPlayerComponent.canDieFromInfection(player)) {
                GameUtils.killPlayer(player, true, infectedRolePlayers.getFirst(),
                        InfectedPlayerComponent.INFECTION_DEATH_REASON, true);
            }
        }
    }

    /**
     * 获取加速状态（供HUD使用）
     */
    public static boolean isAccelerated() {
        return wasAccelerated;
    }

    /**
     * 当疫使从鹈鹕肚子里被释放时调用，重新检查并触发疫使时刻条件
     */
    public static void onInfectedReleasedFromPelican(ServerLevel level) {
        var gameWorldComponent = SREGameWorldComponent.KEY.maybeGet(level).orElse(null);
        if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
            return;
        }

        ArrayList<ServerPlayer> inftectRolePlayers = new ArrayList<>();
        boolean hasInfected = false;
        boolean hasKiller = false;
        boolean hasDoctor = false;
        boolean hasLooseEnd = false;
        boolean hasSafeTime = false;

        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            if (!hasSafeTime && player.hasEffect(ModEffects.SAFE_TIME)) {
                hasSafeTime = true;
            }
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                hasInfected = true;
                inftectRolePlayers.add(player);
                continue;
            }
            var role = gameWorldComponent.getRole(player);
            if (role != null && role.isKiller() && !hasKiller) {
                hasKiller = true;
            }
            if (!hasDoctor && (gameWorldComponent.isRole(player, ModRoles.DOCTOR)
                    || (gameWorldComponent.getRole(player) instanceof SRERole r && !r.canBePoisoned()))) {
                hasDoctor = true;
            }
            if (!hasLooseEnd && gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                hasLooseEnd = true;
            }
        }

        if (!hasInfected) {
            if (wasAccelerated) {
                InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                wasAccelerated = false;
            }
            return;
        }

        boolean killersAllDead = !hasKiller;
        boolean shouldAccelerate = killersAllDead && !hasDoctor && !hasLooseEnd && !hasSafeTime;

        if (shouldAccelerate) {
            if (!wasAccelerated) {
                // 有医生直接趋势，直接不管后面逻辑避免一堆神秘bug和性能消耗
                if (hasDoctor) {
                    for (ServerPlayer p : inftectRolePlayers) {
                        GameUtils.forceKillPlayer(p, true, null, GameConstants.DeathReasons.CANNOT_WIN);
                        return;
                    }
                }
                InfectedPlayerComponent.setSpreadAcceleratedForAll(level, true);
                wasAccelerated = true;
                // 同步加速状态到疫使玩家自身的组件（供客户端HUD读取）
                for (ServerPlayer p : inftectRolePlayers) {
                    {
                        InfectedPlayerComponent comp = ModComponents.INFECTED.get(p);
                        comp.spreadAccelerated = true;
                        comp.sync();
                    }
                }
                for (ServerPlayer p : level.players()) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.WITCH_CELEBRATE, SoundSource.MASTER, 1.0F, 1.0F);
                }
                Component broadcast = Component.translatable("message.noellesroles.infected.time.triggered")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD);
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, new BroadcastMessageS2CPacket(broadcast));
                }
                for (ServerPlayer p : inftectRolePlayers) {
                    {
                        SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(p);
                        abilityComponent.resetAllCooldowns();
                    }
                }
            }
            checkAndTriggerLastInfected(level, inftectRolePlayers);
        } else {
            if (wasAccelerated) {
                InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                wasAccelerated = false;
                // 同步取消加速状态到疫使玩家自身的组件（供客户端HUD读取）
                for (ServerPlayer p : inftectRolePlayers) {
                    {
                        InfectedPlayerComponent comp = ModComponents.INFECTED.get(p);
                        comp.spreadAccelerated = false;
                        comp.sync();
                    }
                }
            }
        }
    }

    /**
     * 检查是否处于疫使时刻（加速传播阶段）
     * 只有进入疫使时刻后，疫使感染所有玩家才能获得胜利
     */
    public static boolean isInInfectedMoment() {
        return wasAccelerated;
    }

    /**
     * 重置疫使时刻状态（游戏开始/结束时调用）
     */
    public static void resetAcceleratedState() {
        wasAccelerated = false;
    }
}
