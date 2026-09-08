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

package org.agmas.noellesroles.client.hud.roles;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class KaenbyouRinHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.KAENBYOU_RIN.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
           var cca = SREAbilityPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10; // 距离右边缘
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;

            if (cca.cooldown > 0) {
                float cdSeconds = cca.getCooldownSeconds();
                Component cdText = Component.translatable("hud.noellesroles.kaenbyou_rin.cooldown", String.format("%.1f",cdSeconds))
                        .withStyle(ChatFormatting.RED);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y, 0xffffffff);

            }else{
                
                Component cdText = Component.translatable("hud.noellesroles.kaenbyou_rin.ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y, 0xffffffff);
            }
        });
    }
}
