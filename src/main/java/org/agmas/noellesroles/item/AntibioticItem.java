package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.repack.HSRSounds;
import org.jetbrains.annotations.NotNull;

/**
 * 抗生素
 * - 一次性道具
 * - 对目标使用后使目标解除中毒（参考解毒剂）
 */
public class AntibioticItem extends Item {
    public AntibioticItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }
    
    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (!user.isSpectator()) {
            if (remainingUseTicks < this.getUseDuration(stack, user) - 10 && user instanceof Player) {
                Player attacker = (Player) user;
                HitResult collision = getAntibioticTarget(attacker);
                if (collision instanceof EntityHitResult) {
                    EntityHitResult entityHitResult = (EntityHitResult) collision;
                    Entity target = entityHitResult.getEntity();
                    if (attacker instanceof ServerPlayer player) {
                        if (!((double)target.distanceTo(player) > 3.0F)) {
                            final var playerPoisonComponent = SREPlayerPoisonComponent.KEY.get(target);
                            ((SREPlayerPoisonComponent) playerPoisonComponent).init();
                            playerPoisonComponent.sync();
                            target.playSound(HSRSounds.ITEM_SYRINGE_STAB, 0.4F, 1.0F);
                            final var blockPos = target.blockPosition();
                            ((ServerLevel) world).playLocalSound(blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                                    SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.4F, 1.0F, false);
                            player.swing(InteractionHand.MAIN_HAND);
                            if (!player.isCreative()) {
                                player.getMainHandItem().shrink(1);
                            }
                        }
                    }
                }
            }
        }
    }
    
    public static HitResult getAntibioticTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            if (entity instanceof Player player) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    return true;
                }
            }
            return false;
        }, 3.0F);
    }
    
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }
    
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }
}
