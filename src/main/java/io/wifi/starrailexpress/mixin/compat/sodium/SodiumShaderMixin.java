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


import io.wifi.starrailexpress.compat.SodiumShaderInterface;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import org.spongepowered.asm.mixin.Mixin;

// import java.util.List;
// import java.util.function.Supplier;

@Mixin(SodiumShader.class)
public abstract class SodiumShaderMixin implements SodiumShaderInterface {
//    @Unique
//    private GlUniformBlock uniformOffsets;

    // @Inject(method = "<init>", at = @At("RETURN"))
    // private void tmm$addUniform(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, ShaderBindingContext context, int handle, BlendModeOverride blendModeOverride, List bufferBlendOverrides, CustomUniforms customUniforms, Supplier flipState, float alphaTest, boolean containsTessellation, CallbackInfo ci) {
    //     uniformOffsets = context.bindUniformBlock("ubo_SectionOffsets", 1);
    // }

    // @Override
    // public void tmm$set(GlMutableBuffer buffer) {
    //     if (uniformOffsets == null) {
    //         return;
    //     }

    //     uniformOffsets.bindBuffer(buffer);
    // }
}
