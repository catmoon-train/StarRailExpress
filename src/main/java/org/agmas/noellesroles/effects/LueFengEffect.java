package org.agmas.noellesroles.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 掠风效果
 * 
 * 提供50%移速加成，持续10秒
 */
public class LueFengEffect extends MobEffect {

    public LueFengEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}