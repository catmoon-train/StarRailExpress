package org.agmas.noellesroles.mixin.client.roles.insane;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InsaneHudMixin {
    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void phantomHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.isSpectator())
            return;
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());
        if (gameWorldComponent.isRole(Minecraft.getInstance().player,
                ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {

            final var insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY.get(Minecraft.getInstance().player);
            if (insaneKillerPlayerComponent.inNearDeath()) {
                var text1 = Component.translatable("insane.tip.neardeath.line1").withStyle(ChatFormatting.YELLOW);
                var text2 = Component
                        .translatable("insane.tip.neardeath.line2", insaneKillerPlayerComponent.deathState / 20)
                        .withStyle(ChatFormatting.RED);
                var text3 = Component.translatable("insane.tip.neardeath.line3").withStyle(ChatFormatting.GRAY);
                context.drawString(getFont(), text1, context.guiWidth() - getFont().width(text1) - 10,
                        context.guiHeight() - 40, java.awt.Color.YELLOW.getRGB());
                context.drawString(getFont(), text2, context.guiWidth() - getFont().width(text2) - 10,
                        context.guiHeight() - 30, java.awt.Color.YELLOW.getRGB());
                context.drawString(getFont(), text3, context.guiWidth() - getFont().width(text3) - 10,
                        context.guiHeight() - 20, java.awt.Color.YELLOW.getRGB());
            } else if (insaneKillerPlayerComponent.isActive) {
                var text = Component.translatable("insane.tip.over",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString()).append(" ");
                context.drawString(getFont(), text, context.guiWidth() - getFont().width(text),
                        context.guiHeight() - 20, ModRoles.MORPHLING.color());

            } else {
                final var morphTicks = insaneKillerPlayerComponent.cooldown;
                if (morphTicks > 0) {
                    var text = Component.translatable("insane.tip", ((int) (morphTicks * 0.05))).append(" ");
                    context.drawString(getFont(), text, context.guiWidth() - getFont().width(text),
                            context.guiHeight() - 20, ModRoles.MORPHLING.color());
                } else {
                    var text = Component.translatable("insane.tip.ready",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString()).append(" ");
                    context.drawString(getFont(), text, context.guiWidth() - getFont().width(text),
                            context.guiHeight() - 20, ModRoles.MORPHLING.color());
                }
            }
        }
    }
}
