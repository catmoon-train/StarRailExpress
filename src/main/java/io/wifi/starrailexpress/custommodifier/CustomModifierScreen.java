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
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionType;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.EffectData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.AttributeData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义修饰符编辑界面（6 页：基础 / 关联 / 生成 / 生成限制 / 触发条件 / 触发内容）。
 *
 * <p>
 * UI 风格与 {@code CustomRoleScreen} 一致（面板 + 页签 + 自绘标签 + EditBox + ModernButton + 滚动）；
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

    private record LabelEntry(Component text, int x, int baseY, int color) {
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
        int rawH = Math.min((int) (height * USABLE_RATIO), MAX_PANEL_HEIGHT);
        panelHeight = Math.max(rawH, MIN_PANEL_HEIGHT);
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
            maxY = Math.max(maxY, label.baseY() + 10);
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
        int tw = 74, th = 20, tg = 4;
        int total = tw * TAB_NAMES.length + tg * (TAB_NAMES.length - 1);
        int sx = panelLeftX + (panelWidth - total) / 2;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            var builder = ModernButton.builder(
                    Component.translatableWithFallback("sre.custom_modifier.tab." + TAB_NAMES[i],
                            tabFallback(TAB_NAMES[i])),
                    button -> {
                        activeTab = index;
                        scrollOffset = 0;
                        requestRebuild();
                    }).bounds(sx + i * (tw + tg), panelTopY + 8, tw, th);
            if (activeTab == i) {
                builder.accentBar(AccentSide.BOTTOM);
            } else {
                builder.accentBar();
            }
            var built = builder.build();
            addRenderableWidget(built);
            tabBarButtons.add(built);
        }
    }

    private static String tabFallback(String name) {
        return switch (name) {
            case "basic" -> "基础";
            case "relations" -> "关联";
            case "generation" -> "生成";
            case "restriction" -> "生成限制";
            case "trigger" -> "触发条件";
            default -> "触发内容";
        };
    }

    // ══════════════════════════════════════════════════════════════════
    // 控件工具
    // ══════════════════════════════════════════════════════════════════
    private void addLabel(int row, String key, String fallback) {
        contentLabels.add(new LabelEntry(
                Component.translatableWithFallback(key, fallback).withStyle(s -> s.withColor(0xCCDDEE)),
                labelX(), baseY(row), 0xFFFFFF));
    }

    private void addHint(int row, String fallback, int color) {
        contentLabels.add(new LabelEntry(Component.literal(fallback).withStyle(s -> s.withColor(color)),
                labelX(), baseY(row), color));
    }

    private <T extends AbstractWidget> T track(T widget, int baseYValue) {
        widgetBaseY.put(widget, baseYValue);
        contentWidgets.add(widget);
        return widget;
    }

    private EditBox box(int row, int x, int w, String value, String hint, java.util.function.Consumer<String> setter) {
        EditBox box = new EditBox(font, x, rowY(baseY(row)), w, 18, Component.empty());
        box.setValue(value == null ? "" : value);
        box.setMaxLength(256);
        box.setResponder(setter);
        if (hint != null && !hint.isEmpty()) {
            box.setHint(Component.literal(hint));
        }
        return track(box, baseY(row));
    }

    /** 左侧标签 + 右侧输入框。 */
    private EditBox labeledBox(int row, String key, String fallback, String value, String hint,
            java.util.function.Consumer<String> setter) {
        addLabel(row, key, fallback);
        return box(row, fieldX(), 220, value, hint, setter);
    }

    /** 逗号分隔的字符串列表输入框。 */
    private void listBox(int row, String key, String fallback, List<String> list, String hint) {
        addLabel(row, key, fallback);
        box(row, fieldX(), 260, String.join(",", safeList(list)), hint, value -> {
            list.clear();
            list.addAll(splitList(value));
        });
    }

    private AbstractWidget button(int row, int x, int w, int h, Component text, Runnable onClick, AccentSide accent) {
        var builder = ModernButton.builder(text, b -> onClick.run()).bounds(x, rowY(baseY(row)), w, h);
        if (accent == null) {
            builder.accentBar();
        } else {
            builder.accentBar(accent);
        }
        return track(builder.build(), baseY(row));
    }

    private void boolButton(int row, String key, String fallback, boolean current,
            java.util.function.Consumer<Boolean> setter) {
        Component state = current
                ? Component.literal(" [✓]").withStyle(s -> s.withColor(0x55FF55))
                : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFF5555));
        button(row, fieldX(), 260, 18,
                Component.translatableWithFallback(key, fallback).copy().append(state),
                () -> {
                    setter.accept(!current);
                    requestRebuild();
                },
                current ? AccentSide.LEFT : AccentSide.RIGHT);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础
    // ══════════════════════════════════════════════════════════════════
    private void buildBasicTab() {
        int r = 0;
        box(r, fieldX(), 260, data.englishId, "my_modifier", v -> data.englishId = v.toLowerCase()).setMaxLength(64);
        addLabel(r, "sre.custom_modifier.label.english_id", "英文ID");
        r++;

        labeledBox(r++, "sre.custom_modifier.label.display_name", "中文名称", data.displayName, "修饰符显示的名字",
                v -> data.displayName = v);
        labeledBox(r++, "sre.custom_modifier.label.description", "描述", data.description, "修饰符描述",
                v -> data.description = v);

        addLabel(r, "sre.custom_modifier.label.color", "颜色(RGB)");
        box(r, fieldX(), 60, String.valueOf(data.colorR), "R", v -> data.colorR = parseInt(v, data.colorR));
        box(r, fieldX() + 66, 60, String.valueOf(data.colorG), "G", v -> data.colorG = parseInt(v, data.colorG));
        box(r, fieldX() + 132, 60, String.valueOf(data.colorB), "B", v -> data.colorB = parseInt(v, data.colorB));
        r++;

        boolButton(r++, "sre.custom_modifier.label.hidden", "隐藏修饰符", data.hidden, v -> data.hidden = v);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：关联（仅作用于介绍页面）
    // ══════════════════════════════════════════════════════════════════
    private void buildRelationsTab() {
        int r = 0;
        listBox(r++, "sre.custom_modifier.label.both_related_roles", "双向关联职业", data.bothRelatedRoles,
                "roleId1,roleId2");
        listBox(r++, "sre.custom_modifier.label.related_roles", "单向关联职业", data.relatedRoles, "roleId1,roleId2");
        listBox(r++, "sre.custom_modifier.label.remove_related_roles", "单向移除关联职业", data.removeRelatedRoles,
                "roleId1,roleId2");
        listBox(r++, "sre.custom_modifier.label.both_related_modifiers", "双向关联修饰符", data.bothRelatedModifiers,
                "modifierId1,modifierId2");
        listBox(r++, "sre.custom_modifier.label.related_modifiers", "单向关联修饰符", data.relatedModifiers,
                "modifierId1,modifierId2");
        listBox(r++, "sre.custom_modifier.label.remove_related_modifiers", "单向移除关联修饰符",
                data.removeRelatedModifiers, "modifierId1,modifierId2");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 2：生成
    // ══════════════════════════════════════════════════════════════════
    private void buildGenerationTab() {
        int r = 0;
        addLabel(r, "sre.custom_modifier.label.default_max", "局内最大刷新数量");
        box(r++, fieldX(), 80, String.valueOf(data.defaultMax), "1",
                v -> data.defaultMax = parseInt(v, data.defaultMax));

        addLabel(r, "sre.custom_modifier.label.enable_chance", "生成概率(万分比)");
        box(r++, fieldX(), 80, String.valueOf(data.defaultEnableChance), "0-10000",
                v -> data.defaultEnableChance = parseInt(v, data.defaultEnableChance));

        addLabel(r, "sre.custom_modifier.label.min_players", "最少玩家启用数");
        box(r++, fieldX(), 80, String.valueOf(data.enableNeededPlayerCount), "-1=无门槛",
                v -> data.enableNeededPlayerCount = parseInt(v, data.enableNeededPlayerCount));

        addLabel(r, "sre.custom_modifier.label.max_players", "最多玩家启用数");
        box(r++, fieldX(), 80, String.valueOf(data.enableMaxPlayerCount), "-1=无上限",
                v -> data.enableMaxPlayerCount = parseInt(v, data.enableMaxPlayerCount));

        listBox(r++, "sre.custom_modifier.label.spawn_maps", "限定地图", data.spawnMaps, "mapId1,mapId2");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 3：生成限制
    // ══════════════════════════════════════════════════════════════════
    private void buildRestrictionTab() {
        int r = 0;
        addHint(r++, "阵营限制（左=不给该阵营刷新，右=仅给该阵营刷新）", 0x88AACC);
        for (RoleTeam team : RoleTeam.values()) {
            Component label = Component.translatableWithFallback("sre.custom_modifier.team." + team.name(),
                    teamFallback(team));
            boolean cannot = data.cannotAppliedToTeams.contains(team.name());
            boolean only = data.canOnlyAppliedToTeams.contains(team.name());
            button(r, fieldX(), 110, 18,
                    Component.literal((cannot ? "§c✗ " : "§7· ")).append(label),
                    () -> {
                        toggle(data.cannotAppliedToTeams, team.name());
                        requestRebuild();
                    },
                    cannot ? AccentSide.LEFT : null);
            button(r, fieldX() + 116, 110, 18,
                    Component.literal((only ? "§a✓ " : "§7· ")).append(label),
                    () -> {
                        toggle(data.canOnlyAppliedToTeams, team.name());
                        requestRebuild();
                    },
                    only ? AccentSide.LEFT : null);
            r++;
        }
        listBox(r++, "sre.custom_modifier.label.cannot_roles", "不作用于特定职业", data.cannotBeAppliedTo,
                "roleId1,roleId2");
        listBox(r++, "sre.custom_modifier.label.only_roles", "仅作用于特定职业", data.canOnlyBeAppliedTo,
                "roleId1,roleId2");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 4：触发条件
    // ══════════════════════════════════════════════════════════════════
    private void buildTriggerTab() {
        int r = 0;
        boolean global = data.conditions.isEmpty();
        addHint(r++, global ? "当前为【全局触发】：拥有该修饰符即持续生效（不加条件）" : "条件从左到右按「与 / 或」串联；满足时触发",
                global ? 0x88DD88 : 0xFFCC88);

        for (int i = 0; i < data.conditions.size(); i++) {
            final int index = i;
            ConditionData condition = data.conditions.get(i);
            ConditionType type = conditionType(condition);

            // 类型：点击切换到下一个条件类型
            button(r, fieldX(), 112, 18,
                    Component.translatableWithFallback("sre.custom_modifier.condition." + type.name(),
                            conditionFallback(type)),
                    () -> {
                        ConditionType[] values = ConditionType.values();
                        applyDefaultParams(condition, values[(type.ordinal() + 1) % values.length]);
                        requestRebuild();
                    },
                    AccentSide.LEFT);

            switch (paramKind(type)) {
                case 1 -> box(r, fieldX() + 116, 64, num(condition.value), valueHint(type),
                        v -> condition.value = parseDouble(v, condition.value));
                case 2 -> box(r, fieldX() + 116, 150, condition.stringValue, "id / 文本",
                        v -> condition.stringValue = v);
                case 3 -> {
                    button(r, fieldX() + 116, 74, 18,
                            Component.translatableWithFallback(
                                    "sre.custom_modifier.comparison." + condition.comparison,
                                    comparisonFallback(condition.comparison)),
                            () -> {
                                condition.comparison = nextComparison(condition.comparison);
                                requestRebuild();
                            },
                            null);
                    box(r, fieldX() + 196, 64, num(condition.value), "数值",
                            v -> condition.value = parseDouble(v, condition.value));
                }
                case 4 -> button(r, fieldX() + 116, 90, 18,
                        Component.translatableWithFallback("sre.custom_modifier.time." + condition.worldTimeType,
                                timeFallback(condition.worldTimeType)),
                        () -> {
                            condition.worldTimeType = nextTime(condition.worldTimeType);
                            requestRebuild();
                        },
                        null);
                case 5 -> {
                    box(r, fieldX() + 116, 54, String.valueOf(condition.intervalSeconds), "间隔秒",
                            v -> condition.intervalSeconds = parseInt(v, condition.intervalSeconds));
                    box(r, fieldX() + 176, 64, String.valueOf(condition.chance), "概率万分比",
                            v -> condition.chance = parseInt(v, condition.chance));
                }
                default -> {
                }
            }

            // 与 / 或（与下一个条件的关系）
            boolean or = "OR".equalsIgnoreCase(condition.logic);
            button(r, fieldX() + 286, 40, 18,
                    Component.translatableWithFallback(or ? "sre.custom_modifier.logic.or" : "sre.custom_modifier.logic.and",
                            or ? "或" : "与"),
                    () -> {
                        condition.logic = or ? "AND" : "OR";
                        requestRebuild();
                    },
                    null);

            // 删除
            button(r, fieldX() + 332, 18, 18, Component.literal("§c×"), () -> {
                data.conditions.remove(index);
                requestRebuild();
            }, AccentSide.RIGHT);
            r++;
        }

        button(r++, fieldX(), 140, 18,
                Component.translatableWithFallback("sre.custom_modifier.trigger.add", "＋ 添加条件"), () -> {
                    ConditionData condition = new ConditionData();
                    applyDefaultParams(condition, ConditionType.TIMER);
                    data.conditions.add(condition);
                    requestRebuild();
                }, AccentSide.BOTTOM);
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
            default -> {
            }
        }
    }

    private static String valueHint(ConditionType type) {
        return switch (type) {
            case TIME_ANCHOR, ELAPSED_TIME -> "秒(自游戏开始)";
            case TIMER -> "秒";
            default -> "数值";
        };
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 5：触发内容
    // ══════════════════════════════════════════════════════════════════
    private void buildEffectTab() {
        int r = 0;
        boolean global = data.conditions.isEmpty();

        // 执行指令（全局触发时不显示）
        if (!global) {
            addHint(r++, "执行指令（<player> = 拥有该修饰符的玩家，位置随该玩家）", 0xCCDDEE);
            for (int i = 0; i < data.commands.size(); i++) {
                final int index = i;
                box(r, fieldX(), 320, data.commands.get(i), "say <player>",
                        v -> data.commands.set(index, v));
                button(r, fieldX() + 326, 18, 18, Component.literal("§c×"), () -> {
                    data.commands.remove(index);
                    requestRebuild();
                }, AccentSide.RIGHT);
                r++;
            }
            button(r++, fieldX(), 140, 18,
                    Component.translatableWithFallback("sre.custom_modifier.effect.add_command", "＋ 添加指令"),
                    () -> {
                        data.commands.add("");
                        requestRebuild();
                    }, AccentSide.BOTTOM);
        }

        // 给予药水效果
        addHint(r++, global ? "给予药水效果（全局：持续获得，直到失去该修饰符）"
                : "给予药水效果（条件触发时给予指定时长）", 0xCCDDEE);
        for (int i = 0; i < data.effects.size(); i++) {
            final int index = i;
            EffectData effect = data.effects.get(i);
            box(r, fieldX(), 140, effect.effectId, "minecraft:speed", v -> effect.effectId = v);
            box(r, fieldX() + 146, 44, String.valueOf(effect.amplifier), "等级",
                    v -> effect.amplifier = parseInt(v, effect.amplifier));
            if (!global) {
                box(r, fieldX() + 196, 50, String.valueOf(effect.durationSeconds), "时长秒",
                        v -> effect.durationSeconds = parseInt(v, effect.durationSeconds));
            } else {
                addHint(r, "", 0xFFFFFF);
            }
            button(r, fieldX() + 252, 18, 18, Component.literal("§c×"), () -> {
                data.effects.remove(index);
                requestRebuild();
            }, AccentSide.RIGHT);
            r++;
        }
        button(r++, fieldX(), 140, 18,
                Component.translatableWithFallback("sre.custom_modifier.effect.add_effect", "＋ 添加药水效果"),
                () -> {
                    EffectData effect = new EffectData();
                    effect.effectId = "minecraft:speed";
                    effect.durationSeconds = 10;
                    data.effects.add(effect);
                    requestRebuild();
                }, AccentSide.BOTTOM);

        // 玩家属性（仅全局触发）
        if (global) {
            addHint(r++, "玩家属性（仅全局触发可用；失去修饰符后自动重置）", 0xCCDDEE);
            for (int i = 0; i < data.attributes.size(); i++) {
                final int index = i;
                AttributeData attribute = data.attributes.get(i);
                box(r, fieldX(), 190, attribute.attributeId, "minecraft:generic.scale",
                        v -> attribute.attributeId = v);
                box(r, fieldX() + 196, 70, num(attribute.value), "数值",
                        v -> attribute.value = parseDouble(v, attribute.value));
                button(r, fieldX() + 272, 18, 18, Component.literal("§c×"), () -> {
                    data.attributes.remove(index);
                    requestRebuild();
                }, AccentSide.RIGHT);
                r++;
            }
            button(r++, fieldX(), 140, 18,
                    Component.translatableWithFallback("sre.custom_modifier.effect.add_attribute", "＋ 添加属性"),
                    () -> {
                        data.attributes.add(new AttributeData());
                        requestRebuild();
                    }, AccentSide.BOTTOM);
        } else {
            boolButton(r++, "sre.custom_modifier.effect.remove_on_trigger", "条件触发后移除该修饰符",
                    data.removeModifierOnTrigger, v -> data.removeModifierOnTrigger = v);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 底部按钮
    // ══════════════════════════════════════════════════════════════════
    private void buildBottomButtons() {
        int by = panelTopY + panelHeight - 26, bw = 110, gap = 8;
        int sx = panelLeftX + (panelWidth - (bw * 3 + gap * 2)) / 2;

        var save = ModernButton.builder(Component.translatable("sre.custom_role.save"), b -> save())
                .bounds(sx, by, bw, 20).accentBar(AccentSide.BOTTOM).build();
        var manage = ModernButton.builder(
                Component.translatableWithFallback("sre.custom_modifier.manage", "§6管理修饰符"),
                b -> {
                    CustomModifierConfig config = CustomModifierConfig.getInstance();
                    config.savePreferWorldPath(minecraft.getSingleplayerServer());
                    minecraft.setScreen(new CustomModifierManageScreen(() -> new CustomModifierScreen()));
                }).bounds(sx + bw + gap, by, bw, 20).accentBar(AccentSide.BOTTOM).build();
        var cancel = ModernButton.builder(Component.translatable("sre.custom_role.cancel"), b -> onClose())
                .bounds(sx + (bw + gap) * 2, by, bw, 20).accentBar(AccentSide.BOTTOM).build();

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
                        Component.translatableWithFallback("sre.custom_modifier.error.empty_id", "§c请填写英文ID"),
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
                    CustomModifierLoader.reload(server);
                } catch (Exception ignored) {
                }
            });
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatableWithFallback("sre.custom_modifier.saved", "§a已保存自定义修饰符: %s",
                            data.englishId),
                    false);
        }
        onClose();
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 统一在这里重建，避免在按钮回调中修改控件列表
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildWidgets();
            return;
        }

        renderBackground(g, mouseX, mouseY, partialTick);
        for (AbstractWidget widget : tabBarButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        g.enableScissor(panelLeftX, contentTop(), panelLeftX + panelWidth, contentBottom());
        for (AbstractWidget widget : contentWidgets) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
        for (LabelEntry label : contentLabels) {
            String text = label.text().getString();
            g.drawString(font, font.plainSubstrByWidth(text, LABEL_W - 10), label.x(), rowY(label.baseY()),
                    label.color(), false);
        }
        g.disableScissor();

        for (AbstractWidget widget : bottomButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        if (maxScroll > 0) {
            renderScrollbar(g, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + panelWidth + 6, panelTopY + panelHeight + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + panelWidth + 6, panelTopY - 2, 0xFF5577CC);
        g.fill(panelLeftX - 6, contentBottom(), panelLeftX + panelWidth + 6, panelTopY + panelHeight + 3, 0xCC080C18);
        Component title = Component.translatableWithFallback("sre.custom_modifier.title", "自定义修饰符");
        g.drawString(font, title, panelLeftX - 4, panelTopY - 16, 0x55BBFF, false);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelWidth + 1;
        int sbY = contentTop();
        int sbH = contentBottom() - contentTop();
        g.fill(sbX, sbY, sbX + SCROLL_W, sbY + sbH, 0xFF111828);
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hover = inside(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        g.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH, hover ? 0xFF8899CC : 0xFF556699);
        g.fill(sbX + 1, thumbY + 1, sbX + SCROLL_W - 1, thumbY + thumbH - 1, hover ? 0xFFAABBEE : 0xFF7788BB);
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

    /** 条件参数形态：0 无；1 数值；2 字符串；3 比较+数值；4 世界时间；5 间隔+概率。 */
    private static int paramKind(ConditionType type) {
        return switch (type) {
            case TIMER, TIME_ANCHOR, ELAPSED_TIME -> 1;
            case HAS_ITEM, USE_ITEM, SPEAK, HAS_EFFECT, NEED_TASK_TYPE, DEATH -> 2;
            case COIN_AMOUNT, HAS_KILLED, PLAYER_COUNT, ALIVE_PLAYERS, MOOD_VALUE, ARMOR_AMOUNT, TASK_STREAK,
                    PSYCHOS_ACTIVE ->
                3;
            case WORLD_TIME -> 4;
            case INTERVAL_CHANCE -> 5;
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

    private static String comparisonFallback(String comparison) {
        return switch (comparison == null ? "" : comparison.toUpperCase()) {
            case "GREATER" -> "大于";
            case "LESS" -> "小于";
            case "GREATER_EQUAL" -> "大于等于";
            case "LESS_EQUAL" -> "小于等于";
            default -> "等于";
        };
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

    private static String timeFallback(String time) {
        return switch (time == null ? "" : time.toUpperCase()) {
            case "NOON" -> "中午";
            case "SUNSET" -> "傍晚";
            case "NIGHT" -> "夜晚";
            case "MIDNIGHT" -> "午夜";
            default -> "白天";
        };
    }

    private static String teamFallback(RoleTeam team) {
        return switch (team) {
            case CIVILIAN -> "平民";
            case SHERIFF -> "警长";
            case NEUTRAL -> "中立";
            case NEUTRAL_INNOCENT -> "好人方中立";
            case NEUTRAL_KILLER -> "杀手方中立";
            case NEUTRAL_SPECIAL -> "特殊中立";
            case KILLER -> "杀手";
        };
    }

    static String conditionFallback(ConditionType type) {
        return switch (type) {
            case TIMER -> "自动定时";
            case TIME_ANCHOR -> "时间锚点";
            case HAS_ITEM -> "有特定物品";
            case DEATH -> "死亡";
            case USE_ITEM -> "使用物品";
            case SPEAK -> "说话";
            case COIN_AMOUNT -> "金币数量";
            case HAS_KILLED -> "击杀过玩家";
            case PLAYER_COUNT -> "玩家数量";
            case ALIVE_PLAYERS -> "存活玩家数量";
            case IS_SNEAKING -> "潜行状态";
            case IS_SPRINTING -> "疾跑状态";
            case HAS_EFFECT -> "有特定效果";
            case INTERVAL_CHANCE -> "定时概率";
            case WORLD_TIME -> "世界时间";
            case MOOD_VALUE -> "心情值";
            case IS_PSYCHO -> "疯狂模式";
            case IS_POISONED -> "中毒";
            case IS_INFECTED -> "感染";
            case ARMOR_AMOUNT -> "护盾值";
            case HAS_TASK -> "是否有任务";
            case TASK_STREAK -> "连续完成任务数";
            case PSYCHOS_ACTIVE -> "活跃疯狂玩家数量";
            case IS_BLACKOUT -> "关灯状态";
            case IS_MONITOR_BROKEN -> "监控失灵";
            case NEED_TASK_TYPE -> "完成特定类型任务";
            case PLAYER_DAMAGED_BY_PLAYER -> "受到玩家伤害";
            case PLAYER_DAMAGED_BY_NON_PLAYER -> "受到非玩家伤害";
            case ELAPSED_TIME -> "游戏经过时间";
            case FAKE_POISONED -> "触发过假毒";
            case HAS_WEAK_ARMOR -> "拥有弱效护盾";
        };
    }
}
