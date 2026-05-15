package org.agmas.noellesroles.mixin.roles.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.Innocent.builder.BuilderWallPositions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 弹射物碰撞 Mixin
 * 让建筑师的客户端墙能够阻挡弹射物（箭、手榴弹等）
 * 
 * 通过在弹射物 tick 中检查其移动路径是否穿过建筑师的墙方块来拦截弹射物
 */
@Mixin(Projectile.class)
public abstract class ProjectileWallBlockMixin {

    /**
     * 在弹射物 tick 方法的头部注入
     * 沿弹射物的移动轨迹逐步检查是否穿过建筑师的墙方块
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void noellesroles$checkBuilderWallCollision(CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        Level level = self.level();
        if (level.isClientSide) return;
        if (self.isRemoved()) return;

        Vec3 currentPos = self.position();
        Vec3 delta = self.getDeltaMovement();
        Vec3 nextPos = currentPos.add(delta);

        // 沿移动轨迹逐步检测（每步0.5格精度）
        double steps = Math.max(1, Math.ceil(currentPos.distanceTo(nextPos) * 2));
        double invSteps = 1.0 / steps;

        for (int i = 0; i <= steps; i++) {
            double t = i * invSteps;
            double x = currentPos.x + (nextPos.x - currentPos.x) * t;
            double y = currentPos.y + (nextPos.y - currentPos.y) * t;
            double z = currentPos.z + (nextPos.z - currentPos.z) * t;

            BlockPos checkPos = BlockPos.containing(x, y, z);
            if (BuilderWallPositions.isWallAt(checkPos)) {
                // 弹射物撞到了建筑师的墙
                self.discard();
                return;
            }
        }
    }
}
