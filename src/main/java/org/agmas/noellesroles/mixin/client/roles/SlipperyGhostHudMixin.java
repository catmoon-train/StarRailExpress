package org.agmas.noellesroles.mixin.client.roles;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 滑头鬼 HUD 显示
 * 金币显示已由 MoneyHudMixin 统一处理
 * 此 Mixin 暂时不需要额外显示
 */
@Mixin(Gui.class)
public abstract class SlipperyGhostHudMixin {
    
    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderSlipperyGhostHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        // 金币显示已由 MoneyHudMixin 统一处理在右上角
        // 此方法暂时不需要额外渲染
    }
}