package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

import org.agmas.noellesroles.client.event.MutableComponentResult;
import org.agmas.noellesroles.client.event.OnMessageBelowMoneyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.mortuusars.exposure.util.color.Color;

@Mixin(Gui.class)
public abstract class MessageBelowMoney {
    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderSimpleHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        MutableComponentResult texts = OnMessageBelowMoneyRenderer.EVENT.invoker().onRenderer(client, context,
                tickCounter);
        List<MutableComponent> infoLines = texts.mutipleContent;
        int y = 20;
        int width = context.guiWidth();
        var font = getFont();
        int lineHeight = font.lineHeight + 4;
        for (var line : infoLines) {
            context.drawString(font, line, width - 10 - font.width(line), y, Color.WHITE.getRGB());
            y += lineHeight;
        }
    }
}