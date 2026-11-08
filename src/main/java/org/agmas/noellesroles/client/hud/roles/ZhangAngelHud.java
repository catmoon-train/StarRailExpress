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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.ZhangAngelRoleData;

public final class ZhangAngelHud {
    /** 距屏幕右边缘的留白。 */
    private static final int MARGIN = 10;
    /**
     * 统一技能 HUD（{@code UnifiedSkillHud}）的下沿距屏幕底部的距离。
     *
     * <p>
     * 它按 {@code guiHeight - panelH - 20} 定位，因此无论有几行技能，面板下沿恒为 {@code guiHeight - 20}。
     * 本 HUD 的整行文字必须落在这条线以下，否则会压到技能名与冷却状态。
     */
    private static final int SKILL_HUD_BOTTOM = 20;
    /** 与技能 HUD 下沿之间留出的空隙，避免文字紧贴面板。 */
    private static final int SKILL_HUD_GAP = 2;
    /** 隐身提示与吞电计数并排时的间隔。 */
    private static final int STEALTH_GAP = 6;

    private ZhangAngelHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.ZHANG_ANGEL_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            var dataOpt = RoleData.getOptional(ZhangAngelRoleData.class, client.player);
            if (dataOpt.isEmpty()) {
                return;
            }
            ZhangAngelRoleData data = dataOpt.get();
            var font = client.font;
            int right = guiGraphics.guiWidth() - MARGIN;
            // 屏幕 y 越大越靠下：从技能 HUD 下沿（guiHeight - 20）再往下让 2px，
            // 整行文字落在 guiHeight-18 ~ guiHeight-9，完全在技能 HUD 之下，不会重叠。
            int y = guiGraphics.guiHeight() - SKILL_HUD_BOTTOM + SKILL_HUD_GAP;

            Component swallowText = Component.translatable("hud.noellesroles.zhang_angel.swallows",
                    data.getSwallowCount(), ZhangAngelRoleData.SWALLOW_NEEDED)
                    .withStyle(data.getSwallowCount() >= ZhangAngelRoleData.SWALLOW_NEEDED
                            ? ChatFormatting.GOLD : ChatFormatting.AQUA);
            // 隐身提示与吞电计数同一行、排在左侧：这一行已经在技能 HUD 下方，不能再往上叠。
            int swallowLeft = right - font.width(swallowText);
            if (data.isDarknessStealthActive()) {
                Component stealthText = Component.translatable("hud.noellesroles.zhang_angel.stealth")
                        .withStyle(ChatFormatting.GRAY);
                guiGraphics.drawString(font, stealthText,
                        swallowLeft - STEALTH_GAP - font.width(stealthText), y, 0xAAAAAA, true);
            }
            guiGraphics.drawString(font, swallowText, swallowLeft, y, 0xFFFFFF, true);
        });
    }
}
