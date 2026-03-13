package org.agmas.noellesroles.mixin.client.roles.phantom;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.effect.MobEffects;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.StarAbilityPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class PhantomHudMixin {
    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void phantomHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.isSpectator())
            return;
        StarGameWorldComponent gameWorldComponent = (StarGameWorldComponent) StarGameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());
        StarAbilityPlayerComponent abilityPlayerComponent = (StarAbilityPlayerComponent) StarAbilityPlayerComponent.KEY
                .get(Minecraft.getInstance().player);
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.PHANTOM)) {
            int drawY = context.guiHeight();

            Component line = Component.translatable("tip.phantom",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage());

            if (abilityPlayerComponent.cooldown > 0) {
                line = Component.translatable("tip.noellesroles.cooldown", abilityPlayerComponent.cooldown / 20);
            }
            var inve = Minecraft.getInstance().player.getEffect(MobEffects.INVISIBILITY);
            if (inve != null) {
                int time = inve.getDuration();
                if (time > 0) {
                    line = Component.translatable("tip.phantom.activing", time / 20,
                            Component.keybind("key.noellesroles.ability"));
                }
            }

            drawY -= getFont().lineHeight;
            context.drawString(getFont(), line, context.guiWidth() - getFont().width(line) - 12, drawY - 12,
                    CommonColors.RED);
        }
    }
}
