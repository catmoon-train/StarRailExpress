package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsArrowEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 60s 手弩/重弩 — 继承原版 CrossbowItem，使用60s箭矢 */
public class SixtySecondsCrossbowItem extends CrossbowItem {

    private final float powerMult;
    private final int drawTicks;

    public SixtySecondsCrossbowItem(Properties properties, float powerMult, int drawTicks) {
        super(properties);
        this.powerMult = powerMult;
        this.drawTicks = drawTicks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level))
            return InteractionResultHolder.pass(stack);
        if (!player.isCreative() && findArrowSlot(player) < 0)
            return InteractionResultHolder.fail(stack);
        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return drawTicks > 0 ? drawTicks : 25;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)
                || !SixtySecondsMod.isActive(level)) {
            super.releaseUsing(stack, level, entity, timeLeft);
            return;
        }
        // 已装填则发射60s箭
        if (!CrossbowItem.isCharged(stack)) {
            super.releaseUsing(stack, level, entity, timeLeft);
            return;
        }

        ArrowType arrowType = ArrowType.CRUDE;
        int slot = findArrowSlot(player);
        if (slot >= 0) {
            arrowType = ((SixtySecondsArrowItem) player.getInventory().getItem(slot).getItem()).type();
            if (!player.isCreative())
                player.getInventory().getItem(slot).shrink(1);
        } else if (!player.isCreative()) {
            return;
        }

        float monsterDamage = arrowType.monsterDamage * powerMult;
        int playerInjury = Math.max(1, Math.round(arrowType.playerInjury * powerMult));

        SixtySecondsArrowEntity arrow = new SixtySecondsArrowEntity(serverLevel, player,
                new ItemStack(arrowType.item()), stack);
        arrow.configure(arrowType, monsterDamage, playerInjury);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                3.15F * powerMult, 1.0F);
        arrow.setCritArrow(true);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        serverLevel.addFreshEntity(arrow);

        stack.hurtAndBreak(1, player,
                net.minecraft.world.entity.LivingEntity.getSlotForHand(player.getUsedItemHand()));
        player.getCooldowns().addCooldown(this, drawTicks);

        // 让原版 CrossbowItem 清空 charged 状态
        super.releaseUsing(stack, level, entity, timeLeft);
    }

    private static int findArrowSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof SixtySecondsArrowItem)
                return i;
        }
        return -1;
    }
}
