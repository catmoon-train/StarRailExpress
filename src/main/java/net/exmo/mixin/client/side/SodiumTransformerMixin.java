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

package net.exmo.mixin.client.side;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.statement.CompoundStatement;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;
import net.irisshaders.iris.pipeline.transform.transformer.SodiumTransformer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SodiumTransformer.class)
public class SodiumTransformerMixin {
    // _draw_id 是 section 在 region 内的本地索引（0..255），数组按它下标；
    // 须与 ShaderLoaderMixin 及 DefaultChunkRendererMixin 上传的缓冲大小一致
    private static final int SRE_OFFSET_COUNT = 256;

    @Inject(method = "transform", at = @At("TAIL"), remap = false)
    private static void wathe$addVertexOffset(ASTParser t, TranslationUnit tree, Root root, SodiumParameters parameters, CallbackInfo ci) {
        if (parameters.type.glShaderType == ShaderType.VERTEX) {
            List<String> declarations = List.of(
                    "struct Offset { vec4 pos; };",
                    "layout(std140) uniform ubo_SectionOffsets { Offset Offsets["
                            + SRE_OFFSET_COUNT + "]; };"
            );
            tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS, declarations.stream());

            CompoundStatement vertInit = tree.getOneFunctionDefinitionBody("_vert_init");
            if (vertInit != null) {
                vertInit.getStatements().add(
                        t.parseStatement(root, "_vert_position = _vert_position + Offsets[_draw_id].pos.xyz;")
                );
            }
        }
    }
}
