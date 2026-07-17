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

/** 60s 手弩/重弩 — 继承原版 CrossbowItem 获得装填/瞄准动画，使用60s箭矢 */
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
        // 有箭才能拉弦
        if (!player.isCreative() && findArrowSlot(player) < 0)
            return InteractionResultHolder.fail(stack);
        // 用原版弩的充能逻辑（底层处理 charged 状态）
        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return drawTicks > 0 ? drawTicks : 25;
    }

    /**
     * 发射时由原版 performShooting 调用，此处仅 override 以用 60s 箭矢。
     * 实际上我们 hook 在 super.use() 的充能完成后由原版 CrossbowItem 内部自动发射。
     * 为了让弩在服务端正确发射60s箭矢，重写 tryLoadProjectiles 或 createProjectile。
     * 但由于原版 CrossbowItem 内部逻辑复杂，采用最简方式：在 use() 时只做检查，
     * 发射后在服务端替换为60s箭矢逻辑——通过 LivingEntityUseItemTick 或 EntityTick 事件。
     *
     * 最终采用：拦截 setCharged 后的首次 use() 执行60s箭矢发射。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)
                || !SixtySecondsMod.isActive(level)) {
            return;
        }
        // 仅已装填(charged=true)时才发射60s箭
        if (!CrossbowItem.isCharged(stack))
            return;

        // 取60s箭矢
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

        // 卸下charged状态，设置冷却
        CrossbowItem.setCharged(stack, false);
        stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(player.getUsedItemHand()));
        player.getCooldowns().addCooldown(this, drawTicks);
    }

    private static int findArrowSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof SixtySecondsArrowItem)
                return i;
        }
        return -1;
    }
}
