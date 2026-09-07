package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class TomatoFormMobEffect extends SimpleMobEffect {

    public TomatoFormMobEffect(MobEffectCategory mobEffectCategory, int i) {
        super(mobEffectCategory, i);
    }

    @Override
    public void onEffectStarted(LivingEntity livingEntity, int i) {
        if (livingEntity instanceof ServerPlayer sp) {
            GameUtils.refreshPlayerDimension(sp);
        }
    }

    @Override
    public void onEffectEnded(LivingEntity livingEntity) {
        if (livingEntity instanceof ServerPlayer sp) {
            GameUtils.refreshPlayerDimension(sp);
        }
    }

}
