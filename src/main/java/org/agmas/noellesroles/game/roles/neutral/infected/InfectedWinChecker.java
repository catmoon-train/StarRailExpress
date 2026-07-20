package org.agmas.noellesroles.game.roles.neutral.infected;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    
    private static boolean wasAccelerated = false;  // 记录上一个tick的加速状态
    private static int tickCounter = 0;             // tick 计数器，用于节流
    private static final int TICK_INTERVAL = 20;    // 每20 tick（1秒）执行一次检查（原来每tick执行，减少95%）
    
    /**
     * 检查场上是否存在医生或故障机器人（都能阻止疫使时刻并让乘客获胜）
     */
    private static boolean hasDoctor(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            if (gameWorldComponent.isRole(player, ModRoles.DOCTOR)) {
                return true;
            }
            if (gameWorldComponent.isRole(player, ModRoles.GLITCH_ROBOT)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查场上是否没有纵火犯（忽略乘客）
     */
    private static boolean noPyromaniac(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        ResourceLocation arsonistId = ResourceLocation.fromNamespaceAndPath("stupid_express", "arsonist");
        
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                ResourceLocation roleId = role.identifier();
                if (roleId != null && roleId.equals(arsonistId)) {
                    return false;  // 发现纵火犯
                }
            }
        }
        return true;  // 没有纵火犯
    }
    
    /**
     * 检查场上是否没有平民（乘客）和纵火犯
     * 当没有这些角色时，疫使可以正常结束游戏
     */
    private static boolean noPassengersAndPyromaniac(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        // 纵火犯的标识符
        ResourceLocation arsonistId = ResourceLocation.fromNamespaceAndPath("stupid_express", "arsonist");
        
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            // 跳过疫使
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                continue;
            }
            // 跳过被感染者（他们会被疫使控制）
            InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
            if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                continue;
            }
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                // 检查是否是乘客阵营（平民）
                if (role.isInnocent()) {
                    return false;
                }
                // 检查是否是纵火犯（通过 ResourceLocation 比较）
                ResourceLocation roleId = role.identifier();
                if (roleId != null && roleId.equals(arsonistId)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * 清除所有玩家的感染状态
     */
    private static void clearAllInfection(ServerLevel level) {
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
            if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                infectedComponent.cure();
            }
        }
    }

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
            
            for (ServerPlayer player : players) {
                if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                    infectedAlive = true;
                    infectedCount++;
                }
                
                // 检查玩家是否被感染
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
                if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                    if (!gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                        infectedInfectedCount++;
                    }
                }
            }
            
            // 只有疫使存活（必须进入疫使时刻才能胜利）
            if (infectedAlive && totalAlive == infectedCount && wasAccelerated) {
                // 清除所有感染状态
                clearAllInfection(serverWorld);
                // 疫使胜利 - 算作杀手胜利
                RoleUtils.customWinnerWin(serverWorld, WinStatus.KILLERS,
                    org.agmas.noellesroles.role.ModRoles.INFECTED.identifier().getPath(),
                    java.util.OptionalInt.of(org.agmas.noellesroles.role.ModRoles.INFECTED.color()));
                return WinStatus.KILLERS;
            }

            // 疫使存活且其他所有玩家都被感染（必须进入疫使时刻才能胜利）
            if (infectedAlive && infectedInfectedCount == totalAlive - infectedCount && wasAccelerated) {
                // 清除所有感染状态
                clearAllInfection(serverWorld);
                // 疫使胜利 - 算作杀手胜利
                RoleUtils.customWinnerWin(serverWorld, WinStatus.KILLERS,
                    org.agmas.noellesroles.role.ModRoles.INFECTED.identifier().getPath(),
                    java.util.OptionalInt.of(org.agmas.noellesroles.role.ModRoles.INFECTED.color()));
                return WinStatus.KILLERS;
            }

            // 如果是杀手胜利，疫使不阻止
            if (winStatus == WinStatus.KILLERS) {
                return WinStatus.NOT_MODIFY;
            }

            // 防止乘客胜利 - 疫使存活时阻止游戏结束判定
            if (infectedAlive && winStatus == WinStatus.PASSENGERS) {
                // 条件4：纵火犯阵亡+有医生，无论是否进入疫使时刻都判定乘客胜利
                if (noPyromaniac(serverWorld, gameWorldComponent) && hasDoctor(serverWorld, gameWorldComponent)) {
                    return WinStatus.PASSENGERS;
                }
                
                // wasAccelerated=true时的特殊处理
                if (wasAccelerated) {
                    // 条件3：无乘客和纵火犯时疫使胜利
                    if (noPassengersAndPyromaniac(serverWorld, gameWorldComponent)) {
                        // 清除所有感染状态
                        clearAllInfection(serverWorld);
                        RoleUtils.customWinnerWin(serverWorld, WinStatus.KILLERS,
                            ModRoles.INFECTED.identifier().getPath(),
                            java.util.OptionalInt.of(ModRoles.INFECTED.color()));
                        return WinStatus.KILLERS;
                    }
                    // 条件3：有纵火犯存在时阻止游戏结束
                    return WinStatus.NONE;
                }
                
                // wasAccelerated=false时阻止乘客胜利
                return WinStatus.NONE;
            }

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
            boolean hasInfected = false;
            boolean hasKiller = false;
            boolean hasDoctor = false;
            boolean hasLooseEnd = false;
            boolean hasSafeTime = false;

            for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                    hasInfected = true;
                }
                // 只检查真正的杀手阵营（isKiller），不含杀手方中立
                var role = gameWorldComponent.getRole(player);
                if (role != null && role.isKiller() && !hasKiller) {
                    hasKiller = true;
                }
                if (!hasDoctor && (gameWorldComponent.isRole(player, ModRoles.DOCTOR)
                        || gameWorldComponent.isRole(player, ModRoles.GLITCH_ROBOT))) {
                    hasDoctor = true;
                }
                if (!hasLooseEnd && gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                    hasLooseEnd = true;
                }
                // 检查是否处于安全时间（游戏开始安全时间、阳光自选、职业轮抽的选择阶段）
                if (!hasSafeTime && player.hasEffect(ModEffects.SAFE_TIME)) {
                    hasSafeTime = true;
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
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, true);
                    wasAccelerated = true;
                    // 同步加速状态到疫使玩家自身的组件（供客户端HUD读取）
                    for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                        if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
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
                    // 疫使技能冷却立刻清零（同时重置统一冷却和独立技能状态冷却）
                    for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                        if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
                            SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(p);
                            abilityComponent.resetAllCooldowns();
                            abilityComponent.status = 2;
                        }
                    }
                }
                checkAndTriggerLastInfected(level);
            } else {
                // 取消加速传播
                if (wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                    wasAccelerated = false;
                    // 同步取消加速状态到疫使玩家自身的组件（供客户端HUD读取）
                    for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                        if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
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
    static void checkAndTriggerLastInfected(ServerLevel serverWorld) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(serverWorld);
        var players = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
        ServerPlayer infectedPlayer = null;
        int totalNonInfected = 0;
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                infectedPlayer = player;
                continue;
            }
            InfectedPlayerComponent ic = ModComponents.INFECTED.get(player);
            if (ic.infectedTicks <= 0) totalNonInfected++;
        }
        if (infectedPlayer == null || totalNonInfected > 0) return;
        SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(infectedPlayer);
        abilityComponent.resetAllCooldowns();
        abilityComponent.cooldown = GameConstants.getInTicks(0, 3);
        abilityComponent.getSkillState(SRE.id("infected_infect")).cooldown = abilityComponent.cooldown;
        abilityComponent.sync();
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) continue;
            InfectedPlayerComponent ic = ModComponents.INFECTED.get(player);
            if (ic.infectedTicks > 0 && InfectedPlayerComponent.canDieFromInfection(player)) {
                GameUtils.killPlayer(player, true, infectedPlayer,
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

        boolean hasInfected = false;
        boolean hasKiller = false;
        boolean hasDoctor = false;
        boolean hasLooseEnd = false;
        boolean hasSafeTime = false;

        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                hasInfected = true;
            }
            var role = gameWorldComponent.getRole(player);
            if (role != null && role.isKiller() && !hasKiller) {
                hasKiller = true;
            }
            if (!hasDoctor && (gameWorldComponent.isRole(player, ModRoles.DOCTOR)
                    || gameWorldComponent.isRole(player, ModRoles.GLITCH_ROBOT))) {
                hasDoctor = true;
            }
            if (!hasLooseEnd && gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                hasLooseEnd = true;
            }
            if (!hasSafeTime && player.hasEffect(ModEffects.SAFE_TIME)) {
                hasSafeTime = true;
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
                InfectedPlayerComponent.setSpreadAcceleratedForAll(level, true);
                wasAccelerated = true;
                // 同步加速状态到疫使玩家自身的组件（供客户端HUD读取）
                for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                    if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
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
                for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                    if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
                        SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(p);
                        abilityComponent.resetAllCooldowns();
                    }
                }
            }
            checkAndTriggerLastInfected(level);
        } else {
            if (wasAccelerated) {
                InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                wasAccelerated = false;
                // 同步取消加速状态到疫使玩家自身的组件（供客户端HUD读取）
                for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                    if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
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
