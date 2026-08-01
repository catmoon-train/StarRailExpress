/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.neutral.commander;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;

public class CommanderHudRender {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.COMMANDER_ID, (guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            {
                var comc = SREAbilityPlayerComponent.KEY.maybeGet(client.player).orElse(null);
                if (comc == null)
                    return;
                int screenWidth = guiGraphics.guiWidth();
                int screenHeight = guiGraphics.guiHeight();
                var font = client.font;
                int yOffset = screenHeight - 14 - font.lineHeight * 3; // 右下角
                int xOffset = screenWidth - 10; // 距离右边缘
                var channelText = Component.translatable("message.commander.channel.normal")
                        .withStyle(ChatFormatting.GREEN);
                var channelTip = Component.translatable("message.commander.channel.normal.tip")
                        .withStyle(ChatFormatting.WHITE);
                var bindTip = Component
                        .translatable("message.commander.channel.normal.tip.bind",
                                NoellesrolesClient.abilityBind
                                        .getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GRAY);
                if (comc.status == 1) {
                    channelText = Component.translatable("message.commander.channel.killer")
                            .withStyle(ChatFormatting.RED);
                    channelTip = Component.translatable("message.commander.channel.killer.tip2")
                            .withStyle(ChatFormatting.WHITE);
                    guiGraphics.drawString(font, channelTip, xOffset - font.width(channelTip),
                            yOffset + 4 + font.lineHeight * 2,
                            Color.WHITE.getRGB());
                    channelTip = Component.translatable("message.commander.channel.killer.tip")
                            .withStyle(ChatFormatting.WHITE);
                }
                guiGraphics.drawString(font, bindTip, xOffset - font.width(bindTip),
                        yOffset - 2 - font.lineHeight, Color.WHITE.getRGB());
                var text = Component.translatable("message.commander.channel.tip", channelText)
                        .withStyle(ChatFormatting.GOLD);
                guiGraphics.drawString(font, text, xOffset - font.width(text), yOffset,
                        Color.WHITE.getRGB());
                guiGraphics.drawString(font, channelTip, xOffset - font.width(channelTip),
                        yOffset + 2 + font.lineHeight,
                        Color.WHITE.getRGB());
                return;
            }
        });
    }
}
