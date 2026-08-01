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

package io.wifi.mixins.client;

import dev.doctor4t.wathe.client.model.WatheModelLayers;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.model.geom.ModelLayerLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(WatheModelLayers.class)
public interface IHateWatheModelLayers {

    @Overwrite(remap = false)
    static void initialize() {
        // 空实现 —— 阻断渲染器注册、粒子、HUD 等
    }

    @Overwrite(remap = false)
    private static ModelLayerLocation layer(String id, String name) {
        return new ModelLayerLocation(SRE.id(id), name);
    }

    @Overwrite(remap = false)
    private static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(SRE.id(id), "main");
    }
}