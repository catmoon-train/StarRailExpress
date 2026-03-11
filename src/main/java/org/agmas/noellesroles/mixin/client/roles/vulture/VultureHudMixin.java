package org.agmas.noellesroles.mixin.client.roles.vulture;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.agmas.noellesroles.component.NoellesRolesAbilityPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.vulture.VulturePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class VultureHudMixin {
    @Shadow public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void phantomHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.isSpectator())
            return;
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(Minecraft.getInstance().player.level());
        NoellesRolesAbilityPlayerComponent abilityPlayerComponent = (NoellesRolesAbilityPlayerComponent) NoellesRolesAbilityPlayerComponent.KEY.get(Minecraft.getInstance().player);
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.VULTURE)) {
            VulturePlayerComponent vulturePlayerComponent = VulturePlayerComponent.KEY.get(Minecraft.getInstance().player);
            int drawY = context.guiHeight();

            Component line = Component.translatable("tip.vulture", vulturePlayerComponent.bodiesEaten, vulturePlayerComponent.bodiesRequired);

            if (abilityPlayerComponent.cooldown > 0) {
                line = Component.translatable("tip.noellesroles.cooldown", abilityPlayerComponent.cooldown/20);
            }

            drawY -= getFont().wordWrapHeight(line, 999999);
            context.drawString(getFont(), line, context.guiWidth() - getFont().width(line), drawY, ModRoles.VULTURE.color());
        }
    }
}
