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

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THLostForestRoles;

public class MagicianHud {

    public static void register() {
        final BiConsumer<FakeGuiGraphics, DeltaTracker> magicianHud = (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) {
                return;
            }

            SRERole magicianRole = TMMRoles.ROLES.get(ModRoles.MAGICIAN_ID);
            if (magicianRole == null) {
                return;
            }

            var magicianComponent = ModComponents.MAGICIAN.get(client.player);
            if (magicianComponent == null) {
                return;
            }

            ResourceLocation disguiseId = magicianComponent.getDisguiseRoleId();
            if (disguiseId == null) {
                return;
            }

            // 获取伪装角色的翻译
            Component roleText = Component.translatable("announcement.star.role." + disguiseId.getPath());
            Component fullText = Component.translatable("message.magician.cosplay", roleText)
                    .withStyle(ChatFormatting.GOLD);

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int textWidth = client.font.width(fullText);

            // 右下角显示，留出一些边距
            int x = screenWidth - textWidth - 10;
            int y = screenHeight - 35;

            context.drawString(client.font, fullText, x, y, 0xFFD700);
        };
        RoleHudRenderCallback.EVENT.register(THLostForestRoles.KAGUYA_ID, magicianHud);
        RoleHudRenderCallback.EVENT.register(ModRoles.MAGICIAN_ID, magicianHud);
    }
}
