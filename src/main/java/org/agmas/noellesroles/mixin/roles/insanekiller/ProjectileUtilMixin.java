package org.agmas.noellesroles.mixin.roles.insanekiller;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
//    @Inject(method = "getHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",shift = At.Shift.BEFORE))
//    private static void getHitResult(Vec3 vec3, Entity entity, Predicate<Entity> predicate, Vec3 vec32, Level level, float f, ClipContext.Block block, CallbackInfoReturnable<HitResult> cir) {
//        InsaneKillerPlayerComponent.skipPD = true;
//    }
}
