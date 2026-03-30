package org.agmas.noellesroles.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.agmas.noellesroles.entity.WheelchairEntity;

/**
 * 轮椅模式特殊方块效果处理器
 * 
 * 功能：
 * - 绿宝石块：轮椅加速
 * - 煤炭块：轮椅减速
 * - 铁块：修复轮椅耐久
 * - 红石块：瘫痪轮椅 3 秒
 */
public class WheelchairEffectBlockHandler {

    /**
     * 检查并应用特殊方块效果
     * @param wheelchair 轮椅实体
     * @param player 控制轮椅的玩家
     */
    public static void checkAndApplyEffects(WheelchairEntity wheelchair, Player player) {
        if (wheelchair.level() instanceof ServerLevel serverLevel) {
            // 尝试多种位置检测：优先使用玩家脚下的方块（与 ChairWheelRaceGame 保持一致），
            // 其次检查轮椅所在方块与下方方块，覆盖边界情况。
            BlockPos playerPos = player != null ? player.getOnPos().above(-1) : null;
            BlockPos entityPos = wheelchair.blockPosition();
            BlockPos belowEntity = entityPos.below();

            BlockPos[] checkPositions = playerPos != null
                    ? new BlockPos[]{playerPos, entityPos, belowEntity}
                    : new BlockPos[]{entityPos, belowEntity};

            for (BlockPos pos : checkPositions) {
                if (serverLevel.getBlockState(pos).is(Blocks.EMERALD_BLOCK)) {
                    applyEmeraldBlockEffect(wheelchair, player, serverLevel);
                    return;
                }
                if (serverLevel.getBlockState(pos).is(Blocks.COAL_BLOCK)) {
                    applyCoalBlockEffect(wheelchair, player, serverLevel);
                    return;
                }
                if (serverLevel.getBlockState(pos).is(Blocks.IRON_BLOCK)) {
                    applyIronBlockEffect(wheelchair, player, serverLevel);
                    return;
                }
                if (serverLevel.getBlockState(pos).is(Blocks.REDSTONE_BLOCK)) {
                    applyRedstoneBlockEffect(wheelchair, player, serverLevel);
                    return;
                }
            }
        }
    }

    /**
     * 绿宝石块效果：加速轮椅
     */
    private static void applyEmeraldBlockEffect(WheelchairEntity wheelchair, Player player, ServerLevel serverLevel) {
        wheelchair.boost();
        
        // 发送粒子效果
        for (int i = 0; i < 10; i++) {
            serverLevel.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                    wheelchair.getX() + serverLevel.random.nextDouble(),
                    wheelchair.getY() + serverLevel.random.nextDouble() * 2,
                    wheelchair.getZ() + serverLevel.random.nextDouble(),
                1, 0, 0, 0, 0
            );
        }
        
        // 播放音效
        serverLevel.playSound(null, wheelchair.blockPosition(),
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.5F);
        
        // 发送消息
        player.displayClientMessage(
            Component.literal("绿宝石块加速！")
                .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD),
            true
        );
    }

    /**
     * 煤炭块效果：减速轮椅
     */
    private static void applyCoalBlockEffect(WheelchairEntity wheelchair, Player player, ServerLevel serverLevel) {
        if (wheelchair.isBoosting()) {
            wheelchair.stopBoost();
        }
        
        // 发送粒子效果
        for (int i = 0; i < 5; i++) {
            serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                wheelchair.getX() + serverLevel.random.nextDouble(),
                wheelchair.getY() + 0.5,
                wheelchair.getZ() + serverLevel.random.nextDouble(),
                1, 0, 0.1, 0, 0
            );
        }
        
        // 播放音效
        serverLevel.playSound(null, wheelchair.blockPosition(),
            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
        
        // 发送消息
        player.displayClientMessage(
            Component.literal("煤炭块减速！")
                .withStyle(net.minecraft.ChatFormatting.GRAY),
            true
        );
    }

    /**
     * 铁块效果：修复轮椅耐久
     */
    private static void applyIronBlockEffect(WheelchairEntity wheelchair, Player player, ServerLevel serverLevel) {
        int previousDurability = wheelchair.durability;
        wheelchair.durability = Math.min(wheelchair.durability + 20, 60);
        
        if (previousDurability < 60) {
            // 发送粒子效果
            for (int i = 0; i < 8; i++) {
                serverLevel.sendParticles(
                    ParticleTypes.HEART,
                    wheelchair.getX() + serverLevel.random.nextDouble(),
                        wheelchair.getY() + 1.0 + serverLevel.random.nextDouble(),
                        wheelchair.getZ() + serverLevel.random.nextDouble(),
                    1, 0, 0, 0, 0
                );
            }
            
            // 播放音效
            serverLevel.playSound(null, wheelchair.blockPosition(),
                SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
            
            // 发送消息
            player.displayClientMessage(
                Component.literal("铁块修复耐久：" + previousDurability + " → " + wheelchair.durability + "/60")
                    .withStyle(net.minecraft.ChatFormatting.WHITE),
                true
            );
        }
    }

    /**
     * 红石块效果：瘫痪轮椅 3 秒
     */
    private static void applyRedstoneBlockEffect(WheelchairEntity wheelchair, Player player, ServerLevel serverLevel) {
        wheelchair.setStunTime(60);

        // 停止所有加速效果
        wheelchair.stopBoost();
        
        // 发送粒子效果
        for (int i = 0; i < 15; i++) {
            serverLevel.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                wheelchair.getX() + serverLevel.random.nextDouble(),
                wheelchair.getY() + 1.0 + serverLevel.random.nextDouble(),
                wheelchair.getZ() + serverLevel.random.nextDouble(),
                1, 0, 0, 0, 0
            );
        }
        
        // 播放音效
        serverLevel.playSound(null, wheelchair.blockPosition(),
            SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 0.8F);
        
        // 发送消息
        player.displayClientMessage(
            Component.literal("红石块瘫痪！3 秒内无法移动！")
                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD),
            true
        );
    }
}
