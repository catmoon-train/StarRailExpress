package org.agmas.noellesroles.mixin.client.echo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.agmas.noellesroles.game.modes.echo.client.EchoHotbar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 回声模式分组显示三工具与六物资，替代原版快捷栏。 */
@Mixin(Gui.class)
public abstract class EchoHotbarMixin {
    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (EchoHotbar.render(graphics)) ci.cancel();
    }
    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoItemName(GuiGraphics graphics, CallbackInfo ci) {
        if (EchoHotbar.active()) ci.cancel();
    }
}
