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

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 腿瘸：按药水等级让走路一瘸一拐。等级越高，弱侧步伐越拖、身体越歪。
 * <p>
 * 与原版迈腿周期相同（{@code cos(walkPos * 0.6662)}），右腿前摆时为弱侧。
 */
public class LimpEffect extends SimpleMobEffect {

    /** 与 {@code HumanoidModel.setupAnim} 迈腿频率一致。 */
    public static final float GAIT_FREQ = 0.6662F;

    public LimpEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B4A2A);
    }

    public static int amplifierOf(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(ModEffects.LIMP);
        return instance != null ? instance.getAmplifier() : -1;
    }

    public static boolean isActive(LivingEntity entity) {
        return amplifierOf(entity) >= 0;
    }

    /** 0..1，等级 I≈0.28、II≈0.52、III≈0.72，之后趋近 1。 */
    public static float severity(int amplifier) {
        int level = Math.max(0, amplifier) + 1;
        return Mth.clamp(1f - (float) Math.pow(0.72f, level), 0.2f, 1f);
    }

    /**
     * 右腿前摆阶段为弱侧。等级越高，弱侧占步态的比重越大。
     */
    public static boolean isWeakStep(LivingEntity entity, int amplifier) {
        return isWeakPhase(entity.walkAnimation.position(), amplifier);
    }

    public static boolean isWeakPhase(float walkPos, int amplifier) {
        float threshold = Mth.clamp(-amplifier * 0.08f, -0.35f, 0f);
        return Mth.cos(walkPos * GAIT_FREQ) > threshold;
    }

    /**
     * 弱侧权重 0..severity，可按部分 tick 插值，避免第一人称视角硬切。
     */
    public static float limpWeight(float walkPos, int amplifier) {
        return Mth.clamp(Mth.cos(walkPos * GAIT_FREQ), 0f, 1f) * severity(amplifier);
    }

    /** 弱侧步伐的水平速度倍率。 */
    public static float weakStepScale(int amplifier) {
        return Mth.clamp(0.58f - amplifier * 0.14f, 0.16f, 0.6f);
    }

    /** 弱侧步伐额外侧倾（相对移动的 strafe 分量，朝受伤的右侧）。 */
    public static float sideSway(int amplifier) {
        return 0.08f + amplifier * 0.05f;
    }

    public static float cameraDip(int amplifier) {
        return 0.035f + amplifier * 0.02f;
    }

    public static float cameraPitch(int amplifier) {
        return 3.2f + amplifier * 1.6f;
    }
}
