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

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.network.CustomRoleClientNetwork;
import io.wifi.starrailexpress.customrole.CustomRoleLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.util.Locale;

public final class RavenHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RAVEN_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(player);
            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 55;

            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.charge_progress",
                            String.format(Locale.ROOT, "%.2f", raven.moodProgress),
                            String.format(Locale.ROOT, "%.2f", raven.moodProgressThreshold)),
                    x, y, 0x8B5EB8);

            // Hunt charges
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.charges", raven.charges, RavenPlayerComponent.MAX_CHARGES),
                    x, y + 11, 0x6B4B9E);

            // Kill progress
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.kills", raven.kills, raven.requiredKills),
                    x, y + 22, 0xA66DCC);

            // Cooldown countdown
            if (raven.cooldownTicks > 0) {
                int seconds = (raven.cooldownTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.cooldown", seconds),
                        x, y + 33, 0xAAAAAA);
            }

            // Hunt time remaining during hunt
            if (raven.isHunting()) {
                int seconds = (raven.huntTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.hunt_time", seconds),
                        x, y - 11, 0xCC8844);
            }

            // Target role during hunt
            if (raven.isHunting() && raven.targetRoleId != null) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.target", roleDisplayName(raven.targetRoleId)),
                        x, y - 22, 0xFF5555);
            }

            // Return hint during hunt
            if (raven.isHunting()) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.return_hint"),
                        x, y - 33, 0xFFD700);
            }
        });
    }

    private static Component roleDisplayName(ResourceLocation roleId) {
        String path = roleId.getPath();
        var customData = CustomRoleLoader.getCustomRoleData(path);
        if (customData != null && !customData.displayName.isEmpty()) {
            return Component.literal(customData.displayName);
        }
        customData = CustomRoleClientNetwork.getSyncedRole(path);
        if (customData != null && !customData.displayName.isEmpty()) {
            return Component.literal(customData.displayName);
        }
        return Component.translatable("announcement.star.role." + path);
    }
}
