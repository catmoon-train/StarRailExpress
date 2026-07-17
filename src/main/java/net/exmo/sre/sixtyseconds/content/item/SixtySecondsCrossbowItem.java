package net.exmo.sre.sixtyseconds.content.item;

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

/** 60s 弩 — 继承原版 CrossbowItem，使用60s箭矢 */
public class SixtySecondsCrossbowItem extends CrossbowItem {

    private final float powerMult;
    private final int drawTicks;

    public SixtySecondsCrossbowItem(Properties properties, float powerMult, int drawTicks) {
        super(properties);
        this.powerMult = powerMult;
        this.drawTicks = drawTicks;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // 原版弩是无限使用时长（直到装填完成自动停止）
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SixtySecondsMod.isActive(level))
            return InteractionResultHolder.pass(stack);

        // 已装填状态：单击发射
        if (CrossbowItem.isCharged(stack)) {
            if (!level.isClientSide) {
                shoot60sArrow(level, player, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // 未装填：需要60s箭才能开始装填
        if (!player.isCreative() && findArrowSlot(player) < 0)
            return InteractionResultHolder.fail(stack);

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        if (!level.isClientSide && usedTicks >= drawTicks && !CrossbowItem.isCharged(stack)) {
            // 消耗一支60s箭 → 设置 charged 状态
            if (entity instanceof ServerPlayer player) {
                if (!player.isCreative()) {
                    int slot = findArrowSlot(player);
                    if (slot >= 0) {
                        player.getInventory().getItem(slot).shrink(1);
                    } else {
                        player.stopUsingItem();
                        return;
                    }
                }
            }
            // 手动设置 CHARGED_PROJECTILES（使 isCharged=true 触发 charged 模型谓词）
            stack.set(DataComponents.CHARGED_PROJECTILES,
                    ChargedProjectiles.of(new ItemStack(Items.ARROW)));
            if (entity instanceof Player player) {
                player.stopUsingItem();
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
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

        float monsterDamage = arrowType.monsterDamage * powerMult;
        int playerInjury = Math.max(1, Math.round(arrowType.playerInjury * powerMult));

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
