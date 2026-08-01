/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;", at = @At("HEAD"), cancellable = true)
    private static void onGetEntityHitResult(Level level, Entity entity, Vec3 vec3, Vec3 vec32, AABB aABB,
            Predicate<Entity> predicate, float f, CallbackInfoReturnable<EntityHitResult> cir) {
        double closestDistance = Double.MAX_VALUE;
        Entity closestEntity = null;

        for (Entity candidate : level.getEntities(entity, aABB, predicate)) {
            // 忽略轮椅实体
            if (candidate instanceof WheelchairEntity) {
                continue;
            }

            AABB expandedBox = candidate.getBoundingBox().inflate(f);
            Optional<Vec3> hitOptional = expandedBox.clip(vec3, vec32);
            if (hitOptional.isPresent()) {
                double distanceSq = vec3.distanceToSqr(hitOptional.get());
                if (distanceSq < closestDistance) {
                    closestEntity = candidate;
                    closestDistance = distanceSq;
                }
            }
        }

        if (closestEntity == null) {
            cir.setReturnValue(null);
        } else {
            cir.setReturnValue(new EntityHitResult(closestEntity));
        }
        cir.cancel();
    }
}