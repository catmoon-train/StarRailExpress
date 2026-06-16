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
