package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.entity.HallucinationAreaManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 迷幻瓶物品
 * - 迷幻师专属道具
 * - 右键使用，制造大量烟雾
 * - 20格范围内玩家视野会随机偏离视角
 * - 迷雾范围：20格
 * - 持续时间：3秒
 * - 触发间隔：1秒
 * - 耐久：2点
 */
public class HallucinationBottleItem extends Item {
    
    // 迷幻效果半径：20格
    private static final double HALLUCINATION_RADIUS = 20.0;
    // 持续时间：3秒 = 60 ticks
    private static final int HALLUCINATION_DURATION_TICKS = 60;
    // 触发间隔：1秒 = 20 ticks
    private static final int TRIGGER_INTERVAL_TICKS = 20;
    
    public HallucinationBottleItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        
        // 播放使用音效
        world.playSound(null, user.getX(), user.getY(), user.getZ(), 
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 
                1.0F, 0.8F + (world.random.nextFloat() - 0.5f) / 5f);
        
        if (!world.isClientSide && world instanceof ServerLevel serverWorld) {
            // 创建迷幻烟雾区域
            Vec3 center = user.position();
            
            // 初始烟雾粒子爆发 - 大量粒子（烟雾弹的2倍 = 3000个）
            spawnInitialSmokeParticles(serverWorld, center);
            
            // 创建持续的迷幻区域
            HallucinationAreaManager.createHallucinationArea(
                serverWorld, 
                center, 
                HALLUCINATION_RADIUS, 
                HALLUCINATION_DURATION_TICKS,
                TRIGGER_INTERVAL_TICKS,
                user
            );
            
            // 立即对范围内玩家应用视角偏移
            applyInitialHallucinationEffect(serverWorld, user, center);
        }
        
        user.awardStat(Stats.ITEM_USED.get(this));
        
        // 消耗耐久
        if (itemStack.isDamageableItem()) {
            itemStack.hurtAndBreak(1, user, hand == InteractionHand.MAIN_HAND 
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND 
                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        
        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }
    
    /**
     * 生成初始烟雾粒子 - 数量是烟雾弹的2倍
     */
    private void spawnInitialSmokeParticles(ServerLevel world, Vec3 center) {
        // 3000个粒子（烟雾弹1500的2倍）
        for (int i = 0; i < 100; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * HALLUCINATION_RADIUS * 2;
            double offsetY = world.random.nextDouble() * 4;  // 高度范围
            double offsetZ = (world.random.nextDouble() - 0.5) * HALLUCINATION_RADIUS * 2;
            
            // 主要烟雾粒子 - 使用紫色效果
            world.sendParticles(ParticleTypes.DRAGON_BREATH,
                    center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                    2, 0.15, 0.15, 0.15, 0.02);
            
            // 添加大型烟雾粒子
            if (i % 2 == 0) {
                world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                        2, 0.2, 0.2, 0.2, 0.04);
            }
            
            // 添加迷幻效果粒子
            if (i % 4 == 0) {
                world.sendParticles(ParticleTypes.WITCH,
                        center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                        1, 0.1, 0.1, 0.1, 0.03);
            }
        }
    }
    
    /**
     * 对范围内玩家应用初始迷幻效果
     */
    private void applyInitialHallucinationEffect(ServerLevel world, Player user, Vec3 center) {
        AABB area = new AABB(
                center.x - HALLUCINATION_RADIUS, center.y - 5, center.z - HALLUCINATION_RADIUS,
                center.x + HALLUCINATION_RADIUS, center.y + 10, center.z + HALLUCINATION_RADIUS
        );
        
        List<ServerPlayer> players = world.getEntitiesOfClass(
                ServerPlayer.class, area,
                player -> GameUtils.isPlayerAliveAndSurvival(player) && player != user
        );
        
        for (ServerPlayer player : players) {
            // 检查玩家是否在球形范围内
            double distance = player.position().distanceTo(center);
            if (distance <= HALLUCINATION_RADIUS) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, HALLUCINATION_DURATION_TICKS));
                // 发送视角偏移效果（使用客户端网络包）
                HallucinationAreaManager.applyHallucinationToPlayer(player);
            }
        }
    }
}