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

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.MirrorReunionSceneManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 破镜重圆生效时隐藏所有实体（玩家、掉落物、展示框等），避免坍缩后仍漂在空中。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MirrorReunionEntityCullMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void noellesroles$mirrorReunionHideEntities(Entity entity, Frustum frustum, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (MirrorReunionSceneManager.shouldHideEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
