package org.agmas.noellesroles.item;

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
                net.minecraft.world.phys.HitResult hitResult = getTarget(player);

                if (hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHitResult) {
                    if (entityHitResult.getEntity() instanceof Player target) {
                        SREPlayerPoisonComponent component = SREPlayerPoisonComponent.KEY.get(target);
                        boolean isPoisoned = component.poisonTicks > 0;

                        if (isPoisoned) {
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.antidote_reagent.poisoned", target.getName()), true);
                        } else {
                            player.displayClientMessage(Component.translatable(
                                    "message.noellesroles.antidote_reagent.safe", target.getName()), true);
                        }

                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
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