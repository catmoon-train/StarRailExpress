package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;

/**
 * 解毒试剂
 * - 检测玩家是否中毒、感染或两者兼有
 * - 给予医生对应提示
 * - 不会损坏（不减少物品）
 */
public class AntidoteReagentItem extends Item {
    public AntidoteReagentItem(Properties properties) {
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
                HitResult hitResult = getTarget(player);

                if (hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHitResult) {
                    if (entityHitResult.getEntity() instanceof Player target) {
                        SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.get(target);
                        InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(target);
                        
                        boolean isPoisoned = poisonComponent.poisonTicks > 0;
                        boolean isInfected = infectedComponent.infectedTicks > 0;

                        if (isPoisoned && isInfected) {
                            // 两者兼有
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.antidote_reagent.both", target.getName()), true);
                            poisonComponent.init();
                            poisonComponent.sync();
                            infectedComponent.cure();
                        } else if (isPoisoned) {
                            // 只有中毒
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.antidote_reagent.poisoned_only", target.getName()), true);
                            poisonComponent.init();
                            poisonComponent.sync();
                        } else if (isInfected) {
                            // 只有感染
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.antidote_reagent.infected_only", target.getName()), true);
                            infectedComponent.cure();
                        } else {
                            // 安全
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.antidote_reagent.safe", target.getName()), true);
                        }
                        // 不会损坏（不减少物品数量）
                    }
                }
            }
        }
    }

    public static HitResult getTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player), 10f);
    }
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 200;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
}
