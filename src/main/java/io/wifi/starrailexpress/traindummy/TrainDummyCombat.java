/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 把对玩家的击杀、虚拟伤害、药水和中毒转到列车假人。
 * 护盾抵挡不算死亡；虚拟血量归零才记死因。
 */
public final class TrainDummyCombat {

    private TrainDummyCombat() {
    }

    public static boolean isDummy(Entity entity) {
        return entity instanceof TrainDummyEntity dummy && dummy.canReceiveHits();
    }

    public static void markHit(TrainDummyEntity dummy, @Nullable Entity attacker) {
        if (dummy != null && dummy.canReceiveHits()) {
            dummy.showHit(attacker);
        }
    }

    /**
     * 直接致死（刀、枪、中毒倒计时结束）。护盾挡住则不算死亡。
     *
     * @return 是否真正死亡
     */
    public static boolean applyLethal(TrainDummyEntity dummy, @Nullable Player attacker, ResourceLocation reason) {
        if (dummy == null || !dummy.canReceiveHits() || reason == null) {
            return false;
        }
        dummy.showHit(attacker);
        if (tryBlock(dummy, attacker, reason, false)) {
            return false;
        }
        dummy.killByReason(attacker, reason);
        return true;
    }

    /**
     * 虚拟血量伤害。先扣虚拟护盾，剩余再扣虚拟血量；足以致死时才问普通护盾和弱效护盾。
     */
    public static boolean applyVirtualDamage(TrainDummyEntity dummy, @Nullable Player attacker, int damage,
            ResourceLocation reason, boolean ignoreVirtualShield, boolean pierceOtherShields) {
        if (dummy == null || !dummy.canReceiveHits() || damage <= 0 || reason == null) {
            return false;
        }
        dummy.showHit(attacker);
        int remaining = ignoreVirtualShield ? damage : dummy.absorbVirtualShield(damage);
        if (remaining <= 0) {
            return true;
        }
        int hp = dummy.virtualHp();
        if (hp - remaining <= 0) {
            if (!pierceOtherShields && tryBlock(dummy, attacker, reason, true)) {
                return false;
            }
            if (pierceOtherShields) {
                dummy.clearShields();
            }
            dummy.killByReason(attacker, reason);
            return true;
        }
        dummy.setVirtualHp(hp - remaining);
        return true;
    }

    public static void applyEffect(TrainDummyEntity dummy, MobEffectInstance effect) {
        if (dummy == null || !dummy.canReceiveHits() || effect == null) {
            return;
        }
        dummy.addEffect(new MobEffectInstance(effect));
    }

    public static void applyPoison(TrainDummyEntity dummy, int ticks, boolean fake, @Nullable UUID poisoner) {
        if (dummy == null || !dummy.canReceiveHits()) {
            return;
        }
        dummy.setPoison(ticks, fake, poisoner);
    }

    /**
     * 普通护盾（含限时）优先于弱效护盾，与玩家死亡管线一致。
     * 攻击者为空时不消耗普通护盾，弱效护盾仍可按死因抵挡。
     */
    private static boolean tryBlock(TrainDummyEntity dummy, @Nullable Player attacker, ResourceLocation reason,
            boolean lethalVirtual) {
        if (attacker != null && dummy.totalShieldLayers() > 0) {
            dummy.consumeShieldLayer();
            dummy.showShieldBroken();
            return true;
        }
        if (dummy.weakCanBlock(reason)) {
            dummy.consumeWeakLayer();
            dummy.showShieldBroken();
            return true;
        }
        return false;
    }
}
