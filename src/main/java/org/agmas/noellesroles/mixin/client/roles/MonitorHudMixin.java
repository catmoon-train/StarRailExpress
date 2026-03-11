package org.agmas.noellesroles.mixin.client.roles;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.UUID;

import org.agmas.noellesroles.component.MonitorPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MonitorHudMixin {

    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderMonitorHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        if (client.player.isSpectator())
            return;

        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.MONITOR))
            return;
        if (!GameFunctions.isPlayerAliveAndSurvival(client.player))
            return;

        MonitorPlayerComponent monitorComponent = MonitorPlayerComponent.KEY.get(client.player);
        UUID target = monitorComponent.markedTarget;

        Component text;
        int color;

        if (monitorComponent.cooldown > 0) {
            int seconds = (monitorComponent.cooldown + 19) / 20;
            text = Component.translatable("gui.noellesroles.monitor.cooldown", seconds);
            color = 0xFF5555; // 红色
        } else {
            text = Component.translatable("gui.noellesroles.monitor.ready");
            color = 0x55FF55; // 绿色
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int textWidth = getFont().width(text);

        // 右下角显示，留出一些边距
        int x = screenWidth - 20;
        int y = screenHeight - 30;
        if (target != null) {
            var player = client.level.getPlayerByUUID(target);
            var player_text = Component.translatable("gui.noellesroles.monitor.target_not_found")
                    .withStyle(ChatFormatting.YELLOW);
            if (player != null) {
                Component display_player = player.getDisplayName();
                player_text = Component
                        .translatable("gui.noellesroles.monitor.target",
                                Component.literal("").append(display_player).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.AQUA);
            }
            context.drawString(getFont(), player_text, x - getFont().width(player_text), y - 20, 0xffffff);

        }
        context.drawString(getFont(), text, x - textWidth, y, color);
    }
}