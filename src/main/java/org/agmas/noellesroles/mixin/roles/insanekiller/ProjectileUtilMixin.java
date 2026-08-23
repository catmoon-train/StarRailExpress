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

package org.agmas.noellesroles.mixin.roles.insanekiller;

import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
//    @Inject(method = "getHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",shift = At.Shift.BEFORE))
//    private static void getHitResult(Vec3 vec3, Entity entity, Predicate<Entity> predicate, Vec3 vec32, Level level, float f, ClipContext.Block block, CallbackInfoReturnable<HitResult> cir) {
//        InsaneKillerRoleData.skipPD = true;
//    }
}
