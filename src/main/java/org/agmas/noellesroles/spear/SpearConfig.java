package org.agmas.noellesroles.spear;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.NRSounds;

/**
 * 「下界合金矛」的装配参数：挥击 1.15 秒、攻击距离 2.0~4.5、蓄力前摇 0.4 秒，
 * 击落 / 击退 / 伤害条件分别为 2.5s/7.0、3.5s/5.1、8.75s/4.6，伤害倍率 1.2。
 * <p>
 * 音效注册表初始化时机较晚，这里统一在方法中取值，避免类初始化顺序问题。
 */
public final class SpearConfig {

    private SpearConfig() {
    }

    /** 矛类武器标记（由 {@code NetheriteSpearItem} 实现）。 */
    public interface SpearWeapon {
    }

    /** 挥击时长 1.15 秒。 */
    public static final SpearComponents.SwingAnimation SWING = new SpearComponents.SwingAnimation(23,
            SpearComponents.SwingAnimation.STAB);

    /** 攻击距离 2.0 ~ 4.5 格。 */
    public static final SpearComponents.AttackRange ATTACK_RANGE = new SpearComponents.AttackRange(2.0F, 4.5F);

    /** 蓄力时的移动表现：不减速、可疾跑、不产生交互振动。 */
    public static final SpearComponents.UseEffects USE_EFFECTS = new SpearComponents.UseEffects(1.0F, true, false);

    /** 左键直刺的最低蓄力（1.0 = 必须满蓄力）。 */
    public static final float MINIMUM_ATTACK_CHARGE = 1.0F;

    /** 直刺命中的同一目标接触冷却（tick）。 */
    public static final int PIERCE_CONTACT_COOLDOWN_TICKS = 10;

    /** 普攻（左键直刺）参数：命中盒 0.25、有击退、无击落、挥矛 / 命中音效。 */
    public static SpearComponents.PiercingWeapon piercing() {
        return new SpearComponents.PiercingWeapon(0.25F, true, false,
                sound(NRSounds.SPEAR_ATTACK), sound(NRSounds.SPEAR_HIT));
    }

    /** 右键蓄力冲锋参数（对应原模组 KineticWeapon 的实参）。 */
    public static SpearComponents.KineticWeapon kinetic() {
        return new SpearComponents.KineticWeapon(
                0.125F,
                10,
                8, // delayTicks = 0.4s * 20
                SpearComponents.KineticWeapon.Condition.ofMinSpeed(50, 7.0F), // 2.5s / 7.0
                SpearComponents.KineticWeapon.Condition.ofMinSpeed(70, 5.1F), // 3.5s / 5.1
                SpearComponents.KineticWeapon.Condition.ofMinRelativeSpeed(175, 4.6F), // 8.75s / 4.6
                0.38F,
                1.2F,
                sound(NRSounds.SPEAR_USE), sound(NRSounds.SPEAR_HIT));
    }

    /** 突进（Lunge）附魔的 id 路径。 */
    public static final String LUNGE_ENCHANTMENT_PATH = "lunge";

    /** 单级突进的冲刺速度倍率（原模组 lunge.json 的 base 值）。 */
    public static final float LUNGE_IMPULSE_PER_LEVEL = 0.458F;

    /** 单级突进的消耗（原模组 lunge.json 的 base 值）。 */
    public static final float LUNGE_EXHAUSTION_PER_LEVEL = 4.0F;

    /** 惰性取音效（音效注册表初始化前后取值结果一致）。 */
    private static SoundEvent sound(SoundEvent event) {
        return event;
    }

    /** 便捷判定：物品是否为本项目的「矛」。 */
    public static boolean isSpear(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SpearWeapon;
    }
}
