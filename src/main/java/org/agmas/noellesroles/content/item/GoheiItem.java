package org.agmas.noellesroles.content.item;

import org.agmas.noellesroles.content.entity.DanmukuEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
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

public class GoheiItem extends Item implements ChargeableItem, TrainWeapon {

    public static final int MAX_CHARGE = 20 * 20;
    public static final int CHARGE_TICKS = 10;

    public GoheiItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return MAX_CHARGE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 冷却中 / 观战 / 安全时间：不允许开始蓄力。
        if (user.getCooldowns().isOnCooldown(ModItems.DANMUKU)) {
            return InteractionResultHolder.fail(stack);
        }
        if (user.isSpectator() || user.hasEffect(ModEffects.SAFE_TIME)) {
            return InteractionResultHolder.pass(stack);
        }
        if (MCItemsUtils.countItem(user, ModItems.DANMUKU) <= 0) {
            return InteractionResultHolder.pass(stack);
        }

        // 开始蓄力（松手时在 releaseUsing 里判定是否达到 0.8 秒并投掷）。
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        return itemStack;
    }

    @Override
    public void releaseUsing(ItemStack itemStack, Level level, LivingEntity livingEntity, int timeLeft) {
        int charged = this.getUseDuration(itemStack, livingEntity) - timeLeft;
        if (charged < CHARGE_TICKS) {
            return; // 蓄力不足 0.8 秒，不发射
        }
        if (livingEntity instanceof ServerPlayer user) {
            if (!user.isCreative()) {
                if (MCItemsUtils.clearItem(user, ModItems.DANMUKU, 1) > 0) {
                    GoheiItem.shootDamuku(user, 3f, 0.5f);
                }
                user.getCooldowns().addCooldown(ModItems.DANMUKU, GameConstants.getRevolverDefaultTicks());
            } else {
                GoheiItem.shootDamuku(user, 3f, 0.5f);
            }
        }
    }

    public UseAnim getUseAnimation(ItemStack itemStack) {
        return UseAnim.BRUSH;
    }

    public static void shootDamuku(ServerPlayer user, float velocity, float inaccuracy) {
        final var world = user.serverLevel();
        DanmukuEntity axe = new DanmukuEntity(ModEntities.DANMUKU, user, world,
                ModItems.THROWING_AXE.getDefaultInstance());
        axe.setPos(user.getEyePosition());
        axe.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0f, velocity, inaccuracy);
        axe.setOwner(user);
        world.addFreshEntity(axe);
        user.swing(InteractionHand.MAIN_HAND);
    }
}
