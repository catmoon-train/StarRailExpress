package org.agmas.harpymodloader.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class CollisionMixin {

//    @Inject(
//            method = "adjustMovementForCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Lnet/minecraft/world/World;Ljava/util/List;)Lnet/minecraft/util/math/Vec3d;",
//            at = @At(value = "RETURN"), cancellable = true)
//    private static void modifyCollisionMovement(Entity entity, Vec3d movement, Box entityBoundingBox, World world, List<VoxelShape> collisions, CallbackInfoReturnable<Vec3d> cir) {
//        // 将碰撞后的移动向量缩小为原来的1/10 除了y
//        cir.setReturnValue(cir.getReturnValue().multiply(0.2, 1, 0.2));
//    }
}