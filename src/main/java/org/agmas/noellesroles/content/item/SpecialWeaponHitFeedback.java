package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 特殊原版武器的命中反馈；只在虚拟伤害成功后调用，不再次进入伤害/击杀管线。 */
public final class SpecialWeaponHitFeedback {
    private SpecialWeaponHitFeedback() {
    }

    /** 普通近战反馈：基础受击击退 + 攻击击退属性 + 满蓄力疾跑击退。 */
    public static void melee(ServerPlayer attacker, ServerPlayer target, boolean sprintHit) {
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        hit(attacker, target);
        knockback(target, 0.4D, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        double extra = attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK) + (sprintHit ? 1.0D : 0.0D);
        if (extra > 0.0D) {
            knockback(target, extra * 0.5D,
                    Math.sin(Math.toRadians(attacker.getYRot())), -Math.cos(Math.toRadians(attacker.getYRot())));
            attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            attacker.setSprinting(false);
        }
        if (sprintHit) {
            attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** 广播原版受击包（客户端负责红闪和受伤音效），并向受击玩家发送方向动画。 */
    public static void hit(ServerPlayer attacker, ServerPlayer target) {
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        // 不调用 hurt，亦不重置无敌帧：否则会触发伤害拦截、护盾/角色回调或扣原版血。
        target.hurtDuration = 10;
        target.hurtTime = target.hurtDuration;
        target.serverLevel().broadcastDamageEvent(target, target.damageSources().playerAttack(attacker));
        target.indicateDamage(attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
    }

    /** 沿用原版击退抗性计算；hurtMarked 使 ServerEntity 将速度包也发给受击玩家本人。 */
    public static void knockback(ServerPlayer target, double strength, double x, double z) {
        target.knockback(strength, x, z);
        target.hurtMarked = true;
    }
}
