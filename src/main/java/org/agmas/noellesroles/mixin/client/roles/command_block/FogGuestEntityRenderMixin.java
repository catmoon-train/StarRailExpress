package org.agmas.noellesroles.mixin.client.roles.command_block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.FogGuestClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 现有事件无法拦截完整实体渲染；在分发入口遮掉雾内实体及其附属渲染。 */
@Mixin(EntityRenderDispatcher.class)
public abstract class FogGuestEntityRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void nr$hideInFog(E entity, double x, double y, double z, float yaw,
            float partialTick, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (FogGuestClient.hidden(entity)) ci.cancel();
    }
}
