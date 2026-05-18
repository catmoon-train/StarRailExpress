package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.component.WorldModifierComponent;

import java.util.*;

/**
 * 修饰符效果处理器 - 处理需要每tick检查的效果
 */
public class ModifierEffects {
    
    public static void init() {
        registerTickEvents();
        registerEatEvents();
        registerInsaneSeeDeathEvents();
    }
    
    private static void registerTickEvents() {
        // 使用 ServerTickEvents.END_SERVER_TICK
        ServerTickEvents.END_SERVER_TICK.register(ModifierEffects::onServerTick);
    }
    
    private static void onServerTick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // === 回光返照 - 游戏时间3秒后真正死亡 ===
            if (TraitorAndModifiers.LAST_GASP_TRIGGERED.contains(player.getUUID())) {
                Long triggerTime = TraitorAndModifiers.LAST_GASP_TRIGGER_GAME_TIME.get(player.getUUID());
                if (triggerTime != null && gameTime - triggerTime >= 60) { // 60 ticks = 3秒游戏时间
                    if (player.isAlive()) {
                        // 播放死亡粒子效果
                        if (player.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.SOUL,
                                    player.getX(), player.getY() + 1.0, player.getZ(),
                                    20, 0.5, 0.8, 0.5, 0.02);
                        }
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("modifier.noellesroles.last_gasp.death"), true);
                        
                        // 获取击杀者和死亡原因
                        UUID killerUuid = TraitorAndModifiers.LAST_GASP_KILLER.get(player.getUUID());
                        ServerPlayer killer = killerUuid != null ? server.getPlayerList().getPlayer(killerUuid) : null;
                        var deathReason = TraitorAndModifiers.LAST_GASP_DEATH_REASON.get(player.getUUID());
                        
                        GameUtils.killPlayer(player, true, killer, deathReason);
                    }
                    TraitorAndModifiers.LAST_GASP_TRIGGERED.remove(player.getUUID());
                    TraitorAndModifiers.LAST_GASP_TRIGGER_GAME_TIME.remove(player.getUUID());
                    TraitorAndModifiers.LAST_GASP_KILLER.remove(player.getUUID());
                    TraitorAndModifiers.LAST_GASP_DEATH_REASON.remove(player.getUUID());
                }
                continue; // 回光返照中的玩家跳过其他效果检查
            }
            
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
            
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.serverLevel());
            if (gameWorld == null || !gameWorld.isRunning()) continue;
            
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.serverLevel());
            UUID uuid = player.getUUID();
            
            // === 慷慨 - 每1.5分钟给予最近玩家25金币 ===
            if (modifiers.isModifier(uuid, TraitorAndModifiers.GENEROUS)) {
                Long lastTime = TraitorAndModifiers.LAST_GIVE_COIN_TIME.get(uuid);
                if (lastTime == null) {
                    TraitorAndModifiers.LAST_GIVE_COIN_TIME.put(uuid, System.currentTimeMillis());
                } else if (System.currentTimeMillis() - lastTime >= 90 * 1000L) {
                    ServerPlayer nearest = findNearestPlayer(player, 6);
                    if (nearest != null && nearest != player) {
                        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(nearest);
                        shop.setBalance(shop.balance + 25);
                        shop.sync();
                    }
                    TraitorAndModifiers.LAST_GIVE_COIN_TIME.put(uuid, System.currentTimeMillis());
                }
            }
            
            // === 大胃王 - 每1.5分钟获得一个苹果 ===
            if (modifiers.isModifier(uuid, TraitorAndModifiers.BIG_EATER)) {
                Long lastTime = TraitorAndModifiers.LAST_APPLE_TIME.get(uuid);
                if (lastTime == null) {
                    TraitorAndModifiers.LAST_APPLE_TIME.put(uuid, System.currentTimeMillis());
                } else if (System.currentTimeMillis() - lastTime >= 90 * 1000L) {
                    player.addItem(new ItemStack(Items.APPLE));
                    TraitorAndModifiers.LAST_APPLE_TIME.put(uuid, System.currentTimeMillis());
                }
            }
            
            // === 勇敢 - 关灯时恢复0.5心情值 ===
            if (modifiers.isModifier(uuid, TraitorAndModifiers.BRAVE)) {
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.serverLevel());
                if (blackout != null && blackout.isBlackoutActive() && gameTime % 20 == 0) { // 每秒检查一次
                    SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                    if (mood != null) {
                        mood.setMood(mood.getMood() + 0.5f);
                        mood.sync();
                    }
                }
            }
            
            // === 绝境信徒 - 检查唯一杀手 ===
            if (modifiers.isModifier(uuid, TraitorAndModifiers.DESPERATE_FAITH)) {
                if (!TraitorAndModifiers.DESPERATE_FAITH_ACTIVATED.contains(uuid)) {
                    int otherKillerCount = 0;
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (p != player && GameUtils.isPlayerAliveAndSurvival(p)) {
                            SREGameWorldComponent pw = SREGameWorldComponent.KEY.get(p.serverLevel());
                            if (pw != null && pw.getRole(p) != null && pw.getRole(p).canUseKiller() && !pw.getRole(p).isInnocent()) {
                                if (!pw.getRole(p).identifier().getPath().equals("traitor")) {
                                    otherKillerCount++;
                                }
                            }
                        }
                    }
                    
                    if (otherKillerCount == 0) {
                        TraitorAndModifiers.DESPERATE_FAITH_ACTIVATED.add(uuid);
                        
                        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                        shop.setBalance(shop.balance + 100);
                        shop.sync();
                        
                        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, Integer.MAX_VALUE, 4, false, false, false));
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false, false));
                        
                        player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        
                        // 只发送给玩家自己
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("modifier.noellesroles.desperate_faith.activated"), true);
                    }
                }
            }
            
            // === 夜猫子 - 免疫黑暗和失明效果 ===
            if (modifiers.isModifier(uuid, TraitorAndModifiers.NIGHT_OWL)) {
                if (player.hasEffect(MobEffects.DARKNESS)) {
                    player.removeEffect(MobEffects.DARKNESS);
                }
                if (player.hasEffect(MobEffects.BLINDNESS)) {
                    player.removeEffect(MobEffects.BLINDNESS);
                }
            }
        }
    }
    
    private static void registerEatEvents() {
        // 食物消耗效果在别处处理（通过物品使用拦截）
    }
    
    private static void registerInsaneSeeDeathEvents() {
        // 晕血症 - 监听玩家死亡
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim.level().isClientSide) return;
            
            for (Player player : victim.level().players()) {
                if (!(player instanceof ServerPlayer sp)) continue;
                if (!GameUtils.isPlayerAliveAndSurvival(sp)) continue;
                if (sp == victim) continue;
                
                WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
                UUID uuid = player.getUUID();
                
                // 晕血症
                if (modifiers.isModifier(uuid, TraitorAndModifiers.HEMOPHOBIA)) {
                    double dist = player.distanceTo(victim);
                    if (dist <= 30) {
                        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false, false));
                        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 2, false, false, false));
                        sp.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("modifier.noellesroles.hemophobia.trigger"), 
                                true);
                    }
                }
            }
        });
    }
    
    // ==================== 工具方法 ====================
    
    private static ServerPlayer findNearestPlayer(ServerPlayer player, double maxDistance) {
        ServerPlayer nearest = null;
        double nearestDist = maxDistance;
        var playerPos = player.position();
        
        for (Player p : player.level().players()) {
            if (p == player) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            
            double dist = playerPos.distanceTo(p.position());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = (ServerPlayer) p;
            }
        }
        
        return nearest;
    }
    
    /**
     * 狂躁症触发 - 附近有玩家完成任务，每1秒最多触发一次
     */
    public static void onNearbyTaskComplete(ServerPlayer manicPlayer) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(manicPlayer.level());
        UUID uuid = manicPlayer.getUUID();
        
        if (modifiers.isModifier(uuid, TraitorAndModifiers.MANIC)) {
            // 检查是否在1秒冷却期内
            Long lastTime = TraitorAndModifiers.LAST_MANIC_TRIGGER_TIME.get(uuid);
            if (lastTime != null && System.currentTimeMillis() - lastTime < 1000) {
                return; // 还在冷却中
            }
            
            TraitorAndModifiers.LAST_MANIC_TRIGGER_TIME.put(uuid, System.currentTimeMillis());
            
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(manicPlayer);
            if (mood != null) {
                mood.setMood(mood.getMood() + 0.1f);
                mood.sync();
            }
            
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(manicPlayer);
            shop.setBalance(shop.balance + 10);
            shop.sync();
        }
    }
    
    /**
     * 大胃王触发 - 完成任务时恢复理智和金币
     * 平民/中立阵营额外获得25金币，杀手阵营额外获得5金币
     */
    public static void onBigEaterTaskComplete(ServerPlayer player) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        UUID uuid = player.getUUID();
        
        if (modifiers.isModifier(uuid, TraitorAndModifiers.BIG_EATER)) {
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
            if (mood != null) {
                mood.setMood(mood.getMood() + 0.25f);
                mood.sync();
            }
            
            // 根据阵营判断金币奖励
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            int coinReward;
            if (gameWorld.isInnocent(player) || gameWorld.getRole(player).isNeutrals()) {
                // 平民/中立阵营：25金币
                coinReward = 25;
            } else {
                // 杀手阵营：5金币
                coinReward = 5;
            }
            
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.setBalance(shop.balance + coinReward);
            shop.sync();
            // 大胃王不发送粒子效果
        }
    }
    
    /**
     * 计算吝啬返还的金币
     */
    public static int calculateStingyRefund(int originalPrice) {
        return TraitorAndModifiers.calculateStingyRefund(originalPrice);
    }
    
    /**
     * 检查并处理吝啬返还
     */
    public static void onStingyPurchase(ServerPlayer player, int originalPrice) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.level());
        UUID uuid = player.getUUID();
        
        if (modifiers.isModifier(uuid, TraitorAndModifiers.STINGY)) {
            int refund = calculateStingyRefund(originalPrice);
            if (refund > 0) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                shop.setBalance(shop.balance + refund);
                shop.sync();
                
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("modifier.noellesroles.stingy.refund", refund), 
                        true);
            }
        }
    }
}
