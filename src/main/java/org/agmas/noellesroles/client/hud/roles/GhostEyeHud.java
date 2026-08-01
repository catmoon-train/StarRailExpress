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

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.vigilante.ghost_eye.GhostEyePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 鬼眼·杨间 HUD：右下角显示被动扫描倒计时 + 主动技能「诡域」冷却。
 */
public class GhostEyeHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GHOST_EYE_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            Font font = client.font;
            GhostEyePlayerComponent ghostComp = GhostEyePlayerComponent.KEY.get(client.player);
            int x = context.guiWidth() - 12;
            int color = ModRoles.GHOST_EYE.color();

            // 第 1 行：被动扫描倒计时（上方）
            MutableComponent scanLine;
            if (ghostComp.revealTicks > 0) {
                scanLine = Component.translatable("hud.ghost_eye.scanning",
                        String.format("%.1f", ghostComp.revealTicks / 20.0));
            } else {
                scanLine = Component.translatable("hud.ghost_eye.scan_countdown",
                        String.format("%s", ghostComp.scanCountdown / 20));
            }
            int y = context.guiHeight() - 20 - font.lineHeight - 2;
            context.drawString(font, scanLine, x - font.width(scanLine), y, color);

            // 第 2 行：主动技能「诡域」冷却（下方，原位置）
            final var abilityComp = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY.get(client.player);
            MutableComponent abilityLine;
            if (abilityComp.cooldown > 0) {
                abilityLine = Component.translatable("hud.ghost_eye.cooldown", abilityComp.cooldown / 20);
            } else {
                abilityLine = Component.translatable("hud.ghost_eye.ready");
            }
            context.drawString(font, abilityLine,
                    x - font.width(abilityLine),
                    context.guiHeight() - 20, color);
        });
    }
}
