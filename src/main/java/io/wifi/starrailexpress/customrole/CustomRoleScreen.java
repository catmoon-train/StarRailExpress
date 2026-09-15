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

package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.client.gui.HintText;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.customrole.CustomRoleData.EffectEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.InstinctModeData;
import io.wifi.starrailexpress.customrole.CustomRoleData.InitialItemEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.ShopEntryData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class CustomRoleScreen extends Screen {
    // 面板尺寸 - 自适应屏幕（参考 RoleIntroduceScreen）
    private static final float USABLE_RATIO = 0.85f;
    private static final int MAX_PANEL_WIDTH = 520;
    private static final int MAX_PANEL_HEIGHT = 520;
    private static final int MIN_PANEL_HEIGHT = 320;

    private int panelWidth, panelHeight;
    private int panelLeftX, panelTopY, activeTab = 0;

    // 滚动常量
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;

    // 滚动状态
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragScrollStartY = 0;
    private int dragScrollStartOffset = 0;

    /**
     * 换页签时置为 true，下一帧渲染前统一重建。
     *
     * <p>
     * 不在按钮回调里直接重建：那一刻界面正在遍历自己的控件列表，清空会撞上并发修改，
     * 而且原版会在回调返回后把「焦点」设到已经被清掉的那个按钮上。
     */
    private boolean pendingRebuild = false;

    /** 任务列表编辑器里「候选类型」的下标持有者（点按钮循环切换，重建界面后保持）。 */
    private final int[] unrefreshableTaskCursor = { 0 };
    private final int[] onlyRefreshableTaskCursor = { 0 };

    private static final String[] TAB_NAMES = { "basic", "advanced", "ability", "generation", "shop" };
    private static final int FIELD_LEFT = 140, FIELD_W = 200;
    private CustomRoleData data = new CustomRoleData();
    private String originalEnglishId = "";

    private final List<AbstractWidget> tabWidgets0 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets1 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets2 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets3 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets4 = new ArrayList<>();

    private record LabelEntry(String key, int x, int y, int maxWidth, int maxLines) {
        /** 该条文字实际会占用的高度（滚动范围按它算）。 */
        int height() {
            return maxLines > 1 ? HintText.LINE_H * maxLines : HintText.LINE_H;
        }
    }

    private final List<LabelEntry> tabLabels0 = new ArrayList<>();
    private final List<LabelEntry> tabLabels1 = new ArrayList<>();
    private final List<LabelEntry> tabLabels2 = new ArrayList<>();
    private final List<LabelEntry> tabLabels3 = new ArrayList<>();
    private final List<LabelEntry> tabLabels4 = new ArrayList<>();

    // 滚动支持：记录每个内容 widget 的基础 Y 坐标
    private final Map<AbstractWidget, Integer> widgetBaseY = new IdentityHashMap<>();

    // 固定 widget 分组（不被滚动影响）
    private final List<AbstractWidget> tabBarButtons = new ArrayList<>();
    private final List<AbstractWidget> bottomButtons = new ArrayList<>();

    // Toggles
    private int moodIndex;

    public CustomRoleScreen() {
        super(Component.translatable("sre.custom_role.title"));
        syncToggles();
    }

    public CustomRoleScreen(CustomRoleData d) {
        super(Component.translatable("sre.custom_role.title"));
        this.data = d;
        this.originalEnglishId = d.englishId == null ? "" : d.englishId;
        syncToggles();
    }

    private void syncToggles() {
        moodIndex = "FAKE".equalsIgnoreCase(data.moodType) ? 1 : 0;
    }

    // ══════════════════════════════════════════════════════════════════
    // 布局计算
    // ══════════════════════════════════════════════════════════════════
    private void computeLayout() {
        panelWidth = Math.min((int) (width * USABLE_RATIO), MAX_PANEL_WIDTH);
        // 窗口很小时以窗口为准：面板必须完整落在显示区域内，超出的内容靠滚动查看
        int windowLimit = Math.max(120, height - 4);
        int rawH = Math.min((int) (height * USABLE_RATIO), MAX_PANEL_HEIGHT);
        panelHeight = Math.min(Math.max(rawH, MIN_PANEL_HEIGHT), windowLimit);
        panelLeftX = (width - panelWidth) / 2;
        panelTopY = (height - panelHeight) / 2;
    }

    /** 内容区域顶部 Y */
    private int contentTop() {
        return panelTopY + 34;
    }

    /** 内容区域底部 Y */
    private int contentBottom() {
        return panelTopY + panelHeight - 30;
    }

    /** 内容区域可用高度 */
    private int contentHeight() {
        return contentBottom() - contentTop();
    }

    /** 基础行 Y（scrollOffset=0） */
    private int baseRowY(int i) {
        return panelTopY + 34 + i * 22;
    }

    /** 实际行 Y */
    private int rowY(int i) {
        return baseRowY(i) - scrollOffset;
    }

    private int fieldX() {
        return panelLeftX + FIELD_LEFT;
    }

    private int labelX() {
        return panelLeftX + 4;
    }

    /** 字段标签列的可用宽度（右边就是输入框，只能单行 + 省略号，放不下时悬停看全文）。 */
    private int labelW() {
        return Math.max(60, FIELD_LEFT - 12);
    }

    // ══════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        clearTabs();
        widgetBaseY.clear();
        tabBarButtons.clear();
        bottomButtons.clear();
        tabLabels0.clear();
        tabLabels1.clear();
        tabLabels2.clear();
        tabLabels3.clear();
        tabLabels4.clear();

        computeLayout();
        buildTabBar();

        switch (activeTab) {
            case 0:
                buildBasicTab();
                break;
            case 1:
                buildAdvancedTab();
                break;
            case 2:
                buildAbilityTab();
                break;
            case 3:
                buildGenerationTab();
                break;
            case 4:
                buildShopTab();
                break;
        }
        flushTabWidgets();
        buildBottomButtons();

        computeMaxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        applyScrollOffsets();
    }

    private void clearTabs() {
        tabWidgets0.clear();
        tabWidgets1.clear();
        tabWidgets2.clear();
        tabWidgets3.clear();
        tabWidgets4.clear();
    }

    private void computeMaxScroll() {
        int maxY = contentTop();
        for (AbstractWidget w : getActiveTabWidgets()) {
            Integer baseY = widgetBaseY.get(w);
            if (baseY != null) {
                maxY = Math.max(maxY, baseY + w.getHeight());
            }
        }
        var labels = getActiveLabels();
        for (LabelEntry e : labels) {
            maxY = Math.max(maxY, e.y() + e.height());
        }
        maxScroll = Math.max(0, maxY - contentBottom());
    }

    private void applyScrollOffsets() {
        for (AbstractWidget w : getActiveTabWidgets()) {
            Integer baseY = widgetBaseY.get(w);
            if (baseY != null) {
                w.setY(baseY - scrollOffset);
            }
        }
    }

    private List<AbstractWidget> getActiveTabWidgets() {
        return switch (activeTab) {
            case 0 -> tabWidgets0;
            case 1 -> tabWidgets1;
            case 2 -> tabWidgets2;
            case 3 -> tabWidgets3;
            case 4 -> tabWidgets4;
            default -> List.of();
        };
    }

    private List<LabelEntry> getActiveLabels() {
        return switch (activeTab) {
            case 0 -> tabLabels0;
            case 1 -> tabLabels1;
            case 2 -> tabLabels2;
            case 3 -> tabLabels3;
            case 4 -> tabLabels4;
            default -> List.of();
        };
    }

    private void addLabel(List<LabelEntry> l, String key, int r) {
        l.add(new LabelEntry(key, labelX(), baseRowY(r), labelW(), 1));
    }

    private EditBox makeLabeledBox(List<AbstractWidget> wl, List<LabelEntry> ll, int r, int w, String key, String val,
            java.util.function.Consumer<String> cb) {
        addLabel(ll, key, r);
        EditBox b = makeBox(fieldX(), rowY(r), w, 18, val, cb);
        recordWidgetBase(b, baseRowY(r));
        wl.add(b);
        return b;
    }

    /** hintKey 是翻译键：提示文本统一走语言文件，不要在这里硬编码中文。 */
    private EditBox makeLabeledHintBox(List<AbstractWidget> wl, List<LabelEntry> ll, int r, int w, String key,
            String val, String hintKey, java.util.function.Consumer<String> cb) {
        EditBox b = makeLabeledBox(wl, ll, r, w, key, val, cb);
        b.setHint(Component.translatable(hintKey));
        b.setTooltip(Tooltip.create(Component.translatable(hintKey)));
        return b;
    }

    private void recordWidgetBase(AbstractWidget w, int baseY) {
        widgetBaseY.put(w, baseY);
    }

    private AbstractWidget makeButton(int x, int baseY, int w, int h, Component text, Runnable onClick) {
        var btn = Button.builder(text, b -> {
            onClick.run();
        })
                .bounds(x, baseY, w, h).build();
        recordWidgetBase(btn, baseY);
        return btn;
    }

    private void buildTabBar() {
        int th = 20, tg = 4, tabs = 5;
        int tw = Math.min(68, Math.max(48, (panelWidth - 24 - tg * (tabs - 1)) / tabs));
        int sx = panelLeftX + (panelWidth - (tw * 5 + tg * 4)) / 2;
        tabBarButtons.clear();
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            var b = Button.builder(tabLabel(i),
                    btn -> {
                        if (activeTab != idx) {
                            activeTab = idx;
                            // 新页签从头看起，避免继承上一个页签的滚动位置
                            scrollOffset = 0;
                            pendingRebuild = true;
                        }
                    })
                    .bounds(sx + i * (tw + tg), panelTopY + 8, tw, th);
            var btn = b.build();
            addRenderableWidget(btn);
            tabBarButtons.add(btn);
        }
    }

    // ---- TAB 0: Basic ----
    private void buildBasicTab() {
        int r = 0;
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.english_id", data.englishId,
                "sre.custom_role.hint.english_id", v -> data.englishId = v.toLowerCase()).setMaxLength(64);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.display_name",
                data.displayName, "sre.custom_role.hint.display_name", v -> data.displayName = v);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.goals", data.goals,
                "sre.custom_role.hint.goals", v -> data.goals = v);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.description", data.description,
                "sre.custom_role.hint.description", v -> data.description = v);

        StringBuilder efSb = new StringBuilder();
        for (EffectEntry e : data.initialEffects) {
            if (!efSb.isEmpty())
                efSb.append(";");
            efSb.append(e.effectId).append(",").append(e.amplifier);
        }
        addLabel(tabLabels0, "sre.custom_role.label.effects", r);
        EditBox effectsBox = makeBox(fieldX(), rowY(r), FIELD_W + 40, 18, efSb.toString(), v -> {
            data.initialEffects.clear();
            if (v == null || v.isBlank())
                return;
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("([a-z0-9_\\-.:]+)[,:](\\d+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(v);
            while (m.find()) {
                String id = m.group(1).trim();
                int lvl = 0;
                try {
                    lvl = Integer.parseInt(m.group(2));
                } catch (Exception ignored) {
                }
                data.initialEffects.add(new EffectEntry(id, lvl));
            }
        });
        effectsBox.setHint(Component.translatable("sre.custom_role.hint.effects_example"));
        effectsBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.effects_example")));
        recordWidgetBase(effectsBox, baseRowY(r));
        tabWidgets0.add(effectsBox);
        r++;

        addLabel(tabLabels0, "sre.custom_role.label.color_rgb", r++);
        int colorRow = r - 1;
        EditBox rBox = makeBox(fieldX(), rowY(colorRow), 40, 18, String.valueOf(data.colorR), v -> {
            try {
                data.colorR = clamp(v, 255);
            } catch (Exception e) {
            }
        });
        EditBox gBox = makeBox(fieldX() + 48, rowY(colorRow), 40, 18, String.valueOf(data.colorG), v -> {
            try {
                data.colorG = clamp(v, 255);
            } catch (Exception e) {
            }
        });
        EditBox bBox = makeBox(fieldX() + 96, rowY(colorRow), 40, 18, String.valueOf(data.colorB), v -> {
            try {
                data.colorB = clamp(v, 255);
            } catch (Exception e) {
            }
        });
        recordWidgetBase(rBox, baseRowY(colorRow));
        recordWidgetBase(gBox, baseRowY(colorRow));
        recordWidgetBase(bBox, baseRowY(colorRow));
        tabWidgets0.addAll(List.of(rBox, gBox, bBox));

        addBoolBtn(tabWidgets0, r++, "sre.custom_role.is_innocent", data.isInnocent, v -> data.isInnocent = v, true);
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.can_use_killer", data.canUseKiller, v -> data.canUseKiller = v,
                true);

        Component ml = Component.translatable("sre.custom_role.mood." + (moodIndex == 0 ? "real" : "fake"));
        int moodRow = r++;
        addLabel(tabLabels0, "sre.custom_role.label.mood", moodRow);
        var moodBtn = makeButton(fieldX(), baseRowY(moodRow), FIELD_W, 18,
                Component.translatable("sre.custom_role.mood.current").append(": ").append(ml),
                () -> {
                    moodIndex = (moodIndex + 1) % 2;
                    data.moodType = moodIndex == 0 ? "REAL" : "FAKE";
                    init(minecraft, width, height);
                });
        tabWidgets0.add(moodBtn);

        // 心情颜色覆盖（R/G/B，任一 <0 视为不覆盖）
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 60, "sre.custom_role.label.mood_color_r",
                String.valueOf(data.moodColorR), "sre.custom_role.hint.minus_one_default",
                v -> {
                    try {
                        data.moodColorR = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 60, "sre.custom_role.label.mood_color_g",
                String.valueOf(data.moodColorG), "sre.custom_role.hint.minus_one_default",
                v -> {
                    try {
                        data.moodColorG = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 60, "sre.custom_role.label.mood_color_b",
                String.valueOf(data.moodColorB), "sre.custom_role.hint.minus_one_default",
                v -> {
                    try {
                        data.moodColorB = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });

        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 80, "sre.custom_role.label.sprint_mult",
                String.valueOf(data.sprintMultiplier), "sre.custom_role.hint.default_one",
                v -> {
                    try {
                        data.sprintMultiplier = Double.parseDouble(v);
                    } catch (Exception ignored) {
                    }
                });
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.infinite_sprint", data.infiniteSprint,
                v -> data.infiniteSprint = v, true);
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.can_see_time", data.canSeeTime, v -> data.canSeeTime = v, true);
    }

    // ---- TAB 1: Advanced ----
    private void buildAdvancedTab() {
        int r = 0;
        addBoolBtn(tabWidgets1, r++, "sre.custom_role.can_see_coin", data.canSeeCoin, v -> data.canSeeCoin = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.able_pickup_revolver", data.ableToPickUpRevolver,
                v -> data.ableToPickUpRevolver = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.set_neutrals", data.setNeutrals, v -> data.setNeutrals = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.set_neutral_for_killer", data.setNeutralForKiller,
                v -> data.setNeutralForKiller = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.set_vigilante_team", data.setVigilanteTeam,
                v -> data.setVigilanteTeam = v, true);
        addTriBtn(tabWidgets1, r++, "sre.custom_role.can_see_teammate_killer", data.canSeeTeammateKiller,
                v -> data.canSeeTeammateKiller = v, true);
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.occupied_role_count",
                String.valueOf(data.occupiedRoleCount), "sre.custom_role.hint.default_one",
                v -> {
                    try {
                        data.occupiedRoleCount = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.max_count",
                String.valueOf(data.maxCount), "sre.custom_role.hint.default_one",
                v -> {
                    try {
                        data.maxCount = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        addTriBtn(tabWidgets1, r++, "sre.custom_role.can_auto_add_money", data.canAutoAddMoney,
                v -> data.canAutoAddMoney = v, true);
        addBoolBtn(tabWidgets1, r, "sre.custom_role.can_be_randomed", data.canBeRandomedByOtherRoles,
                v -> data.canBeRandomedByOtherRoles = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_ignore_blackout", data.canIgnoreBlackout,
                v -> data.canIgnoreBlackout = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_see_body_items", data.canSeeBodyItems,
                v -> data.canSeeBodyItems = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_see_body_role_info", data.canSeeBodyRoleInfo,
                v -> data.canSeeBodyRoleInfo = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_see_body_death_reason", data.canSeeBodyDeathReason,
                v -> data.canSeeBodyDeathReason = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_see_body_killer", data.canSeeBodyKiller,
                v -> data.canSeeBodyKiller = v, true);

        // === 职业通用属性补全 ===
        addTriBtn(tabWidgets1, r, "sre.custom_role.neutral_for_innocent", data.neutralForInnocent,
                v -> data.neutralForInnocent = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.mafia_team", data.mafiaTeam, v -> data.mafiaTeam = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_see_body_name", data.canSeeBodyName,
                v -> data.canSeeBodyName = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_use_skill_while_spectator", data.canUseSkillWhileSpectator,
                v -> data.canUseSkillWhileSpectator = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_be_poisoned", data.canBePoisoned, v -> data.canBePoisoned = v,
                true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.hidden_for_role_rotation", data.hiddenForRoleRotation,
                v -> data.hiddenForRoleRotation = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.special_vigilante", data.specialVigilante,
                v -> data.specialVigilante = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.refreshable_special_vigilante", data.refreshableSpecialVigilante,
                v -> data.refreshableSpecialVigilante = v, true);
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.refresh_special_vigilante_chance",
                String.valueOf(data.refreshableSpecialVigilanteChance), "sre.custom_role.hint.chance_range_10000",
                v -> {
                    try {
                        data.refreshableSpecialVigilanteChance = Math.min(10000, Math.max(0, Integer.parseInt(v)));
                    } catch (Exception ignored) {
                    }
                });
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_jump_manhole", data.canJumpManhole, v -> data.canJumpManhole = v,
                true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_across_fog", data.canAcrossFog, v -> data.canAcrossFog = v,
                true);
        addTriBtn(tabWidgets1, r++, "sre.custom_role.can_use_sabotage", data.canUseSabotage,
                v -> data.canUseSabotage = v, true);

        // === 免疫 / 经济 / 战斗 / 杀手同伙 ===
        addTriBtn(tabWidgets1, r, "sre.custom_role.fall_damage_immune", data.fallDamageImmune,
                v -> data.fallDamageImmune = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.darkness_immune", data.darknessImmune,
                v -> data.darknessImmune = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.environmental_immune", data.environmentalImmune,
                v -> data.environmentalImmune = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.no_coin_system", data.noCoinSystem, v -> data.noCoinSystem = v,
                true);
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.initial_coin_count",
                String.valueOf(data.initialCoinCount), "sre.custom_role.hint.minus_one_no_change",
                v -> {
                    try {
                        data.initialCoinCount = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        addTriBtn(tabWidgets1, r++, "sre.custom_role.cannot_earn_coin_from_kills", data.cannotEarnCoinFromKills,
                v -> data.cannotEarnCoinFromKills = v, true);
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.neutral_kill_coin",
                String.valueOf(data.neutralKillCoin), "sre.custom_role.hint.zero_no_give",
                v -> {
                    try {
                        data.neutralKillCoin = Math.max(0, Integer.parseInt(v));
                    } catch (Exception ignored) {
                    }
                });
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_kill_with_bow_crossbow", data.canKillWithBowAndCrossbow,
                v -> data.canKillWithBowAndCrossbow = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_kill_with_trident", data.canKillWithTrident,
                v -> data.canKillWithTrident = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.cannot_knife_left_click", data.cannotKnifeLeftClick,
                v -> data.cannotKnifeLeftClick = v, true);
        addTriBtn(tabWidgets1, r++, "sre.custom_role.can_use_dream_axe", data.canUseDreamAxe,
                v -> data.canUseDreamAxe = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.killer_teammate_visibility_enabled",
                data.killerTeammateVisibilityEnabled, v -> data.killerTeammateVisibilityEnabled = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_be_seen_as_killer_teammate", data.canBeSeenAsKillerTeammate,
                v -> data.canBeSeenAsKillerTeammate = v, true);

        // === 自定义独立胜利 (仅中立 && !杀手方中立时可用) ===
        boolean isNeutral = data.setNeutrals != null && data.setNeutrals;
        boolean isNotKillerNeutral = data.setNeutralForKiller != null && !data.setNeutralForKiller;
        if (isNeutral && isNotKillerNeutral) {
            addLabel(tabLabels1, "sre.custom_role.custom_win_section", r++);
            addBoolBtnX(tabWidgets1, r++, "sre.custom_role.enable_custom_win", data.enableCustomWin,
                    v -> data.enableCustomWin = v, true);
            if (data.enableCustomWin) {
                makeLabeledBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_title",
                        data.customWinTitle, v -> data.customWinTitle = v);
                makeLabeledBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_subtitle",
                        data.customWinSubtitle, v -> data.customWinSubtitle = v);
                addBoolBtn(tabWidgets1, r, "sre.custom_role.custom_win_survive", data.customWinSurviveToLast, v -> {
                    data.customWinSurviveToLast = v;
                    if (v)
                        data.customWinLastAlive = false;
                }, true);
                addBoolBtnX(tabWidgets1, r++, "sre.custom_role.custom_win_last_alive", data.customWinLastAlive, v -> {
                    data.customWinLastAlive = v;
                    if (v)
                        data.customWinSurviveToLast = false;
                }, true);
                makeLabeledHintBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_with_roles",
                        String.join(";", data.customWinLastWithRoles), "sre.custom_role.hint.role_semicolon_list",
                        v -> {
                            data.customWinLastWithRoles.clear();
                            for (String s : v.split(";")) {
                                String t = s.trim();
                                if (!t.isEmpty())
                                    data.customWinLastWithRoles.add(t);
                            }
                        });
                makeLabeledHintBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_tag_sleep",
                        data.customWinTagSleep, "sre.custom_role.hint.customwin_tag",
                        v -> data.customWinTagSleep = v.trim());
                makeLabeledHintBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_held_item",
                        data.customWinHeldItem, "sre.custom_role.hint.item_example",
                        v -> data.customWinHeldItem = v.trim());
            }
        }

        // 特殊地图类型限制（枚举按钮，默认 ALL）
        int smRow = r++;
        addLabel(tabLabels1, "sre.custom_role.special_map_role", smRow);
        var smBtn = makeButton(fieldX(), baseRowY(smRow), FIELD_W, 18,
                Component.translatable("sre.custom_role.special_map_role.current").append(": ")
                        .append(Component.literal(data.specialMapRole)),
                () -> {
                    // 直接取自枚举，避免像以前那样漏掉 HORSE 之类的特性
                    String[] vals = java.util.Arrays.stream(MapSpecialFeatures.values()).map(MapSpecialFeatures::name)
                            .toArray(String[]::new);
                    int idx = java.util.Arrays.asList(vals).indexOf(data.specialMapRole);
                    if (idx < 0) {
                        // 配置里是非法值（或大小写不符）：先回到 ALL，避免按一下按钮就跳到别的特性上
                        data.specialMapRole = "ALL";
                    } else {
                        data.specialMapRole = vals[(idx + 1) % vals.length];
                    }
                    init(minecraft, width, height);
                });
        tabWidgets1.add(smBtn);

        // 小游戏任务独立计时 / 被透视时隐藏职业信息
        addTriBtn(tabWidgets1, r, "sre.custom_role.independent_minigame_timing",
                data.independentMinigameTiming, v -> data.independentMinigameTiming = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.hide_role_info_when_seen",
                data.hideRoleInfoWhenSeen, v -> data.hideRoleInfoWhenSeen = v, true);
        // 能否"小脑"别人 / 能否被别人"小脑"
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_xiaonao",
                data.canXiaonao, v -> data.canXiaonao = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_be_xiaonao",
                data.canBeXiaonao, v -> data.canBeXiaonao = v, true);
        // 结算时计入"存活好人" / "存活杀手"计数
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_increase_surviving_innocents",
                data.canIncreaseSurvivingInnocents, v -> data.canIncreaseSurvivingInnocents = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_increase_surviving_killers",
                data.canIncreaseSurvivingKillers, v -> data.canIncreaseSurvivingKillers = v, true);
    }

    // ---- TAB 2: Ability ----
    private void buildAbilityTab() {
        int r = 0;
        if (data.initialItems.isEmpty())
            data.initialItems.add(new InitialItemEntry());
        for (int i = 0; i < data.initialItems.size(); i++) {
            final int idx = i;
            InitialItemEntry en = data.initialItems.get(i);
            int y = rowY(r);
            addLabel(tabLabels2, "sre.custom_role.label.initial_items", r);
            EditBox ib = makeBox(fieldX(), y, 130, 18, en.itemId, v -> en.itemId = v);
            ib.setHint(Component.translatable("sre.custom_role.hint.item_id"));
            ib.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.item_id")));
            EditBox cb = makeBox(fieldX() + 138, y, 50, 18, String.valueOf(en.count), v -> {
                try {
                    en.count = Integer.parseInt(v);
                } catch (Exception ignored) {
                }
            });
            cb.setHint(Component.translatable("sre.custom_role.hint.count"));
            cb.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.count")));
            recordWidgetBase(ib, baseRowY(r));
            recordWidgetBase(cb, baseRowY(r));
            tabWidgets2.addAll(List.of(ib, cb));
            var plusBtn = makeButton(fieldX() + 196, baseRowY(r), 20, 18, Component.literal("+"),
                    () -> {
                        data.initialItems.add(new InitialItemEntry());
                        init(minecraft, width, height);
                    });
            tabWidgets2.add(plusBtn);
            if (data.initialItems.size() > 1) {
                var minusBtn = makeButton(fieldX() + 220, baseRowY(r), 20, 18, Component.literal("-"),
                        () -> {
                            data.initialItems.remove(idx);
                            init(minecraft, width, height);
                        });
                tabWidgets2.add(minusBtn);
            }
            r++;
        }
        r++; // spacer

        // ═══ 任务奖励（完成 N 个任务给物品） ═══
        addLabel(tabLabels2, "sre.custom_role.task_reward_section", r++);
        makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 60, "sre.custom_role.label.task_reward_count",
                String.valueOf(data.taskRewardCount), "sre.custom_role.hint.zero_off",
                v -> {
                    try {
                        data.taskRewardCount = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 60, "sre.custom_role.label.task_reward_max_triggers",
                String.valueOf(data.taskRewardMaxTriggers), "sre.custom_role.hint.default_one",
                v -> {
                    try {
                        data.taskRewardMaxTriggers = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.task_reward_unlimited", data.taskRewardUnlimited,
                v -> data.taskRewardUnlimited = v, true);
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.task_reward_silent", data.taskRewardSilent,
                v -> data.taskRewardSilent = v, true);
        makeLabeledBox(tabWidgets2, tabLabels2, r++, FIELD_W, "sre.custom_role.task_reward_message",
                data.taskRewardMessage, v -> data.taskRewardMessage = v);
        if (data.taskRewardItems.isEmpty())
            data.taskRewardItems.add(new InitialItemEntry());
        for (int i = 0; i < data.taskRewardItems.size(); i++) {
            final int idx = i;
            InitialItemEntry en = data.taskRewardItems.get(i);
            int y = rowY(r);
            addLabel(tabLabels2, "sre.custom_role.label.task_reward_items", r);
            EditBox ib = makeBox(fieldX(), y, 130, 18, en.itemId, v -> en.itemId = v);
            ib.setHint(Component.translatable("sre.custom_role.hint.item_id"));
            ib.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.item_id")));
            EditBox cb = makeBox(fieldX() + 138, y, 50, 18, String.valueOf(en.count), v -> {
                try {
                    en.count = Integer.parseInt(v);
                } catch (Exception ignored) {
                }
            });
            cb.setHint(Component.translatable("sre.custom_role.hint.count"));
            cb.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.count")));
            recordWidgetBase(ib, baseRowY(r));
            recordWidgetBase(cb, baseRowY(r));
            tabWidgets2.addAll(List.of(ib, cb));
            var plusBtn = makeButton(fieldX() + 196, baseRowY(r), 20, 18, Component.literal("+"),
                    () -> {
                        data.taskRewardItems.add(new InitialItemEntry());
                        init(minecraft, width, height);
                    });
            tabWidgets2.add(plusBtn);
            if (data.taskRewardItems.size() > 1) {
                var minusBtn = makeButton(fieldX() + 220, baseRowY(r), 20, 18, Component.literal("-"),
                        () -> {
                            data.taskRewardItems.remove(idx);
                            init(minecraft, width, height);
                        });
                tabWidgets2.add(minusBtn);
            }
            r++;
        }
        r++; // spacer

        // ═══ 直觉系统 ═══
        addLabel(tabLabels2, "sre.custom_role.instinct_section", r++);

        // 启用直觉 + 夜视
        addBoolBtn(tabWidgets2, r, "sre.custom_role.can_use_instinct", data.canUseInstinct,
                v -> {
                    data.canUseInstinct = v;
                    if (!v)
                        data.instinctModes.clear();
                    init(minecraft, width, height);
                }, true);
        addTriBtnX(tabWidgets2, r++, "sre.custom_role.instinct_night_vision", data.instinctNightVision,
                v -> data.instinctNightVision = v, true);

        if (data.canUseInstinct) {
            ensureInstinctMode();
            InstinctModeData mode = data.instinctModes.get(0);

            // 看别人
            addLabel(tabLabels2, "sre.custom_role.instinct_seeing", r);
            var seeingOffBtn = makeInstinctTypeBtn(fieldX(), baseRowY(r), 155, 18,
                    () -> mode.seeingOff, v -> mode.seeingOff = v);
            var seeingOnBtn = makeInstinctTypeBtn(fieldX() + 163, baseRowY(r), 155, 18,
                    () -> mode.seeingOn, v -> mode.seeingOn = v);
            tabWidgets2.add(seeingOffBtn);
            tabWidgets2.add(seeingOnBtn);
            r++;

            // 被看
            addLabel(tabLabels2, "sre.custom_role.instinct_be_seen", r);
            var beSeenOffBtn = makeInstinctTypeBtn(fieldX(), baseRowY(r), 155, 18,
                    () -> mode.beSeenOff, v -> mode.beSeenOff = v);
            var beSeenOnBtn = makeInstinctTypeBtn(fieldX() + 163, baseRowY(r), 155, 18,
                    () -> mode.beSeenOn, v -> mode.beSeenOn = v);
            tabWidgets2.add(beSeenOffBtn);
            tabWidgets2.add(beSeenOnBtn);
            r++;

            // 自定义颜色输入（当任一字段为 CUSTOM 时显示）
            boolean hasCustom = isCustomType(mode.seeingOff) || isCustomType(mode.seeingOn)
                    || isCustomType(mode.beSeenOff) || isCustomType(mode.beSeenOn);
            if (hasCustom) {
                // 收集所有 CUSTOM 字段，使用同一个颜色输入
                final String sharedHex = findFirstCustomHex(mode);
                addLabel(tabLabels2, "sre.custom_role.instinct_custom_color", r);
                EditBox colorBox = makeBox(fieldX(), rowY(r), 80, 18, sharedHex, v -> {
                    String hex = v.trim().replaceAll("[^0-9a-fA-F]", "");
                    if (hex.isEmpty())
                        hex = "FF0000";
                    String newVal = "CUSTOM(0x" + hex + ")";
                    if (isCustomType(mode.seeingOff))
                        mode.seeingOff = newVal;
                    if (isCustomType(mode.seeingOn))
                        mode.seeingOn = newVal;
                    if (isCustomType(mode.beSeenOff))
                        mode.beSeenOff = newVal;
                    if (isCustomType(mode.beSeenOn))
                        mode.beSeenOn = newVal;
                });
                colorBox.setHint(Component.translatable("sre.custom_role.hint.hex"));
                colorBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.hex")));
                recordWidgetBase(colorBox, baseRowY(r));
                tabWidgets2.add(colorBox);
                r++;
            }

            // 看别人最大距离（透视范围）
            makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.instinct_range",
                    mode.maxRange, "sre.custom_role.hint.any_range", v -> mode.maxRange = v);
            // 被透视最大距离（被别人看范围，独立）
            makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.instinct_beseen_range",
                    mode.beSeenMaxRange == null ? "*" : mode.beSeenMaxRange, "sre.custom_role.hint.any_range_beseen",
                    v -> mode.beSeenMaxRange = v);
            addBoolBtn(tabWidgets2, r++, "sre.custom_role.instinct_unlimited_teammate", mode.unlimitedTeammate,
                    v -> mode.unlimitedTeammate = v, false);
        }

        r++;
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.enable_ability", data.enableAbility, v -> data.enableAbility = v,
                true);
        if (data.enableAbility) {
            // 是否启用切换技能
            addBoolBtn(tabWidgets2, r++, "sre.custom_role.enable_skill_switch", data.enableSkillSwitch,
                    v -> {
                        data.enableSkillSwitch = v;
                        init(minecraft, width, height);
                    }, true);

            if (data.enableSkillSwitch) {
                // ===== 多技能模块（技能1、技能2…） =====
                if (data.skillModules.isEmpty())
                    data.skillModules.add(new CustomRoleData.SkillData());
                for (int m = 0; m < data.skillModules.size(); m++) {
                    final int mi = m;
                    CustomRoleData.SkillData sd = data.skillModules.get(mi);

                    // 模块标题（技能N）+ 删除模块按钮
                    addLabel(tabLabels2, "sre.custom_role.skill_module", r);
                    var moduleTitle = makeButton(fieldX(), baseRowY(r), FIELD_W - 24, 18,
                            Component.translatable("sre.custom_role.skill_module_title", m + 1),
                            () -> {
                            });
                    tabWidgets2.add(moduleTitle);
                    var delModule = makeButton(fieldX() + FIELD_W - 22, baseRowY(r), 22, 18,
                            Component.literal("X"),
                            () -> {
                                data.skillModules.remove(mi);
                                init(minecraft, width, height);
                            });
                    tabWidgets2.add(delModule);
                    r++;

                    // 技能名称（本模块专用，用于 HUD 显示）
                    makeLabeledBox(tabWidgets2, tabLabels2, r++, FIELD_W, "sre.custom_role.skill_name", sd.name,
                            v -> sd.name = v);

                    // 技能执行指令
                    if (sd.commands.isEmpty())
                        sd.commands.add("");
                    for (int i = 0; i < sd.commands.size(); i++) {
                        final int idx = i;
                        int y = rowY(r);
                        addLabel(tabLabels2, "sre.custom_role.label.ability_commands", r);
                        EditBox cmdBox = makeBox(fieldX(), y, 250, 18, sd.commands.get(i),
                                v -> sd.commands.set(idx, v));
                        cmdBox.setHint(Component.translatable("sre.custom_role.hint.command"));
                        cmdBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.command")));
                        recordWidgetBase(cmdBox, baseRowY(r));
                        tabWidgets2.add(cmdBox);
                        var plusBtn2 = makeButton(fieldX() + 258, baseRowY(r), 20, 18, Component.literal("+"),
                                () -> {
                                    sd.commands.add("");
                                    init(minecraft, width, height);
                                });
                        tabWidgets2.add(plusBtn2);
                        if (sd.commands.size() > 1) {
                            var minusBtn2 = makeButton(fieldX() + 282, baseRowY(r), 20, 18,
                                    Component.literal("-"),
                                    () -> {
                                        sd.commands.remove(idx);
                                        init(minecraft, width, height);
                                    });
                            tabWidgets2.add(minusBtn2);
                        }
                        r++;
                    }
                    makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_cooldown",
                            String.valueOf(sd.cooldownSeconds), "sre.custom_role.hint.cooldown_seconds",
                            v -> {
                                try {
                                    sd.cooldownSeconds = Integer.parseInt(v);
                                } catch (Exception ignored) {
                                }
                            });
                    makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80,
                            "sre.custom_role.label.ability_initial_cooldown", String.valueOf(sd.initialCooldownSeconds),
                            "sre.custom_role.hint.initial_cooldown_seconds",
                            v -> {
                                try {
                                    sd.initialCooldownSeconds = Integer.parseInt(v);
                                } catch (Exception ignored) {
                                }
                            });
                    makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_delay_seconds",
                            String.valueOf(sd.delaySeconds), "sre.custom_role.hint.delay_seconds",
                            v -> {
                                try {
                                    sd.delaySeconds = Integer.parseInt(v);
                                } catch (Exception ignored) {
                                }
                            });
                    if (sd.delayedCommands.isEmpty())
                        sd.delayedCommands.add("");
                    for (int i = 0; i < sd.delayedCommands.size(); i++) {
                        final int idx = i;
                        int y = rowY(r);
                        addLabel(tabLabels2, "sre.custom_role.label.ability_delayed_commands", r);
                        EditBox dcBox = makeBox(fieldX(), y, 250, 18, sd.delayedCommands.get(i),
                                v -> sd.delayedCommands.set(idx, v));
                        dcBox.setHint(Component.translatable("sre.custom_role.hint.command_no_slash"));
                        dcBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.command_no_slash")));
                        recordWidgetBase(dcBox, baseRowY(r));
                        tabWidgets2.add(dcBox);
                        var dplus = makeButton(fieldX() + 258, baseRowY(r), 20, 18, Component.literal("+"),
                                () -> {
                                    sd.delayedCommands.add("");
                                    init(minecraft, width, height);
                                });
                        tabWidgets2.add(dplus);
                        if (sd.delayedCommands.size() > 1) {
                            var dminus = makeButton(fieldX() + 282, baseRowY(r), 20, 18, Component.literal("-"),
                                    () -> {
                                        sd.delayedCommands.remove(idx);
                                        init(minecraft, width, height);
                                    });
                            tabWidgets2.add(dminus);
                        }
                        r++;
                    }
                    if (sd.gameEndCommands.isEmpty())
                        sd.gameEndCommands.add("");
                    for (int i = 0; i < sd.gameEndCommands.size(); i++) {
                        final int idx = i;
                        int y = rowY(r);
                        addLabel(tabLabels2, "sre.custom_role.label.game_end_commands", r);
                        EditBox geBox = makeBox(fieldX(), y, 250, 18, sd.gameEndCommands.get(i),
                                v -> sd.gameEndCommands.set(idx, v));
                        geBox.setHint(Component.translatable("sre.custom_role.hint.command_no_slash"));
                        geBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.command_no_slash")));
                        recordWidgetBase(geBox, baseRowY(r));
                        tabWidgets2.add(geBox);
                        var gePlus = makeButton(fieldX() + 258, baseRowY(r), 20, 18, Component.literal("+"),
                                () -> {
                                    sd.gameEndCommands.add("");
                                    init(minecraft, width, height);
                                });
                        tabWidgets2.add(gePlus);
                        if (sd.gameEndCommands.size() > 1) {
                            var geMinus = makeButton(fieldX() + 282, baseRowY(r), 20, 18, Component.literal("-"),
                                    () -> {
                                        sd.gameEndCommands.remove(idx);
                                        init(minecraft, width, height);
                                    });
                            tabWidgets2.add(geMinus);
                        }
                        r++;
                    }
                    r++; // 模块间隔
                }
                // 添加技能模块按钮
                var addModuleBtn = makeButton(fieldX(), baseRowY(r), 160, 18,
                        Component.translatable("sre.custom_role.add_skill_module"),
                        () -> {
                            data.skillModules.add(new CustomRoleData.SkillData());
                            init(minecraft, width, height);
                        });
                tabWidgets2.add(addModuleBtn);
                r++;
            } else {
                // ===== 单技能（旧字段，向后兼容） =====
                // 技能名称（HUD 显示在冷却上方）
                makeLabeledBox(tabWidgets2, tabLabels2, r++, FIELD_W, "sre.custom_role.ability_name", data.abilityName,
                        v -> data.abilityName = v);

                if (data.abilitySkillCommands.isEmpty())
                    data.abilitySkillCommands.add("");
                for (int i = 0; i < data.abilitySkillCommands.size(); i++) {
                    final int idx = i;
                    int y = rowY(r);
                    addLabel(tabLabels2, "sre.custom_role.label.ability_commands", r);
                    EditBox cmdBox = makeBox(fieldX(), y, 250, 18, data.abilitySkillCommands.get(i),
                            v -> data.abilitySkillCommands.set(idx, v));
                    cmdBox.setHint(Component.translatable("sre.custom_role.hint.command"));
                    cmdBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.command")));
                    recordWidgetBase(cmdBox, baseRowY(r));
                    tabWidgets2.add(cmdBox);
                    var plusBtn2 = makeButton(fieldX() + 258, baseRowY(r), 20, 18, Component.literal("+"),
                            () -> {
                                data.abilitySkillCommands.add("");
                                init(minecraft, width, height);
                            });
                    tabWidgets2.add(plusBtn2);
                    if (data.abilitySkillCommands.size() > 1) {
                        var minusBtn2 = makeButton(fieldX() + 282, baseRowY(r), 20, 18, Component.literal("-"),
                                () -> {
                                    data.abilitySkillCommands.remove(idx);
                                    init(minecraft, width, height);
                                });
                        tabWidgets2.add(minusBtn2);
                    }
                    r++;
                }
                makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_cooldown",
                        String.valueOf(data.abilityCooldownSeconds), "sre.custom_role.hint.cooldown_seconds",
                        v -> {
                            try {
                                data.abilityCooldownSeconds = Integer.parseInt(v);
                            } catch (Exception ignored) {
                            }
                        });
                makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_initial_cooldown",
                        String.valueOf(data.abilityInitialCooldownSeconds), "sre.custom_role.hint.initial_cooldown_seconds",
                        v -> {
                            try {
                                data.abilityInitialCooldownSeconds = Integer.parseInt(v);
                            } catch (Exception ignored) {
                            }
                        });
                makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_delay_seconds",
                        String.valueOf(data.abilityDelaySeconds), "sre.custom_role.hint.delay_seconds",
                        v -> {
                            try {
                                data.abilityDelaySeconds = Integer.parseInt(v);
                            } catch (Exception ignored) {
                            }
                        });
                if (data.abilityDelayedCommands.isEmpty())
                    data.abilityDelayedCommands.add("");
                for (int i = 0; i < data.abilityDelayedCommands.size(); i++) {
                    final int idx = i;
                    int y = rowY(r);
                    addLabel(tabLabels2, "sre.custom_role.label.ability_delayed_commands", r);
                    EditBox dcBox = makeBox(fieldX(), y, 250, 18, data.abilityDelayedCommands.get(i),
                            v -> data.abilityDelayedCommands.set(idx, v));
                    dcBox.setHint(Component.translatable("sre.custom_role.hint.command_no_slash"));
                    dcBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.command_no_slash")));
                    recordWidgetBase(dcBox, baseRowY(r));
                    tabWidgets2.add(dcBox);
                    var dplus = makeButton(fieldX() + 258, baseRowY(r), 20, 18, Component.literal("+"),
                            () -> {
                                data.abilityDelayedCommands.add("");
                                init(minecraft, width, height);
                            });
                    tabWidgets2.add(dplus);
                    if (data.abilityDelayedCommands.size() > 1) {
                        var dminus = makeButton(fieldX() + 282, baseRowY(r), 20, 18, Component.literal("-"),
                                () -> {
                                    data.abilityDelayedCommands.remove(idx);
                                    init(minecraft, width, height);
                                });
                        tabWidgets2.add(dminus);
                    }
                    r++;
                }
                if (data.gameEndCommands.isEmpty())
                    data.gameEndCommands.add("");
                for (int i = 0; i < data.gameEndCommands.size(); i++) {
                    final int idx = i;
                    int y = rowY(r);
                    addLabel(tabLabels2, "sre.custom_role.label.game_end_commands", r);
                    EditBox geBox = makeBox(fieldX(), y, 250, 18, data.gameEndCommands.get(i),
                            v -> data.gameEndCommands.set(idx, v));
                    geBox.setHint(Component.translatable("sre.custom_role.hint.command_no_slash"));
                    geBox.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.command_no_slash")));
                    recordWidgetBase(geBox, baseRowY(r));
                    tabWidgets2.add(geBox);
                    var gePlus = makeButton(fieldX() + 258, baseRowY(r), 20, 18, Component.literal("+"),
                            () -> {
                                data.gameEndCommands.add("");
                                init(minecraft, width, height);
                            });
                    tabWidgets2.add(gePlus);
                    if (data.gameEndCommands.size() > 1) {
                        var geMinus = makeButton(fieldX() + 282, baseRowY(r), 20, 18, Component.literal("-"),
                                () -> {
                                    data.gameEndCommands.remove(idx);
                                    init(minecraft, width, height);
                                });
                        tabWidgets2.add(geMinus);
                    }
                    r++;
                }
            }
        }
    }

    // ---- TAB 3: Generation ----
    private void buildGenerationTab() {
        int r = 0;
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.two_way_opposing",
                String.join(",", data.twoWayOpposingJobs), "sre.custom_role.hint.role_list",
                v -> {
                    data.twoWayOpposingJobs.clear();
                    for (String s : v.split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty())
                            data.twoWayOpposingJobs.add(t);
                    }
                });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.opposing",
                String.join(",", data.opposingJobs), "sre.custom_role.hint.role_list",
                v -> {
                    data.opposingJobs.clear();
                    for (String s : v.split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty())
                            data.opposingJobs.add(t);
                    }
                });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.bind_with",
                String.join(",", data.bindWithRoles), "sre.custom_role.hint.role_list",
                v -> {
                    data.bindWithRoles.clear();
                    for (String s : v.split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty())
                            data.bindWithRoles.add(t);
                    }
                });
        // 相关职业 / 相关修饰符（介绍页展示）
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.both_related_roles",
                String.join(",", data.bothRelatedRoles), "sre.custom_role.hint.role_list",
                v -> replaceCsv(data.bothRelatedRoles, v));
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.related_roles",
                String.join(",", data.relatedRoles), "sre.custom_role.hint.role_list",
                v -> replaceCsv(data.relatedRoles, v));
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.remove_related_roles",
                String.join(",", data.removeRelatedRoles), "sre.custom_role.hint.role_list",
                v -> replaceCsv(data.removeRelatedRoles, v));
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.both_related_modifiers",
                String.join(",", data.bothRelatedModifiers), "sre.custom_role.hint.modifier_list",
                v -> replaceCsv(data.bothRelatedModifiers, v));
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.related_modifiers",
                String.join(",", data.relatedModifiers), "sre.custom_role.hint.modifier_list",
                v -> replaceCsv(data.relatedModifiers, v));
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.remove_related_modifiers",
                String.join(",", data.removeRelatedModifiers), "sre.custom_role.hint.modifier_list",
                v -> replaceCsv(data.removeRelatedModifiers, v));
        // 关联（绑定生成）职业的移除 / 清空（清空在添加之前生效）
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.remove_occupation_roles",
                String.join(",", data.removeOccupationRoles), "sre.custom_role.hint.role_list",
                v -> replaceCsv(data.removeOccupationRoles, v));
        addBoolBtn(tabWidgets3, r++, "sre.custom_role.label.clear_occupation_roles", data.clearOccupationRoles,
                v -> data.clearOccupationRoles = v, true);
        // 任务刷新黑 / 白名单（按钮选择任务类型）
        r = buildTaskListEditor(tabWidgets3, tabLabels3, r, "sre.custom_role.label.unrefreshable_tasks",
                data.unrefreshableTasks, unrefreshableTaskCursor);
        r = buildTaskListEditor(tabWidgets3, tabLabels3, r, "sre.custom_role.label.only_refreshable_tasks",
                data.onlyRefreshableTasks, onlyRefreshableTaskCursor);

        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.map_restrict",
                String.join(",", data.mapRestrictedTo), "sre.custom_role.hint.map_list",
                v -> replaceCsv(data.mapRestrictedTo, v));
        // 组合地图特性条件（setSpecialMapRolesCondition）
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.special_map_roles",
                String.join(",", data.specialMapRoles), "sre.custom_role.hint.special_map_roles",
                v -> replaceCsv(data.specialMapRoles, v));
        addBoolBtn(tabWidgets3, r++, "sre.custom_role.label.special_map_roles_match_all",
                data.specialMapRolesMatchAll, v -> data.specialMapRolesMatchAll = v, true);
        // 自定义生成条件（setCanSpawnInMap）
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.spawn_conditions",
                String.join(",", data.canSpawnInMapConditions), "sre.custom_role.hint.spawn_conditions",
                v -> replaceCsv(data.canSpawnInMapConditions, v));
        addBoolBtn(tabWidgets3, r++, "sre.custom_role.label.spawn_conditions_match_all",
                data.canSpawnInMapMatchAll, v -> data.canSpawnInMapMatchAll = v, true);
        addBoolBtn(tabWidgets3, r++, "sre.custom_role.use_rare_chance", data.useRareChance, v -> data.useRareChance = v,
                true);
        if (data.useRareChance)
            makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.enable_rare_chance",
                    String.valueOf(data.enableRareChance), "sre.custom_role.hint.chance_range_10000",
                    v -> {
                        try {
                            data.enableRareChance = Math.min(10000, Math.max(0, Integer.parseInt(v)));
                        } catch (Exception ignored) {
                        }
                    });
        else
            makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.enable_chance",
                    String.valueOf(data.enableChance), "sre.custom_role.hint.chance_range_100",
                    v -> {
                        try {
                            data.enableChance = Math.min(100, Math.max(0, Integer.parseInt(v)));
                        } catch (Exception ignored) {
                        }
                    });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.min_players",
                String.valueOf(data.enableNeededPlayerCount), "sre.custom_role.hint.minus_one_no_threshold",
                v -> {
                    try {
                        data.enableNeededPlayerCount = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.max_players",
                String.valueOf(data.defaultEnableMaxPlayerCount), "sre.custom_role.hint.minus_one_no_limit",
                v -> {
                    try {
                        data.defaultEnableMaxPlayerCount = Integer.parseInt(v);
                    } catch (Exception ignored) {
                    }
                });
    }

    // ---- TAB 4: Shop ----
    private void buildShopTab() {
        int lx = fieldX(), bh = 18, r = 0;
        String[] types = { "item", "psycho", "blackout", "monitor_fail", "custom" };
        for (int i = 0; i < data.shopEntries.size(); i++) {
            final int idx = i;
            ShopEntryData en = data.shopEntries.get(i);
            // 类型按钮
            var typeBtn = makeButton(lx, baseRowY(r), 75, bh, Component.literal("[" + en.type + "]"),
                    () -> {
                        int next = (java.util.Arrays.asList(types).indexOf(en.type) + 1) % types.length;
                        if (next < 0)
                            next = 0;
                        en.type = types[next];
                        init(minecraft, width, height);
                    });
            tabWidgets4.add(typeBtn);
            // 价格
            EditBox pb = makeBox(lx + 83, rowY(r), 55, bh, String.valueOf(en.price), v -> {
                try {
                    en.price = Math.max(0, Integer.parseInt(v));
                } catch (Exception ignored) {
                }
            });
            pb.setHint(Component.translatable("sre.custom_role.hint.price"));
            pb.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.price")));
            recordWidgetBase(pb, baseRowY(r));
            tabWidgets4.add(pb);
            // 冷却(仅 item 和 custom)
            if ("item".equals(en.type) || "custom".equals(en.type)) {
                EditBox cd = makeBox(lx + 146, rowY(r), 45, bh, String.valueOf(en.cooldownSeconds), v -> {
                    try {
                        en.cooldownSeconds = Math.max(0, Integer.parseInt(v));
                    } catch (Exception ignored) {
                    }
                });
                cd.setHint(Component.translatable("sre.custom_role.hint.cd_seconds"));
                cd.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.cd_seconds")));
                recordWidgetBase(cd, baseRowY(r));
                tabWidgets4.add(cd);
            }
            // 禁止重复(item)
            if ("item".equals(en.type)) {
                boolean nd = !en.allowDuplicate;
                var dupBtn = makeButton(lx + 199, baseRowY(r), 55, bh,
                        Component.translatable(
                                nd ? "sre.custom_role.shop.dup_forbidden" : "sre.custom_role.shop.dup_allowed"),
                        () -> {
                            en.allowDuplicate = !en.allowDuplicate;
                            init(minecraft, width, height);
                        });
                tabWidgets4.add(dupBtn);
                var delBtn = makeButton(lx + 260, baseRowY(r), 20, bh, Component.literal("X"),
                        () -> {
                            data.shopEntries.remove(idx);
                            init(minecraft, width, height);
                        });
                tabWidgets4.add(delBtn);
            } else {
                var delBtn = makeButton(lx + 199, baseRowY(r), 20, bh, Component.literal("X"),
                        () -> {
                            data.shopEntries.remove(idx);
                            init(minecraft, width, height);
                        });
                tabWidgets4.add(delBtn);
            }
            r++;
            if ("item".equals(en.type)) {
                addLabel(tabLabels4, "sre.custom_role.label.shop_item_id", r);
                EditBox ib2 = makeBox(lx, rowY(r), 160, bh, en.itemId, v -> en.itemId = v);
                ib2.setHint(Component.translatable("sre.custom_role.hint.item_id"));
                ib2.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.item_id")));
                recordWidgetBase(ib2, baseRowY(r));
                tabWidgets4.add(ib2);
                r++;
            }
            if ("custom".equals(en.type)) {
                addLabel(tabLabels4, "sre.custom_role.label.shop_custom_name", r);
                EditBox nb = makeBox(lx, rowY(r), 130, bh, en.displayName, v -> en.displayName = v);
                nb.setHint(Component.translatable("sre.custom_role.hint.shop_name"));
                nb.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.shop_name")));
                recordWidgetBase(nb, baseRowY(r));
                tabWidgets4.add(nb);
                r++;
                addLabel(tabLabels4, "sre.custom_role.label.shop_custom_icon", r);
                EditBox ib3 = makeBox(lx, rowY(r), 130, bh, en.itemId, v -> en.itemId = v);
                ib3.setHint(Component.translatable("sre.custom_role.hint.shop_icon"));
                ib3.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.shop_icon")));
                recordWidgetBase(ib3, baseRowY(r));
                tabWidgets4.add(ib3);
                r++;
                if (en.commands.isEmpty())
                    en.commands.add("");
                for (int c = 0; c < en.commands.size(); c++) {
                    final int cdx = c;
                    int y = rowY(r);
                    addLabel(tabLabels4, "sre.custom_role.label.shop_custom_cmd", r);
                    EditBox cm = makeBox(lx, y, 230, bh, en.commands.get(c), v -> en.commands.set(cdx, v));
                    cm.setHint(Component.translatable("sre.custom_role.hint.command"));
                    cm.setTooltip(Tooltip.create(Component.translatable("sre.custom_role.hint.command")));
                    recordWidgetBase(cm, baseRowY(r));
                    tabWidgets4.add(cm);
                    var plusBtn = makeButton(lx + 238, baseRowY(r), 20, bh, Component.literal("+"),
                            () -> {
                                en.commands.add("");
                                init(minecraft, width, height);
                            });
                    tabWidgets4.add(plusBtn);
                    if (en.commands.size() > 1) {
                        var minusBtn = makeButton(lx + 262, baseRowY(r), 20, bh, Component.literal("-"),
                                () -> {
                                    en.commands.remove(cdx);
                                    init(minecraft, width, height);
                                });
                        tabWidgets4.add(minusBtn);
                    }
                    r++;
                }
            }
        }
        var addEntryBtn = makeButton(lx, baseRowY(r), 140, bh,
                Component.translatable("sre.custom_role.add_shop_entry"),
                () -> {
                    data.shopEntries.add(new ShopEntryData());
                    init(minecraft, width, height);
                });
        tabWidgets4.add(addEntryBtn);
    }

    // ══════════════════════════════════════════════════════════════════
    // 新直觉系统 GUI 辅助方法
    // ══════════════════════════════════════════════════════════════════
    private static final String[] INSTINCT_TYPE_NAMES = {
            "DEFAULT", "NONE", "KILLER_INSTINCT", "OBSERVER_ROLE_COLOR", "TARGET_ROLE_COLOR"
    };

    /** 确保 data.instinctModes 存在至少一个模式，否则从旧字段自动补全 */
    private void ensureInstinctMode() {
        if (data.instinctModes.isEmpty()) {
            InstinctModeData m = new InstinctModeData();
            if (data.instinctSameColorFrame)
                m.seeingOn = "OBSERVER_ROLE_COLOR";
            if (!"*".equals(data.instinctMaxRange))
                m.maxRange = data.instinctMaxRange;
            m.unlimitedTeammate = data.instinctUnlimitedTeammate;
            data.instinctModes.add(m);
        }
    }

    /** 将类型字符串循环到下一个预定义类型 */
    private String cycleInstinctTypeStr(String current) {
        String upper = current.toUpperCase().trim();
        if (upper.startsWith("CUSTOM("))
            return "DEFAULT";
        for (int i = 0; i < INSTINCT_TYPE_NAMES.length; i++) {
            if (INSTINCT_TYPE_NAMES[i].equals(upper))
                return (i + 1 < INSTINCT_TYPE_NAMES.length) ? INSTINCT_TYPE_NAMES[i + 1] : "CUSTOM(0xFFE06B65)";
        }
        return "DEFAULT";
    }

    /** 获取类型字符串的显示名 */
    private String instinctTypeDisplay(String s) {
        if (s == null || s.isEmpty())
            return "DEFAULT";
        String upper = s.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(")) {
            String hex = upper.substring(7, upper.length() - 1).trim();
            try {
                return "CUSTOM(#" + Integer.toHexString(Long.decode(hex).intValue()).toUpperCase().substring(2) + ")";
            } catch (Exception e) {
                return "CUSTOM(???)";
            }
        }
        return upper;
    }

    /** 提取 CUSTOM 颜色字符串中的 hex 部分（不含 0x 前缀） */
    private String extractCustomHex(String s) {
        if (s == null)
            return "FF0000";
        String upper = s.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(") && upper.endsWith(")")) {
            String hex = upper.substring(7, upper.length() - 1).trim();
            try {
                return Integer.toHexString(Long.decode(hex).intValue()).toUpperCase().substring(2);
            } catch (Exception e) {
                return "FF0000";
            }
        }
        return "FF0000";
    }

    /** 检查类型字符串是否为 CUSTOM */
    private boolean isCustomType(String s) {
        return s != null && s.toUpperCase().trim().startsWith("CUSTOM(");
    }

    /** 从模式中提取第一个 CUSTOM 类型的 hex 颜色字符串 */
    private String findFirstCustomHex(InstinctModeData m) {
        for (String s : new String[] { m.seeingOff, m.seeingOn, m.beSeenOff, m.beSeenOn }) {
            if (isCustomType(s))
                return extractCustomHex(s);
        }
        return "FF0000";
    }

    /** 构建一个点击循环的类型按钮，并返回 */
    private Button makeInstinctTypeBtn(int x, int baseY, int w, int h, java.util.function.Supplier<String> getter,
            java.util.function.Consumer<String> setter) {
        String cur = getter.get();
        String display = instinctTypeDisplay(cur);
        Button btn = Button.builder(
                Component.literal(display).append(Component.literal(" ↻").withStyle(s -> s.withColor(0xFF9E8B6E))),
                b -> {
                    setter.accept(cycleInstinctTypeStr(getter.get()));
                    init(minecraft, width, height);
                }).bounds(x, baseY, w, h).build();
        recordWidgetBase(btn, baseY);
        return btn;
    }

    // ---- Bottom ----
    private void buildBottomButtons() {
        int by = panelTopY + panelHeight - 26, bw = 100, gap = 8;
        int sx = panelLeftX + (panelWidth - (bw * 3 + gap * 2)) / 2;
        var btn1 = Button.builder(Component.translatable("sre.custom_role.save"), b -> saveRole())
                .bounds(sx, by, bw, 20).build();
        var btn2 = Button.builder(Component.translatable("sre.custom_role.manage"), b -> {
            CustomRoleConfig config = CustomRoleConfig.getInstance();
            config.savePreferWorldPath(minecraft.getSingleplayerServer());
            minecraft.setScreen(new CustomRoleManageScreen(new CustomRoleScreen()));
        }).bounds(sx + bw + gap, by, bw, 20).build();
        var btn3 = Button.builder(Component.translatable("sre.custom_role.cancel"), b -> onClose())
                .bounds(sx + (bw + gap) * 2, by, bw, 20).build();
        addRenderableWidget(btn1);
        addRenderableWidget(btn2);
        addRenderableWidget(btn3);
        bottomButtons.add(btn1);
        bottomButtons.add(btn2);
        bottomButtons.add(btn3);
    }

    private void saveRole() {
        CustomRoleConfig config = CustomRoleConfig.getInstance();
        if (originalEnglishId != null && !originalEnglishId.isBlank())
            config.removeRole(originalEnglishId);
        config.removeRole(data.englishId);
        config.addRole(data);
        config.savePreferWorldPath(minecraft.getSingleplayerServer());
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            try {
                config.saveToDefaultPath();
                io.wifi.starrailexpress.customrole.CustomRoleLoader.reloadClient();
            } catch (Exception ignored) {
            }
            
            server.execute(() -> {
                try {
                    // 走重载命令的路径：除了重建服务端索引，还会给在线客户端重新握手
                    CustomRoleReloadCommand.reload(server);
                } catch (Exception ignored) {
                }
            });
        }
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.translatable("sre.custom_role.saved", data.englishId),
                    false);
        onClose();
    }

    // ══════════════════════════════════════════════════════════════════
    // Toggle Helpers
    // ══════════════════════════════════════════════════════════════════
    private void addBoolBtn(List<AbstractWidget> l, int r, String key, boolean cur,
            java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        Component st = cur ? Component.literal(" [✓]").withStyle(s -> s.withColor(0xFF72C17B))
                : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFFE06B65));
        var btn = Button.builder(Component.translatable(key).copy().append(st), b -> {
            toggle.accept(!cur);
            if (rebuild)
                init(minecraft, width, height);
        }).bounds(fieldX(), baseRowY(r), FIELD_W, 18).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }

    private void addBoolBtnX(List<AbstractWidget> l, int r, String key, boolean cur,
            java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        Component st = cur ? Component.literal(" [✓]").withStyle(s -> s.withColor(0xFF72C17B))
                : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFFE06B65));
        var btn = Button.builder(Component.translatable(key).copy().append(st), b -> {
            toggle.accept(!cur);
            if (rebuild)
                init(minecraft, width, height);
        }).bounds(fieldX() + 170, baseRowY(r), 150, 18).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }

    /** 逗号分隔文本 → 列表（逐项 trim，忽略空项）。 */
    private static List<String> splitCsv(String value) {
        List<String> result = new java.util.ArrayList<>();
        if (value == null)
            return result;
        for (String s : value.split(",")) {
            String t = s.trim();
            if (!t.isEmpty())
                result.add(t);
        }
        return result;
    }

    /** 用逗号分隔文本覆盖列表。 */
    private static void replaceCsv(List<String> target, String value) {
        if (target == null)
            return;
        target.clear();
        target.addAll(splitCsv(value));
    }

    /**
     * 任务类型列表编辑器：一行「候选类型（点击循环切换）+ 添加」，之后每个已选类型一行（点击移除）。
     *
     * @param cursor 候选类型下标的持有者
     * @return 下一行行号
     */
    private int buildTaskListEditor(List<AbstractWidget> widgets, List<LabelEntry> labels, int row, String labelKey,
            List<String> list, int[] cursor) {
        SREPlayerTaskComponent.Task[] tasks = SREPlayerTaskComponent.Task.values();
        if (tasks.length == 0) {
            return row;
        }
        if (cursor[0] < 0 || cursor[0] >= tasks.length) {
            cursor[0] = 0;
        }
        final SREPlayerTaskComponent.Task candidate = tasks[cursor[0]];
        addLabel(labels, labelKey, row);
        widgets.add(makeButton(fieldX(), baseRowY(row), 130, 18,
                Component.translatable("sre.custom_role.task_candidate", taskName(candidate)),
                () -> {
                    cursor[0] = (cursor[0] + 1) % tasks.length;
                    init(minecraft, width, height);
                }));
        widgets.add(makeButton(fieldX() + 136, baseRowY(row), 64, 18,
                Component.translatable("sre.custom_role.task_add"),
                () -> {
                    if (!list.contains(candidate.name())) {
                        list.add(candidate.name());
                    }
                    init(minecraft, width, height);
                }));
        row++;
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            widgets.add(makeButton(fieldX() + 40, baseRowY(row), 226, 18,
                    Component.translatable("sre.custom_role.task_remove", taskNameOf(list.get(i))),
                    () -> {
                        list.remove(index);
                        init(minecraft, width, height);
                    }));
            row++;
        }
        return row;
    }

    /** 任务显示名：优先 {@code task.<小写枚举名>} 翻译键，缺失时回退枚举名。 */
    private static Component taskName(SREPlayerTaskComponent.Task task) {
        String name = task.name().toLowerCase(java.util.Locale.ROOT);
        if ("raed_book".equals(name)) {
            name = "read_book"; // 枚举名是历史拼写，语言文件里的键是 read_book
        }
        String key = "task." + name;
        return net.minecraft.locale.Language.getInstance().has(key)
                ? Component.translatable(key)
                : Component.literal(task.name());
    }

    /** 配置里存的枚举名 → 显示名（解析失败时按原样显示）。 */
    private static Component taskNameOf(String raw) {
        try {
            return taskName(SREPlayerTaskComponent.Task.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Component.literal(raw);
        }
    }

    private static int safeColor(Boolean b) {
        if (b == null)
            return 0xFF9E8B6E;
        return b.booleanValue() ? 0xFF72C17B : 0xFFE06B65;
    }

    private static Boolean safeNext(Boolean cur) {
        if (cur == null)
            return Boolean.TRUE;
        return cur.booleanValue() ? Boolean.FALSE : null;
    }

    private void addTriBtn(List<AbstractWidget> l, int r, String key, Boolean cur,
            java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        String ss;
        if (cur == null) {
            ss = " (--)";
        } else if (cur.booleanValue()) {
            ss = " [✓]";
        } else {
            ss = " [✗]";
        }
        final Boolean captured = cur;
        var btn = Button.builder(Component.translatable(key)
                .append(Component.literal(ss).withStyle(s -> s.withColor(safeColor(captured)))), b -> {
                    toggle.accept(safeNext(captured));
                    if (rebuild)
                        init(minecraft, width, height);
                }).bounds(fieldX(), baseRowY(r), 150, 18).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }

    private void addTriBtnX(List<AbstractWidget> l, int r, String key, Boolean cur,
            java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        String ss;
        if (cur == null) {
            ss = " (--)";
        } else if (cur.booleanValue()) {
            ss = " [✓]";
        } else {
            ss = " [✗]";
        }
        final Boolean captured = cur;
        var btn = Button.builder(Component.translatable(key)
                .append(Component.literal(ss).withStyle(s -> s.withColor(safeColor(captured)))), b -> {
                    toggle.accept(safeNext(captured));
                    if (rebuild)
                        init(minecraft, width, height);
                }).bounds(fieldX() + 170, baseRowY(r), 150, 18).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }

    // ---- Misc ----
    private void flushTabWidgets() {
        // 通过 addRenderableWidget 注册到事件系统，但不用于渲染
        // 渲染由 render() 手动遍历 getActiveTabWidgets() 完成
        tabWidgets0.forEach(w -> addRenderableWidget(w));
        tabWidgets1.forEach(w -> addRenderableWidget(w));
        tabWidgets2.forEach(w -> addRenderableWidget(w));
        tabWidgets3.forEach(w -> addRenderableWidget(w));
        tabWidgets4.forEach(w -> addRenderableWidget(w));
    }

    private EditBox makeBox(int x, int y, int w, int h, String text, java.util.function.Consumer<String> cb) {
        EditBox box = new EditBox(font, x, y, w, h, Component.empty());
        box.setValue(text);
        box.setMaxLength(256);
        box.setResponder(cb);
        return box;
    }

    private int clamp(String v, int max) {
        return Math.min(max, Math.max(0, Integer.parseInt(v)));
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染（参考 RoleIntroduceScreen：scissor 裁剪 + 滚动条）
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        // 面板背景
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, panelWidth + 12, panelHeight + 6);
        // 内容区域下方填充（覆盖超出内容的 widget 绘制）
        g.fill(panelLeftX - 6, contentBottom(), panelLeftX + panelWidth + 6, panelTopY + panelHeight + 3, SREPanelStyle.PANEL_BG_BOTTOM);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 换页签的重建放在这一帧开始前，且不 return：return 会让这一帧什么都不画，切换标签会闪一下
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildWidgets();
        }

        // 1. 面板背景
        renderBackground(g, mx, my, pt);

        // 2. 固定 widget：标签栏按钮（不裁剪）
        for (var w : tabBarButtons) {
            w.render(g, mx, my, pt);
        }

        // 3. 启用 scissor 裁剪内容区域
        g.enableScissor(panelLeftX, contentTop(), panelLeftX + panelWidth, contentBottom());

        // 4. 内容 widget
        for (var w : getActiveTabWidgets()) {
            w.render(g, mx, my, pt);
        }

        // 5. 内容标签（受 scrollOffset 影响；放不下用省略号截断，悬停看全文）
        List<LabelEntry> al = getActiveLabels();
        List<HintText.Line> lines = new ArrayList<>(al.size());
        for (LabelEntry e : al) {
            lines.add(new HintText.Line(Component.translatable(e.key()), e.x(), e.y() - scrollOffset + 4,
                    0xFFC8B898, e.maxWidth(), e.maxLines()));
        }
        HintText.Line hoveredText = HintText.draw(g, font, lines, mx, my);

        g.disableScissor();

        // tooltip 不能被内容区的裁剪切掉，放在关闭裁剪之后
        if (hoveredText != null) {
            HintText.drawTooltip(g, font, hoveredText, mx, my);
        }

        // 6. 滚动条（覆盖在面板右侧）
        if (maxScroll > 0) {
            renderVScrollbar(g, mx, my);
        }

        // 7. 底部按钮（不裁剪）
        for (var w : bottomButtons) {
            w.render(g, mx, my, pt);
        }

        // 8. 标题
        g.drawCenteredString(font,
                Component.translatable("sre.custom_role.title").withStyle(s -> s.withColor(0xFFD4AF37).withBold(true)),
                panelLeftX + panelWidth / 2, panelTopY + 18, 0xFFFFFF);
    }

    // ══════════════════════════════════════════════════════════════════
    // 滚动条渲染
    // ══════════════════════════════════════════════════════════════════
    private void renderVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelWidth - 6 - SCROLL_W;
        int sbY = contentTop();
        int sbH = contentHeight();

        // 轨道
        // 滑块
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));

        boolean hl = isDraggingScroll || isInRect(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        SREPanelStyle.drawScrollbar(g, sbX, sbY, sbH, thumbY, thumbH, hl);
    }

    // ══════════════════════════════════════════════════════════════════
    // 鼠标事件
    // ══════════════════════════════════════════════════════════════════
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= panelLeftX && mx < panelLeftX + panelWidth
                && my >= contentTop() && my < contentBottom()
                && maxScroll > 0) {
            scrollOffset = Mth.clamp(
                    (int) (scrollOffset - scrollY * 22),
                    0, maxScroll);
            applyScrollOffsets();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int sbX = panelLeftX + panelWidth - 6 - SCROLL_W;
            int sbY = contentTop();
            int sbH = contentHeight();
            if (isInRect((int) mx, (int) my, sbX, sbY, SCROLL_W, sbH) && maxScroll > 0) {
                isDraggingScroll = true;
                dragScrollStartY = my;
                dragScrollStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll > 0) {
            int sbH = contentHeight();
            int totalContentH = sbH + maxScroll;
            float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
            double trackH = sbH - thumbH;
            if (trackH > 0) {
                scrollOffset = Mth.clamp(
                        (int) (dragScrollStartOffset + (my - dragScrollStartY) / trackH * maxScroll),
                        0, maxScroll);
                applyScrollOffsets();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════════
    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 页签文字：活跃页签用金色粗体。
     *
     * <p>
     * 按钮已换成原版按钮（没有 accent 装饰条了），页签的活跃状态用文字区分，
     * 符合 {@code docs/ui_style.md} 第 5 节的文字层级（重点金色 / 次要土褐）。
     */
    private Component tabLabel(int index) {
        Component label = Component.translatable("sre.custom_role.tab." + TAB_NAMES[index]);
        if (index == activeTab) {
            return label.copy().withStyle(style -> style.withBold(true).withColor(SREPanelStyle.GOLD));
        }
        return label.copy().withStyle(style -> style.withColor(SREPanelStyle.MUTED));
    }
}
