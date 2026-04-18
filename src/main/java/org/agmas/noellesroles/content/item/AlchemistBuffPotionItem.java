package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import org.agmas.noellesroles.init.NRSounds;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 药剂师增益药水（一次性道具，对目标使用）
 */
public class AlchemistBuffPotionItem extends Item {

    public enum EffectType {
        MOOD_DRAIN_REDUCE("mood_drain_reduction"),
        MOOD_DRAIN_IGNORE("mood_drain_immunity"),
        MOOD_REGEN("mood_regeneration"),
        INFINITE_STAMINA("infinite_stamina"),
        STAMINA_BOOST("stamina_boost"),
        STAMINA_RECOVERY("stamina_recovery");

        private final String effectId;

        EffectType(String effectId) {
            this.effectId = effectId;
        }

        public String getTranslationKey() {
            return "effect.noellesroles." + effectId;
        }
    }

    private final EffectType fixedEffectType;

    public AlchemistBuffPotionItem(Properties settings) {
        this(settings, null);
    }

    public AlchemistBuffPotionItem(Properties settings, @Nullable EffectType effectType) {
        super(settings);
        this.fixedEffectType = effectType;
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

        EffectType appliedEffect = applyEffect(targetPlayer);
        if (appliedEffect == null) {
            return;
        }

        notifyTarget(targetPlayer, appliedEffect);

        target.playSound(NRSounds.ITEM_SYRINGE_STAB, 0.4F, 1.0F);
        final var blockPos = target.blockPosition();
        ((ServerLevel) world).playLocalSound(blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.4F, 1.0F, false);
        serverPlayer.swing(InteractionHand.MAIN_HAND);

        if (!serverPlayer.isCreative()) {
            serverPlayer.getMainHandItem().shrink(1);
        }
    }

    private @Nullable EffectType applyEffect(Player targetPlayer) {
        EffectType effectType = pickEffectType(targetPlayer);
        if (effectType == null) {
            return null;
        }

        switch (effectType) {
            case MOOD_DRAIN_REDUCE -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.MOOD_DRAIN_REDUCTION, 25 * 20, 0, true, true, true));
            case MOOD_DRAIN_IGNORE -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.MOOD_DRAIN_IMMUNITY, 25 * 20, 0, true, true, true));
            case MOOD_REGEN -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.MOOD_REGENERATION, 25 * 20, 0, true, true, true));
            case INFINITE_STAMINA -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.INFINITE_STAMINA, 25 * 20, 0, true, true, true));
            case STAMINA_BOOST -> {
                if (isInfiniteStaminaRole(targetPlayer)) {
                    return null;
                }
                targetPlayer.addEffect(new MobEffectInstance(ModEffects.STAMINA_BOOST, 25 * 20, 0, true, true, true));
            }
            case STAMINA_RECOVERY -> targetPlayer.addEffect(new MobEffectInstance(ModEffects.STAMINA_RECOVERY, 25 * 20, 0, true, true, true));
        }

        return effectType;
    }

    private @Nullable EffectType pickEffectType(Player targetPlayer) {
        if (fixedEffectType != null) {
            return fixedEffectType;
        }

        List<EffectType> candidates = new ArrayList<>(List.of(EffectType.values()));
        if (isInfiniteStaminaRole(targetPlayer)) {
            candidates.remove(EffectType.STAMINA_BOOST);
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(targetPlayer.getRandom().nextInt(candidates.size()));
    }

    private boolean isInfiniteStaminaRole(Player player) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
        SRERole role = gameComponent.getRole(player);
        return role != null && role.getMaxSprintTime(player) == Integer.MAX_VALUE;
    }

    private void notifyTarget(Player targetPlayer, EffectType effectType) {
        targetPlayer.displayClientMessage(
                Component.translatable(
                        "message.noellesroles.alchemist_buff_potion.applied",
                        Component.translatable(effectType.getTranslationKey())
                ).withStyle(ChatFormatting.AQUA),
                true
        );
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
