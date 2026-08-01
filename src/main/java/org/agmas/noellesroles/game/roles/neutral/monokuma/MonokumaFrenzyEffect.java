/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.neutral.monokuma;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.flag.FeatureFlagSet;
import org.agmas.noellesroles.Noellesroles;

/**
 * 狂暴前奏效果
 *
 * 当黑白触发狂暴前奏时，给全服所有存活玩家施加此效果：
 * - 移速减少20%
 * - 无法打开背包（客户端拦截）
 * - 客户端应用水墨风shader
 *
 * 效果持续60秒
 */
public class MonokumaFrenzyEffect extends MobEffect {

    public MonokumaFrenzyEffect() {
        super(MobEffectCategory.HARMFUL, 0x808080); // 灰色
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Noellesroles.id("monokuma_frenzy_slow"),
                -0.20, // 减速20%
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean isEnabled(FeatureFlagSet featureFlagSet) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 效果在客户端用于shader判断，服务端用于移速减缓
        return super.applyEffectTick(entity, amplifier);
    }
}
