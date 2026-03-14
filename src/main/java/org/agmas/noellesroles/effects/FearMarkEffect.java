package org.agmas.noellesroles.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 恐惧标记效果
 * 
 * 标记被恐惧影响的玩家，用于追踪SAN掉落
 */
public class FearMarkEffect extends MobEffect {

    public FearMarkEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}