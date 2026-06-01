package org.agmas.noellesroles.game.roles.neutral.infected;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.NRSounds;

/**
 * 疫使技能处理器
 * 处理疫使的感染能力和加速触发
 */
public class InfectedAbilityHandler {
    
    /**
     * 处理疫使使用技能（感染玩家）
     */
    public static boolean handleInfectedAbility(ServerPlayer player, ServerPlayer target) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.serverLevel());
        
        // 检查是否是疫使
        if (!gameWorldComponent.isRole(player, org.agmas.noellesroles.role.ModRoles.INFECTED)) {
            return false;
        }
        
        // 检查游戏状态
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        
        // 检查目标是否已被感染
        InfectedPlayerComponent targetComponent = ModComponents.INFECTED.get(target);
        if (targetComponent.infectedTicks > 0) {
            return false; // 已被感染
        }
        
        // 检查疫使技能冷却
        SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(player);
        if (abilityComponent.cooldown > 0) {
            return false;
        }
        
        // 检查目标是否是杀手阵营或杀手方中立
        var targetRole = gameWorldComponent.getRole(target);
        if (targetRole != null && (targetRole.isKillerTeam() || targetRole.isKiller())) {
            // 杀手阵营可以被感染但不会致死，这里仍然可以感染
        }
        
        // 感染目标
        targetComponent.infect(player);
        
        // 重置疫使技能冷却
        abilityComponent.cooldown = GameConstants.getInTicks(1, 20); // 80秒冷却
        abilityComponent.sync();
        
        // 播放注射器刺入音效 - 附近所有人都能听到
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
            NRSounds.SYRINGE_STAB, SoundSource.PLAYERS, 0.5f, 0.5f);
        
        return true;
    }
    
    /**
     * 检查场上是否只剩下疫使或所有非疫使玩家都被感染
     * 如果是，则触发加速效果
     */
    public static void checkAndTriggerLastInfected(ServerLevel serverWorld) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(serverWorld);
        var players = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
        
        ServerPlayer infectedPlayer = null;
        int totalNonInfected = 0;
        int totalInfected = 0;
        
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, org.agmas.noellesroles.role.ModRoles.INFECTED)) {
                infectedPlayer = player;
                continue;
            }
            
            InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
            if (infectedComponent.infectedTicks > 0) {
                totalInfected++;
            } else {
                totalNonInfected++;
            }
        }
        
        if (infectedPlayer == null) return;
        
        // 如果只剩下疫使，或所有其他玩家都被感染
        if (totalNonInfected == 0) {
            // 重置疫使技能冷却为3秒
            SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(infectedPlayer);
            abilityComponent.cooldown = GameConstants.getInTicks(0, 3); // 3秒冷却
            abilityComponent.sync();
            
            // 触发所有被感染者立即致死
            for (ServerPlayer player : players) {
                if (gameWorldComponent.isRole(player, org.agmas.noellesroles.role.ModRoles.INFECTED)) {
                    continue;
                }
                
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
                if (infectedComponent.infectedTicks > 0) {
                    // 立即致死
                    if (InfectedPlayerComponent.canDieFromInfection(player)) {
                        GameUtils.killPlayer(player, true, infectedPlayer, 
                            InfectedPlayerComponent.INFECTION_DEATH_REASON, true);
                    }
                }
            }
        }
    }
}
