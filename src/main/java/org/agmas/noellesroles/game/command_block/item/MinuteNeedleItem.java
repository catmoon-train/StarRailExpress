package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.MinuteNeedleEntity;
import org.agmas.noellesroles.game.command_block.PendulumRole;
import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.api.ChargeableItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** 分针：按住右键蓄力一秒后松手投出，每次投掷消耗一件。 */
public class MinuteNeedleItem extends Item implements ChargeableItem {
    public static final int CHARGE_TICKS = 20;
    private static final int MAX_USE_DURATION = 72000;

    public MinuteNeedleItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(this)
                || player.hasEffect(ModEffects.USED_BANED)
                || (!level.isClientSide && !PendulumRole.canUse(player))) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof ServerPlayer self) || stack.isEmpty() || !stack.is(this)
                || getUseDuration(stack, entity) - remainingUseTicks < CHARGE_TICKS
                || !PendulumRole.canUse(self) || self.hasEffect(ModEffects.USED_BANED)
                || self.getCooldowns().isOnCooldown(this)) {
            return;
        }
        if (MinuteNeedleEntity.throwFrom(self, stack)) {
            stack.shrink(1);
            self.swing(self.getUsedItemHand());
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
            InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }
}
