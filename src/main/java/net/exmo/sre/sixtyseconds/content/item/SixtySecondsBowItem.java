package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsArrowEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 60s 自研弓（继承原版 BowItem 获取拉弓动画与声效），自定义箭矢与伤害。
 * 拉满时间由 {@link #drawTicks} 决定（影响伤害/速度的充能曲线）。
 */
public class SixtySecondsBowItem extends BowItem {

    private final float powerMult;
    private final int drawTicks;

    public SixtySecondsBowItem(Properties properties, float powerMult, int drawTicks) {
        super(properties);
        this.powerMult = powerMult;
        this.drawTicks = drawTicks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!player.isCreative() && findArrowSlot(player) < 0) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)
                || !SixtySecondsMod.isActive(level)) {
            return;
        }
        int usedTicks = 72000 - timeLeft;
        float charge = charge(usedTicks);
        if (charge < 0.1F) return;

        ArrowType arrowType = ArrowType.CRUDE;
        int slot = findArrowSlot(player);
        if (slot >= 0) {
            arrowType = ((SixtySecondsArrowItem) player.getInventory().getItem(slot).getItem()).type();
            if (!player.isCreative()) {
                player.getInventory().getItem(slot).shrink(1);
            }
        } else if (!player.isCreative()) {
            return;
        }
        float monsterDamage = arrowType.monsterDamage * powerMult * charge;
        int playerInjury = Math.max(1, Math.round(arrowType.playerInjury * powerMult * charge));

        SixtySecondsArrowEntity arrow = new SixtySecondsArrowEntity(serverLevel, player,
                new ItemStack(arrowType.item()), stack);
        arrow.configure(arrowType, monsterDamage, playerInjury);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                charge * powerMult * 3.0F, 1.0F);
        if (charge >= 1.0F) arrow.setCritArrow(true);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        serverLevel.addFreshEntity(arrow);

        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + charge * 0.5F);
    }

    /** 0..1 充能曲线，drawTicks 拉满。 */
    private float charge(int usedTicks) {
        float f = Math.min(1.0F, usedTicks / (float) drawTicks);
        return (f * f + f * 2.0F) / 3.0F;
    }

    private static int findArrowSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof SixtySecondsArrowItem) {
                return i;
            }
        }
        return -1;
    }
}
