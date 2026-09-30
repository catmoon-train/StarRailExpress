/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 为开启 canUseSpVanillaWeapon 的职业增加烟花弩效果。
 * 原版爆炸结算保持不变；这里额外记录精确命中并扣除周围玩家的虚拟血量。
 *
 * <p>精确命中不再直接判死，改为扣除 {@link #DIRECT_HIT_VIRTUAL_DAMAGE}（20）点虚拟血量，
 * 虚拟血量归零才按 {@code firework_crossbow} 死因判死。
 *
 * <p>溅射伤害默认不会使玩家致死（{@code hurtWithoutKilling}）；
 * 但若射手是<b>杀手职业</b>（{@code isKiller()}，如 Dream / 爆炸狂），
 * 则溅射伤害可以致死（{@code hurt}，死因 {@code firework_crossbow}）。
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketMixin {
    @Shadow
    private List<FireworkExplosion> getExplosions() {
        throw new AssertionError();
    }

    /** 精确命中造成的虚拟伤害（不再直接判死，虚拟血量归零才死）。 */
    @Unique
    private static final int DIRECT_HIT_VIRTUAL_DAMAGE = 20;

    @Unique
    private ServerPlayer noellesroles$directHitPlayer;

    @Unique
    private static boolean noellesroles$isEnabledRocket(FireworkRocketEntity rocket,
            @Nullable ServerPlayer[] shooterHolder) {
        if (SRE.isLobby || rocket.level().isClientSide() || !rocket.isShotAtAngle()) {
            return false;
        }
        if (!(rocket.getOwner() instanceof ServerPlayer shooter)) {
            return false;
        }
        SRERole role = SREGameWorldComponent.KEY.get(rocket.level()).getRole(shooter);
        if (role == null || !role.canUseSpVanillaWeapon()) {
            return false;
        }
        if (shooterHolder != null && shooterHolder.length > 0) {
            shooterHolder[0] = shooter;
        }
        return true;
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void noellesroles$rememberDirectHit(EntityHitResult hitResult, CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        if (!(hitResult.getEntity() instanceof ServerPlayer target)) {
            return;
        }
        if (noellesroles$isEnabledRocket(rocket, null)) {
            noellesroles$directHitPlayer = target;
        }
    }

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void noellesroles$damageDirectHit(EntityHitResult hitResult, CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        if (!(hitResult.getEntity() instanceof ServerPlayer target)
                || noellesroles$directHitPlayer != target) {
            return;
        }
        ServerPlayer[] shooterHolder = new ServerPlayer[1];
        if (!noellesroles$isEnabledRocket(rocket, shooterHolder)) {
            return;
        }
        ServerPlayer shooter = shooterHolder[0];
        if (!shooter.isSpectator() && target != shooter) {
            // 精确命中：扣 20 点虚拟血量，归零才判死（死因仍为 firework_crossbow）
            DreamHealthComponent.KEY.get(target).hurt(shooter, DIRECT_HIT_VIRTUAL_DAMAGE,
                    GameConstants.DeathReasons.FIREWORK_CROSSBOW);
            shooter.getCooldowns().addCooldown(Items.CROSSBOW, 12 * 20);
        }
    }

    @Inject(method = "explode", at = @At("TAIL"))
    private void noellesroles$applyVirtualSplashDamage(CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        ServerPlayer[] shooterHolder = new ServerPlayer[1];
        if (!noellesroles$isEnabledRocket(rocket, shooterHolder)) {
            return;
        }
        List<FireworkExplosion> explosions = getExplosions();
        if (explosions.isEmpty()) {
            return;
        }

        ServerPlayer shooter = shooterHolder[0];
        Level level = rocket.level();
        float explosionDamage = 5.0F + explosions.size() * 2.0F;
        Vec3 explosionPosition = rocket.position();
        AABB affectedArea = rocket.getBoundingBox().inflate(5.0D);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, affectedArea)) {
            if (!(entity instanceof ServerPlayer target) || target == noellesroles$directHitPlayer) {
                continue;
            }
            if (rocket.distanceToSqr(target) > 25.0D) {
                continue;
            }

            boolean visible = false;
            for (int height = 0; height < 2; height++) {
                Vec3 targetPosition = new Vec3(target.getX(), target.getY(0.5D * height), target.getZ());
                if (level.clip(new ClipContext(explosionPosition, targetPosition,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rocket)).getType() == HitResult.Type.MISS) {
                    visible = true;
                    break;
                }
            }
            if (!visible) {
                continue;
            }

            float damage = explosionDamage
                    * Mth.sqrt((float) ((5.0D - rocket.distanceTo(target)) / 5.0D));
            DreamHealthComponent health = DreamHealthComponent.KEY.get(target);
            if (noellesroles$isKillerShooter(rocket, shooter)) {
                // 杀手职业：溅射伤害可以致死
                health.hurt(shooter, Mth.ceil(damage), GameConstants.DeathReasons.FIREWORK_CROSSBOW);
            } else {
                // 其它职业（如网警）：溅射伤害保留 1 点，不致死
                health.hurtWithoutKilling(shooter, Mth.ceil(damage));
            }
        }
    }

    /** 射手是否为杀手职业（杀手溅射可致死；非杀手溅射保留 1 点）。 */
    @Unique
    private static boolean noellesroles$isKillerShooter(FireworkRocketEntity rocket, ServerPlayer shooter) {
        SRERole role = SREGameWorldComponent.KEY.get(rocket.level()).getRole(shooter);
        return role != null && role.isKiller();
    }
}
