package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.network.chat.Component;
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
 * 消毒绷带（灶台合成：破布+酒精）：需按住右键包扎，完成后恢复 {@link #HEAL} 点健康。
 * 使用时间 8 秒（160 ticks），治疗类消耗品。
 */
public class SixtySecondsBandageItem extends Item {
    public static final int HEAL = 30;
    /** 绷带包扎时间：8 秒 */
    private static final int USE_DURATION = 160;

    /** 本绷带的回复量（简易绷带 10 / 消毒绷带 25）。 */
    private final int heal;

    public SixtySecondsBandageItem(Properties properties) {
        this(properties, HEAL);
    }

    public SixtySecondsBandageItem(Properties properties, int heal) {
        super(properties);
        this.heal = heal;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.WOOL_PLACE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(serverPlayer);
            // 健康上限是 healthMax(150)，不是 MAX(100)——MAX 是饥饿/口渴/理智的上限。
            // 满血判定必须以 healthMax 为准，否则 100→150 这段健康永远用不了绷带。
            if (stats.health >= stats.healthMax) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.sixty_seconds.bandage_full"), true);
                return InteractionResultHolder.pass(stack);
            }
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
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(serverPlayer);
        // 健康上限是 healthMax(150)，不是 MAX(100)——否则回复只能到 100、永远回不到 150。
        if (stats.health >= stats.healthMax) {
            return stack;
        }
        // 绷带缓慢恢复：设置 HoT 剩余量，由 SixtySecondsHealthSystem.tick 每秒恢复 1 点
        stats.bandageHealRemaining = Math.min(heal, stats.healthMax - stats.health);
        stats.sync();
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 1.2F);
        serverPlayer.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.bandage_used", stats.bandageHealRemaining), true);
        return stack;
    }
}
