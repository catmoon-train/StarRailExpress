package io.wifi.starrailexpress.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.util.SkinDownloadRetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 原版下载失败不会结束皮肤结果，也不会再次下载；在原纹理上恢复下载。 */
@Mixin(HttpTexture.class)
public abstract class HttpTextureRetryMixin {
    @Shadow private CompletableFuture<?> future;
    @Unique private final SkinDownloadRetry sre$downloadRetry = new SkinDownloadRetry();

    @Inject(method = "loadCallback", at = @At("HEAD"))
    private void sre$markDownloaded(NativeImage image, CallbackInfo ci) {
        sre$downloadRetry.markSucceeded();
    }

    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("RETURN"))
    private void sre$retryFailedDownload(ResourceManager resources, CallbackInfo ci) {
        CompletableFuture<?> download = future;
        sre$downloadRetry.observe(download, () -> {
            if (future != download) {
                return;
            }
            future = null;
            try {
                ((HttpTexture) (Object) this).load(Minecraft.getInstance().getResourceManager());
            } catch (IOException error) {
                SRE.LOGGER.warn("重试玩家皮肤下载失败", error);
            }
        }, (delay, task) -> CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS)
                .execute(() -> RenderSystem.recordRenderCall(task::run)));
    }
}
