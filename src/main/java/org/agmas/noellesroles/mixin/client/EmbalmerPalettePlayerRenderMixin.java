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

package org.agmas.noellesroles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.ClientEmbalmerPaletteState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 嬉命人·调色盘：给活着的玩家实体覆一层随机 RGB。
 *
 * <p>把 {@code EntityModel#renderToBuffer} 的颜色参数替换为「原色向调色盘颜色混合」的结果，
 * 尸体不走这里，见 {@code io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer}。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class EmbalmerPalettePlayerRenderMixin {

    private static final String RENDER_DESC =
            "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    private static final ThreadLocal<LivingEntity> NR$RENDERING_ENTITY = new ThreadLocal<>();

    @Inject(method = "render" + RENDER_DESC, at = @At("HEAD"))
    private void nr$captureRenderingEntity(LivingEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        NR$RENDERING_ENTITY.set(entity);
    }

    @Inject(method = "render" + RENDER_DESC, at = @At("RETURN"))
    private void nr$releaseRenderingEntity(LivingEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        NR$RENDERING_ENTITY.remove();
    }

    @WrapOperation(
            method = "render" + RENDER_DESC,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void nr$applyPaletteColor(EntityModel<?> instance, PoseStack poseStack, VertexConsumer buffer,
            int packedLight, int packedOverlay, int color, Operation<Void> original) {
        LivingEntity entity = NR$RENDERING_ENTITY.get();
        int result = color;
        if (entity instanceof Player player) {
            result = ClientEmbalmerPaletteState.blend(player.getUUID(), color);
        }
        original.call(instance, poseStack, buffer, packedLight, packedOverlay, result);
    }
}
