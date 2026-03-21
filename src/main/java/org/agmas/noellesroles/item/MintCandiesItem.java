package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 薄荷糖
 * - 心理学家专属物品
 * - 游戏开始时给予一个
 * - 在商店可以花费100金币购买
 * - 吃掉时恢复0.5的san值（50%）
 */
public class MintCandiesItem extends Item {
    
    /** san值恢复量（50%） */
    public static final float SANITY_RESTORE_AMOUNT = 0.5f;
    
    public MintCandiesItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        
        // 检查游戏是否正在进行
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning()) {
            return InteractionResultHolder.pass(itemStack);
        }
        
        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.pass(itemStack);
        }
        
        // 开始使用（吃）
        user.startUsingItem(hand);

        return InteractionResultHolder.consume(itemStack);
    }
    
    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level world, @NotNull LivingEntity user) {
        if (user instanceof Player player) {
            if (!world.isClientSide()) {
                // 恢复san值
                SREPlayerMoodComponent moodComponent = SREPlayerMoodComponent.KEY.get(player);
                float currentMood = moodComponent.getMood();
                float newMood = Math.min(1.0f, currentMood + SANITY_RESTORE_AMOUNT);
                moodComponent.setMood(newMood);
                moodComponent.sync();
                final var playerShopComponent = SREPlayerShopComponent.KEY.get(player);
                playerShopComponent.setBalance(playerShopComponent.balance +15);
                // 播放吃东西的音效
                world.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, 1.0F);
            }
            
            // 消耗物品
            stack.shrink(1);
        }
        
        return stack;
    }
    
    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.EAT;
    }
    
    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity user) {
        return 32; // 标准食物食用时间
    }
}