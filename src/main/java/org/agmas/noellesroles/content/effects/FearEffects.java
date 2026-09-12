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

package org.agmas.noellesroles.content.effects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 害怕：先坐下 1 秒，随后屏幕与玩家模型发抖。
 */
public final class FearEffects {
    public static final int DURATION_TICKS = 80;
    public static final int SIT_TICKS = 20;

    private FearEffects() {
    }

    public static boolean isSitting(LivingEntity entity) {
        MobEffectInstance instance = effect(entity);
        if (instance == null) {
            return false;
        }
        if (instance.isInfiniteDuration()) {
            return Math.floorMod(entity.tickCount, DURATION_TICKS) < SIT_TICKS;
        }
        return instance.getDuration() > DURATION_TICKS - SIT_TICKS;
    }

    public static boolean isTrembling(LivingEntity entity) {
        MobEffectInstance instance = effect(entity);
        if (instance == null) {
            return false;
        }
        if (instance.isInfiniteDuration()) {
            return Math.floorMod(entity.tickCount, DURATION_TICKS) >= SIT_TICKS;
        }
        return instance.getDuration() <= DURATION_TICKS - SIT_TICKS;
    }

    private static MobEffectInstance effect(LivingEntity entity) {
        return entity == null ? null : entity.getEffect(ModEffects.FEAR);
    }
}
