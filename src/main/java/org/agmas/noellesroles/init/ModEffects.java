package org.agmas.noellesroles.init;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.effects.FearMarkEffect;
import org.agmas.noellesroles.effects.LiShiJieDarknessEffect;
import org.agmas.noellesroles.effects.LueFengEffect;
import org.agmas.noellesroles.effects.NuoMianYouHunEffect;
import org.agmas.noellesroles.effects.TimeStopEffect;

public class ModEffects {
    /**
     * 掠风效果 - 提供移速加成
     */
    public static final MobEffect LUE_FENG = Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            Noellesroles.id("lue_feng"),
            new LueFengEffect(MobEffectCategory.BENEFICIAL, 0x00FFFF) // 青色
    );

    /**
     * 傩面游魂效果 - 灵界状态（隐身+无敌）
     */
    public static final MobEffect NUO_MIAN_YOU_HUN = Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            Noellesroles.id("nuo_mian_you_hun"),
            new NuoMianYouHunEffect(MobEffectCategory.BENEFICIAL, 0x800080) // 紫色
    );

    /**
     * 里世界黑暗效果 - 失明效果
     */
    public static final MobEffect LI_SHI_JIE_DARKNESS = Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            Noellesroles.id("li_shi_jie_darkness"),
            new LiShiJieDarknessEffect(MobEffectCategory.HARMFUL, 0x000000) // 黑色
    );

    /**
     * 恐惧标记效果 - 标记被恐惧影响的玩家
     */
    public static final MobEffect FEAR_MARK = Registry.register(
            BuiltInRegistries.MOB_EFFECT,
            Noellesroles.id("fear_mark"),
            new FearMarkEffect(MobEffectCategory.HARMFUL, 0x4B0082) // 深紫色
    );
    /**
     * 时间停止效果
     * - 中性效果
     * - 白色粒子
     */
    public static final Holder<MobEffect> TIME_STOP = register("time_stop", new TimeStopEffect());

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
