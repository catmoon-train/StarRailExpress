package io.wifi.starrailexpress.content.minigame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小游戏注册表（纯服务端安全，不引用任何客户端类）
 * 所有小游戏任务点可用的小游戏都在这里注册
 */
public class QuestMinigames {

    private static final Map<String, QuestMinigame> REGISTRY = new LinkedHashMap<>();

    /** 撬锁小游戏 */
    public static final QuestMinigame LOCKPICK = register(
            QuestMinigame.of("lockpick", "minigame.starrailexpress.lockpick"));

    /** 配钥匙小游戏 */
    public static final QuestMinigame KEY_MAKING = register(
            QuestMinigame.of("key_making", "minigame.starrailexpress.key_making"));

    /** 数学题小游戏 */
    public static final QuestMinigame MATH = register(
            QuestMinigame.of("math", "minigame.starrailexpress.math"));

    /** 烹饪小游戏 */
    public static final QuestMinigame COOKING = register(
            QuestMinigame.of("cooking", "minigame.starrailexpress.cooking"));

    public static final QuestMinigame REACTOR_TEMPERATURE = register(
            QuestMinigame.of("reactor_temperature", "minigame.starrailexpress.reactor_temperature"));
    public static final QuestMinigame BOX_SORT = register(
            QuestMinigame.of("box_sort", "minigame.starrailexpress.box_sort"));
    public static final QuestMinigame WIRE_CONNECT = register(
            QuestMinigame.of("wire_connect", "minigame.starrailexpress.wire_connect"));
    public static final QuestMinigame HOLD_BUTTON = register(
            QuestMinigame.of("hold_button", "minigame.starrailexpress.hold_button"));
    public static final QuestMinigame CLEAN_DEBRIS = register(
            QuestMinigame.of("clean_debris", "minigame.starrailexpress.clean_debris"));
    public static final QuestMinigame SHOOTING = register(
            QuestMinigame.of("shooting", "minigame.starrailexpress.shooting"));
    public static final QuestMinigame WIRE_TUNING = register(
            QuestMinigame.of("wire_tuning", "minigame.starrailexpress.wire_tuning"));
    public static final QuestMinigame SIGNAL_CALIBRATION = register(
            QuestMinigame.of("signal_calibration", "minigame.starrailexpress.signal_calibration"));
    public static final QuestMinigame SHAPE_MATCH = register(
            QuestMinigame.of("shape_match", "minigame.starrailexpress.shape_match"));
    public static final QuestMinigame SEQUENCE_BUTTONS = register(
            QuestMinigame.of("sequence_buttons", "minigame.starrailexpress.sequence_buttons"));
    public static final QuestMinigame CLEAN_STAINS = register(
            QuestMinigame.of("clean_stains", "minigame.starrailexpress.clean_stains"));
    public static final QuestMinigame PULL_LEVER = register(
            QuestMinigame.of("pull_lever", "minigame.starrailexpress.pull_lever"));
    public static final QuestMinigame TRASH_RECYCLE = register(
            QuestMinigame.of("trash_recycle", "minigame.starrailexpress.trash_recycle"));
    public static final QuestMinigame ITEM_CHECKLIST = register(
            QuestMinigame.of("item_checklist", "minigame.starrailexpress.item_checklist"));
    public static final QuestMinigame SWIPE_CARD = register(
            QuestMinigame.of("swipe_card", "minigame.starrailexpress.swipe_card"));
    public static final QuestMinigame PLAY_MUSIC = register(
            QuestMinigame.of("play_music", "minigame.starrailexpress.play_music"));
    public static final QuestMinigame RHYTHM = register(
            QuestMinigame.of("rhythm", "minigame.starrailexpress.rhythm"));
    public static final QuestMinigame LIGHT_CANDLES = register(
            QuestMinigame.of("light_candles", "minigame.starrailexpress.light_candles"));
    public static final QuestMinigame WHACK_MOLE = register(
            QuestMinigame.of("whack_mole", "minigame.starrailexpress.whack_mole"));
    public static final QuestMinigame PUZZLE = register(
            QuestMinigame.of("puzzle", "minigame.starrailexpress.puzzle"));
    public static final QuestMinigame STORAGE = register(
            QuestMinigame.of("storage", "minigame.starrailexpress.storage"));
    public static final QuestMinigame SLICE_FOOD = register(
            QuestMinigame.of("slice_food", "minigame.starrailexpress.slice_food"));
    public static final QuestMinigame THREE_CARDS = register(
            QuestMinigame.of("three_cards", "minigame.starrailexpress.three_cards"));
    public static final QuestMinigame BREAK_JAR = register(
            QuestMinigame.of("break_jar", "minigame.starrailexpress.break_jar"));
    public static final QuestMinigame ZONE_CALIBRATION = register(
            QuestMinigame.of("zone_calibration", "minigame.starrailexpress.zone_calibration"));

    // ══════════════════════════════════════════════
    // 注册方法
    // ══════════════════════════════════════════════

    private static QuestMinigame register(QuestMinigame minigame) {
        REGISTRY.put(minigame.id(), minigame);
        return minigame;
    }

    /** 根据ID获取小游戏信息 */
    public static QuestMinigame get(String id) {
        return REGISTRY.get(id);
    }

    /** 获取所有已注册的小游戏列表 */
    public static List<QuestMinigame> getAll() {
        return new ArrayList<>(REGISTRY.values());
    }

    /** 获取默认小游戏ID */
    public static String getDefaultId() {
        return REGISTRY.isEmpty() ? "" : REGISTRY.keySet().iterator().next();
    }
}
