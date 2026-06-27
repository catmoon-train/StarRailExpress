package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.singer.SingerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 歌手 HUD 显示
 * 
 * 显示：
 * - 技能冷却时间或就绪提示
 */
public class SingerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SINGER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            // 获取歌手组件
            SingerPlayerComponent singerComp = ModComponents.SINGER.get(client.player);
            if (!singerComp.isActive)
                return;

            Font textRenderer = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();

            // 基础Y位置（屏幕中下方）
            int baseY = screenHeight - 80;

            // ==================== 显示角色名称 ====================
            Component titleText = Component.translatable("hud.noellesroles.singer.title")
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            int titleWidth = textRenderer.width(titleText);
            context.drawString(textRenderer, titleText,
                    (screenWidth - titleWidth) / 2, baseY - 20, 0xFF69B4);

            // ==================== 显示技能状态 ====================
            Component abilityText;
            if (singerComp.isPlayingMusic()) {
                // 冷却中
                abilityText = Component.translatable("hud.noellesroles.singer.playing",
                        String.format("%.0f", ((float) singerComp.musicRemainingTicks / 20)))
                        .withStyle(ChatFormatting.AQUA);
            } else {

                // 就绪
                abilityText = Component.translatable("hud.noellesroles.singer.idle")
                        .withStyle(ChatFormatting.GREEN);
            }
            int abilityWidth = textRenderer.width(abilityText);
            context.drawString(textRenderer, abilityText,
                    (screenWidth - abilityWidth) / 2, baseY,
                    0x55FF55);
        });
    }
}