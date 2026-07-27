package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.SlimeGrenadeEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

/**
 * 粘液弹物品：右键蓄力投掷，蓄力时间越长投掷越远。落点形成半径 4 的粘液区域，范围内玩家无法跳跃且移动缓慢（缓慢 III）。
 */
public class SlimeGrenadeItem extends Item {

    public static final int MAX_CHARGE_TIME = 20; // 最大蓄力时间（ticks），同 GrenadeItem

    public SlimeGrenadeItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator())
            return;
        if (!world.isClientSide) {
            // 计算蓄力时间
            int chargeTime = this.getUseDuration(stack, user) - remainingUseTicks;
            chargeTime = Math.max(0, Math.min(chargeTime, MAX_CHARGE_TIME));

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL,
                    0.5F, 1F + (world.random.nextFloat() - .5f) / 10f);

            SlimeGrenadeEntity grenade = new SlimeGrenadeEntity(ModEntities.SLIME_GRENADE, world);
            grenade.setOwner(user);
            grenade.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());

            // 根据蓄力时间计算投掷速度（最小速度0.4，最大速度1.15）
            float velocity = 0.4F + (0.75F * (float) chargeTime / MAX_CHARGE_TIME);
            grenade.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, velocity, 1.0F);
            world.addFreshEntity(grenade);
        }

        stack.consume(1, user);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
}
