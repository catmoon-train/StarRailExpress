package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;

public class BroadcasterHud {

    public static void renderBroadcast(FakeGuiGraphics context, DeltaTracker tickCounter) {
        // 广播消息渲染（所有玩家可见）
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        long nowTime = client.level.getGameTime();
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
            Font textRenderer = client.font;
            for (int i = 0; i < count; i++) {
                if (i >= 1 && (y >= (screenHeight / 2 - 40) || i >= 4) && i < count - 1) {
                    Component message = Component.translatable("message.broadcast.more_message", (count - i - 1))
                            .withStyle(ChatFormatting.GRAY);
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
                int textWidth = textRenderer.width(message);
                int x = (screenWidth - textWidth) / 2;
                int padding = 4;
                int bgColor = 0x80000000;
                context.fill(x - padding, y - padding, x + textWidth + padding,
                        y + textRenderer.lineHeight + padding,
                        bgColor);
                context.drawString(textRenderer, message, x, y, 0xFFFFFF);
                y += 20;
            }
        }
    }

    public static void register() {

        // 播报者角色提示（仅播报者可见）
        RoleHudRenderCallback.EVENT.register(ModRoles.BROADCASTER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            Font font = client.font;
            Component line = Component.translatable("tip.broadcaster.with_cost",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage(), 100);
            int drawY = context.guiHeight() - font.wordWrapHeight(line, 999999);
            context.drawString(font, line, context.guiWidth() - font.width(line), drawY,
                    ModRoles.BROADCASTER.color());
        });
    }
}
