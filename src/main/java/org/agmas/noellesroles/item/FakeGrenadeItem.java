package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * 假手雷
 * - 魔术师可在商店花费200金币购买
 * - 有拉栓动作，但不会放出手雷
 * - 模仿真实手雷的使用方式，用于迷惑敌人
 */
public class FakeGrenadeItem extends Item {
    public static final int MAX_CHARGE_TIME = 20; // 最大蓄力时间（ticks），对应1秒

    public FakeGrenadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClientSide) {
            if (user instanceof Player player && player.getCooldowns().isOnCooldown(stack.getItem())) {
                return;
            }
            
            // 设置冷却时间
            if (user instanceof Player player) {
                player.getCooldowns().addCooldown(stack.getItem(), 25 * 20);
            }

            // 计算蓄力时间
            int chargeTime = this.getUseDuration(stack, user) - remainingUseTicks;

            // 确保蓄力时间在合理范围内
            chargeTime = Math.max(0, Math.min(chargeTime, MAX_CHARGE_TIME));

            // 播放投掷声音
            world.playSound(null, user.getX(), user.getY(), user.getZ(), 
                    TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL, 
                    0.5F, 1F + (world.random.nextFloat() - .5f) / 10f);

            // 假手雷不创建实体，只播放声音
            // 这样可以让敌人以为有手雷飞过来，但实际上没有

            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), 
                        BuiltInRegistries.ITEM.getKey(this));
            }

            // 假手雷不会被消耗
        }
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }
}
