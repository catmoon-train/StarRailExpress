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

package io.wifi.starrailexpress.custommodifier;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.client.gui.HintText;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionType;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.EffectData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.AttributeData;
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

/**
 * 自定义修饰符编辑界面（6 页：基础 / 关联 / 生成 / 生成限制 / 触发条件 / 触发内容）。
 *
 * <p>
 * UI 风格与 {@code CustomRoleScreen} 一致（面板 + 页签 + 自绘标签 + EditBox + Button + 滚动）；
 * 重建统一走 {@link #requestRebuild()}，在 render 里执行，避免在按钮回调中清空控件列表。
 */
@Environment(EnvType.CLIENT)
public class CustomModifierScreen extends Screen {

    private static final float USABLE_RATIO = 0.92f;
    private static final int MAX_PANEL_WIDTH = 640;
    private static final int MAX_PANEL_HEIGHT = 520;
    private static final int MIN_PANEL_HEIGHT = 320;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_H = 22;
    private static final int LABEL_W = 150;

    private static final String[] TAB_NAMES = { "basic", "relations", "generation", "restriction", "trigger",
            "effect" };

    private int panelWidth, panelHeight, panelLeftX, panelTopY, activeTab = 0;
    private int scrollOffset = 0, maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragScrollStartY = 0;
    private int dragScrollStartOffset = 0;

    private CustomModifierData data = new CustomModifierData();
    private String originalEnglishId = "";

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final List<LabelEntry> contentLabels = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetBaseY = new IdentityHashMap<>();
    private final List<AbstractWidget> tabBarButtons = new ArrayList<>();
    private final List<AbstractWidget> bottomButtons = new ArrayList<>();

    /** 需要重建界面时置为 true，在 render 中统一重建（避免在控件回调里改控件列表）。 */
    private boolean pendingRebuild = false;

    private record LabelEntry(Component text, int x, int baseY, int color, int maxWidth, int maxLines) {
        /** 该条文字实际会占用的高度（换行的提示要算两行，滚动范围才不会少算）。 */
        int height() {
            return maxLines > 1 ? HintText.LINE_H * maxLines : HintText.LINE_H;
        }
    }

    public CustomModifierScreen() {
        super(Component.translatable("sre.custom_modifier.title"));
    }

    public CustomModifierScreen(CustomModifierData source) {
        super(Component.translatable("sre.custom_modifier.title"));
        if (source != null) {
            this.data = source;
            this.originalEnglishId = source.englishId == null ? "" : source.englishId;
        }
    }

    private CustomModifierData.ConditionType conditionType(ConditionData condition) {
        try {
            return ConditionType.valueOf(condition.type);
        } catch (Exception e) {
            return ConditionType.TIMER;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 布局
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

    private int contentTop() {
        return panelTopY + 34;
    }

    private int contentBottom() {
        return panelTopY + panelHeight - 30;
    }

    private int baseY(int row) {
        return contentTop() + row * ROW_H;
    }

    private int rowY(int baseY) {
        return baseY - scrollOffset;
    }

    private int fieldX() {
        return panelLeftX + LABEL_W;
    }

    private int labelX() {
        return panelLeftX + 6;
    }

    /** 字段标签列的可用宽度（旁边就是输入框，只能单行 + 省略号）。 */
    private int labelW() {
        return LABEL_W - 10;
    }

    /** 整段提示的可用宽度：独占一行，可以直接用面板内容宽度。 */
    private int hintW() {
        return Math.max(60, panelWidth - 12);
    }

    private void requestRebuild() {
        this.pendingRebuild = true;
    }

    // ══════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        contentWidgets.clear();
        contentLabels.clear();
        widgetBaseY.clear();
        tabBarButtons.clear();
        bottomButtons.clear();

        computeLayout();
        // 「仅标记使用」时触发条件 / 触发内容页签不可见，回到基础页
        if (data.markerOnly && (activeTab == 4 || activeTab == 5)) {
            activeTab = 0;
            scrollOffset = 0;
        }
        buildTabBar();

        switch (activeTab) {
            case 0 -> buildBasicTab();
            case 1 -> buildRelationsTab();
            case 2 -> buildGenerationTab();
            case 3 -> buildRestrictionTab();
            case 4 -> buildTriggerTab();
            case 5 -> buildEffectTab();
            default -> {
            }
        }

        for (AbstractWidget widget : contentWidgets) {
            addRenderableWidget(widget);
        }
        buildBottomButtons();

        computeMaxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        applyScroll();
    }

    private void computeMaxScroll() {
        int maxY = contentTop();
        for (AbstractWidget widget : contentWidgets) {
            Integer base = widgetBaseY.get(widget);
            if (base != null) {
                maxY = Math.max(maxY, base + widget.getHeight());
            }
        }
        for (LabelEntry label : contentLabels) {
            maxY = Math.max(maxY, label.baseY() + label.height());
        }
        maxScroll = Math.max(0, maxY + 4 - contentBottom());
    }

    private void applyScroll() {
        for (AbstractWidget widget : contentWidgets) {
            Integer base = widgetBaseY.get(widget);
            if (base != null) {
                widget.setY(base - scrollOffset);
            }
        }
    }

    private void buildTabBar() {
        List<Integer> visible = visibleTabs();
        int th = 20, tg = 4, tabs = visible.size();
        int tw = Math.min(74, Math.max(48, (panelWidth - 24 - tg * (tabs - 1)) / tabs));
        int total = tw * tabs + tg * (tabs - 1);
        int sx = panelLeftX + (panelWidth - total) / 2;
        for (int slot = 0; slot < visible.size(); slot++) {
            int index = visible.get(slot);
            var builder = Button.builder(
                    tabLabel(index),
                    button -> {
                        activeTab = index;
                        scrollOffset = 0;
                        requestRebuild();
                    }).bounds(sx + slot * (tw + tg), panelTopY + 8, tw, th);
            var built = builder.build();
            addRenderableWidget(built);
            tabBarButtons.add(built);
        }
    }

    /** 可见页签下标：「仅标记使用」时隐藏「触发条件 / 触发内容」。 */
    private List<Integer> visibleTabs() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (data.markerOnly && (i == 4 || i == 5)) {
                continue;
            }
            result.add(i);
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════
    // 控件工具（文案一律走翻译键，代码里不再出现硬编码文本）
    // ══════════════════════════════════════════════════════════════════
    private void addLabel(int row, String key) {
        contentLabels.add(new LabelEntry(
                Component.translatable(key).withStyle(s -> s.withColor(0xFFFFF4DC)),
                labelX(), baseY(row), 0xFFFFFF, labelW(), 1));
    }

    private void addHint(int row, Component text, int color) {
        // 整段提示占满一行，放不下就换行（最多两行），再放不下靠悬停看全文
        contentLabels.add(new LabelEntry(text.copy().withStyle(s -> s.withColor(color)),
                labelX(), baseY(row), color, hintW(), 2));
    }

    /** 段落提示（走翻译键）。 */
    private void addHintKey(int row, String key, int color) {
        addHint(row, Component.translatable(key), color);
    }

    /** 输入框占位提示（null / 空 = 不设提示）。 */
    private static Component hint(String key) {
        return key == null || key.isEmpty() ? null : Component.translatable(key);
    }

    private <T extends AbstractWidget> T track(T widget, int baseYValue) {
        widgetBaseY.put(widget, baseYValue);
        contentWidgets.add(widget);
        return widget;
    }

    private EditBox box(int row, int x, int w, String value, Component hint,
            java.util.function.Consumer<String> setter) {
        EditBox box = new EditBox(font, x, rowY(baseY(row)), w, 18, Component.empty());
        box.setValue(value == null ? "" : value);
        box.setMaxLength(256);
        box.setResponder(setter);
        if (hint != null) {
            box.setHint(hint);
            // 输入框内画不下完整提示：悬停看全文
            box.setTooltip(Tooltip.create(hint));
        }
        return track(box, baseY(row));
    }

    /** 左侧标签 + 右侧输入框（标签与占位提示都是翻译键）。 */
    private EditBox labeledBox(int row, String labelKey, String value, String hintKey,
            java.util.function.Consumer<String> setter) {
        addLabel(row, labelKey);
        return box(row, fieldX(), 220, value, hint(hintKey), setter);
    }

    /** 逗号分隔的字符串列表输入框。 */
    private void listBox(int row, String labelKey, List<String> list, String hintKey) {
        addLabel(row, labelKey);
        box(row, fieldX(), 260, String.join(",", safeList(list)), hint(hintKey), value -> {
            list.clear();
            list.addAll(splitList(value));
        });
    }

    private AbstractWidget button(int row, int x, int w, int h, Component text, Runnable onClick) {
        var builder = Button.builder(text, b -> onClick.run()).bounds(x, rowY(baseY(row)), w, h);
        return track(builder.build(), baseY(row));
    }

    /** 删除按钮（红色 ×，文案走翻译键）。 */
    private AbstractWidget removeButton(int row, int x, Runnable onClick) {
        return button(row, x, 18, 18, Component.translatable("sre.custom_modifier.remove"), onClick);
    }

    private void boolButton(int row, String key, boolean current,
            java.util.function.Consumer<Boolean> setter) {
        Component state = current
                ? Component.literal(" [✓]").withStyle(s -> s.withColor(0xFF72C17B))
                : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFFE06B65));
        button(row, fieldX(), 260, 18, Component.translatable(key).copy().append(state),
                () -> {
                    setter.accept(!current);
                    requestRebuild();
                });
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础
    // ══════════════════════════════════════════════════════════════════
    private void buildBasicTab() {
        int r = 0;
        box(r, fieldX(), 260, data.englishId, Component.literal("my_modifier"),
                v -> data.englishId = v.toLowerCase()).setMaxLength(64);
        addLabel(r, "sre.custom_modifier.label.english_id");
        r++;

        labeledBox(r++, "sre.custom_modifier.label.display_name", data.displayName,
                "sre.custom_modifier.hint.display_name", v -> data.displayName = v);
        labeledBox(r++, "sre.custom_modifier.label.description", data.description,
                "sre.custom_modifier.hint.description", v -> data.description = v);

        addLabel(r, "sre.custom_modifier.label.color");
        box(r, fieldX(), 60, String.valueOf(data.colorR), Component.literal("R"),
                v -> data.colorR = parseInt(v, data.colorR));
        box(r, fieldX() + 66, 60, String.valueOf(data.colorG), Component.literal("G"),
                v -> data.colorG = parseInt(v, data.colorG));
        box(r, fieldX() + 132, 60, String.valueOf(data.colorB), Component.literal("B"),
                v -> data.colorB = parseInt(v, data.colorB));
        r++;

        boolButton(r++, "sre.custom_modifier.label.hidden", data.hidden, v -> data.hidden = v);
        boolButton(r++, "sre.custom_modifier.label.marker_only", data.markerOnly, v -> data.markerOnly = v);
        addHintKey(r++, "sre.custom_modifier.hint.marker_only", 0xFF9E8B6E);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：关联（仅作用于介绍页面）
    // ══════════════════════════════════════════════════════════════════
    private void buildRelationsTab() {
        int r = 0;
        listBox(r++, "sre.custom_modifier.label.both_related_roles", data.bothRelatedRoles,
                "sre.custom_modifier.hint.role_list");
        listBox(r++, "sre.custom_modifier.label.related_roles", data.relatedRoles,
                "sre.custom_modifier.hint.role_list");
        listBox(r++, "sre.custom_modifier.label.remove_related_roles", data.removeRelatedRoles,
                "sre.custom_modifier.hint.role_list");
        listBox(r++, "sre.custom_modifier.label.both_related_modifiers", data.bothRelatedModifiers,
                "sre.custom_modifier.hint.modifier_list");
        listBox(r++, "sre.custom_modifier.label.related_modifiers", data.relatedModifiers,
                "sre.custom_modifier.hint.modifier_list");
        listBox(r++, "sre.custom_modifier.label.remove_related_modifiers", data.removeRelatedModifiers,
                "sre.custom_modifier.hint.modifier_list");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 2：生成
    // ══════════════════════════════════════════════════════════════════
    private void buildGenerationTab() {
        int r = 0;
        addLabel(r, "sre.custom_modifier.label.default_max");
        box(r++, fieldX(), 80, String.valueOf(data.defaultMax), Component.literal("1"),
                v -> data.defaultMax = parseInt(v, data.defaultMax));

        addLabel(r, "sre.custom_modifier.label.enable_chance");
        box(r++, fieldX(), 80, String.valueOf(data.defaultEnableChance), Component.literal("0-10000"),
                v -> data.defaultEnableChance = parseInt(v, data.defaultEnableChance));

        addLabel(r, "sre.custom_modifier.label.min_players");
        box(r++, fieldX(), 80, String.valueOf(data.enableNeededPlayerCount),
                Component.translatable("sre.custom_modifier.hint.min_players"),
                v -> data.enableNeededPlayerCount = parseInt(v, data.enableNeededPlayerCount));

        addLabel(r, "sre.custom_modifier.label.max_players");
        box(r++, fieldX(), 80, String.valueOf(data.enableMaxPlayerCount),
                Component.translatable("sre.custom_modifier.hint.max_players"),
                v -> data.enableMaxPlayerCount = parseInt(v, data.enableMaxPlayerCount));

        listBox(r++, "sre.custom_modifier.label.spawn_maps", data.spawnMaps, "sre.custom_modifier.hint.map_list");

        // 互斥修饰符：逗号分隔，与自定义职业的互斥职业写法一致
        listBox(r++, "sre.custom_modifier.label.two_way_opposing_modifiers", data.twoWayOpposingModifiers,
                "sre.custom_modifier.hint.modifier_list");
        listBox(r++, "sre.custom_modifier.label.opposing_modifiers", data.opposingModifiers,
                "sre.custom_modifier.hint.modifier_list");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 3：生成限制
    // ══════════════════════════════════════════════════════════════════
    private void buildRestrictionTab() {
        int r = 0;
        addHintKey(r++, "sre.custom_modifier.hint.team_restriction", 0xFF5EB7D8);
        for (RoleTeam team : RoleTeam.values()) {
            Component label = Component.translatable("sre.custom_modifier.team." + team.name());
            boolean cannot = data.cannotAppliedToTeams.contains(team.name());
            boolean only = data.canOnlyAppliedToTeams.contains(team.name());
            button(r, fieldX(), 110, 18,
                    Component.literal((cannot ? "§c✗ " : "§7· ")).append(label),
                    () -> {
                        toggle(data.cannotAppliedToTeams, team.name());
                        requestRebuild();
                    });
            button(r, fieldX() + 116, 110, 18,
                    Component.literal((only ? "§a✓ " : "§7· ")).append(label),
                    () -> {
                        toggle(data.canOnlyAppliedToTeams, team.name());
                        requestRebuild();
                    });
            r++;
        }
        listBox(r++, "sre.custom_modifier.label.cannot_roles", data.cannotBeAppliedTo,
                "sre.custom_modifier.hint.role_list");
        listBox(r++, "sre.custom_modifier.label.only_roles", data.canOnlyBeAppliedTo,
                "sre.custom_modifier.hint.role_list");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 4：触发条件
    // ══════════════════════════════════════════════════════════════════
    private void buildTriggerTab() {
        int r = 0;
        boolean global = data.conditions.isEmpty();
        addHintKey(r++, global ? "sre.custom_modifier.hint.global_trigger" : "sre.custom_modifier.hint.condition_logic",
                global ? 0xFF72C17B : 0xFFD4AF37);

        for (int i = 0; i < data.conditions.size(); i++) {
            final int index = i;
            ConditionData condition = data.conditions.get(i);
            ConditionType type = conditionType(condition);

            // 类型：点击切换到下一个条件类型
            button(r, fieldX(), 112, 18,
                    Component.translatable("sre.custom_modifier.condition." + type.name()),
                    () -> {
                        ConditionType[] values = ConditionType.values();
                        applyDefaultParams(condition, values[(type.ordinal() + 1) % values.length]);
                        requestRebuild();
                    });

            switch (paramKind(type)) {
                case 1 -> box(r, fieldX() + 116, 64, num(condition.value), valueHint(type),
                        v -> condition.value = parseDouble(v, condition.value));
                case 2 -> box(r, fieldX() + 116, 150, condition.stringValue, stringHint(type),
                        v -> condition.stringValue = v);
                case 3 -> {
                    button(r, fieldX() + 116, 74, 18,
                            Component.translatable("sre.custom_modifier.comparison." + condition.comparison),
                            () -> {
                                condition.comparison = nextComparison(condition.comparison);
                                requestRebuild();
                            });
                    box(r, fieldX() + 196, 64, num(condition.value),
                            Component.translatable("sre.custom_modifier.hint.number"),
                            v -> condition.value = parseDouble(v, condition.value));
                }
                case 4 -> button(r, fieldX() + 116, 90, 18,
                        Component.translatable("sre.custom_modifier.time." + condition.worldTimeType),
                        () -> {
                            condition.worldTimeType = nextTime(condition.worldTimeType);
                            requestRebuild();
                        });
                case 5 -> {
                    box(r, fieldX() + 116, 54, String.valueOf(condition.intervalSeconds),
                            Component.translatable("sre.custom_modifier.hint.interval_seconds"),
                            v -> condition.intervalSeconds = parseInt(v, condition.intervalSeconds));
                    box(r, fieldX() + 176, 64, String.valueOf(condition.chance),
                            Component.translatable("sre.custom_modifier.hint.chance"),
                            v -> condition.chance = parseInt(v, condition.chance));
                }
                // 阵营：点击在「平民 / 警长 / 中立 / 好人方中立 / 杀手方中立 / 特殊中立 / 杀手」之间切换
                case 6 -> button(r, fieldX() + 116, 120, 18,
                        Component.translatable("sre.custom_modifier.team." + teamName(condition.stringValue)),
                        () -> {
                            condition.stringValue = nextTeam(condition.stringValue);
                            requestRebuild();
                        });
                default -> {
                }
            }

            // 与 / 或（与下一个条件的关系）
            boolean or = "OR".equalsIgnoreCase(condition.logic);
            button(r, fieldX() + 286, 40, 18,
                    Component.translatable(or ? "sre.custom_modifier.logic.or" : "sre.custom_modifier.logic.and"),
                    () -> {
                        condition.logic = or ? "AND" : "OR";
                        requestRebuild();
                    });

            // 删除
            removeButton(r, fieldX() + 332, () -> {
                data.conditions.remove(index);
                requestRebuild();
            });
            r++;
        }

        button(r++, fieldX(), 140, 18, Component.translatable("sre.custom_modifier.trigger.add"), () -> {
            ConditionData condition = new ConditionData();
            applyDefaultParams(condition, ConditionType.TIMER);
            data.conditions.add(condition);
            requestRebuild();
        });
    }

    /**
     * 切换条件类型时给出合理默认参数，避免默认值（例如 EQUALS 0）导致条件永远不成立，
     * 同时也把「时间」类条件的单位含义写清楚。
     */
    private static void applyDefaultParams(ConditionData condition, ConditionType type) {
        condition.type = type.name();
        switch (type) {
            case TIMER -> condition.value = 30;
            case TIME_ANCHOR, ELAPSED_TIME -> {
                condition.value = 60;
                condition.comparison = "GREATER_EQUAL";
            }
            case INTERVAL_CHANCE -> {
                condition.intervalSeconds = 10;
                condition.chance = 10000;
            }
            case COIN_AMOUNT -> {
                condition.value = 100;
                condition.comparison = "GREATER_EQUAL";
            }
            case HAS_KILLED -> {
                condition.value = 1;
                condition.comparison = "GREATER_EQUAL";
            }
            case PLAYER_COUNT -> {
                condition.value = 8;
                condition.comparison = "GREATER_EQUAL";
            }
            case ALIVE_PLAYERS -> {
                condition.value = 4;
                condition.comparison = "GREATER_EQUAL";
            }
            case MOOD_VALUE -> {
                condition.value = 50;
                condition.comparison = "GREATER_EQUAL";
            }
            case ARMOR_AMOUNT, TASK_STREAK, PSYCHOS_ACTIVE -> {
                condition.value = 1;
                condition.comparison = "GREATER_EQUAL";
            }
            case WORLD_TIME -> condition.worldTimeType = "NIGHT";
            case HAS_ITEM -> condition.stringValue = "minecraft:iron_ingot";
            case HAS_EFFECT -> condition.stringValue = "minecraft:speed";
            case NEED_TASK_TYPE -> condition.stringValue = "random";
            case DEATH_COUNTDOWN, DEATH_COUNTDOWN_REVIVE -> condition.value = 30;
            case KILLED_BY_TEAM -> condition.stringValue = RoleTeam.values()[0].name();
            case KILLED_BY_ROLE -> condition.stringValue = "noellesroles:raven";
            case KILLED_BY_MODIFIER -> condition.stringValue = "";
            default -> {
            }
        }
    }

    private static Component valueHint(ConditionType type) {
        return switch (type) {
            case TIME_ANCHOR, ELAPSED_TIME -> Component.translatable("sre.custom_modifier.hint.seconds_since_start");
            case TIMER -> Component.translatable("sre.custom_modifier.hint.seconds");
            case DEATH_COUNTDOWN, DEATH_COUNTDOWN_REVIVE -> Component
                    .translatable("sre.custom_modifier.hint.death_seconds");
            default -> Component.translatable("sre.custom_modifier.hint.number");
        };
    }

    /** 字符串参数的占位提示（职业 / 修饰符 id 各有专门提示）。 */
    private static Component stringHint(ConditionType type) {
        return switch (type) {
            case KILLED_BY_ROLE -> Component.translatable("sre.custom_modifier.hint.role_id");
            case KILLED_BY_MODIFIER -> Component.translatable("sre.custom_modifier.hint.modifier_id");
            default -> Component.translatable("sre.custom_modifier.hint.string_value");
        };
    }

    /** 阵营名（解析失败回退第一个阵营）。 */
    private static String teamName(String current) {
        try {
            return RoleTeam.valueOf(current.trim().toUpperCase()).name();
        } catch (Exception e) {
            return RoleTeam.values()[0].name();
        }
    }

    /** 下一个阵营。 */
    private static String nextTeam(String current) {
        RoleTeam[] values = RoleTeam.values();
        String name = teamName(current);
        for (int i = 0; i < values.length; i++) {
            if (values[i].name().equals(name)) {
                return values[(i + 1) % values.length].name();
            }
        }
        return values[0].name();
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 5：触发内容
    // ══════════════════════════════════════════════════════════════════
    private void buildEffectTab() {
        int r = 0;
        boolean global = data.conditions.isEmpty();

        // 执行指令（全局触发时不显示）
        if (!global) {
            addHintKey(r++, "sre.custom_modifier.hint.commands", 0xFFFFF4DC);
            for (int i = 0; i < data.commands.size(); i++) {
                final int index = i;
                box(r, fieldX(), 320, data.commands.get(i), Component.literal("say <player>"),
                        v -> data.commands.set(index, v));
                removeButton(r, fieldX() + 326, () -> {
                    data.commands.remove(index);
                    requestRebuild();
                });
                r++;
            }
            button(r++, fieldX(), 140, 18, Component.translatable("sre.custom_modifier.effect.add_command"),
                    () -> {
                        data.commands.add("");
                        requestRebuild();
                    });
        }

        // 给予药水效果
        addHintKey(r++, global ? "sre.custom_modifier.hint.effects_global" : "sre.custom_modifier.hint.effects_timed",
                0xFFFFF4DC);
        for (int i = 0; i < data.effects.size(); i++) {
            final int index = i;
            EffectData effect = data.effects.get(i);
            box(r, fieldX(), 140, effect.effectId, Component.literal("minecraft:speed"), v -> effect.effectId = v);
            box(r, fieldX() + 146, 44, String.valueOf(effect.amplifier),
                    Component.translatable("sre.custom_modifier.hint.amplifier"),
                    v -> effect.amplifier = parseInt(v, effect.amplifier));
            if (!global) {
                box(r, fieldX() + 196, 50, String.valueOf(effect.durationSeconds),
                        Component.translatable("sre.custom_modifier.hint.duration_seconds"),
                        v -> effect.durationSeconds = parseInt(v, effect.durationSeconds));
            } else {
                // 全局触发没有时长：占一行空白，保证下面的按钮纵向对齐
                addHint(r, Component.empty(), 0xFFFFFF);
            }
            removeButton(r, fieldX() + 252, () -> {
                data.effects.remove(index);
                requestRebuild();
            });
            r++;
        }
        button(r++, fieldX(), 140, 18, Component.translatable("sre.custom_modifier.effect.add_effect"),
                () -> {
                    EffectData effect = new EffectData();
                    effect.effectId = "minecraft:speed";
                    effect.durationSeconds = 10;
                    data.effects.add(effect);
                    requestRebuild();
                });

        // 玩家属性（仅全局触发）
        if (global) {
            addHintKey(r++, "sre.custom_modifier.hint.attributes", 0xFFFFF4DC);
            for (int i = 0; i < data.attributes.size(); i++) {
                final int index = i;
                AttributeData attribute = data.attributes.get(i);
                box(r, fieldX(), 190, attribute.attributeId, Component.literal("minecraft:generic.scale"),
                        v -> attribute.attributeId = v);
                box(r, fieldX() + 196, 70, num(attribute.value),
                        Component.translatable("sre.custom_modifier.hint.number"),
                        v -> attribute.value = parseDouble(v, attribute.value));
                removeButton(r, fieldX() + 272, () -> {
                    data.attributes.remove(index);
                    requestRebuild();
                });
                r++;
            }
            button(r++, fieldX(), 140, 18, Component.translatable("sre.custom_modifier.effect.add_attribute"),
                    () -> {
                        data.attributes.add(new AttributeData());
                        requestRebuild();
                    });
        } else {
            boolButton(r++, "sre.custom_modifier.effect.remove_on_trigger",
                    data.removeModifierOnTrigger, v -> data.removeModifierOnTrigger = v);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 底部按钮
    // ══════════════════════════════════════════════════════════════════
    private void buildBottomButtons() {
        int by = panelTopY + panelHeight - 26, bw = 110, gap = 8;
        int sx = panelLeftX + (panelWidth - (bw * 3 + gap * 2)) / 2;

        var save = Button.builder(Component.translatable("sre.custom_role.save"), b -> save())
                .bounds(sx, by, bw, 20).build();
        var manage = Button.builder(
                Component.translatable("sre.custom_modifier.manage"),
                b -> {
                    CustomModifierConfig config = CustomModifierConfig.getInstance();
                    config.savePreferWorldPath(minecraft.getSingleplayerServer());
                    minecraft.setScreen(new CustomModifierManageScreen(() -> new CustomModifierScreen()));
                }).bounds(sx + bw + gap, by, bw, 20).build();
        var cancel = Button.builder(Component.translatable("sre.custom_role.cancel"), b -> onClose())
                .bounds(sx + (bw + gap) * 2, by, bw, 20).build();

        addRenderableWidget(save);
        addRenderableWidget(manage);
        addRenderableWidget(cancel);
        bottomButtons.add(save);
        bottomButtons.add(manage);
        bottomButtons.add(cancel);
    }

    private void save() {
        if (data.englishId == null || data.englishId.isBlank()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("sre.custom_modifier.error.empty_id"),
                        false);
            }
            return;
        }
        CustomModifierConfig config = CustomModifierConfig.getInstance();
        if (!originalEnglishId.isBlank()) {
            config.removeModifier(originalEnglishId);
        }
        config.removeModifier(data.englishId);
        config.addModifier(data);
        config.savePreferWorldPath(minecraft.getSingleplayerServer());

        var server = minecraft.getSingleplayerServer();
        try {
            config.saveToDefaultPath();
            CustomModifierLoader.reloadClient();
        } catch (Exception ignored) {
        }
        if (server != null) {
            server.execute(() -> {
                try {
                    // 走重载命令的路径：除了重建服务端索引，还会给在线客户端重新握手
                    CustomModifierReloadCommand.reload(server);
                } catch (Exception ignored) {
                }
            });
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("sre.custom_modifier.saved", data.englishId),
                    false);
        }
        onClose();
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 统一在这里重建，避免在按钮回调中修改控件列表；
        // 重建后不要 return —— return 会让这一帧什么都不画，切换标签时会闪一下
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildWidgets();
        }

        renderBackground(g, mouseX, mouseY, partialTick);
        for (AbstractWidget widget : tabBarButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        g.enableScissor(panelLeftX, contentTop(), panelLeftX + panelWidth, contentBottom());
        for (AbstractWidget widget : contentWidgets) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
        List<HintText.Line> lines = new ArrayList<>(contentLabels.size());
        for (LabelEntry label : contentLabels) {
            lines.add(new HintText.Line(label.text(), label.x(), rowY(label.baseY()), label.color(),
                    label.maxWidth(), label.maxLines()));
        }
        HintText.Line hovered = HintText.draw(g, font, lines, mouseX, mouseY);
        g.disableScissor();

        // tooltip 不能被内容区的裁剪切掉，放在关闭裁剪之后
        if (hovered != null) {
            HintText.drawTooltip(g, font, hovered, mouseX, mouseY);
        }

        for (AbstractWidget widget : bottomButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        if (maxScroll > 0) {
            renderScrollbar(g, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, panelWidth + 12, panelHeight + 6);
        g.fill(panelLeftX - 6, contentBottom(), panelLeftX + panelWidth + 6, panelTopY + panelHeight + 3, SREPanelStyle.PANEL_BG_BOTTOM);
        Component title = Component.translatable("sre.custom_modifier.title");
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), panelLeftX - 4, panelTopY - 16, 0xFFD4AF37, false);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelWidth + 1;
        int sbY = contentTop();
        int sbH = contentBottom() - contentTop();
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hover = inside(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        SREPanelStyle.drawScrollbar(g, sbX, sbY, sbH, thumbY, thumbH, hover);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, panelLeftX, contentTop(), panelWidth, contentBottom() - contentTop())
                && maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY) * ROW_H, 0, maxScroll);
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0 && button == 0) {
            int sbX = panelLeftX + panelWidth + 1;
            int sbY = contentTop();
            int sbH = contentBottom() - contentTop();
            if (inside(mouseX, mouseY, sbX - 2, sbY, SCROLL_W + 4, sbH)) {
                isDraggingScroll = true;
                dragScrollStartY = mouseY;
                dragScrollStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScroll && maxScroll > 0) {
            int sbH = contentBottom() - contentTop();
            double ratio = (mouseY - dragScrollStartY) / Math.max(1, sbH - SCROLL_MIN_THUMB);
            scrollOffset = Mth.clamp(dragScrollStartOffset + (int) (ratio * maxScroll), 0, maxScroll);
            applyScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScroll) {
            isDraggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ══════════════════════════════════════════════════════════════════
    // 静态工具
    // ══════════════════════════════════════════════════════════════════
    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static List<String> splitList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static void toggle(List<String> list, String value) {
        if (!list.remove(value)) {
            list.add(value);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String num(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D ? String.valueOf((long) Math.rint(value))
                : String.valueOf(value);
    }

    /** 条件参数形态：0 无；1 数值；2 字符串；3 比较+数值；4 世界时间；5 间隔+概率；6 阵营。 */
    private static int paramKind(ConditionType type) {
        return switch (type) {
            case TIMER, TIME_ANCHOR, ELAPSED_TIME, DEATH_COUNTDOWN, DEATH_COUNTDOWN_REVIVE -> 1;
            case HAS_ITEM, USE_ITEM, SPEAK, HAS_EFFECT, NEED_TASK_TYPE, DEATH, KILLED_BY_ROLE,
                    KILLED_BY_MODIFIER ->
                2;
            case COIN_AMOUNT, HAS_KILLED, PLAYER_COUNT, ALIVE_PLAYERS, MOOD_VALUE, ARMOR_AMOUNT, TASK_STREAK,
                    PSYCHOS_ACTIVE ->
                3;
            case WORLD_TIME -> 4;
            case INTERVAL_CHANCE -> 5;
            case KILLED_BY_TEAM -> 6;
            default -> 0;
        };
    }

    private static String nextComparison(String current) {
        String[] values = { "EQUALS", "GREATER", "LESS", "GREATER_EQUAL", "LESS_EQUAL" };
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static String nextTime(String current) {
        String[] values = { "DAY", "NOON", "SUNSET", "NIGHT", "MIDNIGHT" };
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    /**
     * 页签文字：活跃页签用金色粗体。
     *
     * <p>
     * 按钮已换成原版按钮（没有 accent 装饰条了），页签的活跃状态用文字区分，
     * 符合 {@code docs/ui_style.md} 第 5 节的文字层级（重点金色 / 次要土褐）。
     */
    private Component tabLabel(int index) {
        Component label = Component.translatable("sre.custom_modifier.tab." + TAB_NAMES[index]);
        if (index == activeTab) {
            return label.copy().withStyle(style -> style.withBold(true).withColor(SREPanelStyle.GOLD));
        }
        return label.copy().withStyle(style -> style.withColor(SREPanelStyle.MUTED));
    }
}
