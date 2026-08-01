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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 宿命的罪人 HUD：显示不同死因的累积进度。技能冷却由通用 UnifiedSkillHud 显示。
 */
public final class DoomedSinnerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.DOOMED_SINNER_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            DoomedSinnerPlayerComponent component = DoomedSinnerPlayerComponent.KEY.get(player);
            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 40;

            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.doomed_sinner.progress",
                            component.getDistinctCount(), component.requiredReasons),
                    x, y, 0xC07ED8);
        });
    }
}
