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

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.content.entity.MechanicalBirdEntity;
import org.agmas.noellesroles.role.ModRoles;

/** 机械小鸟操控提示。 */
public final class SilverWingHud {
    private SilverWingHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SILVER_WING_ID, (graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (!(client.getCameraEntity() instanceof MechanicalBirdEntity bird)) {
                return;
            }
            int width = client.getWindow().getGuiScaledWidth();
            graphics.drawCenteredString(client.font,
                    Component.translatable("hud.noellesroles.silver_wing.bird"), width / 2, 18,
                    0xFFB0C4DE);
            graphics.drawCenteredString(client.font,
                    Component.translatable("hud.noellesroles.silver_wing.controls"), width / 2, 31,
                    0xFFFFFFFF);
            if (bird.isDashing()) {
                int seconds = Math.max(1, (bird.getDashRemainingTicks() + 19) / 20);
                graphics.drawCenteredString(client.font,
                        Component.translatable("hud.noellesroles.silver_wing.dash", seconds), width / 2, 44,
                        0xFFFF6B6B);
            }
        });
    }
}
