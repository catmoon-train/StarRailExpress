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
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.CustomEditorScreen;
import io.wifi.starrailexpress.client.gui.widget.SwitchState;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.AttributeData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionType;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.EffectData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 自定义修饰符编辑界面（6 页：基础 / 关联 / 生成 / 生成限制 / 触发条件 / 触发内容）。
 *
 * <p>
 * 布局、页签、滚动、渲染与鼠标键盘全部交给 {@link CustomEditorScreen}。
 * 「触发条件」「触发内容」这两页原来靠 {@code fieldX() + 332} 这类写死偏移横向拼控件，
 * 面板一窄（GUI scale 大）整排就会戳出面板、露出面板外还点不到；现在改成 {@link #cluster}，
 * 装不下自动折行。
 *
 * <p>
 * 重建统一走 {@link #requestRebuild()}，在 render 里执行；按钮一律就地刷新文案，不重建界面。
 */
@Environment(EnvType.CLIENT)
public class CustomModifierScreen extends CustomEditorScreen {

    private static final String PREFIX = "sre.custom_modifier";
    /** 页签：触发与内容合并成一页（每组 = 一份条件 + 一份内容）。 */
    private static final String[] TABS = { "basic", "relations", "generation", "restriction", "trigger_content" };
    private static final String REMOVE = PREFIX + ".remove";

    private CustomModifierData data = new CustomModifierData();
    private String originalEnglishId = "";

    public CustomModifierScreen() {
        super(Component.translatable(PREFIX + ".title"));
    }

    public CustomModifierScreen(CustomModifierData source) {
        super(Component.translatable(PREFIX + ".title"));
        if (source != null) {
            this.data = source;
            this.originalEnglishId = source.englishId == null ? "" : source.englishId;
        }
    }

    private static ConditionType conditionType(ConditionData condition) {
        try {
            return ConditionType.valueOf(condition.type);
        } catch (Exception e) {
            return ConditionType.TIMER;
        }
    }

    @Override
    protected String translationPrefix() {
        return PREFIX;
    }

    @Override
    protected String[] tabKeys() {
        return TABS;
    }

    /** 「仅标记使用」时隐藏「触发与内容」页签。 */
    @Override
    protected List<Integer> visibleTabs() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < TABS.length; i++) {
            if (data.markerOnly && i == 4) {
                continue;
            }
            result.add(i);
        }
        return result;
    }

    @Override
    protected void buildTab(int tab) {
        switch (tab) {
            case 0 -> buildBasicTab();
            case 1 -> buildRelationsTab();
            case 2 -> buildGenerationTab();
            case 3 -> buildRestrictionTab();
            case 4 -> buildTriggerContentTab();
            default -> {
            }
        }
    }

    @Override
    protected void onSave() {
        if (data.englishId == null || data.englishId.isBlank()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable(PREFIX + ".error.empty_id"), false);
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

        try {
            config.saveToDefaultPath();
            CustomModifierLoader.reloadClient();
        } catch (Exception ignored) {
        }
        var server = minecraft.getSingleplayerServer();
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
                    Component.translatable(PREFIX + ".saved", data.englishId), false);
        }
        onClose();
    }

    @Override
    protected void onOpenManage() {
        CustomModifierConfig config = CustomModifierConfig.getInstance();
        config.savePreferWorldPath(minecraft.getSingleplayerServer());
        minecraft.setScreen(new CustomModifierManageScreen(() -> new CustomModifierScreen()));
    }

    // ══════════════════════════════════════════════════════════════════
    // 行包装
    // ══════════════════════════════════════════════════════════════════

    /** 「标签 + 文本」输入行。 */
    private int textRow(int r, String labelKey, String value, int limit, String hintKey,
            Consumer<String> setter) {
        return field(r, labelKey, value, limit, hint(hintKey), setter);
    }

    /** 「标签 + 定宽数值」输入行。 */
    private int numberRow(int r, String labelKey, int value, int width, String hintKey,
            Consumer<Integer> setter) {
        return cluster(r, labelKey, fixedBox(String.valueOf(value), LIMIT_NUMBER, width, hint(hintKey),
                text -> setter.accept(parseInt(text, value))));
    }

    /** 「标签 + 逗号分隔列表」输入行。 */
    private int listRow(int r, String labelKey, List<String> list, String hintKey) {
        return cluster(r, labelKey, box(String.join(", ", list), LIMIT_TEXT, 120, hint(hintKey),
                value -> replaceList(list, value)));
    }

    /** 输入框占位提示（null / 空 = 不设提示）。 */
    private static Component hint(String key) {
        return key == null || key.isEmpty() ? null : Component.translatable(key);
    }

    private int addRow(int r, String key, Runnable onClick) {
        return cluster(r, null, fixedButton(Component.translatable(key), 160, onClick));
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础
    // ══════════════════════════════════════════════════════════════════
    private void buildBasicTab() {
        int r = 0;
        r = field(r, PREFIX + ".label.english_id", data.englishId, 64, Component.literal("my_modifier"),
                value -> data.englishId = value.toLowerCase());
        r = textRow(r, PREFIX + ".label.display_name", data.displayName, LIMIT_NAME,
                PREFIX + ".hint.display_name", value -> data.displayName = value);
        r = textRow(r, PREFIX + ".label.description", data.description, LIMIT_TEXT,
                PREFIX + ".hint.description", value -> data.description = value);

        // 自定义标签：挂成运行时修饰符的 flags，介绍页就能按它筛选
        r = lines(r, PREFIX + ".label.tags", data.tags, LIMIT_ID, PREFIX + ".hint.tags", null,
                PREFIX + ".add_tag", REMOVE, Integer.MAX_VALUE);

        r = cluster(r, PREFIX + ".label.color",
                fixedBox(String.valueOf(data.colorR), LIMIT_NUMBER, 60, Component.literal("R"),
                        value -> data.colorR = parseInt(value, data.colorR)),
                fixedBox(String.valueOf(data.colorG), LIMIT_NUMBER, 60, Component.literal("G"),
                        value -> data.colorG = parseInt(value, data.colorG)),
                fixedBox(String.valueOf(data.colorB), LIMIT_NUMBER, 60, Component.literal("B"),
                        value -> data.colorB = parseInt(value, data.colorB)));

        r = toggle(r, PREFIX + ".label.hidden", data.hidden, value -> data.hidden = value);
        // 「仅标记使用」会把后面两个页签藏起来，属于结构性变化，必须重建
        r = toggle(r, PREFIX + ".label.marker_only", data.markerOnly, value -> data.markerOnly = value, true);
        r = note(r, PREFIX + ".hint.marker_only", SREPanelStyle.MUTED);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：关联（仅作用于介绍页面）
    // ══════════════════════════════════════════════════════════════════
    private void buildRelationsTab() {
        int r = 0;
        r = listRow(r, PREFIX + ".label.both_related_roles", data.bothRelatedRoles,
                PREFIX + ".hint.role_list");
        r = listRow(r, PREFIX + ".label.related_roles", data.relatedRoles, PREFIX + ".hint.role_list");
        r = listRow(r, PREFIX + ".label.remove_related_roles", data.removeRelatedRoles,
                PREFIX + ".hint.role_list");
        r = listRow(r, PREFIX + ".label.both_related_modifiers", data.bothRelatedModifiers,
                PREFIX + ".hint.modifier_list");
        r = listRow(r, PREFIX + ".label.related_modifiers", data.relatedModifiers,
                PREFIX + ".hint.modifier_list");
        r = listRow(r, PREFIX + ".label.remove_related_modifiers", data.removeRelatedModifiers,
                PREFIX + ".hint.modifier_list");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 2：生成
    // ══════════════════════════════════════════════════════════════════
    private void buildGenerationTab() {
        int r = 0;
        r = numberRow(r, PREFIX + ".label.default_max", data.defaultMax, 80, null,
                value -> data.defaultMax = value);
        r = numberRow(r, PREFIX + ".label.enable_chance", data.defaultEnableChance, 80, "0-10000",
                value -> data.defaultEnableChance = value);
        r = numberRow(r, PREFIX + ".label.min_players", data.enableNeededPlayerCount, 80,
                PREFIX + ".hint.min_players", value -> data.enableNeededPlayerCount = value);
        r = numberRow(r, PREFIX + ".label.max_players", data.enableMaxPlayerCount, 80,
                PREFIX + ".hint.max_players", value -> data.enableMaxPlayerCount = value);

        r = listRow(r, PREFIX + ".label.spawn_maps", data.spawnMaps, PREFIX + ".hint.map_list");

        // 互斥修饰符：逗号分隔，与自定义职业的互斥职业写法一致
        r = listRow(r, PREFIX + ".label.two_way_opposing_modifiers", data.twoWayOpposingModifiers,
                PREFIX + ".hint.modifier_list");
        r = listRow(r, PREFIX + ".label.opposing_modifiers", data.opposingModifiers,
                PREFIX + ".hint.modifier_list");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 3：生成限制
    // ══════════════════════════════════════════════════════════════════
    private void buildRestrictionTab() {
        int r = 0;
        r = note(r, PREFIX + ".hint.team_restriction", SREPanelStyle.BLUE);
        // 一个阵营一个三态按钮（不限 → 仅给 → 不给）：标题只出现一次、状态写成文字，
        // 而且两个列表天然互斥，不会出现「同时只给又不给」的矛盾配置。
        // 这种行自带标题，所以相邻的会自动并排成两个一行。
        for (RoleTeam team : RoleTeam.values()) {
            r = cluster(r, null, teamCell(team));
        }
        r = listRow(r, PREFIX + ".label.cannot_roles", data.cannotBeAppliedTo, PREFIX + ".hint.role_list");
        r = listRow(r, PREFIX + ".label.only_roles", data.canOnlyBeAppliedTo, PREFIX + ".hint.role_list");
    }

    /** 一个阵营的「不限 / 仅给该阵营刷新 / 不给该阵营刷新」三态按钮。 */
    private Cell teamCell(RoleTeam team) {
        String name = team.name();
        SwitchState current = data.canOnlyAppliedToTeams.contains(name) ? SwitchState.ON
                : (data.cannotAppliedToTeams.contains(name) ? SwitchState.OFF : SwitchState.UNSET);
        return triStateCell(Component.translatable(PREFIX + ".team." + name), current,
                state -> Component.translatable(PREFIX + ".team_mode."
                        + switch (state) {
                            case ON -> "only";
                            case OFF -> "deny";
                            case UNSET -> "unset";
                        }),
                state -> {
                    // 先清掉这一阵营在两边列表里的旧记录，再按新状态写回：两边互斥
                    data.canOnlyAppliedToTeams.remove(name);
                    data.cannotAppliedToTeams.remove(name);
                    switch (state) {
                        case ON -> data.canOnlyAppliedToTeams.add(name);
                        case OFF -> data.cannotAppliedToTeams.add(name);
                        case UNSET -> {
                            // 不限：两边都不放，什么都不用做
                        }
                    }
                }, false);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 4：触发与内容（每组 = 一份条件 + 一份内容）
    // ══════════════════════════════════════════════════════════════════
    private void buildTriggerContentTab() {
        int r = 0;
        r = note(r, PREFIX + ".hint.groups_intro", SREPanelStyle.MUTED);
        List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();
        if (groups.isEmpty()) {
            r = note(r, PREFIX + ".hint.groups_empty", SREPanelStyle.GOLD_DIM);
        }
        for (int i = 0; i < groups.size(); i++) {
            r = groupBlock(r, groups.get(i), i);
        }
        r = addRow(r, PREFIX + ".group.add", () -> {
            // 新组默认是「条件组」并带一条默认条件：否则空条件会被当成全局组，用户永远加不上条件
            CustomModifierData.TriggerGroupData group = new CustomModifierData.TriggerGroupData();
            group.setGlobal(false);
            group.conditions.add(defaultCondition());
            data.effectiveGroups().add(group);
            requestRebuild();
        });
    }

    /** 一条默认条件（定时：每 30 秒一次），给「＋ 添加条件」和新建触发组用。 */
    private static ConditionData defaultCondition() {
        ConditionData condition = new ConditionData();
        applyDefaultParams(condition, ConditionType.TIMER);
        return condition;
    }

    /** 一个触发组：组头 + 条件 + 指令 / 效果 / 属性（属性只在全局组里常驻）。 */
    private int groupBlock(int r, CustomModifierData.TriggerGroupData group, int index) {
        boolean global = group.isGlobal();

        // 一个触发组一张卡片：标题「第 N 组」+（全局组 / N 个条件）徽标，卡片头右侧删除
        r = cardBegin(r, "modifier_group_" + index,
                Component.translatable(PREFIX + ".group.title", index + 1),
                global ? Component.translatable(PREFIX + ".group.global")
                        : Component.translatable(PREFIX + ".group.conditional", group.conditions.size()),
                () -> data.effectiveGroups().remove(index));

        // 组类型：全局常驻（无条件、拥有即持续生效）/ 有条件触发。会改变下面显示哪些字段，所以要重建
        r = stateButton(r, PREFIX + ".group.mode",
                () -> Component.translatable(group.isGlobal()
                        ? PREFIX + ".group.mode.global"
                        : PREFIX + ".group.mode.conditional"),
                () -> group.setGlobal(!group.isGlobal()), true);

        // 条件：这一组满足时才执行本组内容（全局组没有条件）
        if (!global) {
            if (group.conditions.isEmpty()) {
                r = note(r, PREFIX + ".hint.condition_empty", SREPanelStyle.GOLD_DIM, 1);
            }
            for (int i = 0; i < group.conditions.size(); i++) {
                r = conditionRow(r, group, i);
            }
            r = addRow(r, PREFIX + ".trigger.add", () -> {
                group.conditions.add(defaultCondition());
                requestRebuild();
            });
        }

        // 内容：指令
        r = note(r, PREFIX + ".hint.commands", SREPanelStyle.GOLD_DIM, 1);
        r = stringListRows(r, group.commands, LIMIT_COMMAND, Component.literal("say <player>"),
                PREFIX + ".effect.add_command");

        // 内容：药水效果（全局组的时长由「常驻刷新」决定，不显示时长格）
        r = note(r, global ? PREFIX + ".hint.effects_global" : PREFIX + ".hint.effects_timed",
                SREPanelStyle.GOLD_DIM, 1);
        r = effectRows(r, group, global);

        // 内容：玩家属性（只在全局组常驻）/ 条件组的「触发后移除修饰符」
        if (global) {
            r = note(r, PREFIX + ".hint.attributes", SREPanelStyle.GOLD_DIM, 1);
            r = attributeRows(r, group);
        } else {
            r = toggle(r, PREFIX + ".effect.remove_on_trigger", group.removeModifierOnTrigger,
                    value -> group.removeModifierOnTrigger = value);
        }
        return gap(cardEnd(gap(r)));
    }

    /** 组内一条条件：类型 + 参数 + 与/或 + 删除。 */
    private int conditionRow(int r, CustomModifierData.TriggerGroupData group, int index) {
        ConditionData condition = group.conditions.get(index);
        ConditionType type = conditionType(condition);

        List<Cell> cells = new ArrayList<>();
        // 类型：点击切到下一个条件类型（参数形态跟着变，所以要重建）
        cells.add(stateButtonCell(
                () -> Component.translatable(PREFIX + ".condition." + conditionType(condition).name()),
                () -> {
                    ConditionType[] values = ConditionType.values();
                    applyDefaultParams(condition,
                            values[(conditionType(condition).ordinal() + 1) % values.length]);
                }, true));

        switch (paramKind(type)) {
            case 1 -> cells.add(fixedBox(num(condition.value), LIMIT_NUMBER, 70, valueHint(type),
                    value -> condition.value = parseDouble(value, condition.value)));
            case 2 -> cells.add(box(condition.stringValue, LIMIT_TEXT, 110, stringHint(type),
                    value -> condition.stringValue = value));
            case 3 -> {
                cells.add(stateButtonCell(
                        () -> Component.translatable(PREFIX + ".comparison." + condition.comparison),
                        () -> condition.comparison = nextComparison(condition.comparison), false));
                cells.add(fixedBox(num(condition.value), LIMIT_NUMBER, 70,
                        Component.translatable(PREFIX + ".hint.number"),
                        value -> condition.value = parseDouble(value, condition.value)));
            }
            case 4 -> cells.add(stateButtonCell(
                    () -> Component.translatable(PREFIX + ".time." + condition.worldTimeType),
                    () -> condition.worldTimeType = nextTime(condition.worldTimeType), false));
            case 5 -> {
                cells.add(fixedBox(String.valueOf(condition.intervalSeconds), LIMIT_NUMBER, 60,
                        Component.translatable(PREFIX + ".hint.interval_seconds"),
                        value -> condition.intervalSeconds = parseInt(value, condition.intervalSeconds)));
                cells.add(fixedBox(String.valueOf(condition.chance), LIMIT_NUMBER, 70,
                        Component.translatable(PREFIX + ".hint.chance"),
                        value -> condition.chance = parseInt(value, condition.chance)));
            }
            // 阵营：点击在「平民 / 警长 / 中立 / …」之间切换
            case 6 -> cells.add(stateButtonCell(
                    () -> Component.translatable(PREFIX + ".team." + teamName(condition.stringValue)),
                    () -> condition.stringValue = nextTeam(condition.stringValue), false));
            default -> {
            }
        }

        // 与 / 或（与下一个条件的关系）
        cells.add(stateButtonCell(
                () -> Component.translatable(isOr(condition) ? PREFIX + ".logic.or" : PREFIX + ".logic.and"),
                () -> condition.logic = isOr(condition) ? "AND" : "OR", false));
        cells.add(removeButton(Component.translatable(REMOVE), () -> {
            group.conditions.remove(index);
            requestRebuild();
        }));
        return cluster(r, null, cells.toArray(new Cell[0]));
    }

    /** 组内一行一个「输入框 + ×」的字符串列表，末尾跟一个 ＋。 */
    private int stringListRows(int r, List<String> list, int limit, Component hint, String addKey) {
        if (list.isEmpty()) {
            list.add("");
        }
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            r = cluster(r, null,
                    box(list.get(i), limit, 200, hint, value -> list.set(index, value)),
                    removeButton(Component.translatable(REMOVE), () -> {
                        list.remove(index);
                        requestRebuild();
                    }));
        }
        return addRow(r, addKey, () -> {
            list.add("");
            requestRebuild();
        });
    }

    /** 组内药水效果：效果 id + 等级（+ 条件组的持续秒数）+ ×。 */
    private int effectRows(int r, CustomModifierData.TriggerGroupData group, boolean global) {
        for (int i = 0; i < group.effects.size(); i++) {
            final int index = i;
            EffectData effect = group.effects.get(i);
            List<Cell> cells = new ArrayList<>();
            cells.add(box(effect.effectId, LIMIT_PATH, 130, Component.literal("minecraft:speed"),
                    value -> effect.effectId = value));
            cells.add(fixedBox(String.valueOf(effect.amplifier), LIMIT_NUMBER, 44,
                    Component.translatable(PREFIX + ".hint.amplifier"),
                    value -> effect.amplifier = parseInt(value, effect.amplifier)));
            if (!global) {
                cells.add(fixedBox(String.valueOf(effect.durationSeconds), LIMIT_NUMBER, 54,
                        Component.translatable(PREFIX + ".hint.duration_seconds"),
                        value -> effect.durationSeconds = parseInt(value, effect.durationSeconds)));
            }
            cells.add(removeButton(Component.translatable(REMOVE), () -> {
                group.effects.remove(index);
                requestRebuild();
            }));
            r = cluster(r, null, cells.toArray(new Cell[0]));
        }
        return addRow(r, PREFIX + ".effect.add_effect", () -> {
            EffectData effect = new EffectData();
            effect.effectId = "minecraft:speed";
            effect.durationSeconds = 10;
            group.effects.add(effect);
            requestRebuild();
        });
    }

    /** 组内玩家属性：属性 id + 数值 + ×。 */
    private int attributeRows(int r, CustomModifierData.TriggerGroupData group) {
        for (int i = 0; i < group.attributes.size(); i++) {
            final int index = i;
            AttributeData attribute = group.attributes.get(i);
            r = cluster(r, null,
                    box(attribute.attributeId, LIMIT_PATH, 160,
                            Component.literal("minecraft:generic.scale"),
                            value -> attribute.attributeId = value),
                    fixedBox(num(attribute.value), LIMIT_NUMBER, 70,
                            Component.translatable(PREFIX + ".hint.number"),
                            value -> attribute.value = parseDouble(value, attribute.value)),
                    removeButton(Component.translatable(REMOVE), () -> {
                        group.attributes.remove(index);
                        requestRebuild();
                    }));
        }
        return addRow(r, PREFIX + ".effect.add_attribute", () -> {
            group.attributes.add(new AttributeData());
            requestRebuild();
        });
    }


    // ══════════════════════════════════════════════════════════════════
    // 静态工具
    // ══════════════════════════════════════════════════════════════════

    private static boolean isOr(ConditionData condition) {
        return "OR".equalsIgnoreCase(condition.logic);
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
            case STAY_STILL -> condition.value = 20;
            default -> {
            }
        }
    }

    private static Component valueHint(ConditionType type) {
        return switch (type) {
            case TIME_ANCHOR, ELAPSED_TIME -> Component
                    .translatable(PREFIX + ".hint.seconds_since_start");
            case TIMER -> Component.translatable(PREFIX + ".hint.seconds");
            case DEATH_COUNTDOWN, DEATH_COUNTDOWN_REVIVE -> Component
                    .translatable(PREFIX + ".hint.death_seconds");
            case STAY_STILL -> Component.translatable(PREFIX + ".hint.ticks");
            default -> Component.translatable(PREFIX + ".hint.number");
        };
    }

    /** 字符串参数的占位提示（职业 / 修饰符 id 各有专门提示）。 */
    private static Component stringHint(ConditionType type) {
        return switch (type) {
            case KILLED_BY_ROLE -> Component.translatable(PREFIX + ".hint.role_id");
            case KILLED_BY_MODIFIER -> Component.translatable(PREFIX + ".hint.modifier_id");
            default -> Component.translatable(PREFIX + ".hint.string_value");
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

    /** 条件参数形态：0 无；1 数值；2 字符串；3 比较+数值；4 世界时间；5 间隔+概率；6 阵营。 */
    private static int paramKind(ConditionType type) {
        return switch (type) {
            case TIMER, TIME_ANCHOR, ELAPSED_TIME, DEATH_COUNTDOWN, DEATH_COUNTDOWN_REVIVE, STAY_STILL -> 1;
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

    private static void toggle(List<String> list, String value) {
        if (!list.remove(value)) {
            list.add(value);
        }
    }
}
