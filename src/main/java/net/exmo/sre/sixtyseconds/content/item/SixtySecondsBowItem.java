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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 60s 自研弓/弩：拉弓蓄力后右键松开发射 {@link SixtySecondsArrowEntity}——
 * 从背包消耗一支 {@link SixtySecondsArrowItem}，伤害 = 箭矢基础 × 本弓 {@link #powerMult} × 拉弓充能。
 * 不走原版 {@code BowItem}/{@code ProjectileWeaponItem} 的弹药机制（自管 60s 箭矢），
 * 命中结算全在箭矢实体（server-authoritative）。弩（crossbow=true）满蓄更快、力度更高、音效不同。
 */
public class SixtySecondsBowItem extends Item {

    /** 强度倍率（越高箭飞得越快、伤害越高）。 */
    private final float powerMult;
    /** 拉满弓所需 tick（越小越快）。 */
    private final int drawTicks;
    /** 是弩（true）还是弓（false）——影响音效。 */
    private final boolean crossbow;

    public SixtySecondsBowItem(Properties properties, float powerMult, int drawTicks, boolean crossbow) {
        super(properties);
        this.powerMult = powerMult;
        this.drawTicks = drawTicks;
        this.crossbow = crossbow;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!player.isCreative() && findArrowSlot(player) < 0) {
            return InteractionResultHolder.fail(stack); // 没有箭
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)
                || !SixtySecondsMod.isActive(level)) {
            return;
        }
        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        float charge = charge(usedTicks);
        if (charge < 0.1F) {
            return;
        }
        // 取一支箭（创造模式默认简易箭且不消耗）
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
        if (charge >= 1.0F) {
            arrow.setCritArrow(true);
        }
        // 60s 箭矢一律不可拾取回背包（避免刷箭）；创造模式的箭本就无来源
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        serverLevel.addFreshEntity(arrow);

        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + charge * 0.5F);
    }

    /** 拉弓充能（0..1）：随蓄力时间平滑上升，drawTicks 拉满。 */
    private float charge(int usedTicks) {
        float f = Math.min(1.0F, usedTicks / (float) drawTicks);
        return (f * f + f * 2.0F) / 3.0F;
    }

    /** 背包里第一支 60s 箭矢的槽位；没有返回 -1。 */
    private static int findArrowSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof SixtySecondsArrowItem) {
                return i;
            }
        }
        return -1;
    }
}
