package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.ModComponents;

public class DefibrillatorItem extends Item {
    public DefibrillatorItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeCharged) {
        if (!level.isClientSide && user instanceof Player player) {
            if (this.getUseDuration(stack, user) - timeCharged >= 10) {
                net.minecraft.world.phys.HitResult hitResult = getDefibrillatorTarget(player);

                if (hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHitResult) {
                    if (entityHitResult.getEntity() instanceof Player target) {
                        // 检查目标是否为存活的玩家
                        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.defibrillator.must_target_alive"),
                                    true);
                            return;
                        }

                        // 检查目标是否已经有起搏器保护
                        DefibrillatorComponent component = ModComponents.DEFIBRILLATOR.get(target);
                        if (component.hasProtection()) {
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.defibrillator.already_protected"),
                                    true);
                            return;
                        }

                        // 设置120秒的保护时间
                        component.setProtection(120 * 20);

                        // 通知使用者
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.defibrillator.used",
                                        target.getName()),
                                true);

                        // 通知目标玩家
                        target.displayClientMessage(
                                Component.translatable("message.noellesroles.defibrillator.protected"),
                                true);

                        // 消耗物品
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    }
                }
            }
        }
    }

    public static net.minecraft.world.phys.HitResult getDefibrillatorTarget(Player user) {
        return net.minecraft.world.entity.projectile.ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            return entity instanceof Player;
        }, 3.0F);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
}