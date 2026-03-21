package org.agmas.noellesroles.init;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import net.minecraft.world.effect.MobEffectCategory;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.effects.NoCollideEffect;
import org.agmas.noellesroles.effects.SimpleMobEffect;
import org.agmas.noellesroles.effects.TimeStopEffect;

public class ModEffects {
    public static final Holder<MobEffect> SKILL_BANED = register("skill_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> BLACK_MONITOR = register("black_monitor", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> MOVE_BANED = register("move_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> TURN_BANED = register("turn_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));
    public static final Holder<MobEffect> USED_BANED = register("used_baned", new SimpleMobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF));

    /**
     * 时间停止效果
     * - 中性效果
     * - 白色粒子
     */
    public static final Holder<MobEffect> TIME_STOP = register("time_stop", new TimeStopEffect());

    /**
     * 无碰撞效果
     * - 中性效果
     * - 绿色粒子
     */
    public static final Holder<MobEffect> NO_COLLIDE = register("no_collide", new NoCollideEffect());

    /**
     * 注册药水效果到注册表
     */

    private static Holder<MobEffect> register(String id, MobEffect statusEffect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Noellesroles.id(id), statusEffect);
    }

    /**
     * 初始化所有药水效果
     */
    public static void init() {

    }
}
