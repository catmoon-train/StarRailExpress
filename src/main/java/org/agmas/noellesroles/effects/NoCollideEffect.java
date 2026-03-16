package org.agmas.noellesroles.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.flag.FeatureFlagSet;

public class NoCollideEffect extends MobEffect{

    public NoCollideEffect() {
        super(MobEffectCategory.NEUTRAL, java.awt.Color.GREEN.getRGB());
    }

    @Override
    public boolean isEnabled(FeatureFlagSet featureFlagSet) {
        return true;
    }

}
