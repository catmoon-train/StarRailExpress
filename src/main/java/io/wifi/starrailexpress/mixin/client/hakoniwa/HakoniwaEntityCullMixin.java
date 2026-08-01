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

package io.wifi.starrailexpress.mixin.client.hakoniwa;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.HakoniwaVisionClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 箱庭视野：切割盒（被隐藏的屋顶 / 墙体区域）内的实体一并剔除 ——
 * 屋顶上的实体只有玩家自己也上了屋顶（outside，无切割）时才可见。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class HakoniwaEntityCullMixin {

    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean sre$hakoniwaCullEntity(boolean original, Entity entity) {
        if (original && HakoniwaVisionClientHandle.shouldCullEntity(entity)) {
            return false;
        }
        return original;
    }
}
