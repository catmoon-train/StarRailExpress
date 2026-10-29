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
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.PoolerRoleData;

/**
 * 祈月者状态 HUD（屏幕右下角）：
 * <ul>
 * <li>四个技能（奔涌 / 吞月 / 水中镜 / 称颂）的冷却，当前选中的技能用 ▶ 标记；</li>
 * <li>月之祝福的冷却；</li>
 * </ul>
 * 数据全部来自本人同步过来的 {@link PoolerRoleData}，旁观者不显示他人状态。
 * 挂在 {@link RoleHudRenderCallback} 上，只在玩家是祈月者时调用；刷新率沿用项目既有的
 * 每 tick 一次（20 Hz）节流，不额外引入每帧钩子。
 */
public final class PoolerHud {

    /** 右下角距屏幕边缘的留白。 */
    private static final int MARGIN = 6;
    /** 行高。 */
    private static final int LINE_H = 10;

    private PoolerHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.POOLER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null)
                return;
            if (SREClient.isPlayerSpectator())
                return;
            PoolerRoleData data = RoleData.getOptional(PoolerRoleData.class, client.player).orElse(null);
            if (data == null)
                return;

            Font font = client.font;
            PoolerRoleData.PoolerSkill selected = data.getSelectedSkill();
            // 面板宽度 = 最长一行的宽度 + 内边距
            int inner = 0;
            for (PoolerRoleData.PoolerSkill skill : PoolerRoleData.PoolerSkill.values()) {
                inner = Math.max(inner, font.width(skillLine(skill, data, skill == selected)));
            }
            inner = Math.max(inner, font.width(moonBlessingLine(data)));

            int panelW = inner + MARGIN * 2;
            int panelH = LINE_H * (PoolerRoleData.PoolerSkill.values().length + 1) + MARGIN * 2;
            int left = context.guiWidth() - panelW - MARGIN;
            int top = context.guiHeight() - panelH - MARGIN;

            // 半透明底板 + 边框，避免压在亮色地形上看不清
            context.fill(left, top, left + panelW, top + panelH, 0x90101218);
            context.renderOutline(left, top, panelW, panelH, 0xFF4FC3F7);

            int x = left + MARGIN;
            int y = top + MARGIN;
            for (PoolerRoleData.PoolerSkill skill : PoolerRoleData.PoolerSkill.values()) {
                boolean isSel = skill == selected;
                Component line = skillLine(skill, data, isSel);
                int color = isSel ? 0xFFFFFF : 0xA8C4D8;
                context.drawString(font, line, x, y, color);
                y += LINE_H;
            }
            context.drawString(font, moonBlessingLine(data), x, y, 0xE8D48A);
        });
    }

    /** 一行技能：选中标记 + 技能名 + 冷却（就绪时显示「就绪」）。 */
    private static Component skillLine(PoolerRoleData.PoolerSkill skill, PoolerRoleData data, boolean selected) {
        int cd = data.cooldownSecondsLeft(skill);
        Component name = Component.translatable(skill.nameKey)
                .withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        Component state = cd > 0
                ? Component.translatable("hud.noellesroles.pooler.cd", cd)
                        .withStyle(ChatFormatting.RED)
                : Component.translatable("hud.noellesroles.pooler.ready")
                        .withStyle(ChatFormatting.GREEN);
        return Component.empty()
                .append(Component.literal(selected ? "▶ " : "  "))
                .append(name)
                .append(Component.literal("  "))
                .append(state);
    }

    /** 月之祝福一行。 */
    private static Component moonBlessingLine(PoolerRoleData data) {
        int cd = data.moonBlessingCooldownSeconds();
        Component state = cd > 0
                ? Component.translatable("hud.noellesroles.pooler.cd", cd)
                        .withStyle(ChatFormatting.RED)
                : Component.translatable("hud.noellesroles.pooler.ready")
                        .withStyle(ChatFormatting.GREEN);
        return Component.empty()
                .append(Component.literal("  "))
                .append(Component.translatable("item.noellesroles.moon_blessing")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  "))
                .append(state);
    }
}
