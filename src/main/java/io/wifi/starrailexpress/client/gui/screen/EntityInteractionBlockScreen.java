package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.SRE;
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

        int conditionsStartY = HEADER_HEIGHT + SECTION_TITLE_HEIGHT + 10;
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
            case TIMER -> Component.translatable("condition.timer", condition.value).getString();
            case TIME_ANCHOR -> Component.translatable("condition.time_anchor", condition.value,
                    Component.translatable("comparison." + condition.comparison.name().toLowerCase())).getString();
            case PROXIMITY_SPHERE -> Component.translatable("condition.proximity_sphere", condition.value).getString();
            case PROXIMITY_LINE -> Component.translatable("condition.proximity_line", condition.value).getString();
            case HAS_ITEM -> Component.translatable("condition.has_item", condition.stringValue).getString();
            case CLICK_BLOCK -> Component.translatable(condition.leftClick ? "condition.click_left" : "condition.click_right").getString();
            case LOOKING_AT -> Component.translatable("condition.looking_at", condition.value).getString();
            case STANDING_ON_BLOCK -> Component.translatable("condition.standing_on_block", condition.value, condition.stringValue).getString();
            case DEATH -> Component.translatable("condition.death", condition.stringValue).getString();
            case USE_ITEM -> Component.translatable("condition.use_item", condition.value).getString();
            case SPEAK -> Component.translatable("condition.speak", condition.value).getString();
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

    private String getActionDisplayText(EntityInteractionBlockEntity.TriggerAction action) {
        return switch (action.type) {
            case EXECUTE_COMMAND -> Component.translatable("action.execute_command", action.stringValue).getString();
            case POISON -> Component.translatable("action.poison", action.value).getString();
            case CURE_POISON -> Component.translatable("action.cure_poison").getString();
            case SET_SHIELD -> Component.translatable("action.set_shield", (int) action.value).getString();
            case DAMAGE_DEATH -> Component.translatable("action.damage_death", action.stringValue).getString();
            case FORCE_KILL -> Component.translatable("action.force_kill", action.stringValue).getString();
            case ENABLE_COLLISION -> Component.translatable("action.enable_collision", action.value).getString();
            case MOOD_CHANGE -> Component.translatable("action.mood_change", action.value).getString();
            case CHANGE_ROLE -> Component.translatable("action.change_role", action.stringValue).getString();
            case CHANGE_TASK -> {
                String taskType = action.taskType != null ? action.taskType : "random";
                yield Component.translatable("action.change_task", Component.translatable("task_type." + taskType)).getString();
            }
            case RESURRECT -> Component.translatable("action.resurrect", action.value).getString();
            case PSYCHO_MODE -> Component.translatable("action.psycho_mode").getString();
            case BLACKOUT -> Component.translatable("action.blackout").getString();
            case MONITOR_BROKEN -> Component.translatable("action.monitor_broken").getString();
            case ADD_TIME -> Component.translatable("action.add_time", action.value).getString();
            case SET_TIME -> Component.translatable("action.set_time", action.value).getString();
            case GAME_WIN -> Component.translatable("action.game_win", action.stringValue).getString();
            case COIN_CHANGE -> Component.translatable("action.coin_change", (int) action.value).getString();
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
        private EntityInteractionBlockEntity.ComparisonType selectedComparison = EntityInteractionBlockEntity.ComparisonType.EQUALS;
        private EntityInteractionBlockEntity.TeamType selectedTeam = EntityInteractionBlockEntity.TeamType.CIVILIAN;
        private boolean leftClick = false;

        public AddConditionScreen(EntityInteractionBlockScreen parent) {
            super(Component.translatable("gui.entity_interaction_block.add_condition"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            this.clearWidgets();

            int centerX = this.width / 2;

            // 条件类型选择
            addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ConditionType>builder(type ->
                            Component.translatable("condition_type." + type.name().toLowerCase()))
                    .withValues(EntityInteractionBlockEntity.ConditionType.values())
                    .withInitialValue(selectedType)
                    .create(centerX - 100, 40, 200, 20,
                            Component.translatable("gui.entity_interaction_block.condition_type"),
                            (b, type) -> {
                                selectedType = type;
                                this.init(); // 刷新界面显示对应输入框
                            }));

            int y = 80;

            // 根据条件类型显示不同的输入框
            switch (selectedType) {
                case TIMER, PROXIMITY_SPHERE, PROXIMITY_LINE, LOOKING_AT, USE_ITEM, SPEAK -> {
                    // 需要数值输入
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.value"));
                    valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    addRenderableWidget(valueInput);
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.seconds"),
                            b -> {}).bounds(centerX + 55, y, 40, 20).build());
                }
                case TIME_ANCHOR -> {
                    // 时间锚点需要数值和比较类型
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.time_value"));
                    valueInput.setFilter(s -> s.matches("[0-9.:]*"));
                    addRenderableWidget(valueInput);

                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y + 30, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case HAS_ITEM, ROLE_IS -> {
                    // 需要ID输入
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.id"));
                    addRenderableWidget(stringInput);
                }
                case CLICK_BLOCK -> {
                    // 左键/右键选择
                    addRenderableWidget(CycleButton.<Boolean>builder(left ->
                                    Component.translatable(left ? "gui.entity_interaction_block.left_click" : "gui.entity_interaction_block.right_click"))
                            .withValues(true, false)
                            .withInitialValue(leftClick)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.click_type"),
                                    (b, left) -> leftClick = left));
                }
                case STANDING_ON_BLOCK -> {
                    // 需要范围和方块ID
                    valueInput = new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.radius"));
                    valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    addRenderableWidget(valueInput);

                    stringInput = new EditBox(this.font, centerX - 10, y, 110, 20,
                            Component.translatable("gui.entity_interaction_block.block_id"));
                    addRenderableWidget(stringInput);
                }
                case DEATH -> {
                    // 死亡原因选择
                    addRenderableWidget(CycleButton.<String>builder(reason ->
                                    Component.literal(reason.equals("*") ? "* (任意)" : reason))
                            .withValues(DEATH_REASONS)
                            .withInitialValue("*")
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.death_reason"),
                                    (b, reason) -> {
                                        if (stringInput == null) {
                                            stringInput = new EditBox(font, 0, 0, 0, 0, Component.empty());
                                        }
                                        stringInput.setValue(reason);
                                    }));
                }
                case COIN_AMOUNT -> {
                    // 金币数量
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.coin_amount"));
                    valueInput.setFilter(s -> s.matches("[0-9]*"));
                    addRenderableWidget(valueInput);
                }
                case ROLE_TEAM -> {
                    // 阵营选择
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.TeamType>builder(team ->
                                    Component.translatable("team." + team.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.TeamType.values())
                            .withInitialValue(selectedTeam)
                            .create(centerX - 100, y, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.team"),
                                    (b, team) -> selectedTeam = team));
                }
                case PLAYER_COUNT, ALIVE_PLAYERS -> {
                    // 玩家数量条件
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.player_count"));
                    valueInput.setFilter(s -> s.matches("[0-9]*"));
                    addRenderableWidget(valueInput);

                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 100, y + 30, 200, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                case IS_SNEAKING, IS_SPRINTING, HAS_KILLED -> {
                    // 不需要额外输入
                }
                case HAS_EFFECT -> {
                    // 效果ID输入
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.effect_id"));
                    addRenderableWidget(stringInput);
                }
                case PROBABILITY -> {
                    // 概率输入（0-100）
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.probability"));
                    valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    addRenderableWidget(valueInput);
                    addRenderableWidget(Button.builder(Component.literal("%"), b -> {}).bounds(centerX + 55, y, 20, 20).build());
                }
                case WORLD_TIME -> {
                    // 世界时间类型选择
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
                    // 实体数量条件：需要范围、实体ID、数量（或*）
                    valueInput = new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.radius"));
                    valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    addRenderableWidget(valueInput);

                    stringInput = new EditBox(this.font, centerX - 10, y, 110, 20,
                            Component.translatable("gui.entity_interaction_block.entity_id"));
                    addRenderableWidget(stringInput);

                    // 数量输入（支持*）
                    EditBox countInput = new EditBox(this.font, centerX - 100, y + 25, 80, 20,
                            Component.translatable("gui.entity_interaction_block.entity_count"));
                    countInput.setValue("*");
                    addRenderableWidget(countInput);

                    // 比较类型选择
                    addRenderableWidget(CycleButton.<EntityInteractionBlockEntity.ComparisonType>builder(comp ->
                                    Component.translatable("comparison." + comp.name().toLowerCase()))
                            .withValues(EntityInteractionBlockEntity.ComparisonType.values())
                            .withInitialValue(selectedComparison)
                            .create(centerX - 10, y + 25, 110, 20,
                                    Component.translatable("gui.entity_interaction_block.comparison"),
                                    (b, comp) -> selectedComparison = comp));
                }
                // PASS_THROUGH 不需要额外输入
                case NEED_TASK_TYPE -> {
                    // 需要完成特定类型任务 - 选择任务类型
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
                    // 需要完成自定义任务 - 填写任务ID
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.custom_task_id"));
                    addRenderableWidget(stringInput);
                }
            }

            // 确认按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.confirm"),
                    b -> confirm()).bounds(centerX - 105, this.height - 40, 100, 20).build());

            // 取消按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.cancel"),
                    b -> this.minecraft.setScreen(parent)).bounds(centerX + 5, this.height - 40, 100, 20).build());
        }

        private void confirm() {
            EntityInteractionBlockEntity.TriggerCondition condition = new EntityInteractionBlockEntity.TriggerCondition();
            condition.type = selectedType;

            if (valueInput != null && !valueInput.getValue().isEmpty()) {
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
                // 查找数量输入框
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
    }

    // 添加触发内容子界面
    private class AddActionScreen extends Screen {
        private final EntityInteractionBlockScreen parent;
        private EntityInteractionBlockEntity.ActionType selectedType = EntityInteractionBlockEntity.ActionType.EXECUTE_COMMAND;
        private EditBox valueInput;
        private EditBox stringInput;
        private String selectedTaskType = "random"; // 默认随机任务

        public AddActionScreen(EntityInteractionBlockScreen parent) {
            super(Component.translatable("gui.entity_interaction_block.add_action"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            this.clearWidgets();

            int centerX = this.width / 2;

            // 触发内容类型选择
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

            int y = 80;

            // 根据触发内容类型显示不同的输入框
            switch (selectedType) {
                case EXECUTE_COMMAND -> {
                    // 指令输入
                    stringInput = new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.command"));
                    stringInput.setMaxLength(500);
                    addRenderableWidget(stringInput);
                    addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.command_hint"),
                            b -> {}).bounds(centerX - 150, y + 25, 300, 15).build());
                }
                case POISON, ENABLE_COLLISION -> {
                    // 需要秒数（*代表无限）
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.seconds_star"));
                    addRenderableWidget(valueInput);
                }
                case SET_SHIELD -> {
                    // 护盾层数
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.shield_layers"));
                    valueInput.setFilter(s -> s.matches("[0-9]*"));
                    addRenderableWidget(valueInput);
                }
                case DAMAGE_DEATH, FORCE_KILL -> {
                    // 死亡原因（可选）
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.death_reason_optional"));
                    addRenderableWidget(stringInput);
                }
                case MOOD_CHANGE -> {
                    // 心情值变化（-1到1）
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.mood_value"));
                    valueInput.setFilter(s -> s.matches("-?[0-9.]*"));
                    addRenderableWidget(valueInput);
                }
                case CHANGE_ROLE -> {
                    // 职业ID（或random）
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.role_id"));
                    addRenderableWidget(stringInput);
                }
                case CHANGE_TASK -> {
                    // 任务类型选择
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
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.resurrect_radius"));
                    valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    addRenderableWidget(valueInput);
                }
                case ADD_TIME, SET_TIME -> {
                    // 时间（分钟:秒 格式）
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.time_minutes"));
                    valueInput.setFilter(s -> s.matches("[0-9.:]*"));
                    addRenderableWidget(valueInput);
                }
                case GAME_WIN -> {
                    // 胜利标语
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.win_message"));
                    addRenderableWidget(stringInput);
                }
                case COIN_CHANGE -> {
                    // 金币数量（负数表示减少）
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.coin_amount_signed"));
                    valueInput.setFilter(s -> s.matches("-?[0-9]*"));
                    addRenderableWidget(valueInput);
                }
                case GIVE_EFFECT -> {
                    // 效果ID输入
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.effect_id"));
                    addRenderableWidget(stringInput);

                    // 持续时间
                    valueInput = new EditBox(this.font, centerX - 100, y + 25, 80, 20,
                            Component.translatable("gui.entity_interaction_block.effect_duration"));
                    valueInput.setFilter(s -> s.matches("[0-9]*"));
                    valueInput.setValue("10");
                    addRenderableWidget(valueInput);

                    // 等级
                    EditBox amplifierInput = new EditBox(this.font, centerX + 10, y + 25, 50, 20,
                            Component.translatable("gui.entity_interaction_block.effect_amplifier"));
                    amplifierInput.setFilter(s -> s.matches("[0-9]*"));
                    amplifierInput.setValue("0");
                    addRenderableWidget(amplifierInput);
                }
                case TELEPORT -> {
                    // 传送点ID
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.teleport_id"));
                    valueInput.setFilter(s -> s.matches("[0-9]*"));
                    addRenderableWidget(valueInput);
                }
                case SHOW_TITLE -> {
                    // 标题文本
                    stringInput = new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.title_text"));
                    addRenderableWidget(stringInput);
                }
                case BROADCAST_MESSAGE -> {
                    // 广播消息
                    stringInput = new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.broadcast_message"));
                    addRenderableWidget(stringInput);
                }
                case ITEM_COOLDOWN -> {
                    // 物品ID
                    stringInput = new EditBox(this.font, centerX - 100, y, 120, 20,
                            Component.translatable("gui.entity_interaction_block.item_id"));
                    stringInput.setValue("*");
                    addRenderableWidget(stringInput);

                    // 冷却时间
                    valueInput = new EditBox(this.font, centerX + 30, y, 70, 20,
                            Component.translatable("gui.entity_interaction_block.cooldown_seconds"));
                    valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    addRenderableWidget(valueInput);
                }
                case BLOCK_COOLDOWN -> {
                    // 方块冷却时间
                    valueInput = new EditBox(this.font, centerX - 50, y, 100, 20,
                            Component.translatable("gui.entity_interaction_block.block_cooldown"));
                    valueInput.setFilter(s -> s.matches("[0-9]*"));
                    addRenderableWidget(valueInput);
                }
                case CLEAR_ENTITIES -> {
                    // 清除实体：需要范围和实体ID
                    valueInput = new EditBox(this.font, centerX - 100, y, 80, 20,
                            Component.translatable("gui.entity_interaction_block.radius"));
                    valueInput.setFilter(s -> s.matches("[0-9.]*"));
                    addRenderableWidget(valueInput);

                    stringInput = new EditBox(this.font, centerX - 10, y, 110, 20,
                            Component.translatable("gui.entity_interaction_block.entity_id"));
                    addRenderableWidget(stringInput);
                }
                // CURE_POISON, PSYCHO_MODE, BLACKOUT, MONITOR_BROKEN 不需要额外输入
                case ADD_CUSTOM_TASK -> {
                    // 添加自定义任务：任务名称和任务ID
                    stringInput = new EditBox(this.font, centerX - 150, y, 300, 20,
                            Component.translatable("gui.entity_interaction_block.custom_task_name"));
                    addRenderableWidget(stringInput);

                    EditBox taskIdInput = new EditBox(this.font, centerX - 150, y + 25, 300, 20,
                            Component.translatable("gui.entity_interaction_block.custom_task_id"));
                    addRenderableWidget(taskIdInput);
                }
                case COMPLETE_CUSTOM_TASK -> {
                    // 完成自定义任务：填写任务ID
                    stringInput = new EditBox(this.font, centerX - 100, y, 200, 20,
                            Component.translatable("gui.entity_interaction_block.custom_task_id"));
                    addRenderableWidget(stringInput);
                }
            }

            // 确认按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.confirm"),
                    b -> confirm()).bounds(centerX - 105, this.height - 40, 100, 20).build());

            // 取消按钮
            addRenderableWidget(Button.builder(Component.translatable("gui.entity_interaction_block.cancel"),
                    b -> this.minecraft.setScreen(parent)).bounds(centerX + 5, this.height - 40, 100, 20).build());
        }

        private void confirm() {
            EntityInteractionBlockEntity.TriggerAction action = new EntityInteractionBlockEntity.TriggerAction();
            action.type = selectedType;

            if (valueInput != null && !valueInput.getValue().isEmpty()) {
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

            // 保存任务类型（用于CHANGE_TASK）
            if (selectedType == EntityInteractionBlockEntity.ActionType.CHANGE_TASK) {
                action.taskType = selectedTaskType;
            }

            // 保存效果参数（用于GIVE_EFFECT）
            if (selectedType == EntityInteractionBlockEntity.ActionType.GIVE_EFFECT) {
                // 查找效果持续时间输入框
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

            // 保存自定义任务参数（用于ADD_CUSTOM_TASK）
            if (selectedType == EntityInteractionBlockEntity.ActionType.ADD_CUSTOM_TASK) {
                // 第一个输入框是任务名称（stringInput）
                if (stringInput != null) {
                    action.customTaskName = stringInput.getValue();
                }
                // 查找任务ID输入框
                for (var widget : this.children()) {
                    if (widget instanceof EditBox box && box.getMessage().getString().contains("custom_task_id")) {
                        action.customTaskId = box.getValue();
                    }
                }
            }

            // 保存自定义任务ID（用于COMPLETE_CUSTOM_TASK）
            if (selectedType == EntityInteractionBlockEntity.ActionType.COMPLETE_CUSTOM_TASK) {
                if (stringInput != null) {
                    action.customTaskId = stringInput.getValue();
                }
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

            // 是否是传送点选择
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

            // 传送点ID输入（仅在是传送点时显示）
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
