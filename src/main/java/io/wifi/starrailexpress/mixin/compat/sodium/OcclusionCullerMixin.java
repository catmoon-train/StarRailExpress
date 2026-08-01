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

package io.wifi.starrailexpress.mixin.compat.sodium;

// import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
// import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
// import io.wifi.starrailexpress.client.SREClient;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.injection.At;

@Mixin(OcclusionCuller.class)
public class OcclusionCullerMixin {

//     @WrapOperation(
//             method = "isWithinFrustum",
//             at = @At(
//                     value = "INVOKE",
//                     target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;isBoxVisible(IIIFFF)Z"
//             ),
//             remap = false
//     )
//     private static boolean sre$wrapIsBoxVisible(
//             Viewport viewport,
//             int cx, int cy, int cz,
//             float sx, float sy, float sz,
//             Operation<Boolean> original
//     ) {
//         return original.call(viewport, cx, cy, cz, sx, sy, sz) || SREClient.needsChunkOffset();
//     }
}
