package net.exmo.sre.sixtyseconds.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 药剂净化试剂（酿造 · 药剂净化科技）：饮用后清除身上的<b>所有药水效果</b>（好坏都清，同牛奶）。
 */
public class SixtySecondsPotionCleanserItem extends Item {

    private static final int USE_DURATION = 40;

    public SixtySecondsPotionCleanserItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide) {
            user.removeAllEffects();
            if (user instanceof ServerPlayer player) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.potion_cleansed"), true);
            }
            level.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 1.1F);
        }
        return stack;
    }
}
