package org.agmas.noellesroles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.client.KnifeGlintContext;
import org.spongepowered.asm.mixin.Mixin;

/** 包裹整个持物渲染，包含自定义3D模型在 renderStatic 内接管的路径。 */
@Mixin(ItemRenderer.class)
public abstract class KnifeGlintRenderMixin {
    @WrapMethod(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V")
    private void sre$knifeFoil(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left,
            PoseStack poses, MultiBufferSource buffers, Level level, int light, int overlay, int seed, Operation<Void> original) {
        KnifeGlintContext.render(entity, stack,
                () -> original.call(entity, stack, context, left, poses, buffers, level, light, overlay, seed));
    }
}
