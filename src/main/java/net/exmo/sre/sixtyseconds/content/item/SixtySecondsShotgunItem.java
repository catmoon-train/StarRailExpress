package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * 霰弹枪：射程短、<b>锥形散射</b>范围伤害——命中准星前方锥体内的全部目标；
 * 距离越近伤害越高（0 距离 ≈ 2×基础伤，满射程 ≈ 0.5×）。对怪物造成高额伤害
 * （近距足以重创 Boss；普通怪基本一枪带走）。使用霰弹枪子弹。
 */
public class SixtySecondsShotgunItem extends SixtySecondsGunItem {

    /** 散射锥半角余弦（约 20°）。 */
    private static final double CONE_COS = 0.94;
    /** 对怪物的基础伤害（随距离衰减；Boss 单次受伤有封顶，近射多打几枪）。 */
    private static final float MONSTER_BASE_DAMAGE = 300.0F;

    public SixtySecondsShotgunItem(Properties properties, int cooldownTicks, double range, int playerDamage,
            Supplier<Item> ammoSupplier) {
        super(properties, cooldownTicks, range, playerDamage, 1, false, ammoSupplier, 0, 0);
    }

    /** 锥形范围结算：忽略单体 hit，扫描射程内视线锥体中的所有可命中目标。 */
    @Override
    protected void resolveHit(ServerPlayer shooter, ServerLevel level, Entity hit) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle().normalize();
        AABB box = shooter.getBoundingBox().inflate(range);
        for (Entity entity : level.getEntities(shooter, box,
                e -> e instanceof LivingEntity && e.isAlive())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || (dist > 1.0E-4 && to.normalize().dot(look) < CONE_COS)) {
                continue;
            }
            double falloff = Math.max(0.5, 2.0 - 1.5 * dist / range); // 近距 2× → 满射程 0.5×
            if (entity instanceof ServerPlayer target) {
                if (!GameUtils.isPlayerAliveAndSurvival(target)
                        || SixtySecondsHealthSystem.isPvpBlocked(level, shooter, target)) {
                    continue;
                }
                SixtySecondsHealthSystem.applyInjury(target, shooter, (int) Math.round(playerDamage * falloff));
            } else if (entity instanceof Player) {
                continue; // 非本模式存活判定的玩家不结算
            } else if (entity instanceof LivingEntity living) {
                living.hurt(shooter.damageSources().playerAttack(shooter),
                        (float) (MONSTER_BASE_DAMAGE * falloff));
            }
        }
    }
}
