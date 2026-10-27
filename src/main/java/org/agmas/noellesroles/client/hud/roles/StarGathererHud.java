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
import org.agmas.noellesroles.role_data.innocence.StarGathererRoleData;

/**
 * 摘星者状态 HUD（屏幕右下角）：
 * <ul>
 * <li>星之光环当前半径（庇护强化期间会从 6 变成 10）；</li>
 * <li>三个技能（庇护强化 / 星之引力 / 施舍）的冷却，当前选中的技能用 ▶ 标记；</li>
 * <li>星之引力本局剩余次数（一局仅 2 次）；</li>
 * <li>星之权杖是否就绪。</li>
 * </ul>
 * 数据全部来自本人同步过来的 {@link StarGathererRoleData}，旁观者不显示他人状态。
 * 挂在 {@link RoleHudRenderCallback} 上，只在玩家是摘星者时调用；刷新率沿用项目既有的
 * 每 tick 一次（20 Hz）节流，不额外引入每帧钩子。
 */
public final class StarGathererHud {

    /** 右下角距屏幕边缘的留白。 */
    private static final int MARGIN = 6;
    /** 行高。 */
    private static final int LINE_H = 10;

    private StarGathererHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.STAR_GATHERER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null)
                return;
            if (SREClient.isPlayerSpectator())
                return;
            StarGathererRoleData data = RoleData.getOptional(StarGathererRoleData.class, client.player)
                    .orElse(null);
            if (data == null)
                return;

            Font font = client.font;
            StarGathererRoleData.StarGathererSkill selected = data.getSelectedSkill();
            StarGathererRoleData.StarGathererSkill[] skills = StarGathererRoleData.StarGathererSkill.values();

            // 面板宽度 = 最长一行的宽度 + 内边距
            int inner = 0;
            inner = Math.max(inner, font.width(auraLine(data)));
            for (StarGathererRoleData.StarGathererSkill skill : skills) {
                inner = Math.max(inner, font.width(skillLine(skill, data, skill == selected)));
            }
            inner = Math.max(inner, font.width(gravityUsesLine(data)));
            inner = Math.max(inner, font.width(scepterLine(data)));

            int panelW = inner + MARGIN * 2;
            int panelH = LINE_H * (skills.length + 3) + MARGIN * 2;
            int left = context.guiWidth() - panelW - MARGIN;
            int top = context.guiHeight() - panelH - MARGIN;

            // 半透明底板 + 金色边框（星星主题），避免压在亮色地形上看不清
            context.fill(left, top, left + panelW, top + panelH, 0x90121810);
            context.renderOutline(left, top, panelW, panelH, 0xFFF2C443);

            int x = left + MARGIN;
            int y = top + MARGIN;
            context.drawString(font, auraLine(data), x, y, 0xFFE9A8);
            y += LINE_H;
            for (StarGathererRoleData.StarGathererSkill skill : skills) {
                boolean isSel = skill == selected;
                int color = isSel ? 0xFFFFFF : 0xD8CFA8;
                context.drawString(font, skillLine(skill, data, isSel), x, y, color);
                y += LINE_H;
            }
            context.drawString(font, gravityUsesLine(data), x, y, 0xBFE3FF);
            y += LINE_H;
            context.drawString(font, scepterLine(data), x, y, 0xF2C443);
        });
    }

    /** 光环半径一行。 */
    private static Component auraLine(StarGathererRoleData data) {
        int radius = (int) Math.round(data.currentAuraRadius());
        return Component.empty()
                .append(Component.literal("  "))
                .append(Component.translatable("hud.noellesroles.star_gatherer.aura", radius)
                        .withStyle(ChatFormatting.GOLD));
    }

    /** 一行技能：选中标记 + 技能名 + 冷却（就绪时显示「就绪」）。 */
    private static Component skillLine(StarGathererRoleData.StarGathererSkill skill, StarGathererRoleData data,
            boolean selected) {
        int cd = data.cooldownSecondsLeft(skill);
        Component name = Component.translatable(skill.nameKey)
                .withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        Component state = cd > 0
                ? Component.translatable("hud.noellesroles.star_gatherer.cd", cd)
                        .withStyle(ChatFormatting.RED)
                : Component.translatable("hud.noellesroles.star_gatherer.ready")
                        .withStyle(ChatFormatting.GREEN);
        return Component.empty()
                .append(Component.literal(selected ? "▶ " : "  "))
                .append(name)
                .append(Component.literal("  "))
                .append(state);
    }

    /**
     * 星之引力剩余次数。
     * <p>
     * {@code gravityUses} 是私有字段，这里按「冷却是否还在跑」反推不出来（冷却结束≠没用过），
     * 因此改用一个独立的 public 只读访问器，见 {@link StarGathererRoleData#gravityUsesLeft()}。
     */
    private static Component gravityUsesLine(StarGathererRoleData data) {
        int left = data.gravityUsesLeft();
        return Component.empty()
                .append(Component.literal("  "))
                .append(Component.translatable("hud.noellesroles.star_gatherer.gravity_uses", left,
                        StarGathererRoleData.GRAVITY_MAX_USES)
                        .withStyle(left > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
    }

    /** 星之权杖就绪状态。 */
    private static Component scepterLine(StarGathererRoleData data) {
        boolean ready = data.isScepterReady();
        return Component.empty()
                .append(Component.literal("  "))
                .append(Component.translatable("item.noellesroles.star_scepter")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  "))
                .append(ready
                        ? Component.translatable("hud.noellesroles.star_gatherer.ready")
                                .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("hud.noellesroles.star_gatherer.not_ready")
                                .withStyle(ChatFormatting.GRAY));
    }
}
