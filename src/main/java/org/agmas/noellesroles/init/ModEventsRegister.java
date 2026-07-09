package org.agmas.noellesroles.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.events.*;

import java.util.*;

/**
 * 事件注册入口 - 委托给各分类事件处理器
 */
public class ModEventsRegister {

    // ==================== 兼容字段（保持外部引用可用） ====================

    public static boolean isMJVerifyEnabled = false;
    public static List<Item> canThrowItems = new ArrayList<>();
    public static final int TRACK_DISTANCE = 8;

    // ==================== 事件注册 ====================

    public static void registerEvents() {
        NRDeathEvents.register();
        NRCombatEvents.register();
        NRGameStateEvents.register();
        NRInteractionEvents.register();
    }

    public static void registerPredicate() {
        NRRulePredicateEvents.register();

        canThrowItems = NRRulePredicateEvents.canThrowItems;
    }

    // ==================== 兼容方法（外部引用） ====================

    public static void reJudgeSpectatorsPenalty(Level level) {
        NRDeathEvents.reJudgeSpectatorsPenalty(level);
    }
}
