package org.agmas.noellesroles.mixin.client.roles.morphling;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MorphilingHudMixin {
    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void phantomHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.isSpectator())
            return;
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());

        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.MORPHLING)) {
            final var morphComp = MorphlingPlayerComponent.KEY.get(Minecraft.getInstance().player);

            final var morphTicks = morphComp.getMorphTicks();
            int seconds = (int) (morphTicks * 0.05);
            boolean is_cooldown = false;
            if (seconds < 0) {
                seconds = -seconds;
                is_cooldown = true;
            }
            MutableComponent content;
            if (seconds > 0) {
                if (is_cooldown) {
                    content = Component.translatable("morphling.cooldown", (seconds));
                } else {
                    content = Component.translatable("morphling.tip", (seconds));
                }
            } else
                content = Component.translatable("morphling.ready");
            context.drawString(getFont(), content,
                    context.guiWidth() - getFont().width(content) - 12,
                    context.guiHeight() - 20, ModRoles.MORPHLING.color());
        }
    }
}
