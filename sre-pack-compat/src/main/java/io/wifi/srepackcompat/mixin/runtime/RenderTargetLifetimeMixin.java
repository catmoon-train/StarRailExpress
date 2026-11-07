package io.wifi.srepackcompat.mixin.runtime;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import io.wifi.srepackcompat.render.AbandonedRenderTargets;
import io.wifi.srepackcompat.render.RenderTargetCleanup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** 跟踪基础 RenderTarget 分配的自有资源；正常销毁失效，遗弃资源由引用队列回收。 */
@Mixin(RenderTarget.class)
public abstract class RenderTargetLifetimeMixin {
    @Shadow protected int colorTextureId;
    @Shadow protected int depthBufferId;
    @Shadow public int frameBufferId;
    @Unique private AbandonedRenderTargets.Registration sreCompat$registration;

    @WrapMethod(method = "createBuffers")
    private void sreCompat$allocated(int width, int height, boolean clearError, Operation<Void> original) {
        try {
            original.call(width, height, clearError);
        } finally {
            // 即使 checkStatus/分配失败，已产生但未销毁的编号也必须被跟踪。
            sreCompat$trackCurrentBuffers();
        }
    }

    @WrapMethod(method = "destroyBuffers")
    private void sreCompat$destroyed(Operation<Void> original) {
        try {
            original.call();
        } finally {
            // 成功删除后字段为 -1；部分失败则仅跟踪仍未删除的资源。
            sreCompat$trackCurrentBuffers();
        }
    }

    @Unique
    private void sreCompat$trackCurrentBuffers() {
        if (sreCompat$registration != null) sreCompat$registration.close();
        sreCompat$registration = RenderTargetCleanup.TRACKER.track(this,
                new AbandonedRenderTargets.Handles(colorTextureId, depthBufferId, frameBufferId));
    }
}
