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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.killer.WarlockRoleData;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 咒术师状态 HUD：咒物数量 / 蚀咒剩余时间 / 领域剩余时间。
 * 技能冷却由 {@code UnifiedSkillHud} 自动渲染，这里只补充资源信息（左下角）。
 */
public class WarlockHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WARLOCK_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator() || client.level == null)
                return;
            var comp = RoleData.getOptional(WarlockRoleData.class, client.player).orElse(null);
            if (comp == null)
                return;
            Font font = client.font;
            int sy = client.getWindow().getGuiScaledHeight();
            long now = SREClient.getTicksFromGameStart();

            int y = sy - 46;
            Component essenceText = Component
                    .translatable("hud.noellesroles.warlock.essences", comp.essences.size())
                    .withStyle(comp.essences.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE);
            context.drawString(font, essenceText, 8, y, 0xFFFFFF);
            y += 12;

            // 当前处于诅咒中的存活目标数量（领域可拉入的候选）
            long cursedCount = comp.cursedPlayers.values().stream().filter(end -> end > now).count();
            if (cursedCount > 0) {
                Component curseText = Component
                        .translatable("hud.noellesroles.warlock.cursing", cursedCount)
                        .withStyle(ChatFormatting.DARK_PURPLE);
                context.drawString(font, curseText, 8, y, 0xFFFFFF);
                y += 12;
            }

            if (comp.domainOpen && comp.domainEndTick > now) {
                int sec = (int) ((comp.domainEndTick - now + 19) / 20);
                Component domainText = Component
                        .translatable("hud.noellesroles.warlock.domain", sec)
                        .withStyle(ChatFormatting.DARK_AQUA);
                context.drawString(font, domainText, 8, y, 0xFFFFFF);
            } else if (comp.domainCooldownEndTick > now) {
                int sec = (int) ((comp.domainCooldownEndTick - now + 19) / 20);
                Component cdText = Component
                        .translatable("hud.noellesroles.warlock.domain_cd", sec)
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(font, cdText, 8, y, 0xFFFFFF);
            }
        });
    }
}
