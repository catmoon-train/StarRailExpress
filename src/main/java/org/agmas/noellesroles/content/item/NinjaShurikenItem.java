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

    // private static final float SHURIKEN_RANGE = 20.0F;

    public NinjaShurikenItem(Properties properties) {
        super(properties);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (user.isSpectator() || user.getCooldowns().isOnCooldown(ModItems.NINJA_SHURIKEN)) {
            return InteractionResultHolder.pass(itemStack);
        }
        user.startUsingItem(hand);
        // user.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0f, 1.0f);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        if (livingEntity instanceof Player attacker) {
            if (attacker.getCooldowns().isOnCooldown(ModItems.NINJA_SHURIKEN)) {
                return itemStack;
            }
            if (!livingEntity.isSpectator()) {
                // 发射飞刀
                if (level.isClientSide) {
                    ClientPlayNetworking.send(new TryThrowItemPacket());
                }
                // itemStack.shrink(1);
                return itemStack;
            }
        }
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
        return 5;
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