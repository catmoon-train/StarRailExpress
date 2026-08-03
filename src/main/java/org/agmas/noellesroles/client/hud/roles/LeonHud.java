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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vigilante.LeonRoleData;

public final class LeonHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.LEON_ID, (context, tickCounter) -> {
            var client = Minecraft.getInstance();
            if (client.player == null)
                return;
            var font = client.font;
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            // 每行文字高度
            int lineHeight = font.lineHeight + 2;

            SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(client.player);
            LeonRoleData leonComponent = RoleData.getOrCreate(LeonRoleData.class, client.player);

            // 竖向排列：技能 → 蓝色药丸 → 红色药丸，从下往上
            int y = screenHeight - 10 - font.lineHeight;

            // --- 第1行：格斗体术 ---
            Component skillText;
            int skillColor;
            if (abilityComponent.cooldown > 0) {
                int seconds = (abilityComponent.cooldown + 19) / 20;
                skillText = Component.translatable("hud.noellesroles.leon.skill.cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
                skillColor = 0xFF5555;
            } else {
                skillText = Component.translatable("hud.noellesroles.leon.skill.ready")
                        .withStyle(ChatFormatting.GREEN);
                skillColor = 0x55FF55;
            }
            int x = screenWidth - font.width(skillText) - 10;
            context.drawString(font, skillText, x, y, skillColor);

            // --- 第2行：蓝色药丸 ---
            y -= lineHeight;
            Component blueText;
            int blueColor;
            if (leonComponent.blueHerbGiven) {
                blueText = Component.translatable("hud.noellesroles.leon.blue_herb.ready")
                        .withStyle(ChatFormatting.AQUA);
                blueColor = 0x55FFFF;
            } else {
                blueText = Component.translatable("hud.noellesroles.leon.blue_herb.not_ready")
                        .withStyle(ChatFormatting.GRAY);
                blueColor = 0x888888;
            }
            x = screenWidth - font.width(blueText) - 10;
            context.drawString(font, blueText, x, y, blueColor);

            // --- 第3行：红色药丸 ---
            y -= lineHeight;
            Component redText;
            int redColor;
            if (leonComponent.redHerbGiven) {
                redText = Component.translatable("hud.noellesroles.leon.red_herb.ready")
                        .withStyle(ChatFormatting.RED);
                redColor = 0xFF5555;
            } else {
                redText = Component.translatable("hud.noellesroles.leon.red_herb.not_ready")
                        .withStyle(ChatFormatting.GRAY);
                redColor = 0x888888;
            }
            x = screenWidth - font.width(redText) - 10;
            context.drawString(font, redText, x, y, redColor);
        });
    }
}
