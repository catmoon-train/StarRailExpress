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

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {
    // changes color parameter constant
    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", ordinal = 1), index = 3)
    protected int renderLabelIfPresent(int color, @Local(argsOnly = true) T entity) {
        if (SREClient.isInLobby) {
            return color;

        }
        if (SREClient.gameComponent ==null)return color;
        return  SREClient.gameComponent.isRole(entity.getUUID(), TMMRoles.KILLER) ? CommonColors.RED : color;
    }
}
