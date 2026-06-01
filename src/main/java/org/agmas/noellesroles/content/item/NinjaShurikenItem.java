package org.agmas.noellesroles.content.item;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.TryThrowItemPacket;

import java.util.List;

public class NinjaShurikenItem extends ThrowingKnife {

    /** 最小蓄力时间：0.2秒 = 4刻 */
    private static final int MIN_CHARGE_TICKS = 4;

    public NinjaShurikenItem(Properties properties) {
        super(properties);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (!(user instanceof Player player)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(ModItems.NINJA_SHURIKEN)) {
            return;
        }
        if (!world.isClientSide) {
            return;
        }
        // 参考短管霰弹枪：蓄力超过最小时间就能发射
        // remainingUseTicks > getUseDuration - MIN_CHARGE_TICKS 表示蓄力不足
        if (remainingUseTicks > this.getUseDuration(stack, user) - MIN_CHARGE_TICKS) {
            // 蓄力不足，取消发射
            return;
        }
        ClientPlayNetworking.send(new TryThrowItemPacket());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (user.isSpectator() || user.getCooldowns().isOnCooldown(ModItems.NINJA_SHURIKEN)) {
            return InteractionResultHolder.pass(itemStack);
        }
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        // 不再自动发射，只返回物品
        return itemStack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 7200;
    }

    @Override
    public String getItemSkinType() {
        return "ninja_shuriken";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.ninja_shuriken.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

}