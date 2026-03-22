package org.agmas.noellesroles.item;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.repack.HSRSounds;
import org.agmas.noellesroles.component.TemporaryEffectPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

/**
 * 狗皮膏药
 * - 一次性道具
 * - 对目标使用后使目标30秒内san值不会下降
 */
public class DogskinPlasterItem extends Item {
    
    /** San值保护持续时间（秒） */
    public static final int SANITY_PROTECTION_DURATION = 30; // 30秒
    
    public DogskinPlasterItem(Properties settings) {
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
                HitResult collision = getDogskinPlasterTarget(attacker);
                if (collision instanceof EntityHitResult) {
                    EntityHitResult entityHitResult = (EntityHitResult) collision;
                    Entity target = entityHitResult.getEntity();
                    if (attacker instanceof ServerPlayer player) {
                        if (!((double)target.distanceTo(player) > 3.0F)) {
                            if (target instanceof Player targetPlayer) {
                                // 使用TemporaryEffectPlayerComponent来设置保护
                                TemporaryEffectPlayerComponent tempEffect = TemporaryEffectPlayerComponent.KEY.get(targetPlayer);
                                tempEffect.setDogskinPlasterProtection(SANITY_PROTECTION_DURATION);
                                targetPlayer.addEffect(new MobEffectInstance(ModEffects.MOOD_DRAIN_IMMUNITY, SANITY_PROTECTION_DURATION * 20, 0, true, true, true));
                                
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
    }
    
    public static HitResult getDogskinPlasterTarget(Player user) {
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
