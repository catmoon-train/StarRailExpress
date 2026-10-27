package org.agmas.noellesroles.mixin.client.echo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.game.modes.echo.client.EchoInterceptorSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 追捕者狂暴形态的 1.25 倍体型。
 *
 * <p>放大发生在 {@link PlayerRenderer#scale} 的 RETURN：此时原版已 push 好矩阵并做完 0.9375
 * 的基础缩放，再且仅再乘一次 1.25；皮肤 Morph 不提供另一份缩放。
 *
 * <p>**碰撞箱不变**：这里只动渲染矩阵，不碰 {@code Entity#getDimensions} / {@code Pose} /
 * {@code getBoundingBox}。因此命中判定、射线距离、挤压、服务器位置全部保持原值。
 *
 * <p>为什么不用 {@code LivingEntityRenderer.scale}：1.21.1 的 {@link PlayerRenderer}
 * 重写了该方法且不调用 {@code super}，父类注入永远不会执行（与既有
 * {@code FatSkinnyLivingRenderMixin} 的结论一致）。
 */
@Mixin(PlayerRenderer.class)
public class EchoInterceptorScaleMixin {
    @Inject(
            method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("RETURN"))
    private void echo$interceptorScale(AbstractClientPlayer entity, PoseStack poseStack, float partialTick,
                                       CallbackInfo ci) {
        if (!EchoInterceptorSkin.applies(entity)) return;
        poseStack.scale(EchoInterceptorSkin.SCALE, EchoInterceptorSkin.SCALE, EchoInterceptorSkin.SCALE);
    }
}