package org.agmas.noellesroles.mixin.client.roles.broadcaster;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class BroadcasterHudMixin {
    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void broadcasterHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        long nowTime = Minecraft.getInstance().level.getGameTime();
        if (NoellesrolesClient.currentBroadcastMessage != null) {
            if (NoellesrolesClient.currentBroadcastMessage.size() > 0) {
                NoellesrolesClient.currentBroadcastMessage.removeIf((messageInfo) -> {
                    return nowTime >= messageInfo.destroyTime();
                });
            }
            int y = 20;
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int count = NoellesrolesClient.currentBroadcastMessage.size();
            // int i = 0;
            for (int i = 0; i < count; i++) {
                if (i >= 1 && (y >= (screenHeight / 2 - 40) || i >= 4) && i < count - 1) {
                    Component message = Component.translatable("message.broadcast.more_message", (count - i - 1))
                            .withStyle(ChatFormatting.GRAY);
                    Font textRenderer = getFont();
                    int textWidth = textRenderer.width(message);
                    int x = (screenWidth - textWidth) / 2;
                    int padding = 4;
                    int bgColor = 0x80000000;
                    context.fill(x - padding, y - padding, x + textWidth + padding,
                            y + textRenderer.lineHeight + padding,
                            bgColor);
                    context.drawString(textRenderer, message, x, y, 0xFFFFFF);
                    y += 20;

                    i = count - 1;
                }
                var info = NoellesrolesClient.currentBroadcastMessage.get(i);
                Component message = info.message();
                Font textRenderer = getFont();
                int textWidth = textRenderer.width(message);
                int x = (screenWidth - textWidth) / 2;
                int padding = 4;
                int bgColor = 0x80000000;
                context.fill(x - padding, y - padding, x + textWidth + padding, y + textRenderer.lineHeight + padding,
                        bgColor);
                context.drawString(textRenderer, message, x, y, 0xFFFFFF);
                y += 20;
            }

        }

        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY
                .get(Minecraft.getInstance().player.level());
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.BROADCASTER)) {
            int drawY = context.guiHeight();

            Component line;
            line = Component.translatable("tip.broadcaster.with_cost",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage(), 100);

            drawY -= getFont().wordWrapHeight(line, 999999);
            context.drawString(getFont(), line, context.guiWidth() - getFont().width(line), drawY,
                    ModRoles.BROADCASTER.color());
        }
    }
}