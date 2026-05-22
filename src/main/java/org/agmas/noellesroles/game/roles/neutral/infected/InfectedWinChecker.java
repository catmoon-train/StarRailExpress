package org.agmas.noellesroles.game.roles.neutral.infected;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 疫使胜利检测器
 * 使用纵火犯的逻辑来防止游戏结束
 */
public class InfectedWinChecker {
    
    private static boolean wasAccelerated = false;  // 记录上一个tick的加速状态
    
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
     * 检查场上是否所有杀手都已阵亡（只检查杀手阵营，不含杀手方中立）
     */
    private static boolean allKillersDead(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            var role = gameWorldComponent.getRole(player);
            // 只检查真正的杀手阵营（isKiller），不含杀手方中立（isKillerTeam）
            if (role != null && role.isKiller()) {
                return false;
            }
        }
        return true;
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
     * 检查场上是否处于亡命时刻（有LOOSE_END存活）
     */
    private static boolean isLooseEndActive(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            if (gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)) {
                return true;
            }
        }
        return false;
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

        // 服务器tick事件 - 检查疫使加速条件
        // 触发条件：所有杀手全部阵亡 且 平民中没有医生
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel level = server.overworld();
            var gameWorldComponent = SREGameWorldComponent.KEY.maybeGet(level).orElse(null);
            if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
                return;
            }

            // 检查场上是否有疫使存活
            boolean hasInfected = false;
            for (ServerPlayer player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                if (gameWorldComponent.isRole(player, ModRoles.INFECTED)) {
                    hasInfected = true;
                    break;
                }
            }

            if (!hasInfected) {
                // 没有疫使，取消加速
                if (wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                    wasAccelerated = false;
                }
                return;
            }

            // 检查触发条件：所有杀手已阵亡 且 没有医生 且 不处于亡命时刻
            boolean killersAllDead = allKillersDead(level, gameWorldComponent);
            boolean noDoctor = !hasDoctor(level, gameWorldComponent);
            boolean shouldAccelerate = killersAllDead && noDoctor && !isLooseEndActive(level, gameWorldComponent);

            if (shouldAccelerate) {
                // 设置加速传播（病毒传染时间缩短至10秒）
                if (!wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, true);
                    wasAccelerated = true;
                    // 全场播放疫使时刻音效
                    for (ServerPlayer p : level.players()) {
                        level.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.WITCH_CELEBRATE, SoundSource.MASTER, 1.0F, 1.0F);
                    }
                    // 疫使技能冷却立刻清零
                    for (ServerPlayer p : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                        if (gameWorldComponent.isRole(p, ModRoles.INFECTED)) {
                            SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(p);
                            abilityComponent.cooldown = 0;
                            abilityComponent.sync();
                        }
                    }
                }
                InfectedAbilityHandler.checkAndTriggerLastInfected(level);
            } else {
                // 取消加速传播
                if (wasAccelerated) {
                    InfectedPlayerComponent.setSpreadAcceleratedForAll(level, false);
                    wasAccelerated = false;
                }
            }
        });
    }

    /**
     * 获取加速状态（供HUD使用）
     */
    public static boolean isAccelerated() {
        return wasAccelerated;
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
