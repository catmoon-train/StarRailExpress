package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.client.resource.TextureUploadDispatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.HttpTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 下载完成后的上传不能被转服清理游戏任务队列时丢弃。 */
@Mixin(HttpTexture.class)
public abstract class HttpTextureUploadMixin {
    @WrapOperation(method = "loadCallback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"))
    private void sre$keepUploadOnRenderQueue(Minecraft client, Runnable upload, Operation<Void> original) {
        // 原版已在此调用前发布皮肤资源标识，不能再把上传留在可被丢弃的游戏队列里。
        TextureUploadDispatch.dispatch(upload, RenderSystem.isOnRenderThread(),
                task -> RenderSystem.recordRenderCall(task::run));
    }
}
