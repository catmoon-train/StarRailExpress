package org.agmas.noellesroles.mixin.client.roles.stalker;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 跟踪者 HUD Mixin
 * 
 * 显示跟踪者的状态：
 * - 当前阶段
 * - 能量值
 * - 击杀数（二阶段）
 * - 免疫状态（二阶段）
 * - 倒计时（三阶段）
 * - 窥视目标数
 * - 蓄力进度（三阶段）
 */
@Mixin(Gui.class)
public class StalkerHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderStalkerHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
    }
}
