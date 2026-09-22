package org.agmas.noellesroles.content.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;

/** Removes harmful effects every tick so repeated applications are also blocked. */
public class NegativeEffectResistanceEffect extends MobEffect {
    public NegativeEffectResistanceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x72C9A4);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        for (MobEffectInstance active : new ArrayList<>(entity.getActiveEffects())) {
            if (active.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                entity.removeEffect(active.getEffect());
            }
        }
        return true;
    }
}
