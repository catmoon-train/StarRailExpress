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

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.client.MirrorReunionSceneManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 破镜重圆生效时隐藏告示牌、旗帜等方块实体。网格剔除不会关掉 BER，必须单独拦截。
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MirrorReunionBlockEntityCullMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void noellesroles$mirrorReunionHideBlockEntities(BlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (blockEntity != null && MirrorReunionSceneManager.shouldHideBlockEntity(blockEntity.getBlockPos())) {
            ci.cancel();
        }
    }
}
