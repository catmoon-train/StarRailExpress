package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsSicknessSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
 * 药品：需按住右键服用，完成后治愈生病、并解除救援后的感染风险（{@code SixtySecondsSicknessSystem.cure}）。
 * 使用时间 5 秒（100 ticks），治疗类消耗品。一次消耗 1 个。
 */
public class SixtySecondsMedicineItem extends Item {
    /** 服药时间：5 秒 */
    private static final int USE_DURATION = 100;

    public SixtySecondsMedicineItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_EAT;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!(user instanceof ServerPlayer serverPlayer)) {
            return stack;
        }
        if (!SixtySecondsMod.isActive(level)) {
            return stack;
        }
        SixtySecondsSicknessSystem.cure(serverPlayer);
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 1.0F);
        return stack;
    }
}
