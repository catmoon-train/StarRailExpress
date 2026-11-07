package org.agmas.noellesroles.mixin.client.roles.youlu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.client.YouluSmokeClient;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在世界和后处理完成后遮住烟内视野，保留随后绘制的 HUD 和菜单。 */
@Mixin(GameRenderer.class)
public abstract class YouluSmokeScreenMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", shift = At.Shift.AFTER))
    private void nr$hideWorldInsideSmoke(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel && YouluSmokeClient.isCameraInsideSmoke()) {
            // 直接清颜色缓冲，避免近处实体、发光轮廓、光影或 F1 绕过世界雾。
            RenderSystem.clearColor(0.015F, 0.015F, 0.015F, 1.0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }
}
