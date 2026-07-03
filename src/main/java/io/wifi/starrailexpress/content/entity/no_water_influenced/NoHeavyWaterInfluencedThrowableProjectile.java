// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package io.wifi.starrailexpress.content.entity.no_water_influenced;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public abstract class NoHeavyWaterInfluencedThrowableProjectile extends Projectile {
   protected NoHeavyWaterInfluencedThrowableProjectile(EntityType<? extends NoHeavyWaterInfluencedThrowableProjectile> entityType,
         Level level) {
      super(entityType, level);
   }

   protected NoHeavyWaterInfluencedThrowableProjectile(EntityType<? extends NoHeavyWaterInfluencedThrowableProjectile> entityType,
         double d, double e, double f, Level level) {
      this(entityType, level);
      this.setPos(d, e, f);
   }

   protected NoHeavyWaterInfluencedThrowableProjectile(EntityType<? extends NoHeavyWaterInfluencedThrowableProjectile> entityType,
         LivingEntity livingEntity, Level level) {
      this(entityType, livingEntity.getX(), livingEntity.getEyeY() - (double) 0.1F, livingEntity.getZ(), level);
      this.setOwner(livingEntity);
   }

   public boolean shouldRenderAtSqrDistance(double d) {
      double e = this.getBoundingBox().getSize() * (double) 4.0F;
      if (Double.isNaN(e)) {
         e = (double) 4.0F;
      }

      e *= (double) 64.0F;
      return d < e * e;
   }

   public boolean canUsePortal(boolean bl) {
      return true;
   }

   public void tick() {
      super.tick();
      HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
      if (hitResult.getType() != Type.MISS) {
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

         h = 0.98F;
      } else {
         h = 0.99F;
      }

      this.setDeltaMovement(vec3.scale((double) h));
      this.applyGravity();
      this.setPos(d, e, f);
   }

   protected double getDefaultGravity() {
      return 0.03;
   }

   /**
    * 禁用气泡柱对其的影响
    */
   @Override
    public void onInsideBubbleColumn(boolean bl) {
      Vec3 vec3 = this.getDeltaMovement();
      double d;
      if (bl) {
         d = Math.max(-0.3, vec3.y - 0.03);
      } else {
         d = Math.min(-0.1, vec3.y + 0.03);
      }

      this.setDeltaMovement(vec3.x, d, vec3.z);
      this.resetFallDistance();
   }

   /**
    * 禁用气泡柱对其的影响 2
    */
   @Override
   public void onAboveBubbleCol(boolean bl) {
      Vec3 vec3 = this.getDeltaMovement();
      double d;
      if (bl) {
         d = Math.max(-0.9, vec3.y - 0.03);
      } else {
         d = Math.min(-0.1, vec3.y + 0.05);
      }

      this.setDeltaMovement(vec3.x, d, vec3.z);
   }

   /**
    * 禁用水对其的影响
    */
   @Override
   public boolean isPushedByFluid() {
      return false;
   }
}
