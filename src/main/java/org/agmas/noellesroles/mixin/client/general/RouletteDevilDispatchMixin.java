package org.agmas.noellesroles.mixin.client.general;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.RouletteDevilClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 分发层也取消，防止原版阴影、着火外观或其他替身渲染绕过玩家渲染器。 */
@Mixin(value = EntityRenderDispatcher.class, priority = 2000)
public abstract class RouletteDevilDispatchMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void noellesroles$hideRouletteDevilExtras(Entity entity, double x, double y, double z,
            float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (RouletteDevilClient.suppressPlayer(entity)) ci.cancel();
    }
}
