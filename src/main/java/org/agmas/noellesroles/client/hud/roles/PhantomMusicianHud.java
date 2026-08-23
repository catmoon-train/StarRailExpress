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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.neutral.PhantomMusicianRoleData;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 幻音师 HUD
 *
 * 显示：
 * - 传送技能冷却 / 已就绪
 * - 商店音效冷却状态
 */
public class PhantomMusicianHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PHANTOM_MUSICIAN_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            var comp = RoleData.getOptional(PhantomMusicianRoleData.class, client.player);
            if (comp.isEmpty()) return;
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(client.player);

            int screenRight = context.guiWidth();
            int drawY = context.guiHeight();
            var font = client.font;

            Component line;
            int color = ModRoles.PHANTOM_MUSICIAN.color();

            // 传送技能冷却显示（优先显示，最下方）
            if (comp.get().teleportCooldown > 0) {
                int seconds = (comp.get().teleportCooldown + 19) / 20;
                line = Component.translatable("hud.noellesroles.musician_phantom.teleport_cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
            } else if (shop.balance >= PhantomMusicianRoleData.TELEPORT_COST) {
                line = Component.translatable("hud.noellesroles.musician_phantom.teleport_ready")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                line = Component.translatable("hud.noellesroles.musician_phantom.teleport_no_coin")
                        .withStyle(ChatFormatting.GOLD);
            }

            drawY -= font.wordWrapHeight(line, 999999);
            context.drawString(font, line,
                    screenRight - font.width(line), drawY, color);
        });
    }
}
