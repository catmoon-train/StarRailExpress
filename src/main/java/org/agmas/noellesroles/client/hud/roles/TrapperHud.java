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
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 设陷者 HUD（重做版）：当前陷阱类型、绊线/泥沼各自的独立冷却、陷阱数量与大招状态。
 */
public class TrapperHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.TRAPPER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (!SREClient.isPlayerAliveAndInSurvival())
                return;

            TrapperPlayerComponent comp = TrapperPlayerComponent.KEY.get(client.player);

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 150;
            int y = screenHeight - 86;

            Font font = client.font;

            // 切换陷阱类型提示
            Component toggleText = Component.translatable("hud.trapper.toggle_mode",
                    NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage());
            context.drawString(font, toggleText, x, y, 0xAAAAAA);

            // 当前陷阱类型
            int typeColor = switch (comp.getSelectedTrapType()) {
                case TrapperPlayerComponent.TRAP_TYPE_MUD -> 0x8B5A2B;
                case TrapperPlayerComponent.TRAP_TYPE_NET -> 0xE0E0E0;
                default -> 0xFF8C00;
            };
            context.drawString(font, Component.translatable(comp.getTrapTypeName()), x, y + 12, typeColor);

            // 数量：绊线 x/4，总数 x/5
            context.drawString(font, Component.translatable("hud.noellesroles.trapper.tripwires",
                    comp.syncedTripwireCount, TrapperPlayerComponent.MAX_TRIPWIRES), x, y + 24, 0xFF8C00);
            context.drawString(font, Component.translatable("hud.noellesroles.trapper.total",
                    comp.syncedTotalCount, TrapperPlayerComponent.MAX_TOTAL_TRAPS), x, y + 36, 0xFFFF00);

            // 独立冷却：绊线 / 泥沼
            drawCooldown(context, font, x, y + 48, "hud.noellesroles.trapper.cd.tripwire",
                    comp.tripwireCooldownTicks);
            drawCooldown(context, font, x, y + 60, "hud.noellesroles.trapper.cd.mud",
                    comp.mudCooldownTicks);

            // 大招状态：未购买显示价格；已购买显示技能冷却
            if (comp.hasNetGun) {
                drawCooldown(context, font, x, y + 72, "hud.noellesroles.trapper.cd.net",
                        comp.netGunCooldownTicks);
            } else {
                context.drawString(font, Component.translatable("hud.noellesroles.trapper.net_cost",
                        TrapperPlayerComponent.NET_GUN_COST), x, y + 72, 0xAAAAAA);
            }
        });
    }

    private static void drawCooldown(io.wifi.utils.client.betterrender.FakeGuiGraphics context, Font font,
            int x, int y, String key, int cooldownTicks) {
        if (cooldownTicks > 0) {
            context.drawString(font, Component.translatable(key,
                    String.format("%.1f", cooldownTicks / 20.0f)), x, y, CommonColors.RED);
        } else {
            context.drawString(font, Component.translatable(key,
                    Component.translatable("hud.noellesroles.trapper.ready")), x, y, CommonColors.GREEN);
        }
    }
}
