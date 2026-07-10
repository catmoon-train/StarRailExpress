package org.agmas.noellesroles.client.hud;

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
 * - 无背景面板，直接叠在画面上，靠文字投影保证可读性；
 * - 技能名（奶油色）左对齐，状态右对齐（就绪=绿 / 冷却=蓝 / 无次数=土褐）；
 * - 冷却期间行底部有 1px 金色进度条，直观显示恢复进度；
 * - 当前选中技能以金色侧边条 + 微亮背景标识（V/Y 键循环选择）；
 * - 有使用上限（maxCharges &gt; 0）时以金色「xN」徽标显示剩余次数；
 * - 被动技能以分隔线下的暗色行收尾，不参与选择。
 */
public final class UnifiedSkillHud {
    private UnifiedSkillHud() {
    }

    // ── 色板（docs/ui_style.md §2）───────────────────────────────
    // 无背景板，文字一律带投影以在亮景色上保持可读性
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int BLUE = 0xFF5EB7D8;
    private static final int DIVIDER = 0x18FFFFFF;
    private static final int COOLDOWN_TRACK = 0x4D000000;

    // ── 布局 ────────────────────────────────────────────────────
    private static final int PAD = 5;
    private static final int ROW_H = 13;
    private static final int ACCENT_W = 2;
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

            // ── 绘制（右下角锚定，无背景板，仅文字/侧边条/进度条）──────
            int panelW = maxRowW + PAD * 2 + ACCENT_W + 3;
            int panelH = rowCount * ROW_H + PAD * 2 + (passives.isEmpty() ? 0 : 2);
            int baseX = graphics.guiWidth() - panelW - 6;
            int baseY = graphics.guiHeight() - panelH - 20;

            int textX = baseX + PAD + ACCENT_W + 3;
            for (int r = 0; r < rowCount; r++) {
                boolean passiveRow = !passives.isEmpty() && r == rowCount - 1;
                int rowY = baseY + PAD + r * ROW_H + (passiveRow ? 2 : 0);

                if (passiveRow) {
                    graphics.fill(baseX + PAD, rowY - 1, baseX + panelW - PAD, rowY, DIVIDER);
                }

                if (r == selectedIdx) {
                    // 选中行：呼吸金色侧边条
                    float pulse = 0.7F + 0.3F * Mth.sin((Util.getMillis() % 1600L) / 1600.0F * Mth.TWO_PI);
                    int accent = (((int) (0xFF * pulse)) << 24) | (GOLD & 0xFFFFFF);
                    graphics.fill(baseX + 2, rowY + 1, baseX + 2 + ACCENT_W, rowY + ROW_H - 2, accent);
                }

                int nameColor = passiveRow ? MUTED : (r == selectedIdx ? TEXT : blend(TEXT, MUTED, 0.35F));
                int textY = rowY + (ROW_H - client.font.lineHeight) / 2 + 1;
                // 无背景板，文字带投影才不至于在亮景色上糊掉
                graphics.drawString(client.font, names[r], textX, textY, nameColor, true);

                if (states[r] != null) {
                    int stateW = client.font.width(states[r]);
                    graphics.drawString(client.font, states[r],
                            baseX + panelW - PAD - stateW, textY, stateColors[r], true);
                }

                // 冷却恢复进度条（行底 1px）
                if (cooldownProgress[r] >= 0.0F) {
                    int trackX0 = textX;
                    int trackX1 = baseX + panelW - PAD;
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
