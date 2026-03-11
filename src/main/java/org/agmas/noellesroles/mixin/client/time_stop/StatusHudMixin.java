package org.agmas.noellesroles.mixin.client.time_stop;

import io.wifi.starrailexpress.client.StatusBarHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatusBarHUD.class)
public class StatusHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    public void render(GuiGraphics guiGraphics, float partialTicks, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player!=null) {
            if (player.hasEffect(ModEffects.TIME_STOP)) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 30, 0);
            }
        }
    }
    @Inject(method = "render", at = @At("RETURN"))
    public void render2(GuiGraphics guiGraphics, float partialTicks, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player!=null) {
            if (player.hasEffect(ModEffects.TIME_STOP)) {
                guiGraphics.pose().popPose();
            }
        }
    }
}
