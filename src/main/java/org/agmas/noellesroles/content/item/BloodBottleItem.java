package org.agmas.noellesroles.content.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.blood.BloodMain;
import org.jetbrains.annotations.NotNull;

/**
 * 血瓶
 * - 右键使用后在附近洒落血液粒子
 * - 使用后消失
 */
public class BloodBottleItem extends Item {

    private static final int BLOOD_PARTICLE_COUNT = 12;  // 血液粒子数量
    private static final double BLOOD_RADIUS = 1.0;      // 血液洒落半径（更集中）

    public BloodBottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        
        if (!level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            
            // 生成血液粒子效果
            spawnBloodParticles(serverLevel, player.position());
            
            // 播放血液洒落音效
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GRASS_FALL, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
        
        // 使用后物品消失
        itemStack.shrink(1);
        
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }

    /**
     * 在指定位置生成血液粒子效果
     */
    private void spawnBloodParticles(ServerLevel level, Vec3 pos) {
        for (int i = 0; i < BLOOD_PARTICLE_COUNT; i++) {
            // 在半径范围内随机生成血液粒子
            double offsetX = (level.random.nextDouble() - 0.5) * BLOOD_RADIUS * 2;
            double offsetY = level.random.nextDouble() * 2;
            double offsetZ = (level.random.nextDouble() - 0.5) * BLOOD_RADIUS * 2;

            double velX = (level.random.nextDouble() - 0.5) * 0.2;
            double velY = level.random.nextDouble() * 0.3;
            double velZ = (level.random.nextDouble() - 0.5) * 0.2;

            // 使用血液粒子类型
            level.sendParticles(BloodMain.BLOOD_PARTICLE,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1,  // 每次生成的数量
                    velX, velY, velZ,  // 速度分量
                    0.5);  // 粒子大小
        }
    }
}
