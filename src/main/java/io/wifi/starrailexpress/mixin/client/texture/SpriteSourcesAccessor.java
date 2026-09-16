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

package io.wifi.starrailexpress.mixin.client.texture;

import com.google.common.collect.BiMap;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link SpriteSources} 的私有类型表：原版只在静态初始化里登记自己的五种来源，
 * 模组要新增来源类型（这里是自定义列车物品的动态图集来源）就得往这张表里塞。
 */
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {

    @Accessor("TYPES")
    static BiMap<ResourceLocation, SpriteSourceType> sre$getTypes() {
        throw new AssertionError();
    }
}
