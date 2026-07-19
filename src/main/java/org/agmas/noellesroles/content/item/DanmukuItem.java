package org.agmas.noellesroles.content.item;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class DanmukuItem extends Item implements ChargeableItem {

    public static final int MAX_CHARGE = 20 * 20;
    public static final int CHARGE_TICKS = 16;

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
    }

    public DanmukuItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return MAX_CHARGE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 冷却中 / 观战 / 安全时间：不允许开始蓄力。
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (user.isSpectator() || user.hasEffect(ModEffects.SAFE_TIME)) {
            return InteractionResultHolder.pass(stack);
        }
        // 开始蓄力（松手时在 releaseUsing 里判定是否达到 0.8 秒并投掷）。
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack itemStack, Level level, LivingEntity livingEntity, int timeLeft) {
        int charged = this.getUseDuration(itemStack, livingEntity) - timeLeft;
        if (charged < CHARGE_TICKS) {
            return; // 蓄力不足 0.8 秒，不发射
        }
        if (livingEntity instanceof ServerPlayer user) {
            if (MCItemsUtils.clearItem(user, ModItems.DANMUKU, 1) > 0) {
                GoheiItem.shootDamuku(user, 2f, 0.5f);
            }

            if (!user.isCreative()) {
                user.getCooldowns().addCooldown(ModItems.DANMUKU, GameConstants.getRevolverDefaultTicks());
                itemStack.shrink(1);
            }
        }
    }

    public UseAnim getUseAnimation(ItemStack itemStack) {
        return UseAnim.SPEAR;
    }

}
