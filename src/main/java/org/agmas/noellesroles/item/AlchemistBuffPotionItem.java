package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
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
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.repack.HSRSounds;
import org.jetbrains.annotations.NotNull;

/**
 * 药剂师增益药水（一次性道具，对目标使用）
 */
public class AlchemistBuffPotionItem extends Item {

    public enum EffectType {
        MOOD_DRAIN_REDUCE,
        MOOD_DRAIN_IGNORE,
        MOOD_REGEN,
        INFINITE_STAMINA,
        STAMINA_BOOST,
        STAMINA_RECOVERY
    }

    private final EffectType effectType;

    public AlchemistBuffPotionItem(Properties settings, EffectType effectType) {
        super(settings);
        this.effectType = effectType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (!(user instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (remainingUseTicks >= this.getUseDuration(stack, user) - 10) {
            return;
        }

        HitResult collision = getPotionTarget(serverPlayer);
        if (!(collision instanceof EntityHitResult entityHitResult)) {
            return;
        }

        Entity target = entityHitResult.getEntity();
        if (!(target instanceof Player targetPlayer)) {
            return;
        }
        if ((double) target.distanceTo(serverPlayer) > 3.0F) {
            return;
        }

        applyEffect(targetPlayer);

        target.playSound(HSRSounds.ITEM_SYRINGE_STAB, 0.4F, 1.0F);
        final var blockPos = target.blockPosition();
        ((ServerLevel) world).playLocalSound(blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.4F, 1.0F, false);
        serverPlayer.swing(InteractionHand.MAIN_HAND);

        if (!serverPlayer.isCreative()) {
            serverPlayer.getMainHandItem().shrink(1);
        }
    }

    private void applyEffect(Player targetPlayer) {
        switch (effectType) {
            case MOOD_DRAIN_REDUCE -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.MOOD_DRAIN_REDUCTION, 45 * 20, 0, true, true, true));
            case MOOD_DRAIN_IGNORE -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.MOOD_DRAIN_IMMUNITY, 20 * 20, 0, true, true, true));
            case MOOD_REGEN -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.MOOD_REGENERATION, 40 * 20, 0, true, true, true));
            case INFINITE_STAMINA -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, 20 * 20, 0, true, true, true));
            case STAMINA_BOOST -> {
                SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(targetPlayer.level());
                SRERole role = gameComponent.getRole(targetPlayer);
                boolean isInfiniteStamina = (role != null && role.getMaxSprintTime(targetPlayer) == Integer.MAX_VALUE);
                if (!isInfiniteStamina) {
                    targetPlayer.addEffect(new MobEffectInstance(ModEffects.STAMINA_BOOST, 45 * 20, 0, true, true, true));
                }
            }
            case STAMINA_RECOVERY -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.STAMINA_RECOVERY, 45 * 20, 0, true, true, true));
        }
    }

    public static HitResult getPotionTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            if (entity instanceof Player player) {
                return GameUtils.isPlayerAliveAndSurvival(player);
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
