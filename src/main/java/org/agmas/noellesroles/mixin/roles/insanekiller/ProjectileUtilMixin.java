package org.agmas.noellesroles.mixin.roles.insanekiller;

import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
//    @Inject(method = "getHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",shift = At.Shift.BEFORE))
//    private static void getHitResult(Vec3 vec3, Entity entity, Predicate<Entity> predicate, Vec3 vec32, Level level, float f, ClipContext.Block block, CallbackInfoReturnable<HitResult> cir) {
//        InsaneKillerPlayerComponent.skipPD = true;
//    }
}
