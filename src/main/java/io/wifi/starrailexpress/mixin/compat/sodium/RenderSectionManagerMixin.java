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

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderSectionManager.class)
public class RenderSectionManagerMixin {

    // @Inject(method = "shouldUseOcclusionCulling",
    //         at = @At("HEAD"),
    //         remap = false,
    //         cancellable = true)
    // private void sre$forceNotUseOcclusionCulling(Camera camera, boolean spectator, CallbackInfoReturnable<Boolean> cir) {
    //     if (SREClient.needsChunkOffset()) {
    //         cir.setReturnValue(false);
    //     }
    // }

//    @ModifyExpressionValue(method = "getSearchDistance",
//            at = @At(value = "FIELD",
//                    target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumGameOptions$PerformanceSettings;useFogOcclusion:Z"),
//            remap = false)
//    private boolean sre$forceNotUseFogOcclusion(boolean original) {
//        if (SREClient.needsChunkOffset()) {
//            return false;
//        }
//        return original;
//    }
}
