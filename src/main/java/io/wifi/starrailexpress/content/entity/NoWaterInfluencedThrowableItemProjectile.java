package io.wifi.starrailexpress.content.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class NoWaterInfluencedThrowableItemProjectile extends ThrowableItemProjectile {

    public NoWaterInfluencedThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entityType,
            Level level) {
        super(entityType, level);
    }

    public NoWaterInfluencedThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entityType, double d,
            double e, double f, Level level) {
        super(entityType, d, e, f, level);
    }

    public NoWaterInfluencedThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> entityType,
            LivingEntity livingEntity, Level level) {
        super(entityType, livingEntity, level);
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.hitTargetOrDeflectSelf(hitResult);
        }

        this.checkInsideBlocks();
        Vec3 vec3 = this.getDeltaMovement();
        double d = this.getX() + vec3.x;
        double e = this.getY() + vec3.y;
        double f = this.getZ() + vec3.z;
        this.updateRotation();
        float h;
        if (this.isInWater()) {
            for (int i = 0; i < 4; ++i) {
                float g = 0.25F;
                this.level().addParticle(ParticleTypes.BUBBLE, d - vec3.x * (double) 0.25F, e - vec3.y * (double) 0.25F,
                        f - vec3.z * (double) 0.25F, vec3.x, vec3.y, vec3.z);
            }

            h = 0.99F;
        } else {
            h = 0.99F;
        }

        this.setDeltaMovement(vec3.scale((double) h));
        this.applyGravity();
        this.setPos(d, e, f);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
