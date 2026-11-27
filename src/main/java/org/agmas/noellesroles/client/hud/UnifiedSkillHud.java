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

package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.api.RolePassive;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.List;

/**
 * 统一技能 HUD —— 复古车票风格（见 docs/ui_style.md）。
 *
 * 设计要点：
 * - 右下角一块低不透明的深棕渐变面板（棕金描边 + 上边缘装饰线），仍然让画面透出来；
 * - 技能名（奶油色）左对齐，前置金色小方块；状态右对齐（就绪=绿 / 冷却=蓝 / 无次数=土褐）；
 * - 冷却期间行底部有 1px 金色进度条，直观显示恢复进度；
 * - 当前选中技能以微亮底 + 金色侧边条标识（V/Y 键循环选择）；
 * - 有使用上限（maxCharges &gt; 0）时以金色「xN」徽标显示剩余次数；
 * - 被动技能以分隔线下的暗色行收尾，不参与选择。
 */
public final class UnifiedSkillHud {
    private UnifiedSkillHud() {
    }

    // ── 色板（docs/ui_style.md §2）───────────────────────────────
    private static final int GOLD = SRETheme.GOLD;
    private static final int TEXT = SRETheme.TEXT;
    private static final int MUTED = SRETheme.MUTED;
    private static final int GREEN = SRETheme.GREEN;
    private static final int BLUE = SRETheme.BLUE;
    private static final int DIVIDER = 0x24FFFFFF;
    private static final int COOLDOWN_TRACK = 0x59000000;
    // 面板范式（§3：渐变底 + 棕金描边 + 上边缘装饰线）；HUD 版把不透明度压到 40% 上下，
    // 依 ui_unified_style_plan.md §7「保留无底板、靠投影」的原则，只给一点层次、不挡场景
    private static final int PANEL_TOP = SRETheme.withAlpha(SRETheme.SURFACE_TOP_SOFT, 0x66);
    private static final int PANEL_BOTTOM = 0x7A0B0704;
    private static final int BORDER = SRETheme.withAlpha(SRETheme.GOLD_DIM, 0x59);
    private static final int TOP_LINE = SRETheme.withAlpha(SRETheme.BRASS_BRIGHT, 0x2E);
    private static final int SELECT_BG = SRETheme.withAlpha(SRETheme.GOLD_DIM, 0x2E);

    // ── 布局 ────────────────────────────────────────────────────
    private static final int PAD = 5;
    private static final int ROW_H = 14;
    private static final int ACCENT_W = 2;
    /** 技能名前的金色小方块所占的横向空间（含与文字的间隔）。 */
    private static final int MARK_W = 5;
    private static final int NAME_STATE_GAP = 12;

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null || SREClient.gameComponent == null) {
                return;
            }
            var role = SREClient.getCachedPlayerRole();
            // 布袋鬼有自绘的鬼术 HUD，跳过
            if (role != null && role.identifier().equals(org.agmas.noellesroles.role.ModRoles.MA_CHEN_XU_ID)) {
                return;
            }
            // 小镇做题家有自绘的 HUD，跳过
            if (role != null && role.identifier().equals(org.agmas.noellesroles.role.ModRoles.EXAMPLER_ID)) {
                return;
            }
            // 归途旅人有自绘的 HUD（两个技能独立状态），跳过通用技能 HUD
            if (role != null && role.identifier().equals(org.agmas.noellesroles.role.ModRoles.RETURN_TRAVELER_ID)) {
                return;
            }
            // 领袖有自绘的 HUD（技能状态/倒计时/追随者列表），跳过通用技能 HUD
            if (role != null && role.identifier().equals(org.agmas.noellesroles.role.ModRoles.LEADER_ID)) {
                return;
            }
            // 文文新闻（ayayaya）的偷取技能是邪恶战争专属（技能执行处有同样的模式门禁）：
            // 其它模式不显示其技能 HUD
            if (role != null && role.identifier().equals(org.agmas.noellesroles.role.touhou.THMountainRoles.AYA_ID)
                    && !(SREClient.gameComponent
                            .getGameMode() instanceof io.wifi.starrailexpress.game.modes.funny.SREEvilWarGameMode)) {
                return;
            }
            List<RoleSkill.Definition> skills = RoleSkill.getDefinitions(role);
            List<RolePassive.Definition> passives = RolePassive.getDefinitions(role);
            if (skills.isEmpty() && passives.isEmpty()) {
                return;
            }

            List<RoleSkill.Definition> visible = skills.stream()
                    .filter(RoleSkill.Definition::showOnHud)
                    .toList();
            if (visible.isEmpty() && passives.isEmpty()) {
                return;
            }

            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            ability.ensureSkills(skills);

            List<RoleSkill.Definition> selectable = RoleSkill.getSelectableDefinitions(role);
            RoleSkill.Definition selectedDef = selectable.isEmpty() ? null
                    : selectable.get(Mth.clamp(ability.getSelectedSkill(), 0, selectable.size() - 1));

            int rowCount = visible.size() + (passives.isEmpty() ? 0 : 1);
            Component[] names = new Component[rowCount];
            Component[] states = new Component[rowCount];
            int[] stateColors = new int[rowCount];
            float[] cooldownProgress = new float[rowCount]; // <0 = 无冷却条
            int selectedIdx = -1;
            int maxRowW = 0;

            int idx = 0;
            for (RoleSkill.Definition skill : visible) {
                SREAbilityPlayerComponent.SkillState state = ability.getSkillState(skill.id());
                // 选中下标是「可循环技能」列表的下标，不能直接拿去索引 HUD 行（HUD 还含潜行技能）
                if (skill.equals(selectedDef)) {
                    selectedIdx = idx;
                }

                Component name = Component.translatable(skill.nameKey());
                if (state.maxCharges > 0) {
                    name = name.copy().append(Component.literal(" x" + Math.max(0, state.charges))
                            .withStyle(s -> s.withColor(GOLD & 0xFFFFFF)));
                }

                Component stateText;
                int stateColor;
                float progress = -1.0F;
                if (state.cooldown > 0) {
                    stateText = Component.translatable("hud.sre.skill.cooldown",
                            String.format("%.1f", state.cooldown / 20.0F));
                    stateColor = BLUE;
                    if (skill.cooldownTicks() > 0) {
                        progress = 1.0F - Mth.clamp(state.cooldown / (float) skill.cooldownTicks(), 0.0F, 1.0F);
                    }
                } else if (state.charges == 0) {
                    stateText = Component.translatable("hud.sre.skill.empty");
                    stateColor = MUTED;
                } else {
                    stateText = skill.shifted()
                            ? Component.translatable("hud.sre.skill.ready_shift",
                                    client.options.keyShift.getTranslatedKeyMessage(),
                                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                            : Component.translatable("hud.sre.skill.ready");
                    stateColor = GREEN;
                }

                names[idx] = name;
                states[idx] = stateText;
                stateColors[idx] = stateColor;
                cooldownProgress[idx] = progress;
                int rowW = client.font.width(name) + NAME_STATE_GAP + client.font.width(stateText);
                maxRowW = Math.max(maxRowW, rowW);
                idx++;
            }

            if (!passives.isEmpty()) {
                Component passiveLine = Component.translatable("hud.sre.passive_label");
                for (int i = 0; i < passives.size(); i++) {
                    passiveLine = passiveLine.copy().append(Component.translatable(passives.get(i).nameKey()));
                    if (i < passives.size() - 1) {
                        passiveLine = passiveLine.copy().append(Component.literal(" · "));
                    }
                }
                names[idx] = passiveLine;
                states[idx] = null;
                stateColors[idx] = MUTED;
                cooldownProgress[idx] = -1.0F;
                maxRowW = Math.max(maxRowW, client.font.width(passiveLine));
            }

            // ── 绘制（右下角锚定：深棕面板 + 行内状态/进度）──────────
            int panelW = maxRowW + PAD * 2 + ACCENT_W + MARK_W + 1;
            int panelH = rowCount * ROW_H + PAD * 2 + (passives.isEmpty() ? 0 : 2);
            int baseX = graphics.guiWidth() - panelW - 6;
            int baseY = graphics.guiHeight() - panelH - 20;
            int right = baseX + panelW;

            // 面板：上下渐变底 → 棕金描边 → 上边缘装饰线（§3）
            graphics.fillGradient(baseX, baseY, right, baseY + panelH, PANEL_TOP, PANEL_BOTTOM);
            graphics.renderOutline(baseX, baseY, panelW, panelH, BORDER);
            graphics.fill(baseX + 1, baseY + 1, right - 1, baseY + 2, TOP_LINE);

            int textX = baseX + PAD + ACCENT_W + MARK_W + 1;
            for (int r = 0; r < rowCount; r++) {
                boolean passiveRow = !passives.isEmpty() && r == rowCount - 1;
                boolean selected = r == selectedIdx;
                int rowY = baseY + PAD + r * ROW_H + (passiveRow ? 2 : 0);

                if (passiveRow) {
                    graphics.fill(baseX + PAD, rowY - 1, right - PAD, rowY, DIVIDER);
                }

                if (selected) {
                    // 选中行：微亮底 + 呼吸金色侧边条
                    graphics.fill(baseX + 1, rowY - 1, right - 1, rowY + ROW_H - 1, SELECT_BG);
                    float pulse = 0.7F + 0.3F * Mth.sin((Util.getMillis() % 1600L) / 1600.0F * Mth.TWO_PI);
                    int accent = (((int) (0xFF * pulse)) << 24) | (GOLD & 0xFFFFFF);
                    graphics.fill(baseX + 2, rowY + 1, baseX + 2 + ACCENT_W, rowY + ROW_H - 2, accent);
                }

                int textY = rowY + (ROW_H - client.font.lineHeight) / 2 + 1;

                if (!passiveRow) {
                    // 技能名前的金色小方块：把主技能行与被动行区分开，也比纯文字多一层层次
                    int markColor = selected ? GOLD : blend(GOLD, MUTED, 0.5F);
                    graphics.fill(baseX + PAD + ACCENT_W + 1, textY + 3,
                            baseX + PAD + ACCENT_W + 1 + 3, textY + 6, markColor);
                }

                int nameColor = passiveRow ? MUTED : (selected ? TEXT : blend(TEXT, MUTED, 0.35F));
                // 面板只有 40% 上下不透明，文字仍带投影，保证亮景色上也读得清
                graphics.drawString(client.font, names[r], textX, textY, nameColor, true);

                if (states[r] != null) {
                    int stateW = client.font.width(states[r]);
                    graphics.drawString(client.font, states[r], right - PAD - stateW, textY, stateColors[r], true);
                }

                // 冷却恢复进度条（行底 1px）
                if (cooldownProgress[r] >= 0.0F) {
                    int trackX0 = textX;
                    int trackX1 = right - PAD;
                    int trackY = rowY + ROW_H - 2;
                    graphics.fill(trackX0, trackY, trackX1, trackY + 1, COOLDOWN_TRACK);
                    int fillX1 = trackX0 + Math.round((trackX1 - trackX0) * cooldownProgress[r]);
                    if (fillX1 > trackX0) {
                        graphics.fill(trackX0, trackY, fillX1, trackY + 1, GOLD);
                    }
                }
            }
        });
    }

    /** ARGB 线性插值（docs/ui_style.md §6）。 */
    private static int blend(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }
}
