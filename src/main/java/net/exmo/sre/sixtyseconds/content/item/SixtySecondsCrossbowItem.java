package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsArrowEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;

/** 60s 弩 — 继承原版 CrossbowItem，使用60s箭矢，蓄力时间与原版一致 */
public class SixtySecondsCrossbowItem extends CrossbowItem {

    private final float powerMult;
    private final int drawTicks; // 蓄力时间，与原版弩一致=25

    public SixtySecondsCrossbowItem(Properties properties, float powerMult, int drawTicks) {
        super(properties);
        this.powerMult = powerMult;
        this.drawTicks = drawTicks;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // 原版弩：72000 (无限)，蓄力进度由 drawTicks/pull 谓词控制
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level))
            return InteractionResultHolder.pass(stack);

        // 已装填 → 射击
        if (CrossbowItem.isCharged(stack)) {
            if (!level.isClientSide) {
                shoot60sArrow(level, player, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // 未装填 → 需要60s箭才能开始蓄力
        if (!player.isCreative() && findArrowSlot(player) < 0)
            return InteractionResultHolder.fail(stack);

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (level.isClientSide) return;
        if (CrossbowItem.isCharged(stack)) return; // 已装填，不再处理

        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        if (usedTicks >= drawTicks) {
            // 消耗一支60s箭 → 装填
            if (entity instanceof ServerPlayer player && !player.isCreative()) {
                int slot = findArrowSlot(player);
                if (slot < 0) return; // 箭不够，继续蓄力等待
                player.getInventory().getItem(slot).shrink(1);
            }
            // 设置 CHARGED_PROJECTILES → isCharged=true → charged 模型谓词生效
            stack.set(DataComponents.CHARGED_PROJECTILES,
                    ChargedProjectiles.of(new ItemStack(Items.ARROW)));
            // 播放装填完成音效
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // 弩的装填由 onUseTick 自动完成，releaseUsing 不做任何事
        // 射击由 use() 中的 isCharged 判断触发
    }

    private void shoot60sArrow(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp))
            return;

        ArrowType arrowType = ArrowType.CRUDE;
        int slot = findArrowSlot(player);
        if (slot >= 0) {
            arrowType = ((SixtySecondsArrowItem) player.getInventory().getItem(slot).getItem()).type();
            if (!player.isCreative())
                player.getInventory().getItem(slot).shrink(1);
        } else if (!player.isCreative()) {
            return;
        }

        float monsterDamage = arrowType.monsterDamage * powerMult * SixtySecondsBalance.BOW_DAMAGE_MULT;
        int playerInjury = Math.max(1, Math.round(arrowType.playerInjury * powerMult * SixtySecondsBalance.BOW_DAMAGE_MULT));

        SixtySecondsArrowEntity arrow = new SixtySecondsArrowEntity(serverLevel, sp,
                new ItemStack(arrowType.item()), stack);
        arrow.configure(arrowType, monsterDamage, playerInjury);
        arrow.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0F,
                3.15F * powerMult, 1.0F);
        arrow.setCritArrow(true);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        serverLevel.addFreshEntity(arrow);

        // 清除 charged 状态
        stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        stack.hurtAndBreak(1, sp, LivingEntity.getSlotForHand(sp.getUsedItemHand()));
        sp.getCooldowns().addCooldown(this, 5);
        serverLevel.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
    }

    private static int findArrowSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof SixtySecondsArrowItem)
                return i;
        }
        return -1;
    }
}
