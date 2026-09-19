package org.agmas.noellesroles.spear;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * 使用「矛」类武器的实体所需的能力接口。
 * <p>
 * 由 {@code LivingEntitySpearMixin} 注入到 {@link net.minecraft.world.entity.LivingEntity} 上，
 * 逻辑本体在 {@link SpearCombat} 中。
 */
public interface SpearUser {
    /** 距上一次「突进（蓄力冲刺）命中」经过的刻数，用于动画。 */
    float getTimeSinceLastKineticAttack(float tickDelta);

    /** 目标是否仍处于接触冷却中（避免同一次冲锋重复命中同一目标）。 */
    boolean isInPiercingCooldown(Entity target, int cooldownTicks);

    /** 记录一次对目标的冲锋接触。 */
    void startPiercingCooldown(Entity target);

    /**
     * 对目标执行一次「穿刺」：伤害 / 击退 / 拉下坐骑。
     *
     * @return 是否成功命中（造成伤害/击退/击落）
     */
    boolean pierce(EquipmentSlot slot, Entity target, float damage, boolean dealDamage, boolean knockback, boolean dismount);
}
