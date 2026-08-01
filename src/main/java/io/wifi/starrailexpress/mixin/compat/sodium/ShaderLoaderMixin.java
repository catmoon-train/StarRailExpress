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


// import io.wifi.starrailexpress.compat.IrisHelper;
// import io.wifi.starrailexpress.util.ShaderEditor;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin {
//     @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
//     private static void tmm$addVertexOffset(ResourceLocation name, CallbackInfoReturnable<String> cir) {
//         if (IrisHelper.isIrisShaderPackInUse()) {
//             return;
//         }

// //         if (name.getPath().contains("block_layer_opaque.vsh")) {
// //             String modifiedShader = new ShaderEditor(cir.getReturnValue())
// //                     .addUniform("struct Offset { vec4 pos; };")
// //                     .addUniform("layout(std140) uniform ubo_SectionOffsets { Offset Offsets[256]; };")
// //                     .addBefore("_vert_position",
// //                             "    _vert_position += Offsets[_draw_id].pos.xyz;")
// //                     .build();

// // //            System.out.println(modifiedShader);
// //             cir.setReturnValue(modifiedShader);
// //         }
//     }
}
