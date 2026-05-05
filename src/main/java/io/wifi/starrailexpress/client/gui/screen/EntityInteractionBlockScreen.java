package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import io.wifi.starrailexpress.network.EntityInteractionBlockPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体交互方块的配置界面
 * 参考k键快捷指令的UI设计
 */
public class EntityInteractionBlockScreen extends Screen {
    private final BlockPos blockPos;
    private List<EntityInteractionBlockEntity.TriggerCondition> conditions;
    private List<EntityInteractionBlockEntity.TriggerAction> actions;
    private int cooldownTicks;

    // UI元素
    private EditBox cooldownInput;
    private int conditionsScrollOffset = 0;
    private int actionsScrollOffset = 0;
    private static final int LINE_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 50;
    private static final int SECTION_TITLE_HEIGHT = 25;
    private static final int PANEL_MARGIN = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SECTION_GAP = 15;

    // 传送点设置
    private boolean isTeleportPoint = false;
    private int teleportPointId = -1;

    // 死亡原因列表（用于DEATH条件）- 参考 GameConstants.DeathReasons + noellesroles + stupid_express
    private static final List<String> DEATH_REASONS = List.of(
            // 基础死亡原因 (GameConstants.DeathReasons)
            "*", "disconnected", "black_white", "backfire", "execute", "generic",
            "knife_stab", "revolver_shot", "derringer_shot", "bat_hit", "grenade",
            "poison", "self_explosion", "fell_out_of_train", "arrow", "trident",
            "sniper_rifle", "sniper_rifle_backfire", "nunchuck_hit",
            // noellesroles 自定义死亡原因
            "noellesroles:voodoo",
            "noellesroles:shot_innocent",
            "noellesroles:insane_killer_death",
            "noellesroles:heart_attack",
            "noellesroles:conspiracy_backfire",
            "noellesroles:stalker_execution",
            "noellesroles:bomb_death",
            "noellesroles:puppeteer_puppet",
            "noellesroles:recorder_mistake",
            "noellesroles:gamble_self_kill",
            "noellesroles:wayfarer_error",
            "noellesroles:nianshou_firecrackers",
            "noellesroles:dnf_tentacle",
            // stupid_express 自定义死亡原因
            "stupid_express:broken_heart",
            "stupid_express:failed_initiation",
            "stupid_express:allergist",
            "stupid_express:failed_ignite",
            "stupid_express:ignited",
            "stupid_express:loose_end",
            "stupid_express:split_personality"
    );

    // 任务类型列表（用于CHANGE_TASK动作）
    private static final List<String> TASK_TYPES = List.of(
            "random", "sleep", "read_book", "eat", "drink", "exercise",
            "meditate", "bathe", "note_block", "toilet", "chair", "breathe"
    );

    public EntityInteractionBlockScreen(BlockPos pos, List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                        List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldownTicks) {
        super(Component.translatable("gui.entity_interaction_block.title"));
        this.blockPos = pos;
        this.conditions = new ArrayList<>(conditions);
        this.actions = new ArrayList<>(actions);
        this.cooldownTicks = cooldownTicks;
    }

    public EntityInteractionBlockScreen(BlockPos pos, List<EntityInteractionBlockEntity.TriggerCondition> conditions,
                                        List<EntityInteractionBlockEntity.TriggerAction> actions, int cooldownTicks,
                                        boolean isTeleportPoint, int teleportPointId) {
        this(pos, conditions, actions, cooldownTicks);
        this.isTeleportPoint = isTeleportPoint;
        this.teleportPointId = teleportPointId;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int centerX = this.width / 2;
        int contentWidth = this.width - 2 * PANEL_MARGIN;

        // ===== 顶部设置区域 =====
        int topY = HEADER_HEIGHT;

        // 冷却时间设置（左侧）
        addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.cooldown"),
                b -> {}).bounds(PANEL_MARGIN, topY, 80, BUTTON_HEIGHT).build());

        cooldownInput = new EditBox(this.font, PANEL_MARGIN + 85, topY, 50, BUTTON_HEIGHT,
                Component.translatable("gui.entity_interaction_block.cooldown_hint"));
        cooldownInput.setValue(String.valueOf(cooldownTicks / 20.0));
        cooldownInput.setFilter(s -> s.matches("[0-9.]*"));
        addRenderableWidget(cooldownInput);

        addRenderableWidget(Button.builder(Component.literal("s"), b -> {}).bounds(PANEL_MARGIN + 140, topY, 20, BUTTON_HEIGHT).build());

        // 传送点设置（右侧）
        addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.teleport_point"),
                b -> this.minecraft.setScreen(new TeleportPointScreen(this)))
                .bounds(this.width - 130, topY, 120, BUTTON_HEIGHT).build());

        // ===== 计算布局区域 =====
        int availableHeight = this.height - HEADER_HEIGHT - SECTION_TITLE_HEIGHT - 80;
        int conditionsHeight = (availableHeight - SECTION_GAP) / 2;
        int actionsHeight = availableHeight - conditionsHeight - SECTION_GAP;

        // 调整条件区域起始Y，确保不与顶部设置区域重合
        int conditionsStartY = HEADER_HEIGHT + SECTION_TITLE_HEIGHT + BUTTON_HEIGHT + 15;
        int actionsStartY = conditionsStartY + conditionsHeight + SECTION_GAP;

        // ===== 条件区域 =====
        // 条件区域标题栏
        int condTitleY = conditionsStartY - SECTION_TITLE_HEIGHT;
        addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.conditions_title"),
                b -> {}).bounds(PANEL_MARGIN, condTitleY, 120, BUTTON_HEIGHT).build());

        // 添加条件按钮
        addRenderableWidget(Button.builder(Component.literal("+").withStyle(s -> s.withBold(true)),
                b -> this.minecraft.setScreen(new AddConditionScreen(this)))
                .bounds(this.width - PANEL_MARGIN - 30, condTitleY, 30, BUTTON_HEIGHT).build());

        // 条件列表滚动按钮
        if (conditions.size() * LINE_HEIGHT > conditionsHeight - 10) {
            addRenderableWidget(Button.builder(Component.literal("↑"),
                    b -> {
                        conditionsScrollOffset = Math.max(0, conditionsScrollOffset - 1);
                        this.init();
                    }).bounds(this.width - PANEL_MARGIN - 60, condTitleY, 25, BUTTON_HEIGHT).build());
        }

        // 显示条件列表
        int condContentY = conditionsStartY + 5;
        int maxVisibleConditions = (conditionsHeight - 10) / LINE_HEIGHT;
        int visibleEndIndex = Math.min(conditions.size(), conditionsScrollOffset + maxVisibleConditions);

        for (int i = conditionsScrollOffset; i < visibleEndIndex; i++) {
            final int index = i;
            EntityInteractionBlockEntity.TriggerCondition condition = conditions.get(i);
            String conditionText = getConditionDisplayText(condition, i);

            int itemY = condContentY + (i - conditionsScrollOffset) * LINE_HEIGHT;

            // 逻辑运算符按钮（第一个条件不显示）
            if (i > 0) {
                EntityInteractionBlockEntity.LogicOperator currentLogic = condition.logicOperator != null
                        ? condition.logicOperator
                        : EntityInteractionBlockEntity.LogicOperator.AND;
                addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.LogicOperator>builder(logic ->
                                Component.translatable("logic." + logic.name().toLowerCase()).withStyle(s -> s.withBold(true)))
                        .withValues(EntityInteractionBlockEntity.LogicOperator.values())
                        .withInitialValue(currentLogic)
                        .create(PANEL_MARGIN, itemY, 50, BUTTON_HEIGHT - 2,
                                Component.empty(),
                                (b, logic) -> {
                                    conditions.get(index).logicOperator = logic;
                                    this.init();
                                }));
            }

            int textX = i > 0 ? PANEL_MARGIN + 55 : PANEL_MARGIN;
            int textWidth = this.width - 2 * PANEL_MARGIN - 100 - (i > 0 ? 55 : 0);

            // 条件显示按钮
            addRenderableWidget(Button.builder(Component.literal(truncateText(conditionText, textWidth)),
                    b -> {}).bounds(textX, itemY, textWidth, BUTTON_HEIGHT - 2).build());

            // 删除按钮
            addRenderableWidget(Button.builder(Component.literal("×").withStyle(s -> s.withColor(0xFF5555)),
                    b -> {
                        conditions.remove(index);
                        if (conditionsScrollOffset >= conditions.size() && conditionsScrollOffset > 0) {
                            conditionsScrollOffset--;
                        }
                        this.init();
                    }).bounds(this.width - PANEL_MARGIN - 45, itemY, 40, BUTTON_HEIGHT - 2).build());
        }

        // 条件区域滚动按钮（下）
        if (conditions.size() * LINE_HEIGHT > conditionsHeight - 10 && conditionsScrollOffset + maxVisibleConditions < conditions.size()) {
            addRenderableWidget(Button.builder(Component.literal("↓"),
                    b -> {
                        conditionsScrollOffset = Math.min(conditions.size() - maxVisibleConditions, conditionsScrollOffset + 1);
                        this.init();
                    }).bounds(this.width - PANEL_MARGIN - 60, conditionsStartY + conditionsHeight - 30, 25, 20).build());
        }

        // ===== 动作区域 =====
        // 动作区域标题栏
        int actionTitleY = actionsStartY - SECTION_TITLE_HEIGHT;
        addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.actions_title"),
                b -> {}).bounds(PANEL_MARGIN, actionTitleY, 120, BUTTON_HEIGHT).build());

        // 添加动作按钮
        addRenderableWidget(Button.builder(Component.literal("+").withStyle(s -> s.withBold(true)),
                b -> this.minecraft.setScreen(new AddActionScreen(this)))
                .bounds(this.width - PANEL_MARGIN - 30, actionTitleY, 30, BUTTON_HEIGHT).build());

        // 动作列表滚动按钮
        if (actions.size() * LINE_HEIGHT > actionsHeight - 10) {
            addRenderableWidget(Button.builder(Component.literal("↑"),
                    b -> {
                        actionsScrollOffset = Math.max(0, actionsScrollOffset - 1);
                        this.init();
                    }).bounds(this.width - PANEL_MARGIN - 60, actionTitleY, 25, BUTTON_HEIGHT).build());
        }

        // 显示动作列表
        int actionContentY = actionsStartY + 5;
        int maxVisibleActions = (actionsHeight - 10) / LINE_HEIGHT;
        int actionVisibleEndIndex = Math.min(actions.size(), actionsScrollOffset + maxVisibleActions);

        for (int i = actionsScrollOffset; i < actionVisibleEndIndex; i++) {
            final int index = i;
            EntityInteractionBlockEntity.TriggerAction action = actions.get(i);
            String actionText = getActionDisplayText(action);

            int itemY = actionContentY + (i - actionsScrollOffset) * LINE_HEIGHT;
            int textWidth = this.width - 2 * PANEL_MARGIN - 100;

            addRenderableWidget(Button.builder(Component.literal(truncateText(actionText, textWidth)),
                    b -> {}).bounds(PANEL_MARGIN, itemY, textWidth, BUTTON_HEIGHT - 2).build());

            addRenderableWidget(Button.builder(Component.literal("×").withStyle(s -> s.withColor(0xFF5555)),
                    b -> {
                        actions.remove(index);
                        if (actionsScrollOffset >= actions.size() && actionsScrollOffset > 0) {
                            actionsScrollOffset--;
                        }
                        this.init();
                    }).bounds(this.width - PANEL_MARGIN - 45, itemY, 40, BUTTON_HEIGHT - 2).build());
        }

        // 动作区域滚动按钮（下）
        if (actions.size() * LINE_HEIGHT > actionsHeight - 10 && actionsScrollOffset + maxVisibleActions < actions.size()) {
            addRenderableWidget(Button.builder(Component.literal("↓"),
                    b -> {
                        actionsScrollOffset = Math.min(actions.size() - maxVisibleActions, actionsScrollOffset + 1);
                        this.init();
                    }).bounds(this.width - PANEL_MARGIN - 60, actionsStartY + actionsHeight - 30, 25, 20).build());
        }

        // ===== 底部保存按钮 =====
        addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.save"),
                b -> saveAndClose())
                .bounds(centerX - 50, this.height - 25, 100, BUTTON_HEIGHT).build());
    }

    private String truncateText(String text, int maxWidth) {
        int maxChars = maxWidth / 6;
        if (text.length() > maxChars) {
            return text.substring(0, maxChars - 3) + "...";
        }
        return text;
    }

    private String getConditionDisplayText(EntityInteractionBlockEntity.TriggerCondition condition, int index) {
        String logicPrefix = "";
        if (index > 0 && condition.logicOperator != null) {
            logicPrefix = "[" + Component.translatable("logic." + condition.logicOperator.name().toLowerCase()).getString() + "] ";
        }

        String conditionText = switch (condition.type) {
            case PASS_THROUGH -> Component.translatable("condition.pass_through").getString();
            case TIMER -> Component.translatable("condition.timer.blocks_display", condition.value).getString();
            case TIME_ANCHOR -> Component.translatable("condition.time_anchor", formatTimeDisplay(condition.value),
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case PROXIMITY_SPHERE -> Component.translatable("condition.proximity_sphere.blocks_display", condition.value).getString();
            case PROXIMITY_LINE -> Component.translatable("condition.proximity_line.blocks_display", condition.value).getString();
            case HAS_ITEM -> Component.translatable("condition.has_item", condition.stringValue).getString();
            case CLICK_BLOCK -> Component.translatable(condition.leftClick ? "condition.click_left" : "condition.click_right").getString();
            case LOOKING_AT -> Component.translatable("condition.looking_at.blocks_display", condition.value).getString();
            case STANDING_ON_BLOCK -> Component.translatable("condition.standing_on_block", condition.value, condition.stringValue).getString();
            case DEATH -> Component.translatable("condition.death", condition.stringValue != null ? condition.stringValue : "*").getString();
            case USE_ITEM -> Component.translatable("condition.use_item.count_display", (int) condition.value).getString();
            case SPEAK -> Component.translatable("condition.speak.count_display", (int) condition.value).getString();
            case COIN_AMOUNT -> Component.translatable("condition.coin_amount", (int) condition.value).getString();
            case ROLE_IS -> Component.translatable("condition.role_is", condition.stringValue).getString();
            case ROLE_TEAM -> Component.translatable("condition.role_team",
                    Component.translatable("team." + condition.teamType.name().toLowerCase())).getString();
            case HAS_KILLED -> Component.translatable("condition.has_killed").getString();
            case PLAYER_COUNT -> Component.translatable("condition.player_count", (int) condition.value,
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case ALIVE_PLAYERS -> Component.translatable("condition.alive_players", (int) condition.value,
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case IS_SNEAKING -> Component.translatable("condition.is_sneaking").getString();
            case IS_SPRINTING -> Component.translatable("condition.is_sprinting").getString();
            case HAS_EFFECT -> Component.translatable("condition.has_effect", condition.stringValue).getString();
            case PROBABILITY -> Component.translatable("condition.probability", condition.value).getString();
            case WORLD_TIME -> Component.translatable("condition.world_time",
                    Component.translatable("world_time." + condition.worldTimeType.name().toLowerCase())).getString();
            case ENTITY_COUNT -> {
                if (condition.checkAnyCount) {
                    yield Component.translatable("condition.entity_count_any", condition.value, condition.stringValue).getString();
                } else {
                    yield Component.translatable("condition.entity_count", condition.value, condition.stringValue,
                            (int) condition.entityCount, Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
                }
            }
            case MOOD_VALUE -> Component.translatable("condition.mood_value", condition.value,
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case IS_PSYCHO -> Component.translatable("condition.is_psycho").getString();
            case IS_POISONED -> Component.translatable("condition.is_poisoned").getString();
            case ARMOR_AMOUNT -> Component.translatable("condition.armor_amount", (int) condition.value,
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case HAS_TASK -> Component.translatable("condition.has_task").getString();
            case TASK_STREAK -> Component.translatable("condition.task_streak", (int) condition.value,
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case GAME_RUNNING -> Component.translatable("condition.game_running").getString();
            case PSYCHOS_ACTIVE -> Component.translatable("condition.psychos_active", (int) condition.value,
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case IS_BLACKOUT -> Component.translatable("condition.is_blackout").getString();
            case IS_MONITOR_BROKEN -> Component.translatable("condition.is_monitor_broken").getString();
            case NEED_TASK_TYPE -> Component.translatable("condition.need_task_type",
                    Component.translatable("task_type." + (condition.stringValue != null ? condition.stringValue : "random"))).getString();
            case NEED_CUSTOM_TASK -> Component.translatable("condition.need_custom_task",
                    condition.stringValue != null ? condition.stringValue : "?").getString();
        };

        return logicPrefix + conditionText;
    }

    private String formatTimeDisplay(double value) {
        int totalSeconds = (int) (value * 60);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private String getActionDisplayText(EntityInteractionBlockEntity.TriggerAction action) {
        return switch (action.type) {
            case EXECUTE_COMMAND -> Component.translatable("action.execute_command", action.stringValue).getString();
            case POISON -> Component.translatable("action.poison", action.value).getString();
            case CURE_POISON -> Component.translatable("action.cure_poison").getString();
            case SET_SHIELD -> Component.translatable("action.set_shield", (int) action.value).getString();
            case DAMAGE_DEATH -> Component.translatable("action.damage_death", action.stringValue != null ? action.stringValue : "*").getString();
            case FORCE_KILL -> Component.translatable("action.force_kill", action.stringValue != null ? action.stringValue : "*").getString();
            case ENABLE_COLLISION -> Component.translatable("action.enable_collision", action.value).getString();
            case MOOD_CHANGE -> {
                // 使用 %+.1f 格式化心情值（保留一位小数并显示正负号），然后作为字符串传递给翻译
                String formatted = String.format("%+.1f", action.value);
                yield Component.translatable("action.mood_change", formatted).getString();
            }
            case CHANGE_ROLE -> Component.translatable("action.change_role", action.stringValue).getString();
            case CHANGE_TASK -> {
                String taskType = action.taskType != null ? action.taskType : "random";
                yield Component.translatable("action.change_task", Component.translatable("task_type." + taskType)).getString();
            }
            case RESURRECT -> Component.translatable("action.resurrect", action.value).getString();
            case PSYCHO_MODE -> Component.translatable("action.psycho_mode").getString();
            case BLACKOUT -> Component.translatable("action.blackout").getString();
            case MONITOR_BROKEN -> Component.translatable("action.monitor_broken").getString();
            case ADD_TIME -> {
                boolean isAdd = action.stringValue == null || !"subtract".equals(action.stringValue);
                String prefix = isAdd ? "+" : "-";
                yield Component.translatable("action.add_time", prefix + formatTimeDisplay(Math.abs(action.value))).getString();
            }
            case SET_TIME -> Component.translatable("action.set_time", formatTimeDisplay(action.value)).getString();
            case GAME_WIN -> Component.translatable("action.game_win", action.stringValue).getString();
            case COIN_CHANGE -> {
                int change = (int) action.value;
                String prefix = change >= 0 ? "+" : "";
                yield Component.translatable("action.coin_change", prefix + change).getString();
            }
            case GIVE_EFFECT -> Component.translatable("action.give_effect", action.stringValue, action.effectDuration).getString();
            case TELEPORT -> Component.translatable("action.teleport", (int) action.value).getString();
            case SHOW_TITLE -> Component.translatable("action.show_title", action.stringValue).getString();
            case BROADCAST_MESSAGE -> Component.translatable("action.broadcast_message", action.stringValue).getString();
            case ITEM_COOLDOWN -> Component.translatable("action.item_cooldown", action.stringValue, action.value).getString();
            case BLOCK_COOLDOWN -> Component.translatable("action.block_cooldown", (int) action.value).getString();
            case CLEAR_ENTITIES -> Component.translatable("action.clear_entities", action.value, action.stringValue).getString();
            case SET_MOOD -> {
                String mode = action.stringValue != null && action.stringValue.equals("add") ? "add" : "set";
                yield Component.translatable("action.set_mood." + mode, action.value).getString();
            }
            case CURE_PSYCHO -> Component.translatable("action.cure_psycho").getString();
            case CLEAR_TASKS -> Component.translatable("action.clear_tasks").getString();
            case COMPLETE_TASK -> Component.translatable("action.complete_task").getString();
            case END_BLACKOUT -> Component.translatable("action.end_blackout").getString();
            case FIX_MONITOR -> Component.translatable("action.fix_monitor").getString();
            case ADD_CUSTOM_TASK -> Component.translatable("action.add_custom_task",
                    action.customTaskName != null ? action.customTaskName : "?",
                    action.customTaskId != null ? action.customTaskId : "?").getString();
            case COMPLETE_CUSTOM_TASK -> Component.translatable("action.complete_custom_task",
                    action.customTaskId != null ? action.customTaskId : "?").getString();
            case NARRATOR -> Component.translatable("action.narrator",
                    action.narratorText != null ? action.narratorText : "?",
                    Component.translatable(action.narratorInterrupt ?
                            "gui.entity_interaction_block.narrator_interrupt" :
                            "gui.entity_interaction_block.narrator_queue")).getString();
        };
    }

    private void saveAndClose() {
        try {
            cooldownTicks = (int) (Double.parseDouble(cooldownInput.getValue()) * 20);
        } catch (NumberFormatException e) {
            cooldownTicks = 40;
        }

        EntityInteractionBlockPayload.sendSaveConfig(blockPos, conditions, actions, cooldownTicks, isTeleportPoint, teleportPointId);
        this.onClose();
    }

    public void setTeleportPoint(boolean isTeleportPoint, int teleportPointId) {
        this.isTeleportPoint = isTeleportPoint;
        this.teleportPointId = teleportPointId;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void addCondition(EntityInteractionBlockEntity.TriggerCondition condition) {
        conditions.add(condition);
    }

    public void addAction(EntityInteractionBlockEntity.TriggerAction action) {
        actions.add(action);
    }

    // 添加条件子界面
    private class AddConditionScreen extends Screen {
        private final EntityInteractionBlockScreen parent;
        private EntityInteractionBlockEntity.ConditionType selectedType = EntityInteractionBlockEntity.ConditionType.PASS_THROUGH;
        private EditBox valueInput;
        private EditBox stringInput;
        private EditBox minutesInput;
        private EditBox secondsInput;
        private EntityInteractionBlockEntity.ComparisonType selectedComparison = EntityInteractionBlockEntity.ComparisonType.EQUALS;
        private EntityInteractionBlockEntity.TeamType selectedTeam = EntityInteractionBlockEntity.TeamType.CIVILIAN;
        private boolean leftClick = false;
        private int scrollY = 0;
        private static final int SCROLL_STEP = 15;

        public AddConditionScreen(EntityInteractionBlockScreen parent) {
            super(Component.translatable("gui.entity_interaction_block.add_condition"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            this.clearWidgets();
            scrollY = 0;

            int centerX = this.width / 2;

            // 条件类型选择（带↑↓提示）
            addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ConditionType>builder(type ->
                            Component.translatable("condition_type." + type.name().toLowerCase()))
                    .withValues(EntityInteractionBlockEntity.ConditionType.values())
                    .withInitialValue(selectedType)
                    .create(centerX - 100, 40, 200, 20,
                            Component.translatable("gui.entity_interaction_block.condition_type"),
                            (b, type) -> {
                                selectedType = type;
                                this.init();
                            }));

            // 添加↑↓切换提示按钮
            addRenderableWidget(Button.builder(Component.literal("↑"), b -> {
                int currentIndex = selectedType.ordinal();
                if (currentIndex > 0) {
                    selectedType = EntityInteractionBlockEntity.ConditionType.values()[currentIndex - 1];
                    this.init();
                }
            }).bounds(centerX - 160, 40, 30, 20).build());

            addRenderableWidget(Button.builder(Component.literal("↓"), b -> {
                int currentIndex = selectedType.ordinal();
                EntityInteractionBlockEntity.ConditionType[] types = EntityInteractionBlockEntity.ConditionType.values();
                if (currentIndex < types.length - 1) {
                    selectedType = types[currentIndex + 1];
                    this.init();
                }
            }).bounds(centerX + 170, 40, 30, 20).build());

            int y = 80;

            // 根据条件类型显示不同的输入框
            switch (selectedType) {
                case TIMER -> {
                    // 自动定时
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.timer_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.timer_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.timer_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case PROXIMITY_SPHERE -> {
                    // 球形范围（半径）
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.blocks_radius")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.blocks_radius"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.proximity_sphere_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case PROXIMITY_LINE -> {
                    // 直线范围
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.blocks_range")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.blocks_range"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.proximity_line_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case LOOKING_AT -> {
                    // 看向方块距离
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.blocks_distance")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.blocks_distance"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.looking_at_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case USE_ITEM -> {
                    // 使用物品次数
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.use_item_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.use_item_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.use_item_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case SPEAK -> {
                    // 说话字数
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.speak_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.speak_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.speak_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case TIME_ANCHOR -> {
                    // 时间锚点 - 分秒分离输入
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.time_value"), b -> {}).bounds(centerX - 100, y, 200, 20).build());
                    y += 25;

                    // 分钟输入
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 50, 20,
                            Component.translatable("gui.entity_interaction_block.minutes")));
                    minutesInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.minutes"));
                    if (minutesInput != null) {
                        minutesInput.setFilter(s -> s.matches("[0-9]*"));
                        minutesInput.setValue("0");
                    }

                    addRenderableWidget(Button.builder(Component.literal(":"), b -> {}).bounds(centerX - 25, y, 20, 20).build());

                    // 秒输入
                    addRenderableWidget(new EditBox(this.font, centerX, y, 50, 20,
                            Component.translatable("gui.entity_interaction_block.seconds")));
                    secondsInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.seconds"));
                    if (secondsInput != null) {
                        secondsInput.setFilter(s -> s.matches("[0-9]*"));
                        secondsInput.setValue("0");
                    }

                    y += 30;
                    // 比较类型
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case HAS_ITEM -> {
                    // 物品ID
                    addRenderableWidget(new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.has_item_hint")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.has_item_hint"));
                }
                case ROLE_IS -> {
                    // 职业ID
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.role_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.role_id"));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.role_is_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case CLICK_BLOCK -> {
                    // 左键/右键
                    addRenderableWidget(CycleButton.<Boolean>builder(left ->
                                    Component.translatable(left ? "gui.entity_interaction_block.left_click" : "gui.entity_interaction_block.right_click"))
                            .withValues(true, false)
                            .withInitialValue(leftClick)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.click_type"),
                                    (b, left) -> leftClick = left));
                }
                case STANDING_ON_BLOCK -> {
                    // 范围和方块ID
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.standing_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.standing_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }

                    y += 25;
                    addRenderableWidget(new EditBox(this.font, centerX - 10, y, 110, 20,
                            Component.translatable("gui.entity_interaction_block.block_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.block_id"));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.standing_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case DEATH -> {
                    // 死亡原因选择 - 使用预设列表
                    addRenderableWidget(CycleButton.<String>builder(reason ->
                                    Component.literal(reason.equals("*") ?
                                            Component.translatable("gui.entity_interaction_block.death_reason_any").getString() : reason))
                            .withValues(DEATH_REASONS)
                            .withInitialValue("*")
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.death_preset_select"),
                                    (b, reason) -> {
                                        if (stringInput == null) {
                                            stringInput = new EditBox(font, 0, 0, 0, 0, Component.empty());
                                        }
                                        stringInput.setValue(reason);
                                    }));

                    y += 30;
                    // 自定义死亡原因输入
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.death_custom_input")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.death_custom_input"));
                }
                case COIN_AMOUNT -> {
                    // 金币数量
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.coin_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.coin_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.coin_amount_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case ROLE_TEAM -> {
                    // 阵营
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.TeamType>builder(team ->
                                    Component.translatable("team." + team.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.TeamType.values())
                            .withInitialValue(selectedTeam)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.team"),
                                    (b, team) -> selectedTeam = team));
                }
                case PLAYER_COUNT -> {
                    // 玩家数量
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.player_count_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.player_count_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }

                    y += 25;
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case ALIVE_PLAYERS -> {
                    // 存活玩家数量
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.alive_players_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.alive_players_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }

                    y += 25;
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case IS_SNEAKING, IS_SPRINTING, HAS_KILLED -> {
                    // 不需要输入
                }
                case HAS_EFFECT -> {
                    // 效果ID
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.effect_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.effect_id"));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.has_effect_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case PROBABILITY -> {
                    // 概率
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.probability_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.probability_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    addRenderableWidget(Button.builder(Component.literal("%"), b -> {}).bounds(centerX + 55, y, 20, 20).build());
                }
                case WORLD_TIME -> {
                    // 世界时间类型
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.WorldTimeType>builder(timeType ->
                                    Component.translatable("world_time." + timeType.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.WorldTimeType.values())
                            .withInitialValue(EntityInteractionBlockEntity.WorldTimeType.DAY)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.world_time"),
                                    (b, timeType) -> {
                                        if (stringInput == null) {
                                            stringInput = new EditBox(font, 0, 0, 0, 0, Component.empty());
                                        }
                                        stringInput.setValue(timeType.name());
                                    }));
                }
                case ENTITY_COUNT -> {
                    // 实体数量
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.blocks_range")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.blocks_range"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }

                    addRenderableWidget(new EditBox(this.font, centerX + 10, y, 90, 20,
                            Component.translatable("gui.entity_interaction_block.entity_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.entity_id"));

                    y += 25;
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.entity_count_hint")));
                    EditBox countInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.entity_count_hint"));
                    if (countInput != null) {
                        countInput.setValue("*");
                    }

                    y += 25;
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.entity_count_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case MOOD_VALUE -> {
                    // 心情值
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.mood_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.mood_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                        valueInput.setValue("0.5");
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.mood_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());

                    y += 20;
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case ARMOR_AMOUNT -> {
                    // 护盾值
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.armor_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.armor_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.armor_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());

                    y += 20;
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case TASK_STREAK -> {
                    // 连续完成任务数
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.task_streak_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.task_streak_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.task_streak_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());

                    y += 20;
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case PSYCHOS_ACTIVE -> {
                    // 活跃疯魔玩家数
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.psychos_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.psychos_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    y += 22;
                    // 添加详细描述
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.psychos_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());

                    y += 20;
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case NEED_TASK_TYPE -> {
                    // 任务类型
                    addRenderableWidget(CycleButton.<String>builder(taskType ->
                                    Component.translatable("task_type." + taskType))
                            .withValues(TASK_TYPES)
                            .withInitialValue("random")
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.task_type"),
                                    (b, taskType) -> {
                                        if (stringInput == null) {
                                            stringInput = new EditBox(font, 0, 0, 0, 0, Component.empty());
                                        }
                                        stringInput.setValue(taskType);
                                    }));
                }
                case NEED_CUSTOM_TASK -> {
                    // 自定义任务ID - 直接创建并保存引用 (使用固定文本以便识别)
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.literal("custom_task_id"));
                    addRenderableWidget(stringInput);
                }
                // PASS_THROUGH 不需要输入
            }

            // 确认按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.confirm"),
                    b -> confirm()).bounds(centerX - 105, this.height - 40, 100, 20).build());

            // 取消按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.cancel"),
                    b -> this.minecraft.setScreen(parent)).bounds(centerX + 5, this.height - 40, 100, 20).build());
        }

        private EditBox findAndAttachInput(Component message) {
            for (var widget : this.children()) {
                if (widget instanceof EditBox box && box.getMessage().getString().equals(message.getString())) {
                    return box;
                }
            }
            return null;
        }

        private void confirm() {
            EntityInteractionBlockEntity.TriggerCondition condition = new EntityInteractionBlockEntity.TriggerCondition();
            condition.type = selectedType;

            // 处理时间锚点的分秒输入
            if (selectedType == EntityInteractionBlockEntity.ConditionType.TIME_ANCHOR) {
                int minutes = 0;
                int seconds = 0;
                if (minutesInput != null && !minutesInput.getValue().isEmpty()) {
                    try {
                        minutes = Integer.parseInt(minutesInput.getValue());
                    } catch (NumberFormatException ignored) {}
                }
                if (secondsInput != null && !secondsInput.getValue().isEmpty()) {
                    try {
                        seconds = Integer.parseInt(secondsInput.getValue());
                    } catch (NumberFormatException ignored) {}
                }
                condition.value = minutes + (seconds / 60.0);
            } else if (valueInput != null && !valueInput.getValue().isEmpty()) {
                try {
                    condition.value = Double.parseDouble(valueInput.getValue());
                } catch (NumberFormatException e) {
                    condition.value = 0;
                }
            }

            if (stringInput != null) {
                condition.stringValue = stringInput.getValue();
            }

            condition.comparison = selectedComparison;
            condition.teamType = selectedTeam;
            condition.leftClick = leftClick;

            // 处理世界时间类型
            if (selectedType == EntityInteractionBlockEntity.ConditionType.WORLD_TIME && stringInput != null) {
                try {
                    condition.worldTimeType = EntityInteractionBlockEntity.WorldTimeType.valueOf(stringInput.getValue());
                } catch (IllegalArgumentException e) {
                    condition.worldTimeType = EntityInteractionBlockEntity.WorldTimeType.DAY;
                }
            }

            // 处理实体数量条件
            if (selectedType == EntityInteractionBlockEntity.ConditionType.ENTITY_COUNT) {
                for (var widget : this.children()) {
                    if (widget instanceof EditBox box) {
                        String msg = box.getMessage().getString();
                        if (msg.contains("entity_count") || (msg.isEmpty() && box.getValue().equals("*"))) {
                            String countStr = box.getValue();
                            if ("*".equals(countStr)) {
                                condition.checkAnyCount = true;
                                condition.entityCount = 1;
                            } else {
                                condition.checkAnyCount = false;
                                try {
                                    condition.entityCount = Integer.parseInt(countStr);
                                } catch (NumberFormatException e) {
                                    condition.entityCount = 1;
                                }
                            }
                        }
                    }
                }
            }

            parent.addCondition(condition);
            this.minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
            // 处理鼠标滚轮滚动
            if (verticalScroll > 0) {
                scrollY = Math.max(0, scrollY - SCROLL_STEP);
                this.init();
            } else if (verticalScroll < 0) {
                scrollY += SCROLL_STEP;
                this.init();
            }
            return true;
        }
    }

    // 添加触发内容子界面
    private class AddActionScreen extends Screen {
        private final EntityInteractionBlockScreen parent;
        private EntityInteractionBlockEntity.ActionType selectedType = EntityInteractionBlockEntity.ActionType.EXECUTE_COMMAND;
        private EditBox valueInput;
        private EditBox stringInput;
        private EditBox minutesInput;
        private EditBox secondsInput;
        private String selectedTaskType = "random";
        private boolean addTimeMode = true; // true=增加，false=减少
        private boolean narratorInterrupt = false; // 语音播报是否打断
        private int scrollY = 0;
        private static final int SCROLL_STEP = 15;

        public AddActionScreen(EntityInteractionBlockScreen parent) {
            super(Component.translatable("gui.entity_interaction_block.add_action"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            this.clearWidgets();
            scrollY = 0;

            int centerX = this.width / 2;

            // 触发内容类型选择（带↑↓提示）
            addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ActionType>builder(type ->
                            Component.translatable("action_type." + type.name().toLowerCase()))
                    .withValues(EntityInteractionBlockEntity.ActionType.values())
                    .withInitialValue(selectedType)
                    .create(centerX - 100, 40, 200, 20,
                            Component.translatable("gui.entity_interaction_block.action_type"),
                            (b, type) -> {
                                selectedType = type;
                                this.init();
                            }));

            // 添加↑↓切换提示按钮
            addRenderableWidget(Button.builder(Component.literal("↑"), b -> {
                int currentIndex = selectedType.ordinal();
                if (currentIndex > 0) {
                    selectedType = EntityInteractionBlockEntity.ActionType.values()[currentIndex - 1];
                    this.init();
                }
            }).bounds(centerX - 160, 40, 30, 20).build());

            addRenderableWidget(Button.builder(Component.literal("↓"), b -> {
                int currentIndex = selectedType.ordinal();
                EntityInteractionBlockEntity.ActionType[] types = EntityInteractionBlockEntity.ActionType.values();
                if (currentIndex < types.length - 1) {
                    selectedType = types[currentIndex + 1];
                    this.init();
                }
            }).bounds(centerX + 170, 40, 30, 20).build());

            int y = 80;

            // 根据触发内容类型显示不同的输入框
            switch (selectedType) {
                case EXECUTE_COMMAND -> {
                    // 指令输入
                    addRenderableWidget(new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.command")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.command"));
                    if (stringInput != null) {
                        stringInput.setMaxLength(500);
                    }
                    y += 25;
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.command_hint"),
                            b -> {}).bounds(centerX - 150, y, 300, 15).build());
                }
                case POISON -> {
                    // 中毒时长
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.poison_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.poison_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.seconds"), b -> {}).bounds(centerX + 55, y, 30, 20).build());
                }
                case ENABLE_COLLISION -> {
                    // 碰撞时长
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.enable_collision_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.enable_collision_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.*]*"));
                    }
                }
                case SET_SHIELD -> {
                    // 护盾层数
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.shield_layers")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.shield_layers"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                }
                case DAMAGE_DEATH, FORCE_KILL -> {
                    // 死亡原因 - 预设选择 + 自定义输入
                    addRenderableWidget(CycleButton.<String>builder(reason ->
                                    Component.literal(reason.equals("*") ?
                                            Component.translatable("gui.entity_interaction_block.death_reason_any").getString() : reason))
                            .withValues(DEATH_REASONS)
                            .withInitialValue("*")
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.death_preset_select"),
                                    (b, reason) -> {
                                        if (stringInput == null) {
                                            stringInput = new EditBox(font, 0, 0, 0, 0, Component.empty());
                                        }
                                        stringInput.setValue(reason);
                                    }));

                    y += 30;
                    // 自定义死亡原因输入
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.death_custom_input")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.death_custom_input"));
                }
                case MOOD_CHANGE -> {
                    // 心情值变化
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.mood_change_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.mood_change_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("-?[0-9.]*"));
                    }
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.mood_change_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case CHANGE_ROLE -> {
                    // 职业ID
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.role_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.role_id"));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.change_role_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case CHANGE_TASK -> {
                    // 任务类型
                    addRenderableWidget(CycleButton.<String>builder(taskType ->
                                    Component.translatable("task_type." + taskType))
                            .withValues(TASK_TYPES)
                            .withInitialValue(selectedTaskType)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.task_type"),
                                    (b, taskType) -> selectedTaskType = taskType));
                }
                case RESURRECT -> {
                    // 复活半径
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.resurrect_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.resurrect_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.blocks"), b -> {}).bounds(centerX + 55, y, 30, 20).build());
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.resurrect_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case ADD_TIME -> {
                    // 增加/减少时间 - 带分秒分离输入
                    addRenderableWidget(CycleButton.<Boolean>builder(isAdd ->
                                    Component.translatable(isAdd ?
                                            "gui.entity_interaction_block.add_time_action" :
                                            "gui.entity_interaction_block.subtract_time_action"))
                            .withValues(true, false)
                            .withInitialValue(addTimeMode)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.add_or_subtract"),
                                    (b, isAdd) -> addTimeMode = isAdd));

                    y += 30;
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.add_time_hint"), b -> {}).bounds(centerX - 100, y, 200, 20).build());
                    y += 25;

                    // 分钟输入
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 50, 20,
                            Component.translatable("gui.entity_interaction_block.minutes")));
                    minutesInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.minutes"));
                    if (minutesInput != null) {
                        minutesInput.setFilter(s -> s.matches("[0-9]*"));
                        minutesInput.setValue("0");
                    }

                    addRenderableWidget(Button.builder(Component.literal(":"), b -> {}).bounds(centerX - 25, y, 20, 20).build());

                    // 秒输入
                    addRenderableWidget(new EditBox(this.font, centerX, y, 50, 20,
                            Component.translatable("gui.entity_interaction_block.seconds")));
                    secondsInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.seconds"));
                    if (secondsInput != null) {
                        secondsInput.setFilter(s -> s.matches("[0-9]*"));
                        secondsInput.setValue("0");
                    }
                }
                case SET_TIME -> {
                    // 设置时间 - 分秒分离
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.set_time_hint"), b -> {}).bounds(centerX - 100, y, 200, 20).build());
                    y += 25;

                    // 分钟输入
                    addRenderableWidget(new EditBox(this.font, centerX - 80, y, 50, 20,
                            Component.translatable("gui.entity_interaction_block.minutes")));
                    minutesInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.minutes"));
                    if (minutesInput != null) {
                        minutesInput.setFilter(s -> s.matches("[0-9]*"));
                        minutesInput.setValue("0");
                    }

                    addRenderableWidget(Button.builder(Component.literal(":"), b -> {}).bounds(centerX - 25, y, 20, 20).build());

                    // 秒输入
                    addRenderableWidget(new EditBox(this.font, centerX, y, 50, 20,
                            Component.translatable("gui.entity_interaction_block.seconds")));
                    secondsInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.seconds"));
                    if (secondsInput != null) {
                        secondsInput.setFilter(s -> s.matches("[0-9]*"));
                        secondsInput.setValue("0");
                    }
                }
                case GAME_WIN -> {
                    // 胜利标语
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.win_message")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.win_message"));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.game_win_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case COIN_CHANGE -> {
                    // 金币变化
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.coin_change_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.coin_change_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("-?[0-9]*"));
                    }
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.coin_change_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case GIVE_EFFECT -> {
                    // 效果ID
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.effect_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.effect_id"));

                    y += 25;
                    // 持续时间
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.effect_duration_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.effect_duration_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                        valueInput.setValue("10");
                    }
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.seconds"), b -> {}).bounds(centerX - 15, y, 30, 20).build());

                    // 等级
                    addRenderableWidget(new EditBox(this.font, centerX + 10, y, 50, 20,
                            Component.translatable("gui.entity_interaction_block.effect_amplifier")));
                    EditBox amplifierInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.effect_amplifier"));
                    if (amplifierInput != null) {
                        amplifierInput.setFilter(s -> s.matches("[0-9]*"));
                        amplifierInput.setValue("0");
                    }
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.give_effect_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case TELEPORT -> {
                    // 传送点ID
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.teleport_id_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.teleport_id_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.teleport_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case SHOW_TITLE -> {
                    // 标题文本
                    addRenderableWidget(new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.title_text")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.title_text"));
                }
                case BROADCAST_MESSAGE -> {
                    // 广播消息
                    addRenderableWidget(new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.broadcast_message")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.broadcast_message"));
                }
                case ITEM_COOLDOWN -> {
                    // 物品ID
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 120, 20,
                            Component.translatable("gui.entity_interaction_block.item_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.item_id"));
                    if (stringInput != null) {
                        stringInput.setValue("*");
                    }

                    y += 25;
                    // 冷却时间
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.item_cooldown_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.item_cooldown_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.seconds"), b -> {}).bounds(centerX + 55, y, 30, 20).build());
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.item_cooldown_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case BLOCK_COOLDOWN -> {
                    // 方块冷却
                    addRenderableWidget(new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.block_cooldown")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.block_cooldown"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9]*"));
                    }
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.seconds"), b -> {}).bounds(centerX + 55, y, 30, 20).build());
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.block_cooldown_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case CLEAR_ENTITIES -> {
                    // 清除实体范围
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.clear_entities_hint")));
                    valueInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.clear_entities_hint"));
                    if (valueInput != null) {
                        valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    }
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.blocks"), b -> {}).bounds(centerX - 15, y, 30, 20).build());

                    y += 25;
                    addRenderableWidget(new EditBox(this.font, centerX - 10, y, 110, 20,
                            Component.translatable("gui.entity_interaction_block.entity_id")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.entity_id"));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.clear_entities_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case ADD_CUSTOM_TASK -> {
                    // 任务名称
                    addRenderableWidget(new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.custom_task_name")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.custom_task_name"));

                    y += 25;
                    // 任务ID (使用固定文本以便识别)
                    addRenderableWidget(new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.literal("custom_task_id")));
                    EditBox taskIdInput = findAndAttachInput(Component.literal("custom_task_id"));
                    if (taskIdInput != null) {
                        taskIdInput.setValue("custom_" + System.currentTimeMillis() % 10000);
                    }
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.add_custom_task_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case COMPLETE_CUSTOM_TASK -> {
                    // 任务ID (使用固定文本以便识别)
                    addRenderableWidget(new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.literal("custom_task_id")));
                    stringInput = findAndAttachInput(Component.literal("custom_task_id"));
                    y += 22;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.complete_custom_task_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                case NARRATOR -> {
                    // 语音播报文本
                    addRenderableWidget(new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.narrator_text")));
                    stringInput = findAndAttachInput(Component.translatable("gui.entity_interaction_block.narrator_text"));
                    if (stringInput != null) {
                        stringInput.setMaxLength(500);
                    }

                    y += 25;
                    // 是否打断当前播报
                    addRenderableWidget(CycleButton.<Boolean>builder(interrupt ->
                                    Component.translatable(interrupt ?
                                            "gui.entity_interaction_block.narrator_interrupt" :
                                            "gui.entity_interaction_block.narrator_queue"))
                            .withValues(true, false)
                            .withInitialValue(narratorInterrupt)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.narrator_mode"),
                                    (b, interrupt) -> narratorInterrupt = interrupt));

                    y += 25;
                    addRenderableWidget(Button.builder(
                            Component.translatable("gui.entity_interaction_block.narrator_desc"), b -> {})
                            .bounds(centerX - 100, y, 200, 15).build());
                }
                // 其他类型不需要输入
            }

            // 确认按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.confirm"),
                    b -> confirm()).bounds(centerX - 105, this.height - 40, 100, 20).build());

            // 取消按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.cancel"),
                    b -> this.minecraft.setScreen(parent)).bounds(centerX + 5, this.height - 40, 100, 20).build());
        }

        private EditBox findAndAttachInput(Component message) {
            for (var widget : this.children()) {
                if (widget instanceof EditBox box && box.getMessage().getString().equals(message.getString())) {
                    return box;
                }
            }
            return null;
        }

        private void confirm() {
            EntityInteractionBlockEntity.TriggerAction action = new EntityInteractionBlockEntity.TriggerAction();
            action.type = selectedType;

            // 处理分秒输入
            if (selectedType == EntityInteractionBlockEntity.ActionType.ADD_TIME ||
                    selectedType == EntityInteractionBlockEntity.ActionType.SET_TIME) {
                int minutes = 0;
                int seconds = 0;
                if (minutesInput != null && !minutesInput.getValue().isEmpty()) {
                    try {
                        minutes = Integer.parseInt(minutesInput.getValue());
                    } catch (NumberFormatException ignored) {}
                }
                if (secondsInput != null && !secondsInput.getValue().isEmpty()) {
                    try {
                        seconds = Integer.parseInt(secondsInput.getValue());
                    } catch (NumberFormatException ignored) {}
                }
                action.value = minutes + (seconds / 60.0);

                // 设置增加/减少模式
                if (selectedType == EntityInteractionBlockEntity.ActionType.ADD_TIME) {
                    action.stringValue = addTimeMode ? "add" : "subtract";
                }
            } else if (valueInput != null && !valueInput.getValue().isEmpty()) {
                try {
                    if (selectedType == EntityInteractionBlockEntity.ActionType.ENABLE_COLLISION &&
                            valueInput.getValue().equals("*")) {
                        action.value = -1; // 无限时间
                    } else {
                        action.value = Double.parseDouble(valueInput.getValue());
                    }
                } catch (NumberFormatException e) {
                    action.value = 0;
                }
            }

            if (stringInput != null) {
                action.stringValue = stringInput.getValue();
            }

            // 保存任务类型
            if (selectedType == EntityInteractionBlockEntity.ActionType.CHANGE_TASK) {
                action.taskType = selectedTaskType;
            }

            // 保存效果参数
            if (selectedType == EntityInteractionBlockEntity.ActionType.GIVE_EFFECT) {
                for (var widget : this.children()) {
                    if (widget instanceof EditBox box && box.getMessage().getString().contains("duration")) {
                        try {
                            action.effectDuration = Integer.parseInt(box.getValue());
                        } catch (NumberFormatException e) {
                            action.effectDuration = 10;
                        }
                    }
                    if (widget instanceof EditBox box && box.getMessage().getString().contains("amplifier")) {
                        try {
                            action.effectAmplifier = Integer.parseInt(box.getValue());
                        } catch (NumberFormatException e) {
                            action.effectAmplifier = 0;
                        }
                    }
                }
            }

            // 保存自定义任务参数
            if (selectedType == EntityInteractionBlockEntity.ActionType.ADD_CUSTOM_TASK) {
                if (stringInput != null) {
                    action.customTaskName = stringInput.getValue();
                }
                for (var widget : this.children()) {
                    if (widget instanceof EditBox box && box.getMessage().getString().contains("custom_task_id")) {
                        action.customTaskId = box.getValue();
                    }
                }
            }

            if (selectedType == EntityInteractionBlockEntity.ActionType.COMPLETE_CUSTOM_TASK) {
                if (stringInput != null) {
                    action.customTaskId = stringInput.getValue();
                }
            }

            // 保存语音播报参数
            if (selectedType == EntityInteractionBlockEntity.ActionType.NARRATOR) {
                if (stringInput != null) {
                    action.narratorText = stringInput.getValue();
                }
                action.narratorInterrupt = narratorInterrupt;
            }

            parent.addAction(action);
            this.minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
            if (verticalScroll > 0) {
                scrollY = Math.max(0, scrollY - SCROLL_STEP);
                this.init();
            } else if (verticalScroll < 0) {
                scrollY += SCROLL_STEP;
                this.init();
            }
            return true;
        }
    }

    // 传送点设置子界面
    private class TeleportPointScreen extends Screen {
        private final EntityInteractionBlockScreen parent;
        private boolean isTeleport;
        private EditBox idInput;

        public TeleportPointScreen(EntityInteractionBlockScreen parent) {
            super(Component.translatable("gui.entity_interaction_block.teleport_point_title"));
            this.parent = parent;
            this.isTeleport = parent.isTeleportPoint;
        }

        @Override
        protected void init() {
            super.init();
            this.clearWidgets();

            int centerX = this.width / 2;
            int y = 60;

            // 是否是传送点
            addRenderableWidget(CycleButton.<Boolean>builder(tp ->
                            Component.translatable(tp ? "gui.entity_interaction_block.yes" : "gui.entity_interaction_block.no"))
                    .withValues(true, false)
                    .withInitialValue(isTeleport)
                    .create(centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.is_teleport_point"),
                            (b, tp) -> {
                                isTeleport = tp;
                                this.init();
                            }));

            y += 40;

            // 传送点ID
            if (isTeleport) {
                idInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                        Component.translatable("gui.entity_interaction_block.teleport_id_input"));
                idInput.setFilter(s -> s.matches("[0-9]*"));
                idInput.setValue(parent.teleportPointId > 0 ? String.valueOf(parent.teleportPointId) : "");
                addRenderableWidget(idInput);
            }

            // 确认按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.confirm"),
                    b -> confirm()).bounds(centerX - 105, this.height - 40, 100, 20).build());

            // 取消按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.cancel"),
                    b -> this.minecraft.setScreen(parent)).bounds(centerX + 5, this.height - 40, 100, 20).build());
        }

        private void confirm() {
            int id = -1;
            if (isTeleport && idInput != null && !idInput.getValue().isEmpty()) {
                try {
                    id = Integer.parseInt(idInput.getValue());
                } catch (NumberFormatException e) {
                    id = -1;
                }
            }
            parent.setTeleportPoint(isTeleport, id);
            this.minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
