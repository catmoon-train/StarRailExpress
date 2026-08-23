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
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.innocence.RecallerRoleData;
import org.agmas.noellesroles.role.ModRoles;

public class RecallerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RECALLER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                    .get(client.player);
            var recallerOpt = RoleData.getOptional(RecallerRoleData.class, client.player);
            if (recallerOpt.isEmpty())
                return;
            RecallerRoleData recallerPlayerComponent = recallerOpt.get();
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(client.player);

            int drawY = context.guiHeight();

            Component line = Component.translatable("tip.recaller.teleport",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
            if (!recallerPlayerComponent.placed) {
                line = Component.translatable("tip.recaller.place",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
            } else {
                if (playerShopComponent.balance < 100) {
                    line = Component.translatable("tip.recaller.not_enough_money");
                }
            }

            if (abilityPlayerComponent.cooldown > 0) {
                line = Component.translatable("tip.noellesroles.cooldown",
                        abilityPlayerComponent.cooldown / 20);
            }

            drawY -= client.font.wordWrapHeight(line, 999999);
            context.drawString(client.font, line,
                    context.guiWidth() - client.font.width(line), drawY, ModRoles.RECALLER.color());
        });
    }
}
