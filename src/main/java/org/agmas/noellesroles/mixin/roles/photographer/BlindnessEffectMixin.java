package org.agmas.noellesroles.mixin.roles.photographer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(Gui.class)
public class BlindnessEffectMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableDepthTest()V",shift = At.Shift.BEFORE), cancellable = true)
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        final var player = Minecraft.getInstance().player;
        if (player.hasEffect(MobEffects.UNLUCK)){
            guiGraphics.fill(0,0, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(), 0xFF000000);
        }
        if (player.hasEffect(MobEffects.WEAVING)){
            //屏幕变红（30%透明度）
            guiGraphics.fill(0,0, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(), 0x4DFF0000);
        }
        if (player.hasEffect(MobEffects.RAID_OMEN)) {
            // 试炼之兆效果 - 屏幕变黑（仿照低san值时的效果）并添加渐进渐出
            float effectIntensity = Math.min(1.0f, player.getEffect(MobEffects.RAID_OMEN).getDuration() / 20.0f); // 假设持续时间为20 ticks
            int alpha = (int) (effectIntensity * 255);
            int color = (alpha << 24); // 黑色带透明度
            guiGraphics.fill(0, 0, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(), color);
        }
    }
}

